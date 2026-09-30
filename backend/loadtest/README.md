# 통계 API 캐시 성능 측정

`docs/PERF_BASELINE.md`(측정 결과 문서)를 재현하기 위한 스크립트 모음.

## 사전 준비 (1회)

```bash
# postgres(pg15)+redis 기동, 운영과 동일하게 병렬 워커 0으로
docker compose -f docker-compose.dev.yml up -d postgres redis
docker exec postgres psql -U $POSTGRES_USER -d postgres -c "ALTER SYSTEM SET max_parallel_workers_per_gather = 0;"
docker restart postgres

# 마이그레이션 (프로필 없음 → 스케줄러 비활성)
cd backend && set -a && source ../.env.dev && set +a && export DB_HOST=localhost
./gradlew bootRun   # "Started BackendApplication" 뜨면 Ctrl+C

# 합성 데이터 시드 (멱등 append — 다시 실행하면 이어서 증량됨)
docker exec -i postgres psql -U $POSTGRES_USER -d $POSTGRES_DB < backend/loadtest/seed/seed_stats_data.sql

# actuator 인증용 로컬 전용 테스트 계정 (운영에 존재하지 않음, 로컬 DB에만 생성됨)
curl -s -X POST http://localhost:8080/api/v1/auth/signup -H "Content-Type: application/json" \
  -d '{"email":"perftest@local.test","password":"perftest1234","confirmPassword":"perftest1234","nickname":"perftest"}'
curl -s -c /tmp/perf_cookies.txt -X POST http://localhost:8080/api/v1/auth/login -H "Content-Type: application/json" \
  -d '{"email":"perftest@local.test","password":"perftest1234"}'
# 이후 curl -b /tmp/perf_cookies.txt http://localhost:8080/actuator/... 로 접근
```

## Step 4 — 후보 엔드포인트 선정 (완료)

**단발 curl로 콜드 응답시간을 재면 안 된다** — 막 기동한 JVM의 첫 요청은 JIT/커넥션풀/
Hibernate 메타모델 초기화 비용이 섞여 캐시 미스 비용과 무관하게 부풀려진다. 워밍업(Postman
`stats-endpoints.postman_collection.json`의 Warmup 폴더 반복 실행) 이후 JVM이 각 쿼리를 한
번씩은 거친 상태에서, `docker exec redis redis-cli FLUSHDB` 후 curl로 미스/히트를 재현성
확인(2회 반복)까지 마쳐 실측 완료:

| 엔드포인트 | 미스 | 히트 |
|---|---|---|
| `weather-by-region?groupBy=sigungu` | **8.05s** | **1.3~1.5s** (히트도 느림) |
| `stats` | **5.11s** | 26~31ms |
| `sigungu/breakdown` | **4.32s** | 24~28ms |
| weather-correlation (제외) | 0.70s | 14~42ms |
| daily-type (제외) | 0.44s | 44~48ms |

`weather-by-region?groupBy=sigungu`는 히트도 1.3초대로 느리다 — 캐시된 페이로드 크기 +
`GenericJackson2JsonRedisSerializer` 역직렬화 비용으로 추정되나 아직 확정은 아니다. Step 5에서
동시 부하(VU10) 하의 p95로 다시 보고, 여전히 느리면 "캐시가 DB 쿼리는 스킵하지만 응답 크기
문제는 못 해결한다"는 관찰로 문서에 그대로 남긴다(절대 규칙: 작게 나온 효과를 숨기지 않는다).

`postman/bottleneck-endpoints.postman_collection.json`에 위 3개 + 실측 수치가 설명과 함께
정리되어 있다 — Postman에서 열어보며 응답 형태를 확인할 수 있다.

## Step 5 — 본 측정 (미스 vs 히트, 조건당 3회)

확정된 3개 엔드포인트 각각에 대해:

```bash
# 미스: k6 실행과 동시에 evict 루프를 백그라운드로 (컨테이너 안에서 scan+del을 한 번에 처리)
while true; do docker exec redis sh -c "redis-cli --scan --pattern 'stats-*' | xargs -r redis-cli DEL" >/dev/null 2>&1; sleep 0.1; done &
EVICT_PID=$!
k6 run --env PATH=<확정된 경로> --env QUERY=<확정된 쿼리> --env VUS=10 --env DURATION=30s \
  --summary-export=backend/loadtest/results/<endpoint>-miss-1.json backend/loadtest/k6/stats-endpoint.js
kill $EVICT_PID

# 히트: 워밍업 1회 GET 후 evict 없이
curl -s -o /dev/null "http://localhost:8080<확정된 경로>?<확정된 쿼리>"
k6 run --env PATH=<확정된 경로> --env QUERY=<확정된 쿼리> --env VUS=10 --env DURATION=30s \
  --summary-export=backend/loadtest/results/<endpoint>-hit-1.json backend/loadtest/k6/stats-endpoint.js
```
대상 3개:
- `PATH=/api/v1/alerts/stats` (QUERY 없음)
- `PATH=/api/v1/alerts/stats/sigungu/breakdown` (QUERY 없음)
- `PATH=/api/v1/alerts/stats/weather-by-region` `QUERY=groupBy=sigungu`

