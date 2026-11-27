# 실행·검증 가이드: 클러스터링용 임베딩 모델 선정

**명세**: [spec.md](spec.md) | **도구 계약**: [contracts/experiment-cli.md](contracts/experiment-cli.md) | **결정 기록**: [research.md](research.md)

유료 단계(`--eval.approve-paid-embedding`, `3-large` 채우기, 재클러스터링)는 **사용자가 그때 명시적으로 "진행해"라고 한 뒤에만** 실행한다. 운영 DB·운영 앱은 어느 단계에서도 건드리지 않는다.

## 0. 준비

- 로컬 Postgres 와 평가 사본 `disaster_alert_eval_v6`(2026-11-20 확인: 알림 62,554건, 임베딩 60,382건).
- 모든 명령은 `backend/` 에서 `./gradlew bootRun --args='--spring.profiles.active=eval --spring.datasource.url=jdbc:postgresql://localhost:5432/<사본> …'` 형태다(006 quickstart 와 같음).
- 공개 모델은 TEI 컨테이너 3개를 띄운다([contracts](contracts/experiment-cli.md) 1절). `--max-client-batch-size 500` 을 빼면(기본 32) 채우기 도구의 500건 요청이 거부된다(2026-11-27 200 → 500).

## 1. 연결 시험 (무료)

- TEI 하나를 띄우고, 공개 모델 설정으로 `--eval.embedding-pairs=<별칭>` 을 돌려 본다. 확인: 1024차원 벡터가 오고, 채팅 설정은 OpenAI 그대로다. `dimensions`·임베딩 전용 `api-key` 가 안 먹으면 research R3 를 고친다.

## 2. 1단계 쌍 거리 실험

1. `--eval.embedding-pairs=stored` → 기준선 저장 거리(무료). 확인 값: 다른 사건 56쌍 중 0.15 이하 1쌍·0.40 이하 30쌍, 같은 사건 81쌍 중 19쌍·70쌍.
2. 공개 모델 3개 각각 `--eval.embedding-pairs=<별칭>`(무료).
3. `3small`·`3large` 를 `--eval.approve-paid-embedding` 없이 돌려 예상 비용 확인(1센트 미만) → 사용자 승인 → `--eval.approve-paid-embedding` 로 실행.
4. `--eval.embedding-pairs-summary=…` 로 리포트를 만든다. 확인:
   - 맨 위 **기준선 재현**: `3small` 과 `stored` 의 쌍별 차이 최댓값 0.01 이하(SC-002). 아니면 여기서 멈춘다.
   - 모델마다 두 지점의 "같은 사건 81쌍 중 M쌍"과 화재 값, 바로 병합 기준을 정한 다른 사건 쌍 두 개의 원문.
   - 바뀐 같은 사건 쌍 목록을 원문으로 몇 개 읽어 숫자가 실제 쌍과 맞는지 본다.
5. 거리 파일과 리포트를 `backend/evaluation/reports/embedding-pairs-<날짜>/` 에 커밋한다.
6. 기준선보다 LLM 후보 지점의 같은 사건 쌍을 더 넣은 후보 중 최대 2개를 2단계로 보낸다(사용자 확인). 없으면 6으로 간다(spec FR-008).

## 3. 병합 거리 설정화 (운영 동작 불변) — 2026-11-27 취소

거리 기준을 모든 모델에 운영 값(0.15·0.40·0.6)으로 고정해 이 단계는 하지 않는다(research R10). 아래 내용은 기록으로 남긴다.

- `clustering.merge-max-distance`(기본 0.15) Red → 사용자 Red 확인 → Green(research R7).

## 4. 2단계 재클러스터링 (유료, 사용자 승인 후)

> 2026-11-27 방향 전환(research R10): 기준선 포함 다섯 모델 모두, 거리 설정은 운영 값 그대로(아래의 `--clustering.merge-max-distance`·`distance-ceil`·`similarity-floor` 덮어쓰기는 하지 않는다), 호출은 `--spring.ai.openai.base-url=http://localhost:4000 --spring.ai.openai.api-key=sk-012-run-<별칭>` 로 프록시를 거친다. 채운 임베딩은 `embedding_store.alert_embedding` 으로 내보내고, 다시 쓸 때는 거기서 가져온다. 순서는 tasks T010·T011.

1. **흔들림 확인**(약 $0.3): v6 를 템플릿으로 `disaster_alert_eval_v6_rerun` 을 만들고 006 quickstart 3 절차로 재클러스터링 → 평가. 지금 라벨로 v6 자체도 평가(무료)해 둘을 비교한다(R8).
2. **후보마다**:
   - v6 템플릿으로 `disaster_alert_eval_e_<별칭>` 생성.
   - 1024차원 모델: HNSW 인덱스 삭제 → `UPDATE disaster_alert SET embedding = NULL` → `ALTER COLUMN embedding TYPE vector(1024)`. 1536(`3large`): `UPDATE … SET embedding = NULL` 만.
   - 후보 설정으로 `--backfill.embed-only=true`(`3large` 는 약 $0.8, 승인 후). `count(*)`·`count(embedding)` 확인.
   - 1024 사본은 인덱스 재생성: `CREATE INDEX idx_disaster_alert_embedding_hnsw ON disaster_alert USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64)`.
   - 재클러스터링(약 $0.3): 006 절차 + 후보 임베딩 설정 + `--clustering.merge-max-distance=<바로 병합 기준> --clustering.llm-fallback.distance-ceil=<LLM 후보 기준> --clustering.cross-region.similarity-floor=<1 − LLM 후보 기준>`.
   - 평가: `--eval.dataset=cluster-eval-2026-11 --eval.bcubed=evaluation/datasets/bcubed-2026-05.labels.json --eval.report=…`.
3. 확인: 후보 점수(균형 정확도) − 기준선 점수가 흔들림보다 큰가. 고쳐진 쌍·새로 틀린 쌍을 원문으로 읽는다.

## 5. 맥북 확인 (조건부)

- 공개 모델이 2단계에서 이겼을 때만: 맥북에서 TEI(`brew` Metal 또는 `cpu-arm64-1.9`)로 알림 1건 임베딩 시간·메모리 측정 — **운영 기기라 사용자 승인 후**.

## 6. 선정 기록

- `decision.md` 에 후보별 표(research R9 항목)를 채우고, 사용자가 판정한다(spec FR-014).
- 확인: 표의 비용이 단가 × 토큰으로 다시 계산되고(SC-004), 점수가 리포트 파일과 같다.
- 운영 교체는 이 기능에서 하지 않는다. 고른 모델이 있으면 교체는 새 명세로 연다.
