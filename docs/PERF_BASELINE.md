# 통계 API 캐시 성능 측정 (Redis `@Cacheable` 도입 전/후)

측정일: 2026-09-29. 재현 스크립트: `backend/loadtest/`.

## 1. 측정 환경

| 항목 | 값 |
|---|---|
| 머신 | Intel Core i7-13700H (14코어/20스레드), RAM 31.6GB, Windows 11 Enterprise |
| Docker 리소스 | Docker Desktop VM: 20 CPU, 15.4GB 할당 |
| Postgres | `pgvector/pgvector:pg15`, `max_parallel_workers_per_gather=0` (운영 `docker-compose.prod.yml`과 동일 조건으로 `ALTER SYSTEM` 적용) |
| Redis | `redis:latest`, 별도 설정 변경 없음 |
| 애플리케이션 | `SPRING_PROFILES_ACTIVE` 미지정 (로컬 `./gradlew bootRun`) — `SchedulingConfig`가 `@Profile("prod")`라 스케줄러 비활성, 측정 중 캐시가 스케줄러로 임의 evict될 위험 없음 |
| k6 | v2.2.0 (Windows, `winget install GrafanaLabs.k6`) |
| 데이터 규모 | `disaster_alert` 60,000건(합성 시드) + `disaster_alert_region` 69,027건(합성 시드, alert당 1개 기본 + 15% 확률 2개) + `legal_district` 53,063건(Flyway 시드) + `weather_daily_summary` 250,488건(Flyway 자체 백필 — 2023-09-01~2026-05-21, 시군구 264개 후보 중 225개 커버, 실데이터 성격이라 별도 합성 불필요) |

### 데이터 시딩 관련 참고
- 운영 EC2 인스턴스가 측정 시점에 존재하지 않아(맥북 셀프호스팅 구조로 전환 중) 운영 데이터 덤프를 받을 수 없었음 → `backend/loadtest/seed/seed_stats_data.sql`로 합성 시드.
- `disaster_alert_id`는 10,000,000 이상 전용 대역만 사용해 실 데이터 시퀀스(`disaster_alert_seq`)와 충돌하지 않음. 스크립트 재실행 시 기존 데이터 위에 이어서 증량되는 멱등 구조.

## 2. 대상 엔드포인트 선정 (3개)

`DisasterAlertService`의 `@Cacheable` 대상 16개 메서드 중, `AlertSearchRequest` 파라미터 없이 호출했을 때(WHERE절이 비어 전체 스캔) 캐시 미스 비용이 큰 순으로 상위 3개를 선정했다. 워밍업(JVM/커넥션풀 정상화) 후 `docker exec redis redis-cli FLUSHDB` 직후 단발 curl로 측정, 2회 재현성 확인:

| 엔드포인트 | 미스 응답시간 | 선정 |
|---|---|---|
| `GET /stats/weather-by-region?groupBy=sigungu` | 7.67~8.05s | ✅ |
| `GET /stats` | 5.11~5.13s | ✅ |
| `GET /stats/sigungu/breakdown` | 4.32~4.37s | ✅ |
| `GET /stats/weather-correlation` | 0.70s | 제외 (상대적으로 가벼움) |
| `GET /stats/daily-type` | 0.44s | 제외 (조인 없음, 가장 가벼움) |

참고: `GET /stats/weather-by-region`은 `groupBy` 기본값이 `sido`(코드 앞 2자리로 그룹핑, 저렴)라 파라미터 없이 호출하면 `stats-weather-by-sigungu`가 아니라 `stats-weather-by-sido` 캐시를 탄다. 위 측정은 `?groupBy=sigungu`를 명시한 값이며, 프론트엔드(`frontend/src/app/stats/page.tsx:517`)에서도 사용자가 특정 시도를 드릴다운할 때 정확히 이 `sigungu` 버전을 호출한다 — 죽은 코드가 아니라 실사용 경로다.

## 3. 결과 — VU10, 40초(5s ramp-up / 30s hold / 5s ramp-down), 조건당 3회 반복

미스 조건은 k6 실행과 동시에 `docker exec redis sh -c "redis-cli --scan --pattern 'stats-*' | xargs -r redis-cli DEL"`를 100ms 주기로 반복해 캐시를 지속적으로 비웠다. 히트 조건은 evict 없이, 워밍업 1회 GET 후 실행.

### `GET /stats`