각 조건 3회 반복(`-1`/`-2`/`-3`), `results/*.json`의 `metrics.http_req_duration` p50/p95/p99와
`metrics.http_reqs.rate`(RPS)를 수기로 중앙값 집계한다.

측정 직후 actuator 스냅샷도 같이 남긴다:
```bash
curl -s -b /tmp/perf_cookies.txt http://localhost:8080/actuator/metrics/cache.gets | python3 -m json.tool
curl -s -b /tmp/perf_cookies.txt http://localhost:8080/actuator/metrics/hikaricp.connections.active | python3 -m json.tool
```

## 측정 조건

- postgres: `pgvector/pgvector:pg15`, `max_parallel_workers_per_gather=0` (운영과 동일)
- 데이터: `disaster_alert` 60,000건(합성) + `disaster_alert_region` 69,027건(합성) +
  `weather_daily_summary` 250,488건(Flyway 자체 백필, 실데이터 성격)
- 앱: `SPRING_PROFILES_ACTIVE` 없음 (스케줄러 비활성), 로컬 `bootRun`

## 알림 팬아웃(`AlertNotificationService`) 측정

`/stats` 라운드의 k6 방식론을 그대로 못 쓴다 — `triggerNotification`이 `@Async`라 HTTP
응답은 즉시 200이 오고 실제 처리는 백그라운드 스레드에서 끝난다. 대신 애플리케이션 로그의
`time=Xms` 줄을 직접 읽는 방식으로 측정한다.

트리거 수단이던 관리자 API(`POST /api/v1/admin/trigger-notification/{alertId}`)는 E2E
테스트 목적을 다해 제거됐다(`AdminController`) — 대신 `NotificationFanoutTriggerTool`
(`domain/notification/tool/`, 기존 `*BackfillTool`류와 동일한 프로필 게이트 수동 도구 패턴)이
시드가 만든 alertId를 자동으로 찾아 트리거한다.

```bash
# 1) 회원 1만 명 + 관심지역(핫 지역 1곳에 전원 등록) + FCM 토큰 + 트리거용 alertId 시드
docker exec -i postgres psql -U $POSTGRES_USER -d $POSTGRES_DB < backend/loadtest/seed/seed_notification_data.sql

# 2) fanout-trigger 프로필로 dry-run 기동 — NotificationFanoutTriggerTool이 부팅 직후
#    시드가 만든 최신 alertId를 자동으로 찾아 triggerNotification()을 호출한다
#    (실제 Firebase 호출 없이 DB 구간만 격리해서 측정)
cd backend && export SPRING_PROFILES_ACTIVE=fanout-trigger
./gradlew bootRun --args='--fcm.dry-run=true'

# 3) 로그에서 소요시간 확인 (bootRun 콘솔 또는 로그 파일에서)
#    "회원 팬아웃 완료 - alertId: <alertId>, 대상: 10000명, time=Xms, avg=Yms/명"
```

### 결과 (실측, 회원 1만 명 동일 데이터로 수정 전/후 비교)

| | 수정 전 (N+1) | 수정 후 (벌크 조회 + flush/clear) | 개선 |
|---|---|---|---|
| 총 소요시간 | 1,073,301ms (17분 53초) | 21,562ms (21.6초) | **약 49.8배** |
| 회원당 평균 | 107.33ms | 2.16ms | 약 49.7배 |

원인은 쿼리 개수(N+1)뿐 아니라 세션(영속성 컨텍스트) 누적이기도 했다 — DB 쿼리 자체는
0.04~0.05ms(EXPLAIN ANALYZE)인데 앱 레벨은 루프 진행에 따라 31ms→93ms로 3배 증가했다.
`triggerNotification()` 전체가 `@Transactional` 메서드 하나로 회원 1만 명 루프를 감싸서
Hibernate가 트랜잭션이 끝날 때까지 관리 엔티티를 계속 쌓아두고, 컨텍스트가 커질수록
매 쿼리 전 더티체킹 비용이 늘어난 것이 원인. 수정은 두 가지: (1) 조회 결과를 엔티티가
아니라 record(JPQL 생성자 프로젝션)로 받아 애초에 컨텍스트에 안 쌓이게 함, (2)
`UserNotificationLog`가 `GenerationType.IDENTITY`라 INSERT 배치가 불가능해서 저장 자체는
개별 INSERT로 남지만 500명마다 `entityManager.flush()+clear()`로 컨텍스트를 비움.

검증: `user_notification_log`에 회원 1만 명 전원이 `SENT`로 기록됨을 SQL로 확인(결측 없음).
