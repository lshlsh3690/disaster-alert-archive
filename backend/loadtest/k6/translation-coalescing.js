// 번역 요청 병합(single-flight) 전후 비교 — 외부 번역 API 호출 횟수를 센다.
//
// 재는 것은 레이턴시가 아니라 **호출 횟수**다. 병합이 없어도 N개 요청이 각자 병렬로
// 번역을 호출하므로 벽시계 시간은 거의 같다. 실제로 달라지는 것은 OpenAI 를 몇 번
// 부르느냐(= 비용·쿼터)다.
//
// 비교 대상
//   전: 534fb44 (upsert 제거됨, 코얼레서 배선 없음 — check-then-act + save())
//   후: develop (TranslationRequestCoalescer + 실행자 안쪽 캐시 재확인)
//   저장 방식이 양쪽 다 save() 라, 둘의 차이는 코얼레서 하나뿐이다.
//
// 실행 전 준비 (매 회차마다)
//   1) mock 기동:  node backend/loadtest/mock/openai-mock.mjs
//   2) 백엔드 기동: ./gradlew bootRun --args='--spring.ai.openai.base-url=http://localhost:4000 --spring.ai.openai.api-key=mock'
//   3) 대상 알림의 번역 캐시 삭제 (캐시가 있으면 번역 경로를 아예 타지 않는다):
//      DELETE FROM disaster_alert_translation WHERE disaster_alert_id = <ALERT_ID> AND language_code = 'JA';
//
// 실행
//   k6 run backend/loadtest/k6/translation-coalescing.js
//   k6 run --env ALERT_ID=123 --env VUS=10 --env BASE=http://localhost:8080 backend/loadtest/k6/translation-coalescing.js

import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.BASE || 'http://localhost:18080';
const MOCK = __ENV.MOCK || 'http://localhost:4000';
const ALERT_ID = __ENV.ALERT_ID || '700000001';
const LANG = __ENV.LANG || 'ja';
const VUS = Number(__ENV.VUS || 10);

// VU 수와 반복 수를 같게 둬서 "동시에 출발하는 N개 요청"이 되게 한다.
// 한 VU 가 두 번 돌면 두 번째는 이미 캐시가 채워진 뒤라 번역 경로를 타지 않는다.
export const options = {
    vus: VUS,
    iterations: VUS,
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
