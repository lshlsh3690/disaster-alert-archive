# 통계 API 성능 측정 및 병목 제거

측정일: 2026-09-29. 재현 스크립트: `backend/loadtest/`. 두 라운드로 진행:
- **1라운드(1~6절)**: 기존 Redis 캐시(`@Cacheable`)의 효과를 k6로 정량 측정 — 과정에서
  Postgres 버퍼 워밍업이 측정치를 교란한다는 걸 발견해 캐시 기반 수치는 이력서에서 폐기.
- **2라운드(7절)**: 1라운드에서 EXPLAIN ANALYZE로 규명한 실제 병목 2가지(인덱스 부재,
  정규식 재계산, 대용량 응답 페이로드)를 실제로 제거. 이쪽이 이력서에 쓸 수 있는 수치.

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

## 6. 1라운드 수치의 이력서 채택 여부

**캐시 미스/히트 관련 수치는 전부 이력서에서 제외한다.** 4절에서 확인했듯 반복 측정 중
Postgres 버퍼 워밍업이 섞여 들어가(rep1→rep3에서 미스 자체가 50.82ms→20.07ms로 빨라짐)
"캐시가 N배 개선했다"는 주장 자체가 방어 불가능해졌다. 이 라운드의 가치는 수치가 아니라
**"왜 병목이 생기는지 EXPLAIN ANALYZE로 규명한 것"**이고, 그 규명 결과가 7절의 실제
개선 작업으로 이어졌다. 1라운드 수치는 면접에서 "측정 방법론을 어떻게 검증했나"를 설명할
때만 근거로 쓴다(9절 참고).

## 7. 2라운드 — 실제 병목 제거

1라운드에서 EXPLAIN ANALYZE로 확인한 두 가지 근본 원인을 실제로 제거했다.
브랜치: `perf/disaster-alert-indexes` → `perf/district-name-precompute` →
`perf/weather-payload` (순서대로 쌓임, 각각 develop 대상 PR 예정).

### 7-1. `disaster_type` 인덱스 부재 (커밋만, 이력서 제외)

`/stats`의 `countByType`(유형별 집계)이 `disaster_type`에 인덱스가 없어 매번 Seq Scan +
디스크 정렬 스필을 했다. `CREATE INDEX idx_disaster_alert_disaster_type`(V116) 추가.

| | Before | After |
|---|---|---|
| 실행계획 | Seq Scan(60,000건) → 외부 정렬(디스크 1.6MB) | Index Scan (정렬 불필요) |
| 실행시간 (EXPLAIN ANALYZE) | 162.9ms | 27.4ms (**약 6배**) |

이력서 제외 이유: 사용자 판단 — 인덱스 추가는 흔한 최적화라 차별화 요소로 약함(코드/커밋에는 남김).

### 7-2. `legal_district.name` 런타임 정규식 파싱 (커밋만, 이력서 제외)

`sigungu/breakdown`, `weather-by-region`이 조회 시점마다 `legal_district.name`을
`regexp_replace`+`split_part`로 파싱해 "시군구명"을 계산했다(`DisasterAlertRepositoryImpl`
6개 호출부, 그중 3개는 `sigunguExpr()` 공유 헬퍼). `legal_district.sigungu_name`을
`GENERATED ALWAYS ... STORED` 컬럼(V117)으로 추가해 조회 시점 계산을 없앴다.

- GENERATED를 택한 이유: `LegalDistrictService.saveAllLegalDistricts()`가 Flyway 밖에서
  CSV로 `legal_district`에 신규 행을 계속 추가하는 애플리케이션 쓰기 경로가 있어, 일반
  컬럼+수동 백필이면 드리프트 위험이 있음(실제 코드로 확인).
- 검증: 마이그레이션 직후 신규 컬럼과 기존 표현식을 전건 대조해 0건 불일치 확인. 코드 변경
  전/후 API 응답 JSON을 실제로 덤프해 `diff`로 바이트 단위 동일함을 확인(로직 변경 아님을 증명).

| | Before(정규식) | After(컬럼) |
|---|---|---|
| `/sigungu/breakdown` 실행시간 (동일 세션 3회 중앙값) | 6,399ms | 6,101ms (약 4.7%) |
| `/weather-by-region` 실행시간 (동일 세션 3회 중앙값) | 7,077.8ms | 6,758.4ms (약 4.5%) |

이력서 제외 이유: 개선폭이 작음 — `work_mem`(Postgres 기본값 4MB)이 69,027행 정렬을
디스크 스필로 떨어뜨리는 게 지배적 비용이라, 정규식 계산 제거는 그 비용의 일부만 줄였다.
`work_mem` 튜닝은 스키마/코드 범위를 벗어나 이번 작업에 포함하지 않음.

### 7-3. `weather-by-region` 응답 페이로드 — **이력서 채택**

`GET /stats/weather-by-region?groupBy=sigungu`가 264개 후보 시군구×최근 3년 일별 전체를
반환해 단건 응답이 **8.2MB(58,265행)** 였다. 두 가지를 적용:

