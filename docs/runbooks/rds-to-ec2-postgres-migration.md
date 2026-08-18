# RDS → EC2 Postgres 컨테이너 이관 런북

## 배경 / 목적

운영 DB를 RDS에서 EC2 위 Docker Postgres 컨테이너(`docker-compose.prod.yml`의 `postgres` 서비스, `pgvector/pgvector:pg16`)로 옮겨 RDS 비용을 없앤다. 진행 순서는 **① 데이터 이관 → ② 신규 컨테이너로 일정 기간 운영하며 정상 저장 확인 → ③ RDS 제거** 3단계이며, 이 문서는 ①·②를 다룬다. ③(RDS 인스턴스 삭제)은 ②의 검증 기간이 끝난 뒤 별도로 진행한다.

이관 자체(운영 RDS/EC2 접근, `pg_dump`/`pg_restore` 실행, `.env` 수정, 서비스 재기동)는 **사용자가 EC2에서 직접 실행**한다. Claude Code 세션은 로컬 저장소 파일만 다룰 수 있고 운영 인프라에 접근할 수 없다 ([[운영 트리거는 직접 쏘지 말 것]], [[운영 .env는 건드리지도 말할 것도 없음]]).

## 사전 준비

- `docker-compose.prod.yml`에 `postgres` 서비스가 추가되어 있어야 한다 (이 커밋에서 추가됨). `develop`에 머지되면 `backend-deploy.yml`이 트리거되지만, 그 워크플로는 `--no-deps`로 `backend`만 재생성하므로 `postgres` 서비스는 **자동으로 뜨지 않는다** — 아래 1단계처럼 최초 1회는 EC2에서 수동으로 띄워야 한다.
- EC2 → RDS 네트워크 경로는 이미 열려 있다 (현재 backend가 RDS에 직접 붙어 있으므로 보안그룹 등은 그대로 재사용 가능).
- `~/apps/disaster/.env`(운영 `.env`)에 `POSTGRES_DB`/`POSTGRES_USER`/`POSTGRES_PASSWORD`가 설정되어 있어야 한다 — RDS와 동일한 값을 재사용해도 되고, 새 값으로 바꿔도 된다(새 값이면 이관 시 `pg_dump`/`pg_restore`에 각각 다른 자격증명을 써야 함에 유의).
- pgvector 확장: `pgvector/pgvector:pg16` 이미지는 확장 바이너리를 이미 포함하고 있다. RDS도 이미 `vector` 확장을 쓰고 있으므로(임베딩 컬럼) `pg_dump` 결과에 `CREATE EXTENSION` 구문이 포함된다 — 복원 대상 DB가 비어 있는 상태여야 충돌 없이 적용된다.
- **`pg_dump`/`pg_restore` 클라이언트 버전은 RDS 서버 버전 이상이어야 한다** (`pg_dump: error: aborting because of server version mismatch`로 실패함). 아래 명령의 `postgres:15` 이미지는 예시이며, 실제로는 `SELECT version();`으로 RDS 버전을 먼저 확인하고 그 major 버전과 같거나 높은 `postgres:<major>` 이미지를 써야 한다 (2026-08-18 실측: RDS가 16.9였는데 pg15 클라이언트로 시도하다 실패 — `postgres` 서비스 이미지도 `pg16`으로 맞춤).

## 1단계 — Postgres 컨테이너 최초 기동 (빈 DB)

```bash
cd ~/apps/disaster
docker compose -f docker-compose.prod.yml up -d postgres
docker compose -f docker-compose.prod.yml logs -f postgres   # "database system is ready to accept connections" 확인 후 Ctrl+C
```

이 시점의 DB는 완전히 빈 상태다. **여기에 backend를 바로 붙이지 말 것** — Flyway가 빈 스키마에 처음부터 전체 마이그레이션을 다시 돌리게 되는데, 그러면 seed 마이그레이션은 재현되지만 RDS에 쌓인 실제 수집 데이터(재난문자, 이벤트, 위험도 이력 등)는 없는 상태로 시작하게 된다. 데이터를 그대로 옮기려면 2단계의 덤프/복원을 거쳐야 한다.

## 2단계 — 예비 덤프 · 복원 (검증용, 다운타임 없음)