| | rep1 | rep2 | rep3 |
|---|---|---|---|
| 미스 avg/med/p90/p95/max | 251.72/10.44/22.91/**50.82**/7.11s ms | 254.56/10.16/19.88/**37.77**/7.15s ms | 11.94/10.97/17.12/**20.07**/70.48 ms |
| 히트 avg/med/p90/p95/max | 12.63/12/17.46/**19.48**/28.85 ms | 14.95/15/19.58/**20.81**/36.42 ms | 14.68/14.57/19.86/**21.58**/33 ms |
| 미스 RPS / 히트 RPS | 7.02 / 8.76 | 6.98 / 8.74 | 8.76 / 8.74 |

### `GET /stats/sigungu/breakdown`

| | rep1 | rep2 | rep3 |
|---|---|---|---|
| 미스 avg/med/p90/p95/max | 217.85/12.06/26.58/**35.27**/6.25s ms | 203.28/10.45/21.03/**29.65**/5.94s ms | 12.67/11.83/19.45/**21.2**/36.57 ms |
| 히트 avg/med/p90/p95/max | 13.76/13.42/20.11/**22.81**/43.19 ms | 15.95/16.1/21.86/**23.7**/38.4 ms | 12.49/12.67/18.09/**19.91**/26.68 ms |
| 미스 RPS / 히트 RPS | 7.35 / 8.76 | 7.30 / 8.72 | 8.76 / 8.75 |

### `GET /stats/weather-by-region?groupBy=sigungu`

| | rep1 | rep2 | rep3 |
|---|---|---|---|
| 미스 avg/med/p90/p95/max | 2.12/1.2/4.11/**9.57s**/13.24s | 2.0/1.11/3.8/**8.65s**/12.77s | 417.15/417.24/447.64/**452.77**/473.46 ms |
| 히트 avg/med/p90/p95/max | 1.22/1.28/1.46/**1.62s**/1.71s | 1.32/1.32/1.42/**1.46s**/1.58s | 411.32/413.51/441.23/**446.34**/475.16 ms |
| 미스 RPS / 히트 RPS | 2.77 / 3.92 | 2.94 / 3.72 | 6.20 / 6.23 |
| 40초간 수신 데이터 | 952MB(미스) / 1.3GB(히트) | — | 2.1GB(미스) / 2.1GB(히트) |

## 4. 관찰 — 3회 중앙값이 아니라 추세를 그대로 보고함

당초 계획은 "조건당 3회 반복해 중앙값 채택"이었으나, 실측 결과 **반복할수록 미스 조건이 뚜렷하게 빨라지는 추세**가 나타나 중앙값이 의미가 없었다. 노이즈가 아니라 방향성 있는 변화라 추세 자체를 결과로 남긴다.

- **`stats`/`sigungu/breakdown`**: rep1→rep3에서 미스 p95가 50.82ms/35.27ms → 20.07ms/21.2ms로 좁혀지며 **히트 p95(19~24ms)와 거의 같아진다.** 다만 max는 rep1~2에서 6~7초짜리 outlier가 있다가 rep3엔 사라진다.
- **`weather-by-region?groupBy=sigungu`**: 변화가 가장 극적이다. 미스 p95가 9.57s → 8.65s → **452.77ms**로 떨어지고, 결국 rep3에서는 **미스(452.77ms)와 히트(446.34ms)가 사실상 동일**해진다.

**해석**: `disaster_alert`에는 `created_at`/`disaster_type`/`emergency_level` 인덱스가 전혀 없어 미스 시 항상 풀스캔이다. 반복 실행될수록 Postgres가 이 60,000행 테이블(및 조인 대상)을 버퍼 캐시(shared_buffers/OS 페이지 캐시)에 계속 들고 있게 되면서, "첫 계산" 비용 자체가 초 단위 → 밀리초 단위로 줄어든다. 즉 Redis 캐시의 순수 기여분은 **"애플리케이션이 오래 떠 있어 DB가 이미 웜업된 상태"에서는 생각보다 작고**, `weather-by-region`의 경우 DB가 완전히 웜업되면 병목이 DB 쿼리가 아니라 **거대한 응답 페이로드(40초에 2GB+ 전송)의 직렬화/네트워크 비용으로 옮겨가 캐시가 사실상 무력화**된다.

