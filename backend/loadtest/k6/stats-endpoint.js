// 통계 API 캐시 효과 측정용 k6 스크립트.
// backend/loadtest/README.md 에 전체 사용법(워밍업 → 후보 선정 스모크 → 본 측정)이 있다.
//
// 환경변수:
//   BASE_URL  기본 http://localhost:8080
//   PATH      대상 엔드포인트 경로, 예: /api/v1/alerts/stats/sigungu/breakdown
//   QUERY     선택, 예: groupBy=sigungu
//   VUS       가상 사용자 수 (스모크: 5, 본 측정: 10)
//   DURATION  실행 시간 (스모크: 10s, 본 측정: 30s)
//
// 실행 예:
//   k6 run --env PATH=/api/v1/alerts/stats --env VUS=5 --env DURATION=10s backend/loadtest/k6/stats-endpoint.js
//
// 캐시 미스 조건: 이 스크립트만으로는 안 되고, 실행과 동시에 별도 쉘에서 redis 캐시를
// 주기적으로 비우는 evict 루프를 함께 띄워야 한다 (README 참고) — k6에는 Redis 클라이언트가
// 없고, @Cacheable(sync=true)는 동시 요청이 같은 키를 치면 1개만 계산하고 나머지는 대기 후
// 히트를 받는 구조라 k6 내부에서 미스를 강제할 수 없다.
import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const PATH = __ENV.PATH || '/api/v1/alerts/stats';
const QUERY = __ENV.QUERY || '';
const VUS = parseInt(__ENV.VUS || '10', 10);
const DURATION = __ENV.DURATION || '30s';

export const options = {
    vus: VUS,
    duration: DURATION,
    thresholds: {
        http_req_failed: ['rate<0.01'],
    },
};

const URL = QUERY ? `${BASE_URL}${PATH}?${QUERY}` : `${BASE_URL}${PATH}`;

export default function () {
    const res = http.get(URL);
    check(res, { '200 응답': (r) => r.status === 200 });
}