RDS는 계속 운영 중인 상태로 스냅샷 덤프를 떠서 새 컨테이너에 복원해보고, 스키마/데이터가 문제없이 옮겨지는지 먼저 검증한다.

```bash
# EC2에서 실행. RDS_HOST/RDS_USER/RDS_DB는 현재 운영 .env의 DB_HOST/POSTGRES_USER/POSTGRES_DB 값
docker run --rm \
  -e PGPASSWORD='<RDS 비밀번호>' \
  -v ~/apps/disaster:/dump \
  postgres:16 \
  pg_dump -h <RDS_HOST> -p 5432 -U <RDS_USER> -d <RDS_DB> \
    -Fc --no-owner --no-acl -f /dump/disaster_alert_$(date +%Y%m%d_%H%M).dump

# 새 컨테이너로 복원
docker cp ~/apps/disaster/disaster_alert_<타임스탬프>.dump disaster-postgres:/tmp/restore.dump
docker exec disaster-postgres pg_restore \
  -U <신규 POSTGRES_USER> -d <신규 POSTGRES_DB> --no-owner --no-acl /tmp/restore.dump
```

### 검증

```bash
# 주요 테이블 행 수 비교 (RDS 쪽은 위 pg_dump와 같은 방식으로 psql 접속해 조회)
docker exec disaster-postgres psql -U <USER> -d <DB> -c "
  SELECT 'disaster_alert', count(*) FROM disaster_alert
  UNION ALL SELECT 'disaster_event', count(*) FROM disaster_event
  UNION ALL SELECT 'region_risk_daily', count(*) FROM region_risk_daily
  UNION ALL SELECT 'member', count(*) FROM member
  UNION ALL SELECT 'flyway_schema_history', count(*) FROM flyway_schema_history;
"
```

`flyway_schema_history`가 함께 복원되었는지 반드시 확인한다 — 이게 없으면 이후 backend가 이 DB에 붙을 때 Flyway가 처음부터 마이그레이션을 다시 시도해 실패한다(`ddl-auto: validate`이므로 스키마 불일치 시 기동 자체가 실패).

이 예비 복원은 검증이 끝나면 지워도 되고(`docker compose -f docker-compose.prod.yml down postgres && docker volume rm disaster_postgres_data` 후 1단계부터 재시작), 그대로 최종 컷오버의 베이스로 이어가도 된다 — 다만 검증과 컷오버 사이에 RDS에 새로 쌓인 데이터가 있으므로 3단계에서 반드시 최종 덤프를 한 번 더 떠야 한다.

## 3단계 — 컷오버 (다운타임 발생)

RDS는 backend가 계속 쓰고 있으므로, 마지막 덤프 시점 이후의 데이터 유실을 막으려면 짧은 점검 시간이 필요하다.

```bash
cd ~/apps/disaster

# 1. backend 정지 — 이 순간부터 새 재난문자 수집/알림 발송이 멈춘다
docker compose -f docker-compose.prod.yml stop backend

# 2. 최종 덤프 (2단계와 동일한 pg_dump 명령, 새 타임스탬프로)
docker run --rm -e PGPASSWORD='<RDS 비밀번호>' -v ~/apps/disaster:/dump postgres:16 \
  pg_dump -h <RDS_HOST> -p 5432 -U <RDS_USER> -d <RDS_DB> \
    -Fc --no-owner --no-acl -f /dump/disaster_alert_final.dump

# 3. 새 컨테이너를 비우고 최종 덤프로 다시 복원 (검증 단계 데이터를 덮어씀)
docker exec disaster-postgres psql -U <USER> -d postgres -c "DROP DATABASE IF EXISTS <DB>; CREATE DATABASE <DB>;"
docker cp ~/apps/disaster/disaster_alert_final.dump disaster-postgres:/tmp/restore.dump
docker exec disaster-postgres pg_restore -U <USER> -d <DB> --no-owner --no-acl /tmp/restore.dump

# 4. .env 수정: DB_HOST=postgres (RDS 엔드포인트 → compose 서비스명)
vi .env

# 5. backend 재기동
docker compose -f docker-compose.prod.yml up -d --force-recreate --no-deps backend
docker compose -f docker-compose.prod.yml logs -f backend   # Flyway가 "Successfully validated" 로 끝나는지, 이후 정상 기동 로그인지 확인
```

