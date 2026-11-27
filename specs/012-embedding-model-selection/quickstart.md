# 실행·검증 가이드: 클러스터링용 임베딩 모델 선정

**명세**: [spec.md](spec.md) | **도구 계약**: [contracts/experiment-cli.md](contracts/experiment-cli.md) | **결정 기록**: [research.md](research.md)

유료 단계(`3-large` 채우기, 재클러스터링)는 **사용자가 그때 명시적으로 "진행해"라고 한 뒤에만** 실행한다. 운영 DB·운영 앱은 어느 단계에서도 건드리지 않는다. 1단계(쌍 거리 실험)는 2026-11-27 코드·결과·문서를 모두 지웠다(research R10).

## 0. 준비

- 로컬 Postgres 와 평가 사본 `disaster_alert_eval_v6`(2026-11-20 확인: 알림 62,554건, 임베딩 60,382건).
- LiteLLM 프록시·Prometheus·Grafana: `docker compose --env-file .env.dev -f docker-compose.dev.yml --profile llm-usage up -d litellm prometheus grafana`([infra/llm-usage/README.md](../../infra/llm-usage/README.md)). 실행별 가상 키 5개와 보관 DB `embedding_store.alert_embedding` 은 2026-11-27 만들어 두었다(tasks T009).
- 공개 모델은 TEI 컨테이너 3개를 띄운다([contracts](contracts/experiment-cli.md) 1절). `--max-client-batch-size 500` 을 빼면(기본 32) 채우기 도구의 500건 요청이 거부된다(2026-11-27 200 → 500).
- 모든 앱 명령은 `backend/` 에서 `./gradlew bootRun --args='--spring.profiles.active=<eval|backfill> --spring.datasource.url=jdbc:postgresql://localhost:5432/<사본> …'` 형태다(006 quickstart 와 같음). 모델 호출은 `--spring.ai.openai.base-url=http://localhost:4000 --spring.ai.openai.api-key=sk-012-run-<별칭>` 로 프록시를 거친다(contracts 2절).

## 1. 기준선 (유료 약 $0.3, 승인 후)

1. 지금 라벨로 v6 기존 배정을 평가(무료): `--spring.profiles.active=eval … disaster_alert_eval_v6 --eval.dataset=cluster-eval-2026-11 --eval.bcubed=evaluation/datasets/bcubed-2026-05.labels.json --eval.report=evaluation/reports/2026-11_<날짜>-embed-baseline-v6.md`.
2. `CREATE DATABASE disaster_alert_eval_e_3small TEMPLATE disaster_alert_eval_v6`(템플릿 DB 에 접속이 없어야 한다).
3. 006 quickstart 3 절차로 재클러스터링 — 키 `sk-012-run-3small`, 임베딩은 저장된 그대로, 거리 설정은 운영 값 그대로.
4. 1 과 같은 옵션으로 `disaster_alert_eval_e_3small` 평가 → `…-embed-3small.md`.
5. 확인: 두 리포트의 과병합·과소병합 차이 = 흔들림(research R8). LiteLLM 화면에서 키 `012-run-3small` 의 `gpt-4o-mini` 호출 수·비용, 실패 호출이 없는지.

## 2. 후보마다 (`3large`·`qwen3_06b`·`e5_large_instruct`·`bge_m3_ko`, 승인 후)

1. `CREATE DATABASE disaster_alert_eval_e_<별칭> TEMPLATE disaster_alert_eval_v6`.
2. 임베딩 비우기: HNSW 인덱스 삭제 → `UPDATE disaster_alert SET embedding = NULL` → 1024차원 모델이면 `ALTER TABLE disaster_alert ALTER COLUMN embedding TYPE vector(1024)`.
3. 임베딩 넣기 — 보관 DB 에 그 모델 행이 있으면 가져오고, 없으면 채운 뒤 보관한다.
   - 가져오기: `psql -d embedding_store -c "COPY (SELECT alert_id, embedding FROM alert_embedding WHERE model = '<model-id>') TO STDOUT"` 를 사본의 임시 표로 `COPY … FROM STDIN` → `UPDATE disaster_alert d SET embedding = t.embedding FROM <임시 표> t WHERE d.disaster_alert_id = t.alert_id`.
   - 채우기: `--spring.profiles.active=backfill --backfill.embed-only=true` + 프록시 주소·그 키 + `--spring.ai.openai.embedding.options.model=<model-id> --spring.ai.openai.embedding.options.dimensions=<1536|1024>`(`3large` 는 약 $0.8). 확인: `count(*)`·`count(embedding)`(본문이 빈 알림 제외).
   - 보관: `COPY (SELECT disaster_alert_id, '<model-id>', <차원>, embedding, now() FROM disaster_alert WHERE embedding IS NOT NULL) TO STDOUT` → `embedding_store` 의 `COPY alert_embedding FROM STDIN`.
4. 인덱스 재생성: `CREATE INDEX idx_disaster_alert_embedding_hnsw ON disaster_alert USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64)`.
5. 재클러스터링(약 $0.3): 006 절차 + 프록시 주소·그 키 + 같은 임베딩 모델 설정. 거리 설정은 운영 값 그대로.
6. 평가: 1 과 같은 옵션 → `backend/evaluation/reports/2026-11_<날짜>-embed-<별칭>.md`.
7. 확인(research R10 판정 기준): 기준선 대비 과병합·과소병합 둘 다 늘지 않고 하나라도 줄었나, 그 차이가 흔들림보다 큰가, 화재 쌍만 따로 셌을 때 과병합·과소병합이 늘지 않았나. 고쳐진 쌍·새로 틀린 쌍을 원문으로 읽는다.

## 3. 맥북 확인 (조건부)

- 공개 모델이 판정 기준을 통과했을 때만: 맥북에서 TEI(`brew` Metal 또는 `cpu-arm64-1.9`)로 알림 1건 임베딩 시간·메모리 측정 — **운영 기기라 사용자 승인 후**.

## 4. 선정 기록

- `decision.md` 에 모델별 표(research R9 항목 + 가상 키별 실제 비용)를 채우고, 판정 기준(research R10)으로 판정 초안을 적은 뒤 사용자가 정한다(spec FR-014).
- 확인: 표의 비용이 가상 키 기록·단가 × 토큰과 같고(SC-004·SC-007), 점수가 리포트 파일과 같다.
- 운영 교체는 이 기능에서 하지 않는다. 고른 모델이 있으면 교체는 새 명세로 연다.
