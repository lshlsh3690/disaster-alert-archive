# 백필·평가 실행 명령어

수동으로 돌리는 도구의 명령어 모음이다. 평가 결과를 읽는 법은 [quickstart.md](../../specs/006-clustering-evaluation/quickstart.md)를 본다.

| 하려는 일 | 프로필 | OpenAI 비용 | 끝나면 |
|---|---|---|---|
| [클러스터링 평가](#클러스터링-평가) | `eval` | 없음 | 자동 종료 |
| [재클러스터링](#재클러스터링) | `backfill` | 애매한 사고성 건만 LLM 호출 | 직접 종료 |
| [임베딩 백필](#임베딩-백필) | `backfill` | 임베딩 호출 | 직접 종료 |
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

**`--spring.datasource.url` 을 빼면 `.env.dev` 의 개발 DB 에 붙는다.** 평가와 재클러스터링은 반드시 평가 사본을 지정한다. `--server.port=0` 은 띄워 둔 다른 서버와 포트가 겹치지 않게 한다.

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
  --spring.datasource.url=$EVAL_DB --server.port=0 \
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

### DB 에 저장된 불일치 쌍 사람 라벨로 평가

관리자 대시보드에서 매긴 불일치 쌍 라벨(`clustering_pair_label`)을 파일 없이 바로 정답으로 쓴다. 평가 사본은 운영 덤프로 만들어 라벨 테이블이 함께 들어온다.

```bash
./gradlew bootRun --args="--spring.profiles.active=eval \
  --spring.datasource.url=$EVAL_DB --server.port=0 \
  --eval.pair-labels-as-of=2026-11-25T00:00 \
  --eval.report=evaluation/reports/<새 리포트 이름>.md"
```

- `--eval.pair-labels-as-of`: ISO 시각(KST). 이 시각까지 저장·수정된 라벨만 쓴다 — 튜닝 전후 비교가 같은 라벨로 돌도록 매번 같은 시각을 준다. `--eval.dataset` 과 함께 주면 오류.
- 리포트 머리의 데이터셋 이름은 `불일치 쌍 사람 라벨(DB, 기준 <시각>, N쌍)` 이다. 이전 리포트와 N 이 다르면 그사이 라벨이 바뀐 것이다(라벨은 덮어쓰기라 기준 시각 뒤에 고친 라벨은 옛 값으로 돌아가지 않고 빠진다).
- 라벨이 0개면 채점하지 않고 0 이 아닌 종료 코드로 끝난다.
- 틀린 쌍 표는 `(알림 A id, 알림 B id)` 오름차순으로 나온다. 채점 이후 `--eval.recheck`·`--eval.judge-agreement` 등은 파일 모드와 똑같이 쓴다.

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
  --spring.datasource.url=$EVAL_DB --server.port=0"

# 3) 위의 클러스터링 평가를 실행해 이전 리포트와 비교
```

임계값을 튜닝할 때는 `application.yml` 을 고치고 이 순서를 반복한다.

## 임베딩 백필

`embedding` 이 비어 있는 알림을 처리한다.

```bash
# 임베딩만 배치로 저장 (빠름, 클러스터링 안 함)
./gradlew bootRun --args="--spring.profiles.active=backfill --backfill.embed-only=true \
  --spring.datasource.url=$EVAL_DB --server.port=0"

# 옵션 없음: 1건씩 임베딩 + 클러스터링 (느림). --backfill.limit=N 으로 건수를 제한할 수 있다
./gradlew bootRun --args="--spring.profiles.active=backfill --backfill.limit=100 \
  --spring.datasource.url=$EVAL_DB --server.port=0"
```

## 사건/비사건 분류기 평가

사람 라벨과 실제 분류기를 대조해 결과를 로그로 남긴다. DB 에는 저장하지 않는다.

```bash
./gradlew bootRun --args="--spring.profiles.active=alert-kind-eval \
  --spring.datasource.url=$EVAL_DB --server.port=0 \
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
  --spring.datasource.url=$EVAL_DB --server.port=0 \
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
./gradlew bootRun --args="--spring.profiles.active=region-backfill --server.port=0"
```

## 누락 기간 알림 백필

공공 API 에서 지정 날짜 이후의 알림을 다시 받아 빠진 것만 저장한다. 이미 있는 알림은 건너뛰고, 푸시·번역·클러스터링은 실행하지 않는다.

```bash
BACKFILL_FROM_DATE=20260830 ./gradlew bootRun --args="--spring.profiles.active=alert-missing-backfill --server.port=0"
```

날짜는 실제 누락 구간보다 하루 여유를 두고 지정한다.

한 번에 최대 5만 건(1,000건씩 50페이지)만 조회하고, 응답이 비거나 파싱에 실패해도 멈춘 뒤 완료 로그를 남긴다. 끝나면 진행 로그의 `누적 조회 N/M` 에서 N 과 M 이 같은지 확인한다. 다르면 시작 날짜를 늦춰 다시 돌린다.

> 위 두 도구는 `--spring.datasource.url` 이 없으면 `.env.dev` 의 DB 에 저장한다. 다른 DB 에 넣으려면 그 옵션을 함께 준다.