1. **gzip 압축** (`server.compression`, 설정 2줄): 전송량은 91.9~91.8% 줄었지만(curl
   단건 8,201,008→665,424 bytes, k6 30초 누적 2.2GB→180MB — 두 측정 교차검증됨),
   **응답 시간은 로컬 환경에서 유의미하게 개선되지 않았다**(p95 1.36s→1.33s, avg는 오히려
   근소 증가) — 로컬호스트는 대역폭이 사실상 무제한이라 전송 절감분이 미미하고 gzip 자체의
   CPU 비용이 이를 상쇄한 것으로 추정.
2. **서버사이드 상위 10개 지역 필터**: 코드 확인 결과 프론트(`WeatherByRegionChart.tsx`)가
   총 건수 기준 상위 10개 지역만 차트에 쓰고 나머지는 버림 — 서버가 처음부터 그 10개만
   반환하도록 `getWeatherBySigungu()`에 2단계 쿼리(랭킹→필터) 추가.
   검증: 서버가 뽑은 상위 10개가 DB 직접 집계 상위 10개와 정확히 일치(11/12위 경계 확인),
   포함된 지역 데이터가 기존 응답과 행 단위로 동일(부동소수점 평균의 최하위 자릿수 표현
   차이 3건 제외)함을 확인. 동률 처리용 2차 정렬 키 누락을 dev-reviewer가 지적해 수정.

**두 개를 합친 결과** (동일 세션, 3회 반복 중앙값, VU10/30s k6 + curl 단건 교차검증):

| | Before | After (압축 + 상위 10개 필터) | 개선 |
|---|---|---|---|
| 응답 행수 | 58,265 | 6,734 | 약 88.4% 감소 |
| 단건 전송량 | 8,201,008 bytes | 74,938 bytes | **약 99.1% 감소 (약 109배)** |
| p95 응답시간 (k6 VU10) | 1.33~1.36s | 158.81ms | **약 8.6배** |
| RPS (k6 VU10) | ~8.7/s | ~76/s | 약 8.7배 |

이 수치가 이력서에 채택된 이유: 압축률(91.9%)은 로컬/운영 환경에 의존하지 않고(같은 JSON이면
어디서 재도 같은 비율), 응답 행수 감소(88.4%)는 DB 실측과 정확히 교차검증됐으며, 그 결과로
나온 응답시간 개선(8.6배)까지 동일 세션·동일 조건에서 재현 가능하게 나왔다 — 앞선 캐시 수치들과
달리 버퍼 워밍업·동시성 등 환경 변수에 흔들리지 않는 측정이다.

## 8. 이력서 문장 (확정)

> **단일 응답이 8.2MB(58,265행)에 달하던 지역별 날씨 통계 API에, 프론트엔드 사용 패턴 분석(상위 10개 지역만 실제 사용)을 근거로 서버사이드 상위 N 필터링과 gzip 압축을 적용 — k6 부하 테스트(VU10) 기준 전송량을 99.1%(8.2MB→75KB), p95 응답시간을 8.6배(1.36s→158.81ms) 개선. curl 단건 측정과 k6 부하 측정으로 교차검증.**

보조 문장(선택):
> Redis 캐시가 이미 적용된 통계 API의 실효성을 k6로 검증하는 과정에서 Postgres 버퍼 워밍업이 측정치를 교란한다는 걸 발견 — EXPLAIN ANALYZE로 실제 병목(인덱스 부재, 런타임 정규식 재계산, 대용량 응답 페이로드)을 구분해 정말 효과적인 개선(응답 페이로드 축소)에 집중했다.

## 9. 면접 대비 — 예상 질문

- **"왜 캐시 효과 수치가 이력서에 없나요?"** → 반복 측정 중 DB 버퍼 워밍업이 섞여 캐시
  기여분을 독립적으로 분리할 수 없었다고 설명. 이게 오히려 "측정을 의심할 줄 안다"는 근거.
- **"인덱스나 컬럼 정규화는 왜 이력서에 없나요?"** → 인덱스(6배)는 있었지만 흔한 최적화라
  차별화 요소로 약하다고 판단해 제외, 코드에는 남아있음(V116/V117). 정규식 제거는 실측상
  개선폭이 4.5~4.7%로 작았고, 원인(`work_mem` 기본값 4MB로 인한 디스크 정렬)까지 규명했다고
  설명.
- **"압축만으로는 왜 부족했나요?"** → 로컬 환경은 대역폭이 사실상 무제한이라 전송 시간
  절감분이 미미했고 압축 자체의 CPU 비용이 이를 상쇄했다(실측: p95 거의 그대로). 응답 행수
  자체를 줄이는 게(상위 10개 필터) DB/직렬화 비용을 실제로 줄여 응답시간 개선으로 이어졌다.

## 재현 방법

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

# 3) 2라운드(압축+상위N) 재현 — Accept-Encoding 명시 필요 (k6 http.get 기본은 미전송)
k6 run --env PATH=/api/v1/alerts/stats/weather-by-region --env QUERY=groupBy=sigungu --env VUS=10 --env DURATION=30s backend/loadtest/k6/stats-endpoint.js
curl -s --compressed -o /dev/null -w "wire bytes: %{size_download}\n" "http://localhost:8080/api/v1/alerts/stats/weather-by-region?groupBy=sigungu"
```
전체 커맨드와 PowerShell 버전은 `backend/loadtest/README.md` 참고.
