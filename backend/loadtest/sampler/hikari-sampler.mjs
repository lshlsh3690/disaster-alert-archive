// 부하 중 DB 커넥션 점유와 번역 코얼레서 카운터를 샘플링한다.
//
// 번역 요청 병합(single-flight) 전후 비교에서 두 번째 지표를 재는 도구다.
// 코얼레서 도입과 함께 @Transactional 을 걷어내기 전에는, 번역(OpenAI, 1~2초) 내내
// DB 커넥션을 쥐고 있었다. 동시 요청 N건이면 커넥션 N개가 그동안 묶이고, 풀 크기를
// 넘어서면 번역과 무관한 조회까지 대기한다. 그 차이를 눈으로 보기 위한 것이다.
//
// actuator 는 인증이 필요하다(SecurityConfig 의 anyRequest().authenticated()).
// 그래서 먼저 로그인해 httpOnly 쿠키를 받아 들고 폴링한다. 계정 준비는
// backend/loadtest/README.md 의 "사전 준비" 절 참고.
//
// 실행:
//   node backend/loadtest/sampler/hikari-sampler.mjs
//   BASE=http://localhost:18080 INTERVAL_MS=100 DURATION_S=30 node backend/loadtest/sampler/hikari-sampler.mjs
//
// 쓰는 순서: 샘플러를 먼저 띄우고 → k6 를 돌리고 → 샘플러가 끝나며 찍는 요약을 읽는다.

const BASE = process.env.BASE ?? 'http://localhost:18080';
const EMAIL = process.env.PERF_EMAIL ?? 'perftest@local.test';
const PASSWORD = process.env.PERF_PASSWORD ?? 'perftest1234';
const INTERVAL_MS = Number(process.env.INTERVAL_MS ?? 100);
const DURATION_S = Number(process.env.DURATION_S ?? 30);

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

let cookie = '';

async function login() {
    const res = await fetch(`${BASE}/api/v1/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: EMAIL, password: PASSWORD }),
    });
    if (!res.ok) {
        throw new Error(`로그인 실패 ${res.status} — README 의 로컬 테스트 계정을 먼저 만든다`);
    }
    // httpOnly 쿠키라 본문이 아니라 Set-Cookie 헤더에서 꺼낸다. 여러 개면 모두 들고 간다.
    const raw = res.headers.getSetCookie?.() ?? [];
    cookie = raw.map((c) => c.split(';')[0]).join('; ');
    if (!cookie) {
        throw new Error('Set-Cookie 가 비어 있다 — 인증 쿠키 설정을 확인한다');
    }
}

/** actuator 의 단일 값 메트릭을 읽는다. 없으면 null (지표가 아직 한 번도 안 찍힌 경우). */
async function metric(name) {
    const res = await fetch(`${BASE}/actuator/metrics/${name}`, { headers: { Cookie: cookie } });
    if (!res.ok) {
        return null;
    }
    const body = await res.json();
    return body?.measurements?.[0]?.value ?? null;
}

function summarize(label, samples) {
    if (samples.length === 0) {
        return `${label}: 표본 없음`;
    }
    const max = Math.max(...samples);
    const avg = samples.reduce((a, b) => a + b, 0) / samples.length;
    return `${label}: 최대 ${max}, 평균 ${avg.toFixed(2)} (표본 ${samples.length}개)`;
}

/**
 * 커넥션·초 — 점유한 커넥션 수를 시간으로 적분한 값이다.
 *
 * 최대값만 보면 전후가 잘 구분되지 않는다. 짧은 조회도 동시에 10개가 겹치면 최대 10 이 찍히기
 * 때문이다. 핵심은 "몇 개를 얼마나 오래 쥐었나" 이므로 면적을 본다.
 * 예: 커넥션 10개를 1.4초 동안 쥐면 14.0, 같은 10개를 0.05초만 쥐면 0.5 다.
 */
function connectionSeconds(samples, intervalMs) {
    const area = samples.reduce((a, b) => a + b, 0) * intervalMs;
    return area / 1000;
}

/** 커넥션을 하나라도 쥐고 있던 구간의 길이(ms). 번역이 도는 동안 묶여 있던 시간에 해당한다. */
function busyMs(samples, intervalMs) {
    return samples.filter((v) => v > 0).length * intervalMs;
}

async function main() {
    await login();
    console.log(`로그인 성공. ${BASE} 를 ${INTERVAL_MS}ms 간격으로 ${DURATION_S}초 동안 본다.`);
    console.log('지금 다른 터미널에서 k6 를 실행한다.\n');

    const active = [];
    const pending = [];
    const before = {
        executed: (await metric('translation.coalesce.executed')) ?? 0,
        joined: (await metric('translation.coalesce.joined')) ?? 0,
        cacheHit: (await metric('translation.cache.hit')) ?? 0,
    };

    const endAt = Date.now() + DURATION_S * 1000;
    while (Date.now() < endAt) {
        const a = await metric('hikaricp.connections.active');
        const p = await metric('hikaricp.connections.pending');
        if (a !== null) {
            active.push(a);
        }
        if (p !== null) {
            pending.push(p);
        }
        // 점유가 올라간 순간만 찍는다 — 0 이 계속 흐르면 읽기 어렵다.
        if (a) {
            process.stdout.write(`\ractive=${a} pending=${p ?? 0}   `);
        }
        await sleep(INTERVAL_MS);
    }

    const after = {
        executed: (await metric('translation.coalesce.executed')) ?? 0,
        joined: (await metric('translation.coalesce.joined')) ?? 0,
        cacheHit: (await metric('translation.cache.hit')) ?? 0,
    };

    console.log('\n');
    console.log('========================================');
    console.log('DB 커넥션 (번역 중 점유가 핵심)');
    console.log('  ' + summarize('active ', active));
    console.log('  ' + summarize('pending', pending));
    console.log(`  점유 면적: ${connectionSeconds(active, INTERVAL_MS).toFixed(2)} 커넥션·초`);
    console.log(`  점유 구간: ${busyMs(active, INTERVAL_MS)}ms (active >= 1 이던 시간)`);
    console.log('----------------------------------------');
    console.log('번역 코얼레서 (이번 구간 증가분)');
    console.log(`  실행자(번역+저장 직접 수행): ${after.executed - before.executed}`);
    console.log(`  합류자(남의 결과 공유받음): ${after.joined - before.joined}`);
    console.log(`  캐시 적중(번역 경로 안 탐): ${after.cacheHit - before.cacheHit}`);
    console.log('========================================');
    console.log('해석');
    console.log('  점유 면적이 전후를 가르는 값이다. 최대값은 짧은 조회가 겹쳐도 올라가 잘 구분되지 않는다.');
    console.log('    @Transactional 이 붙어 있던 과거에는 번역(1~2초) 내내 커넥션을 쥐어 면적이 크다.');
    console.log('  합류자가 0 이면 요청이 겹치지 않은 것이다 — mock 의 DELAY_MS 를 늘려 다시 잰다.');
    console.log('  코얼레서가 없는 "전" 상태에서는 두 카운터가 아예 안 올라간다(그 코드엔 메트릭이 없다).');
}

main().catch((e) => {
    console.error(e.message);
    process.exit(1);
});