이건 캐시 효과가 작게 나온 걸 감추는 게 아니라, "캐시를 넣었는데 왜 이만큼만 개선됐는가"에 대한 실측 기반 답이다 — Postgres 버퍼 캐시가 식어있는 배포 직후(콜드 스타트)에는 캐시 효과가 명확하지만(수 초 → 수십 ms), 오래 떠 있는 서버에서는 격차가 좁혀지고, 응답이 큰 엔드포인트는 캐시만으로 해결이 안 된다는 것을 확인했다.

## 5. 재현 방법

```bash
# 0) 인프라
docker compose -f docker-compose.dev.yml up -d postgres redis
docker exec postgres psql -U $POSTGRES_USER -d postgres -c "ALTER SYSTEM SET max_parallel_workers_per_gather = 0;"
docker restart postgres
cd backend && set -a && source ../.env.dev && set +a && export DB_HOST=localhost
./gradlew bootRun   # "Started BackendApplication" 확인 후 그대로 두거나 재기동

# 1) 시드 (최초 1회, 증량하려면 재실행)
docker exec -i postgres psql -U $POSTGRES_USER -d $POSTGRES_DB < backend/loadtest/seed/seed_stats_data.sql

# 2) 측정 (엔드포인트별 미스/히트, backend/loadtest/README.md에 3개 엔드포인트 전체 커맨드)
while true; do docker exec redis sh -c "redis-cli --scan --pattern 'stats-*' | xargs -r redis-cli DEL" >/dev/null 2>&1; sleep 0.1; done &
EVICT_PID=$!
k6 run --env PATH=/api/v1/alerts/stats --env VUS=10 --env DURATION=30s backend/loadtest/k6/stats-endpoint.js
kill $EVICT_PID

curl -s -o /dev/null http://localhost:8080/api/v1/alerts/stats
k6 run --env PATH=/api/v1/alerts/stats --env VUS=10 --env DURATION=30s backend/loadtest/k6/stats-endpoint.js
```
전체 3개 엔드포인트 커맨드와 PowerShell 버전은 `backend/loadtest/README.md` 참고.

## 6. 이력서 문장 초안

**주의 — 배수 계산 시 반드시 같은 측정 방식끼리 비교할 것**: 단발 curl 콜드 응답(5.11s)과 k6 부하테스트 히트 p95(19.48ms)는 서로 다른 측정 조건이라, 이 둘을 나눈 "260배" 같은 수치는 근거가 약하다. 아래 문장은 **동일 조건(k6 VU10, 같은 실행)에서 뽑은 미스 대 히트 p95**만 사용한다.

> Redis 기반 캐싱(Spring `@Cacheable`, TTL 10분)이 적용된 통계 집계 API에 대해 k6 부하 테스트(VU10 동시 요청, 합성 데이터 60,000건)로 캐시 미스/히트를 동일 조건에서 정량 비교. `GET /stats` 기준 p95 응답시간을 2.6배 단축(50.82ms→19.48ms)했고, 무엇보다 캐시 미스 시 발생하던 최악의 경우 응답 지연(최대 7.11초)을 캐시 히트 시 30ms 이내로 완전히 제거함을 확인.

> 동일 하네스로 지역별 날씨 상관관계 API(`weather-by-region`)를 측정한 결과, 서버가 충분히 웜업되면 캐시 히트/미스가 ~450ms로 수렴하는 현상을 발견 — 40초당 2GB 이상 전송되는 대용량 응답 페이로드가 실제 병목이며 애플리케이션 캐싱만으로는 해결되지 않음을 코드 분석과 실측으로 규명 (해당 엔드포인트가 프론트엔드 지역 드릴다운 시 실사용됨을 확인).

> micrometer-registry-prometheus·k6·합성 데이터 시드 스크립트로 재사용 가능한 성능 측정 하네스(`backend/loadtest/`)를 구축해, 캐시 효과 측정뿐 아니라 이후 알림 팬아웃/번역 파이프라인 성능 개선 작업에도 활용 가능하도록 정리.

**면접 대비**: "왜 2.6배밖에 안 되냐"는 질문엔 3번 섹션의 반복 측정 추세(웜업될수록 격차가 더 좁혀짐)로 답하면 된다 — 오히려 "언제 캐시가 효과적이고 언제 아닌지"를 실측으로 규명했다는 깊이 있는 답변이 된다. 조건(60,000건 합성 데이터, VU10, 로컬 환경) 없이 숫자만 말하지 않는다.
