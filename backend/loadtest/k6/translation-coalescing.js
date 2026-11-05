// 번역 요청 병합(single-flight) 전후 비교 — 외부 번역 API 호출 횟수를 센다.
//
// 재는 것은 레이턴시가 아니라 **호출 횟수**다. 병합이 없어도 N개 요청이 각자 병렬로
// 번역을 호출하므로 벽시계 시간은 거의 같다. 실제로 달라지는 것은 OpenAI 를 몇 번
// 부르느냐(= 비용·쿼터)다.
//
// 비교 대상
//   전: 534fb44 (upsert 제거됨, 코얼레서 배선 없음 — check-then-act + save())
//   후: develop (TranslationRequestCoalescer + 실행자 안쪽 캐시 재확인)
//   저장 방식은 양쪽 다 save() 다. 둘의 차이는 코얼레서와 실행자 안쪽 캐시 재확인 두 가지이므로,
//   호출 횟수 차이를 코얼레서 단독 효과로 읽지 않는다(둘의 결합 효과다).
//
// 실행 전 준비 (매 회차마다)
//   1) mock 기동:  node backend/loadtest/mock/openai-mock.mjs
//   2) 백엔드 기동: ./gradlew bootRun --args='--spring.ai.openai.base-url=http://localhost:4000 --spring.ai.openai.api-key=mock'
//      (환경변수 로드 등 전체 절차는 docs/PERF_TRANSLATION_COALESCING.md 의 "준비" 참고)
//   3) 대상 알림의 번역 캐시 삭제 (캐시가 있으면 번역 경로를 아예 타지 않는다).
//      삭제 조건의 language_code 는 아래 LANG 을 대문자로 한 값이어야 한다(ja -> 'JA', en -> 'EN', zh -> 'ZH').
//      LANG 을 바꿨는데 기본값 'JA' 만 지우면 선택한 언어의 캐시가 남아 호출 0회로 나온다.
//      DELETE FROM disaster_alert_translation WHERE disaster_alert_id = <ALERT_ID> AND language_code = '<LANG 대문자>';
//
// 주의: 일회용/로컬 사본 DB 에서만 돌린다.
//   이 절차는 기존 번역을 지우고, mock 응답(`テスト翻訳`)이 그 자리에 번역으로 저장돼 남는다.
//   같은 DB 로 개발 서버를 다시 띄우면 그 알림에 가짜 번역이 보인다.
//   - 지우기 전에 필요한 번역이 있으면 백업한다.
//   - 측정이 끝나면 3) 의 DELETE 를 한 번 더 실행해 mock 번역을 지운다.
//
// 실행
//   k6 run backend/loadtest/k6/translation-coalescing.js
//   k6 run --env ALERT_ID=123 --env VUS=10 --env TARGET_LANG=en backend/loadtest/k6/translation-coalescing.js

import http from 'k6/http';
import { check } from 'k6';

// 백엔드 기본 포트(application.yml 의 server.port)와 같은 8080. 다른 포트로 띄웠다면 --env BASE= 로 넘긴다.
const BASE = __ENV.BASE || 'http://localhost:8080';
const MOCK = __ENV.MOCK || 'http://localhost:4000';
const ALERT_ID = __ENV.ALERT_ID || '700000001';
// 이름이 TARGET_LANG 인 이유: k6 의 __ENV 는 셸 환경을 그대로 물려받는데, 많은 환경에서 `LANG` 은
// 로케일(ko_KR.UTF-8 등)로 이미 설정돼 있다. `__ENV.LANG` 을 쓰면 그 값이 lang 파라미터로 들어가
// 지원 언어가 아니게 되고, 번역 경로를 아예 타지 않아 **호출 0회**가 나온다. 병합이 완벽히
// 동작한 것처럼 보이는 조용한 오측정이라 이름을 분리했다.
const LANG = __ENV.TARGET_LANG || 'ja';
const VUS = Number(__ENV.VUS || 10);

// "동시에 출발하는 N개 요청"을 만들기 위해 per-vu-iterations 로 VU 마다 정확히 1번만 돌게 한다.
// vus + iterations 만 쓰면 shared-iterations 가 되어 반복을 VU 끼리 나눠 갖는다 — 빠른 VU 가
// 두 번 돌고 다른 VU 가 한 번도 못 돌 수 있고, 두 번째 반복은 이미 캐시가 채워진 뒤라
// 번역 경로를 타지 않아 실제 동시 요청 수보다 적게 측정된다.
// 요청이 정말 겹쳤는지는 별도로 확인한다: "전" 은 mock 호출 수가 요청 수에 가까워야 하고,
// "후" 는 샘플러의 실행자 + 합류자 합이 요청 수와 같아야 한다.
export const options = {
    scenarios: {
        burst: {
            executor: 'per-vu-iterations',
            vus: VUS,
            iterations: 1,
            maxDuration: '60s',
        },
    },
};

// 회차마다 mock 카운터를 0 으로 되돌린다. 안 하면 이전 회차 호출이 섞인다.
export function setup() {
    const reset = http.post(`${MOCK}/__reset`);
    check(reset, { 'mock 카운터 초기화': (r) => r.status === 200 });
    return { startedAt: Date.now() };
}

export default function () {
    const res = http.get(`${BASE}/api/v1/alerts/${ALERT_ID}?lang=${LANG}`);

    check(res, {
        'HTTP 200': (r) => r.status === 200,
        // 500 이 나면 번역 저장 충돌이 호출부 트랜잭션까지 번진 것이다 (전 상태에서 관찰될 수 있다).
        '서버 오류 아님': (r) => r.status < 500,
    });
}

export function teardown() {
    const stats = http.get(`${MOCK}/__stats`).json();

    console.log('========================================');
    console.log(`동시 요청 ${VUS}건 (알림 ${ALERT_ID}, ${LANG.toUpperCase()}, 캐시 미스 상태)`);
    console.log(`번역 API 호출: ${stats.chatCalls}회`);
    console.log('========================================');
    console.log('해석: 요청 병합이 동작하면 본문 1회(+유형이 있으면 1회)로 끝난다.');
    console.log('      병합이 없으면 요청 수만큼 배로 늘어난다.');
}