정지~재기동까지의 다운타임은 덤프/복원 크기에 비례한다 — 사전에 2단계로 한 번 예행연습을 해서 소요 시간을 가늠해두면 좋다.

## 4단계 — 사후 검증 (RDS는 그대로 둔 채로)

- 헬스체크/주요 API 응답 확인.
- 스케줄러(`DisasterFetchScheduler`, `WeatherCollectScheduler`, `RiskDecayScheduler`)가 최소 한 주기 이상 정상적으로 새 컨테이너에 데이터를 쓰는지 로그로 확인.
- FCM 발송, 로그인 등 핵심 플로우 스모크 테스트.
- **이 기간 동안 RDS 인스턴스는 삭제하지 않는다** — 문제가 발견되면 `.env`의 `DB_HOST`를 RDS 엔드포인트로 되돌리고 `docker compose -f docker-compose.prod.yml up -d --force-recreate --no-deps backend`로 즉시 롤백 가능하다 (RDS는 그동안 손대지 않았으므로 데이터는 최종 덤프 시점 이전까지 그대로 보존되어 있다 — 다만 새 컨테이너에서 발생한 신규 쓰기는 RDS에 없으므로 롤백하면 그 구간 데이터는 유실됨을 감안).

### 알려진 이슈 — EC2 메모리 제약과 `postgres` 병렬 워커

컷오버 직후 실측(2026-08-18, EC2 t3.small = RAM 1.9GB) 통계/상관관계 조회(`weather_observation` 650만 건 집계)에서 아래 에러로 API가 실패했다:

```
org.postgresql.util.PSQLException: ERROR: could not resize shared memory segment "/PostgreSQL.xxx" to 8388608 bytes: No space left on device
```

원인은 디스크가 아니라 Docker 컨테이너의 기본 `/dev/shm` 크기(64MB)와 EC2 자체의 RAM 부족(RDS를 쓸 때는 DB 메모리가 별도 인스턴스에 있었지만, 이관 후에는 backend(JVM)·Redis·Caddy와 이 EC2의 RAM을 나눠 써야 함)이 겹쳐서 Postgres 병렬 쿼리 워커가 공유메모리를 확보하지 못한 것이다. `free -h`로 실측한 여유 메모리가 421MB 수준이었고 스왑도 이미 사용 중이었다.

**대응(t3.small 유지 결정, 2026-08-18):** `docker-compose.prod.yml`의 `postgres` 서비스에 `shm_size: '128mb'`와 `command: ["postgres", "-c", "max_parallel_workers_per_gather=0"]`를 추가해 병렬 워커를 비활성화했다. 집계 쿼리가 다소 느려지는 대신 이런 메모리 환경에서 안정성을 확보하는 쪽을 택함.

**인스턴스 사양 검토 결과(참고용, 실제 단가는 AWS 콘솔/Pricing Calculator로 재확인할 것):** RDS 절감액이 월 $21인데, RAM 확보를 위해 t3.medium(4GB)으로 올리면 추가 비용이 절감액을 거의 다 상쇄한다(순절감 $0~3/월 추정). t3.large(8GB)는 오히려 RDS 유지보다 비싸진다(추정 월 $35 안팎 손해). 그래서 t3.small 유지 + 튜닝 쪽으로 결정했다. `t4g.small`(Graviton, ARM)은 스펙(2 vCPU/2GB RAM) 동일에 더 저렴하지만 **RAM 자체가 늘지 않아 이 문제의 해결책이 아니며**, 전환하려면 `backend-deploy.yml`이 지금 x86_64 전용으로만 이미지를 빌드하고 있어 arm64 멀티아키텍처 빌드로 CI를 먼저 바꿔야 한다(redis/caddy/postgres 공식 이미지는 arm64 지원되므로 문제없음).

이 튜닝 이후에도 데이터가 계속 쌓이며(날씨 이력 등) 같은 문제가 재발하면, 그때 인스턴스 업그레이드를 재검토한다.

## 5단계 — RDS 제거 (검증 기간 종료 후, 별도 작업)

검증 기간(예: 1~2주) 동안 이상 없으면 진행한다. 이 문서의 범위 밖이며 별도로 진행 시점에 다시 다룬다 — 삭제 전 RDS 최종 스냅샷을 남겨두는 것을 권장한다.
