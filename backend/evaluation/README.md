# 백필·평가 실행 명령어

수동으로 돌리는 도구의 명령어 모음이다. 평가 결과를 읽는 법은 [quickstart.md](../../specs/006-clustering-evaluation/quickstart.md)를 본다.

| 하려는 일 | 프로필 | OpenAI 비용 | 끝나면 |
|---|---|---|---|
| [클러스터링 평가](#클러스터링-평가) | `eval` | 없음 | 자동 종료 |
| [재클러스터링](#재클러스터링) | `backfill` | 애매한 사고성 건만 LLM 호출 | 직접 종료 |
| [임베딩 백필](#임베딩-백필) | `backfill` | 임베딩 호출 | 직접 종료 |
| [다른 임베딩 모델로 재클러스터링](#다른-임베딩-모델로-재클러스터링) | `backfill` → `eval` | 모델 임베딩(유료 모델만) + LLM 판정 | 직접 종료 → 자동 종료 |
| [사건/비사건 분류기 평가](#사건비사건-분류기-평가) | `alert-kind-eval` | 라벨 1건당 약 $0.0003 | 자동 종료 |
| [사건/비사건 분류 백필](#사건비사건-분류-백필) | `alert-kind-backfill` | 미분류 1건당 약 $0.0003 | 직접 종료 |
| [법정동 매핑 백필](#법정동-매핑-백필) | `region-backfill` | 없음 | 직접 종료 |
| [누락 기간 알림 백필](#누락-기간-알림-백필) | `alert-missing-backfill` | 없음 | 직접 종료 |

"직접 종료"인 도구는 일을 마쳐도 앱이 떠 있다. 로그에 `...Tool 완료`(임베딩만 백필할 때는 `embed-only 완료`)가 찍히면 `Ctrl+C`로 멈춘다.

## 공통 준비

Git Bash 기준이다. 아래 명령은 모두 이 준비를 마친 `backend/` 에서 실행한다.

```bash
docker compose -f docker-compose.dev.yml up -d postgres redis   # 레포 루트에서

cd backend
set -a; . ../.env.dev; set +a
EVAL_DB_NAME=disaster_alert_eval_v5
EVAL_DB=jdbc:postgresql://localhost:5432/$EVAL_DB_NAME
```

**`--spring.datasource.url` 을 빼면 `.env.dev` 의 개발 DB 에 붙는다.** 평가와 재클러스터링은 반드시 평가 사본을 지정한다. 포트는 도구 프로필(`eval`·`backfill` 등)에서 0(빈 포트)으로 정해져 있어 띄워 둔 서버와 겹치지 않는다(`application.yml` 맨 끝).

### 평가 사본 준비

`disaster_alert_eval_v5` 는 저장소에 없는 로컬 DB 다. 위 변수는 주소만 정할 뿐 DB 를 만들지 않으므로, 없으면 운영 DB 덤프(`pg_dump -Fc`)를 복원해 만든다.

```bash
docker exec -i postgres psql -U postgres -c "CREATE DATABASE $EVAL_DB_NAME;"
docker exec -i postgres pg_restore -U postgres -d "$EVAL_DB_NAME" --no-owner < <덤프 파일>
```

라벨은 알림 id 로 DB 를 찾으므로, 덤프에는 라벨된 알림이 같은 id 로 들어 있어야 하고 `embedding`·`alert_kind` 도 채워져 있어야 한다. 이미 있는 사본을 다른 이름으로 복제할 때는 `CREATE DATABASE <새 이름> TEMPLATE disaster_alert_eval_v5;` 를 쓴다.

## 운영 일일 구조 검사

운영 서버가 매일 00:30 KST 에 최근 7일의 사건 알림을 다시 세어 `clustering_daily_check` 에 하루 한 줄로 남긴다. 사람이 실행하는 도구가 아니다.

```sql
SELECT check_date, incident_count, unassigned_count, no_embedding_count, checked_at
FROM clustering_daily_check ORDER BY check_date DESC LIMIT 14;
```

- `unassigned_count`: 이벤트에 들어가지 못한 사건 알림 수. 정상은 0 이다.
- `no_embedding_count`: 임베딩 없는 사건 알림 수. 규칙 경로로 묶이는 알림이 있어 0 이 아니어도 정상일 수 있다.
- 미배정이 새로 잡혔거나 늘어난 날만 `log.error` 로 남긴다. 한 번 알린 미배정은 줄지 않는 한 다시 알리지 않으므로, 이 표를 함께 본다.
- 7일 넘게 앱이 꺼져 있었던 날은 채워지지 않는다.

## 클러스터링 평가

DB 에 저장된 이벤트 배정을 읽어 라벨과 비교한다. 클러스터링을 다시 하지 않으므로, 코드를 바꿨다면 먼저 [재클러스터링](#재클러스터링)을 한다.

```bash
./gradlew bootRun --args="--spring.profiles.active=eval \
  --spring.datasource.url=$EVAL_DB \
  --eval.dataset=cluster-eval-2026-11 \
  --eval.report=evaluation/reports/<새 리포트 이름>.md"
```

- `--eval.dataset`: 평가 셋 이름. 접속한 DB 의 `evaluation_pair_label` 에서 그 셋의 라벨을 읽는다(JSON 파일은 읽지 않는다). 아래 `--eval.pair-labels-as-of` 와 둘 중 정확히 하나가 필요하다(함께 주면 오류). 라벨이 그 DB 에 없으면 오류로 끝난다.
- **라벨은 평가 사본 DB 안에 있어야 한다.** 새 평가 사본에는 [pair_label_data_2026_11.sql](datasets/pair_label_data_2026_11.sql) 을 DBeaver 등으로 한 번 실행해 넣는다(Flyway 아님, 여러 번 실행해도 중복되지 않는다). 이미 라벨이 든 다른 로컬 DB 에서 옮길 때는 `COPY (SELECT … FROM evaluation_pair_label WHERE dataset='cluster-eval-2026-11') TO STDOUT` 를 `COPY evaluation_pair_label (…) FROM STDIN` 으로 이어도 된다.
- `--eval.report`: 없으면 로그로만 출력한다.
- 정상이면 종료 코드 0 이다.
- 2026-11-24부터 대표 점수는 균형 정확도다. 요약은 균형 정확도 → 빠짐없이 묶은 비율 → 섞지 않은 비율 → 보조 맞게 묶은 비율·F1 → 판단 없는 기준선 두 개 순서다. 각 비율은 분자·분모를 함께 읽는다. 같은 사건·다른 사건 쌍 중 한쪽 분모가 0이면 균형 정확도는 `-`다. 이유와 기준선 표는 [006 quickstart](../../specs/006-clustering-evaluation/quickstart.md#대표-균형-정확도와-보조-지표).
- 비교용 복사본에서 `- 실행 시각: `으로 시작하는 한 줄만 제거하고 `git diff --no-index <이전 정규화본>.md <새 정규화본>.md`로 비교한다. 다른 머리글과 틀린 쌍 표는 제외하지 않는다. 틀린 쌍 표의 `-` 줄은 사라진 쌍, `+` 줄은 새로 틀린 쌍이다. 지표 변경만 검증할 때는 기존 세 개수·F1 값과 틀린 쌍 표가 같고 요약 밖에 차이가 없어야 한다. 실행 예시는 [013 quickstart 2절](../../specs/013-balanced-accuracy-metric/quickstart.md).
- 과거 F1 리포트와 006 research 수치는 당시 기록으로 남기고 다시 만들지 않는다. 새 지표와 비교하려면 같은 라벨·배정을 새 기준으로 다시 잰다.

### 판사 일치율 측정 (유료)

판사(LLM)를 272쌍에 실제로 불러 일치율을 잰다. **호출마다 요금이 드니 실행 전에 승인을 받는다**(v2 225쌍 약 $0.05).

```bash
./gradlew bootRun --args="--spring.profiles.active=eval \
  --spring.datasource.url=$EVAL_DB \
  --eval.dataset=cluster-eval-2026-11 --eval.judge-agreement"
```

- 결과는 파일이 아니라 접속한 DB 의 `judge_agreement_run`(측정 1행)·`judge_agreement_run_pair`(쌍별 판정)에 저장되고, 콘솔에 측정 id·일치율·교차표·비용 요약이 남는다(2026-11-26, specs/014). `--eval.report`·`--eval.pair-labels-as-of` 와 함께 쓰면 판사를 부르기 전에 오류로 끝난다.
- 일치율은 저장한 판정과 **현재** 라벨로 관리자 대시보드에서 다시 센다 — 라벨을 고치면 판사를 다시 부르지 않고 값이 바뀐다.
- 새 판사 버전을 운영에 쓰려면, 먼저 `JudgeVersionCatalog` 에 버전·모델·단가를 추가하고 위처럼 잰 뒤, 로컬 측정 행을 고정값 시드 마이그레이션으로 만들어 운영에 넣는다(예: `V146__seed_judge_agreement_v1_v2.sql`). 운영에 그 버전의 측정이 없으면 일일 채점은 건너뛴다.

### DB 에 저장된 불일치 쌍 사람 라벨로 평가

관리자 대시보드에서 매긴 불일치 쌍 라벨(`clustering_pair_label`)을 파일 없이 바로 정답으로 쓴다. 평가 사본은 운영 덤프로 만들어 라벨 테이블이 함께 들어온다.

```bash
./gradlew bootRun --args="--spring.profiles.active=eval \
  --spring.datasource.url=$EVAL_DB \
  --eval.pair-labels-as-of=2026-11-25T00:00 \
  --eval.report=evaluation/reports/<새 리포트 이름>.md"
```

- `--eval.pair-labels-as-of`: ISO 시각(KST). 이 시각까지 저장·수정된 라벨만 쓴다 — 튜닝 전후 비교가 같은 라벨로 돌도록 매번 같은 시각을 준다. `--eval.dataset` 과 함께 주면 오류.
- 리포트 머리의 데이터셋 이름은 `불일치 쌍 사람 라벨(DB, 기준 <시각>, N쌍)` 이다. 이전 리포트와 N 이 다르면 그사이 라벨이 바뀐 것이다(라벨은 덮어쓰기라 기준 시각 뒤에 고친 라벨은 옛 값으로 돌아가지 않고 빠진다).
- 라벨이 0개면 채점하지 않고 0 이 아닌 종료 코드로 끝난다.
- 틀린 쌍 표는 `(알림 A id, 알림 B id)` 오름차순으로 나온다. 채점 이후 `--eval.recheck` 등은 `--eval.dataset` 으로 돌릴 때와 똑같이 쓴다. 판사 일치율 측정(`--eval.judge-agreement`)은 예외다 — 불일치 쌍 라벨(`clustering_pair_label`)로는 재지 않고 `--eval.dataset`(DB `evaluation_pair_label` 의 평가 셋 라벨, 파일 아님)이 있어야 한다(함께 주면 판사를 부르기 전에 오류로 끝난다). 판사·시스템이 갈린 쌍만 모은 라벨이라 버전끼리 비교할 수 있는 일치율이 나오지 않기 때문이다.

## 재클러스터링

평가 사본의 이벤트를 비우고, 저장된 임베딩으로 현재 코드와 설정대로 다시 묶는다. **아래 비우기 명령은 평가 사본에만 실행한다.**

```bash
# 1) 이벤트 비우기 — 알림의 이벤트 배정을 지우고, 이벤트를 참조하는 표를 비운 뒤 이벤트를 DELETE 한다.
#    disaster_alert.event_id 가 disaster_events 를 참조해 disaster_events 는 TRUNCATE 할 수 없다
#    (TRUNCATE ... CASCADE 는 disaster_alert 까지 비우므로 절대 쓰지 않는다).
docker exec -i postgres psql -U postgres -d "$EVAL_DB_NAME" -c \
  "UPDATE disaster_alert SET event_id = NULL, event_sequence_no = NULL, event_similarity_score = NULL, event_merge_method = NULL, event_added_at = NULL WHERE event_id IS NOT NULL OR event_sequence_no IS NOT NULL; TRUNCATE disaster_event_translation, event_region_impact, region_risk_index; DELETE FROM disaster_events; SELECT setval(pg_get_serial_sequence('disaster_events', 'id'), 1, false);"

# 2) 다시 묶기 — 'EventClusteringBackfillTool 완료' 로그가 나오면 Ctrl+C
./gradlew bootRun --args="--spring.profiles.active=backfill --backfill.recluster=true \
  --spring.datasource.url=$EVAL_DB"

# 3) 위의 클러스터링 평가를 실행해 이전 리포트와 비교
```

임계값을 튜닝할 때는 `application.yml` 을 고치고 이 순서를 반복한다.

## 임베딩 백필

`embedding` 이 비어 있는 알림을 처리한다.

```bash
# 임베딩만 배치로 저장 (빠름, 클러스터링 안 함)
./gradlew bootRun --args="--spring.profiles.active=backfill --backfill.embed-only=true \
  --spring.datasource.url=$EVAL_DB"

# 옵션 없음: 1건씩 임베딩 + 클러스터링 (느림). --backfill.limit=N 으로 건수를 제한할 수 있다
./gradlew bootRun --args="--spring.profiles.active=backfill --backfill.limit=100 \
  --spring.datasource.url=$EVAL_DB"
```

`--backfill.embed-only` 는 빈 임베딩을 500건씩 한 요청으로 보낸다(`EventClusteringBackfillTool.EMBED_BATCH_SIZE`). 공개 모델 서버(TEI)는 `--max-client-batch-size 500` 이상으로 띄워야 한다(기본 32).

## 다른 임베딩 모델로 재클러스터링

임베딩 모델을 바꾸면 클러스터링이 어떻게 달라지는지 평가 사본에서 잰다. 2026-11-27 012 에서 이 순서로 다섯 모델을 비교했다(결과 [decision.md](../../specs/012-embedding-model-selection/decision.md), 절차 근거 [012 quickstart](../../specs/012-embedding-model-selection/quickstart.md)). 모델 호출은 모두 로컬 LiteLLM 프록시를 거쳐 실행마다 가상 키 하나로 비용을 나눈다. 비용·호출 수는 Grafana(`http://localhost:3001`, 대시보드 "LLM 사용량", 가상 키 필터)에서 본다.

**준비**

- 프록시·Prometheus·Grafana: `docker compose --env-file .env.dev -f docker-compose.dev.yml --profile llm-usage up -d litellm prometheus grafana`(레포 루트, [infra/llm-usage/README.md](../../infra/llm-usage/README.md)). 실행 중에는 이 compose 를 다시 올리지 않는다 — 프록시가 재생성되면 진행 중인 요청이 끊긴다.
- 가상 키: 실행마다 하나, 그 실행의 임베딩 모델과 `gpt-4o-mini`(LLM 판정)만 허용하고 예산 상한을 건다(012 는 `sk-012-run-<별칭>`, infra README 의 가상 키 절).
- 공개 모델이면 TEI 컨테이너를 띄운다. 명령·포트·지시문(`--default-prompt`, 줄바꿈이 든 문자열)은 [012 contracts 1절](../../specs/012-embedding-model-selection/contracts/experiment-cli.md) 그대로 쓴다.
- 아래 변수(예: qwen):

```bash
ALIAS=qwen3_06b; MODEL=Qwen/Qwen3-Embedding-0.6B; DIMS=1024; KEY=sk-012-run-$ALIAS
DB=disaster_alert_eval_e_$ALIAS
EMB="--spring.ai.openai.embedding.options.model=$MODEL --spring.ai.openai.embedding.options.dimensions=$DIMS"
PROXY="--spring.ai.openai.base-url=http://localhost:4000 --spring.ai.openai.api-key=$KEY"
psql_db() { docker exec -i postgres psql -v ON_ERROR_STOP=1 -U postgres -d "$1"; }
```

**1) 사본 만들기, 이벤트·임베딩 비우기** — 템플릿 사본(`disaster_alert_eval_v6`)에 접속한 세션이 없어야 한다.

```bash
echo "CREATE DATABASE $DB TEMPLATE disaster_alert_eval_v6;" | psql_db postgres
psql_db "$DB" <<SQL
UPDATE disaster_alert SET event_id = NULL, event_sequence_no = NULL, event_similarity_score = NULL, event_merge_method = NULL, event_added_at = NULL WHERE event_id IS NOT NULL OR event_sequence_no IS NOT NULL;
TRUNCATE disaster_event_translation, event_region_impact, region_risk_index;
DELETE FROM disaster_events;
SELECT setval(pg_get_serial_sequence('disaster_events', 'id'), 1, false);
-- 기준선에서 임베딩이 없던 알림은 후보에서도 빼야 처리 대상이 같다(재클러스터링은 embedding IS NOT NULL 만 처리한다)
CREATE TABLE e012_baseline_null AS SELECT disaster_alert_id FROM disaster_alert WHERE embedding IS NULL;
DROP INDEX idx_disaster_alert_embedding_hnsw;
UPDATE disaster_alert SET embedding = NULL;
ALTER TABLE disaster_alert ALTER COLUMN embedding TYPE vector($DIMS);   -- 1536 모델이면 생략
-- 채우기에서 빼려고 임시 0 벡터를 넣는다. 채운 뒤 다시 NULL 로 되돌린다
UPDATE disaster_alert SET embedding = array_fill(0::real, ARRAY[$DIMS])::vector WHERE disaster_alert_id IN (SELECT disaster_alert_id FROM e012_baseline_null);
SQL
```

**2) 임베딩 넣기** — 보관 DB(`embedding_store.alert_embedding`, 알림·모델 한 행씩)에 그 모델이 있으면 가져오고, 없으면 채운 뒤 보관한다.

```bash
# (가) 처음 쓰는 모델: 채우기 전에 같은 20건을 직접 호출·프록시로 채워 비교한다(프록시가 요청을 바꾸지 않는지, 거리 0.01 이하).
#     012 quickstart 2절 3 참고. 그다음 전체 채우기 — 'embed-only 완료' 로그가 나오면 Ctrl+C
./gradlew bootRun --args="--spring.profiles.active=backfill --spring.datasource.url=jdbc:postgresql://localhost:5432/$DB --backfill.embed-only=true $EMB $PROXY"
psql_db "$DB" <<SQL
UPDATE disaster_alert SET embedding = NULL WHERE disaster_alert_id IN (SELECT disaster_alert_id FROM e012_baseline_null);
SELECT count(embedding), string_agg(DISTINCT vector_dims(embedding)::text, ',') FROM disaster_alert;   -- 012 는 60382, 기대 차원 하나
COPY (SELECT disaster_alert_id, '$MODEL', $DIMS, embedding, now() FROM disaster_alert WHERE embedding IS NOT NULL) TO '/tmp/emb_$ALIAS.tsv';
SQL
psql_db embedding_store <<SQL
DELETE FROM alert_embedding WHERE model = '$MODEL';
COPY alert_embedding (alert_id, model, dimensions, embedding, created_at) FROM '/tmp/emb_$ALIAS.tsv';
SQL
docker exec postgres rm -f /tmp/emb_$ALIAS.tsv

# (나) 보관된 모델 다시 쓰기(API 호출 없음) — 2026-11-28 내보내기·불러오기 COPY 형식만 임시 DB 로 확인(qwen 60,382건, 1024차원).
#     사본에 실제로 넣어 재클러스터링한 적은 아직 없어, 처음 쓸 때 마지막 건수를 확인한다
echo "COPY (SELECT alert_id, embedding FROM alert_embedding WHERE model = '$MODEL') TO '/tmp/emb_$ALIAS.tsv';" | psql_db embedding_store
psql_db "$DB" <<SQL
UPDATE disaster_alert SET embedding = NULL WHERE disaster_alert_id IN (SELECT disaster_alert_id FROM e012_baseline_null);
CREATE TABLE e012_import (alert_id bigint PRIMARY KEY, embedding vector($DIMS));
COPY e012_import FROM '/tmp/emb_$ALIAS.tsv';
UPDATE disaster_alert d SET embedding = i.embedding FROM e012_import i WHERE d.disaster_alert_id = i.alert_id;
DROP TABLE e012_import;
SELECT count(embedding) FROM disaster_alert;
SQL
docker exec postgres rm -f /tmp/emb_$ALIAS.tsv
```

**3) 인덱스 재생성, 재클러스터링, 평가** — 거리 기준(`clustering.*`)은 운영 값 그대로 둔다.

```bash
echo "CREATE INDEX idx_disaster_alert_embedding_hnsw ON disaster_alert USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64);" | psql_db "$DB"
# 'EventClusteringBackfillTool 완료' 로그가 나오면 Ctrl+C (LLM 판정 비용이 그 키에 기록된다, 6만 건에 약 $0.05)
./gradlew bootRun --args="--spring.profiles.active=backfill --spring.datasource.url=jdbc:postgresql://localhost:5432/$DB --backfill.recluster=true $EMB $PROXY"
./gradlew bootRun --args="--spring.profiles.active=eval --spring.datasource.url=jdbc:postgresql://localhost:5432/$DB \
  --eval.dataset=cluster-eval-2026-11 --eval.bcubed=evaluation/datasets/bcubed-2026-05.labels.json \
  --eval.report=evaluation/reports/<날짜>-embed-$ALIAS.md"
```

- 기준선 모델(`text-embedding-3-small`)도 같은 방식으로 사본을 만들어 다시 돌린다. 저장된 임베딩을 그대로 쓰므로 1)의 임베딩 비우기와 2), 3)의 인덱스 재생성은 건너뛴다(HNSW 인덱스가 남아 있어 같은 이름으로 만들면 오류가 난다). 1)의 이벤트 비우기만 하고 3)의 재클러스터링·평가로 간다. v6 기존 배정과 이 재실행의 차이가 흔들림이다.
- 거리 눈금이 좁은 모델은 운영 값으로 다른 사건도 바로 묶인다(012 의 e5: 다른 사건 거리 중앙값 0.062, LLM 판정 12회). 결과가 크게 나쁘면 먼저 이 눈금을 본다.
- 운영 맥북에서 공개 모델을 재야 하면 Docker 안에서 메모리 상한을 걸어(`--memory 5g --max-batch-tokens 1024`) 돌린다. Docker 밖 실행은 메모리 상한이 없어 2026-11-28 운영 장애를 냈다(decision.md 맥북 측정).

## 사건/비사건 분류기 평가

사람 라벨과 실제 분류기를 대조해 결과를 로그로 남긴다. DB 에는 저장하지 않는다.

```bash
./gradlew bootRun --args="--spring.profiles.active=alert-kind-eval \
  --spring.datasource.url=$EVAL_DB \
  --alert-kind-eval.labels=evaluation/datasets/alert-kind-test-labels.json,evaluation/datasets/alert-kind-r-labels.json \
  --alert-kind-eval.override=evaluation/datasets/alert-kind-recheck-labels.json"
```

- `--alert-kind-eval.labels`: 필수. 쉼표로 여러 파일을 줄 수 있다.
- `--alert-kind-eval.override`: 재판정 라벨. 앞 파일보다 우선한다.

## 사건/비사건 분류 백필

`alert_kind` 가 비어 있는 알림을 분류해 저장한다. 중간에 멈춰도 같은 명령을 다시 실행하면 남은 알림만 이어서 처리한다.

```bash
# 평가 사본 등 로컬 DB — 소량으로 먼저 확인한 뒤 --alert-kind.limit 을 뺀다
./gradlew bootRun --args="--spring.profiles.active=alert-kind-backfill \
  --spring.datasource.url=$EVAL_DB \
  --alert-kind.limit=100 --alert-kind.threads=4"
```

운영 서버에서는 일회성 컨테이너로 돌린다. 위의 공통 준비와 상관없이 **운영 서버의 레포 루트**(`docker-compose.prod.yml` 이 있는 곳)에서 실행한다.

```bash
export OPENAI_API_KEY=<키>
docker compose -f docker-compose.prod.yml run -d --rm --no-deps \
  -e SPRING_PROFILES_ACTIVE=alert-kind-backfill \
  -e OPENAI_API_KEY \
  --entrypoint sh backend -c 'java $JAVA_OPTS -jar app.jar --alert-kind.limit=300 --alert-kind.threads=8'

docker logs -f <컨테이너>   # 'AlertKindBackfillTool 완료' 를 확인한 뒤
docker stop <컨테이너>
```

> **`SPRING_PROFILES_ACTIVE` 에 `prod` 를 넣지 않는다.** `SchedulingConfig` 가 `@Profile("prod")` 라서, 켜면 이 컨테이너에서도 수집·푸시 스케줄러가 돌아 알림이 중복 발송된다.

`--alert-kind.*` 옵션을 넘기려면 위처럼 `--entrypoint` 를 덮어써야 한다. 기본 ENTRYPOINT 는 인자를 앱에 전달하지 않는다.

## 법정동 매핑 백필

원본 지역명에 `전남광주통합특별시`가 들어 있고 법정동 매핑이 하나도 없는 알림만 다시 매칭한다. 다른 지역의 미매핑 알림은 대상이 아니다.

```bash
./gradlew bootRun --args="--spring.profiles.active=region-backfill"
```

## 누락 기간 알림 백필

공공 API 에서 지정 날짜 이후의 알림을 다시 받아 빠진 것만 저장한다. 이미 있는 알림은 건너뛰고, 푸시·번역·클러스터링은 실행하지 않는다.

```bash
BACKFILL_FROM_DATE=20260830 ./gradlew bootRun --args="--spring.profiles.active=alert-missing-backfill"
```

날짜는 실제 누락 구간보다 하루 여유를 두고 지정한다.

한 번에 최대 5만 건(1,000건씩 50페이지)만 조회하고, 응답이 비거나 파싱에 실패해도 멈춘 뒤 완료 로그를 남긴다. 끝나면 진행 로그의 `누적 조회 N/M` 에서 N 과 M 이 같은지 확인한다. 다르면 시작 날짜를 늦춰 다시 돌린다.

> 위 두 도구는 `--spring.datasource.url` 이 없으면 `.env.dev` 의 DB 에 저장한다. 다른 DB 에 넣으려면 그 옵션을 함께 준다.
