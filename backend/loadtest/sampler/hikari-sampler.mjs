// 부하 중 DB 커넥션 점유와 번역 코얼레서 카운터를 샘플링한다.
//
// 번역 요청 병합(single-flight) 전후 비교에서 두 번째 지표를 재는 도구다.
// 코얼레서 도입과 함께 @Transactional 을 걷어내기 전에는, 번역(OpenAI, 1~2초) 내내
// DB 커넥션을 쥐고 있었다. 동시 요청 N건이면 커넥션 N개가 그동안 묶이고, 풀 크기(이 저장소는
// maximum-pool-size 20)를 넘어서면 번역과 무관한 조회까지 대기한다. 그 차이를 눈으로 보기
// 위한 것이다(실제로는 전후 점유가 같았다 — docs/PERF_TRANSLATION_COALESCING.md 지표 2).
//
// actuator 는 인증이 필요하다(SecurityConfig 의 anyRequest().authenticated()).
// 그래서 먼저 로그인해 httpOnly 쿠키를 받아 들고 폴링한다. 계정 준비는
// backend/loadtest/README.md 의 "사전 준비" 절 참고.
//
// 실행:
//   node backend/loadtest/sampler/hikari-sampler.mjs
//   BASE=http://localhost:8080 INTERVAL_MS=100 DURATION_S=30 node backend/loadtest/sampler/hikari-sampler.mjs
//
// 쓰는 순서: 샘플러를 먼저 띄우고 → k6 를 돌리고 → 샘플러가 끝나며 찍는 요약을 읽는다.

const BASE = process.env.BASE ?? 'http://localhost:8080';
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

/**
 * actuator 의 단일 값 메트릭을 읽는다.
 *
 * 401/403/500 같은 HTTP 오류는 "메트릭이 없다"가 아니라 "측정이 실패했다"이므로 항상 예외로 올린다.
 * 안 그러면 전부 0 으로 보고돼 측정 실패가 0 으로 읽힌다(예: prod 프로필은 metrics 를 노출하지 않는다).
 * 404 는 optional 일 때만 null 로 허용한다 — 코얼레서 카운터는 "전" 코드엔 아예 없는 정상 상황이다.
 * Hikari 메트릭은 필수라 404 도 실패다.
 */
async function metric(name, { optional = false } = {}) {
    const res = await fetch(`${BASE}/actuator/metrics/${name}`, { headers: { Cookie: cookie } });
    if (res.status === 404 && optional) {
        return null;
    }
    if (!res.ok) {
        throw new Error(`메트릭 ${name} 조회 실패 HTTP ${res.status} — 인증 쿠키, actuator 노출 설정, 대상 프로필을 확인한다`);
    }
    const body = await res.json();
    const value = body?.measurements?.[0]?.value;
    if (value === undefined && !optional) {
        throw new Error(`메트릭 ${name} 응답에 measurements 가 없다`);
    }
    return value ?? null;
}

/** 번역 카운터는 없으면 0 으로 센다(404 만 허용된 optional 조회). */
const counter = async (name) => (await metric(name, { optional: true })) ?? 0;

async function counters() {
    return {
        executed: await counter('translation.coalesce.executed'),
        joined: await counter('translation.coalesce.joined'),
        cacheHit: await counter('translation.cache.hit'),
    };
}

function summarize(label, points) {
    if (points.length === 0) {
        return `${label}: 표본 없음`;
    }
    const values = points.map((p) => p.v);
    const max = Math.max(...values);
    const avg = values.reduce((x, y) => x + y, 0) / values.length;
    return `${label}: 최대 ${max}, 평균 ${avg.toFixed(2)} (표본 ${values.length}개)`;
}

/**
 * 표본마다 단조 시계(performance.now) 타임스탬프를 같이 기록한다. 표본 간격은 INTERVAL_MS 에
 * 메트릭 HTTP 왕복 시간이 더해져 고정값이 아니므로, 고정 간격을 곱하면 점유가 과소 계산된다.
 * 각 표본의 값은 다음 표본이 찍히기 전까지 유지됐다고 보고(계단 적분) 실제 경과 시간을 곱한다.
 */
function intervals(points) {
    const out = [];
    for (let i = 0; i + 1 < points.length; i++) {
        out.push({ v: points[i].v, ms: points[i + 1].t - points[i].t });
    }
    return out;
}

/**
 * 커넥션·초 — 점유한 커넥션 수를 시간으로 적분한 값이다.
 *
 * 최대값만 보면 전후가 잘 구분되지 않는다. 짧은 조회도 동시에 10개가 겹치면 최대 10 이 찍히기
 * 때문이다. 핵심은 "몇 개를 얼마나 오래 쥐었나" 이므로 면적을 본다.
 * 예: 커넥션 10개를 1.4초 동안 쥐면 14.0, 같은 10개를 0.05초만 쥐면 0.5 다.
 */
function connectionSeconds(points) {
    return intervals(points).reduce((area, i) => area + i.v * i.ms, 0) / 1000;
}

/** 커넥션을 하나라도 쥐고 있던 구간의 길이(ms). 번역이 도는 동안 묶여 있던 시간에 해당한다. */
function busyMs(points) {
    return intervals(points)
        .filter((i) => i.v > 0)
        .reduce((total, i) => total + i.ms, 0);
}

async function main() {
    await login();

    // 기준 카운터와 종료 시각을 먼저 정한 뒤에 "k6 를 실행하라"고 알린다.
    // 순서가 반대면 기준값을 읽는 사이 k6 요청이 끝나 증가분이 실제보다 작게 나올 수 있다.
    const before = await counters();
    const endAt = Date.now() + DURATION_S * 1000;
    console.log(`로그인 성공. ${BASE} 를 ${INTERVAL_MS}ms 간격으로 ${DURATION_S}초 동안 본다.`);
    console.log('지금 다른 터미널에서 k6 를 실행한다.\n');

    const active = [];
    const pending = [];
    const sample = async () => {
        const t = performance.now();
        const a = await metric('hikaricp.connections.active');
        const p = await metric('hikaricp.connections.pending');
        active.push({ t, v: a });
        pending.push({ t, v: p });
        return { a, p };
    };

    while (Date.now() < endAt) {
        const { a, p } = await sample();
        // 점유가 올라간 순간만 찍는다 — 0 이 계속 흐르면 읽기 어렵다.
        if (a) {
            process.stdout.write(`\ractive=${a} pending=${p}   `);
        }
        await sleep(INTERVAL_MS);
    }
    // 마지막 표본이 차지한 구간까지 적분되도록 끝에서 한 번 더 찍는다.
    await sample();

    const after = await counters();

    console.log('\n');
    console.log('========================================');
    console.log('DB 커넥션 (번역 중 점유가 핵심)');
    console.log('  ' + summarize('active ', active));
    console.log('  ' + summarize('pending', pending));
    console.log(`  점유 면적: ${connectionSeconds(active).toFixed(2)} 커넥션·초 (표본 간 실제 경과 시간으로 적분)`);
    console.log(`  점유 구간: ${busyMs(active).toFixed(0)}ms (active >= 1 이던 시간)`);
    console.log('----------------------------------------');
    console.log('번역 코얼레서 (이번 구간 증가분)');
    console.log(`  실행자(번역+저장 직접 수행): ${after.executed - before.executed}`);
    console.log(`  합류자(남의 결과 공유받음): ${after.joined - before.joined}`);
    console.log(`  캐시 적중(번역 경로 안 탐): ${after.cacheHit - before.cacheHit}`);
    console.log('========================================');
    console.log('해석');
    console.log('  점유 면적이 전후를 가르는 값이다. 최대값은 짧은 조회가 겹쳐도 올라가 잘 구분되지 않는다.');
    console.log('    @Transactional 이 붙어 있던 과거에는 번역(1~2초) 내내 커넥션을 쥐어 면적이 클 것이라 가정했다.');
    console.log('  코얼레서가 있는 버전: 실행자 + 합류자 가 요청 수와 같으면 요청이 모두 겹친 것이다.');
    console.log('    합류자가 0 이고 실행자도 요청 수에 못 미치면 안 겹친 것이니 mock 의 DELAY_MS 를 늘려 다시 잰다.');
    console.log('  코얼레서가 없는 "전"(534fb44): 코드에 메트릭이 없어 겹쳤어도 두 카운터가 0 이다.');
    console.log('    이 버전에서는 합류자 0 을 근거로 쓰지 말고, k6 가 출력한 mock 호출 수가 요청 수에 가까운지로 겹침을 판단한다.');
}

main().catch((e) => {
    console.error(e.message);
    process.exit(1);
});
