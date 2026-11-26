# 작업 목록: 클러스터링용 임베딩 모델 선정

**입력**: `specs/012-embedding-model-selection/` 의 설계 문서
**선행 문서**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/experiment-cli.md](contracts/experiment-cli.md), [quickstart.md](quickstart.md)

**테스트**: 새 순수 로직과 바뀌는 로직은 Red-Green 으로 한다(CLAUDE.md). Red 는 컴파일되고 실행되어 단언에서 실패해야 하며, `tdd-developer`(단계: RED)가 쓰고 스테이징한다(커밋은 사용자가 다음 단계를 말한 뒤, 2026-11-26 규칙). 사용자가 Red 를 확인하기 전에는 Green 을 쓰지 않는다. Red 테스트는 핵심 동작 2~4개만 둔다.

**유료 작업**: `[유료]` 작업은 예상 비용을 먼저 보이고, 사용자가 그때 "진행해"라고 한 뒤에만 실행한다.

## 형식: `[ID] [P?] [Story] 설명`

## 경로 규칙

- 백엔드 코드: `backend/src/main/java/com/disaster/alert/alertapi/domain/event/`
- 백엔드 테스트: `backend/src/test/java/com/disaster/alert/alertapi/domain/event/`
- 실험 결과: `backend/evaluation/reports/`
- 실행: `backend/` 에서 `./gradlew bootRun --args='--spring.profiles.active=eval --spring.datasource.url=jdbc:postgresql://localhost:5432/<사본> --server.port=0 …'`(006 quickstart 와 같은 방식)

---

## Phase 1: 설정

- [x] T001 연결 시험(2026-11-26 완료, 결과 research R3). TEI 컨테이너 하나(`Qwen/Qwen3-Embedding-0.6B`, 포트 8081)를 [contracts/experiment-cli.md](contracts/experiment-cli.md) 1절 명령으로 띄우고 `curl` 로 `/v1/embeddings` 가 1024차원을 돌려주는지 확인한다(`dimensions` 를 받는지 포함). GPU 이미지(`89-1.9`)가 안 뜨면 `cpu-1.9` 로 바꾼다. Spring AI 1.0.0 `OpenAiEmbeddingProperties` 가 `spring.ai.openai.embedding.base-url`·`api-key` 를 채팅과 따로 받는지 jar 에서 확인한다. 결과를 `specs/012-embedding-model-selection/research.md` R3·R4 에 적는다. 따로 받지 못하면 대안을 R3 에 적고 사용자에게 알린 뒤 멈춘다.

---

## Phase 2: 사용자 스토리 1 - 후보 모델이 같은 사건과 다른 사건을 더 잘 가르는지 싸게 본다 (우선순위: P1) 🎯 MVP

**목표**: 1단계 대상 137쌍(같은 사건 81·다른 사건 56)을 `stored` + 후보 5개로 재고 두 지점 비교 리포트를 만든다.

**독립 테스트**: 리포트의 기준선 재현(`3small` 과 `stored` 쌍별 차이 0.01 이하)과 기준선 두 지점 값(같은 사건 81쌍 중 19·70쌍, 다른 사건 56쌍 중 1·30쌍)이 나온다.

- [ ] T002 [US1] Red — `tdd-developer`(단계: RED)로 `backend/src/test/java/com/disaster/alert/alertapi/domain/event/evaluation/EmbeddingPairSubsetTest.java`(키 경로 유형이 한쪽이라도 있는 쌍·먼 시군구 쌍·사람 모르겠음 쌍이 빠지고 이유별로 세어짐)와 `.../evaluation/EmbeddingPairComparisonTest.java`(기준선이 기준 거리에서 붙이는 다른 사건 쌍 수 K 를 정하고, 후보 거리에서 K번째와 K+1번째 다른 사건 거리의 가운데 값을 기준으로 같은 사건 쌍 수를 셈, 바뀐 같은 사건 쌍을 돌려줌)를 쓰고, 뼈대 `backend/src/main/java/com/disaster/alert/alertapi/domain/event/evaluation/EmbeddingPairSubset.java`·`.../evaluation/EmbeddingPairComparison.java`(틀린 기본값 반환)와 함께 스테이징한다. 근거: research R1·R2, data-model 2절.

**체크포인트(멈춤)**: 사용자에게 `./gradlew test --tests "*EmbeddingPair*"` 로 Red 를 확인해 달라고 하고, "Red 확인했다"고 할 때까지 T003 으로 가지 않는다.

- [ ] T003 [US1] Green — `tdd-developer`(단계: GREEN, "사용자가 Red 를 확인했다")로 두 순수 클래스를 채운 뒤, 측정 모드를 더한다. `backend/src/main/java/com/disaster/alert/alertapi/domain/event/evaluation/EmbeddingPairRunner.java`(`@Profile("eval")`): `stored`(저장 임베딩 거리를 SQL 로), `<별칭>`(평가 사본 `disaster_alert.message` 를 주입받은 Spring AI `EmbeddingModel` 로 배치 임베딩 → 코사인 거리), 요약(CSV 들 → `EmbeddingPairComparison` → `embedding-pairs-summary.md`, 기준선 재현 실패면 맨 위 경고 + 종료 코드 1). 임베딩 주소가 `api.openai.com` 이고 `--eval.approve` 가 없으면 예상 비용만 출력하고 호출하지 않는다. `backend/src/main/java/com/disaster/alert/alertapi/domain/event/evaluation/EvaluationRunTool.java` 에 `--eval.embedding-pairs`, `--eval.embedding-pairs-summary`, `--eval.approve` 를 더한다(기존 `--eval.recheck`·`--eval.judge-agreement` 와 함께 주면 오류). 형식은 contracts 3절·data-model 2절. 변경이 100줄 이상이면 커밋 전에 `code-reviewer` 를 돌린다(`git diff` 와 줄 범위를 프롬프트에 넣어서).
- [ ] T004 [US1] 무료 측정 — `--eval.embedding-pairs=stored` 로 기준선 값을 확인하고, TEI 3개(`qwen3_06b` 8081, `e5_large_instruct` 8082, `bge_m3_ko` 8083, contracts 1절)를 띄워 contracts 2절 설정으로 각각 측정한다. 출력: `backend/evaluation/reports/embedding-pairs-<날짜>/embedding-pairs-<별칭>.csv`.
- [ ] T005 [US1] [유료 1센트 미만] `3small`·`3large` 를 `--eval.approve` 없이 돌려 예상 비용을 보이고, 승인 뒤 측정한다. 요약 모드로 `backend/evaluation/reports/embedding-pairs-<날짜>/embedding-pairs-summary.md` 를 만들어 기준선 재현과 기준선 두 지점 값을 확인하고, 결과 폴더를 커밋한다.
- [ ] T006 [US1] `embedding-pairs-summary.md` 결과를 "N쌍 중 M쌍"으로(화재 따로) 사용자에게 보고한다. 기준선보다 LLM 후보 지점의 같은 사건 쌍을 더 넣은 후보 중 2단계로 보낼 것(최대 2개)을 사용자가 정한다. 없으면 T012 로 간다(spec FR-008).

---

## Phase 3: 사용자 스토리 2 - 최종 후보로 실제 클러스터링을 다시 돌려 점수를 비교한다 (우선순위: P2)

**목표**: 최종 후보마다 평가 사본을 후보 임베딩으로 채워 재클러스터링하고 기준선과 같은 형식으로 점수를 낸다.

**독립 테스트**: 기준선 재실행의 흔들림(차이 쌍 수)과 후보 점수 차가 나란히 리포트에 있다.

- [ ] T007 [US2] Red — `tdd-developer`(단계: RED)로 `backend/src/test/java/com/disaster/alert/alertapi/domain/event/service/EventClusteringServiceMergeDistanceTest.java` 를 쓰고 스테이징한다: 설정이 없으면 거리 0.15 이하에 병합하고, `clustering.merge-max-distance=0.30` 이면 거리 0.20 에 병합한다. 뼈대: `EventClusteringService.java` 에 `@Value("${clustering.merge-max-distance:0.15}")` 필드만 더하고 판단은 아직 지역 변수 0.15 를 쓴다. 테스트 구성은 같은 폴더 기존 `EventClusteringService*Test` 를 따른다.

**체크포인트(멈춤)**: 사용자에게 `./gradlew test --tests "*EventClusteringServiceMergeDistanceTest"` 로 Red 확인을 받는다.

- [ ] T008 [US2] Green — `tdd-developer`(단계: GREEN, "사용자가 Red 를 확인했다")로 `backend/src/main/java/com/disaster/alert/alertapi/domain/event/service/EventClusteringService.java` 의 지역 변수 0.15 를 그 필드로 바꾸고, `CLAUDE.md` 이벤트 클러스터링 절의 "(설정이 아니라 코드에 고정)"을 "`clustering.merge-max-distance`(기본 0.15, 운영 설정 없음)"로 고친다. 운영 `application.yml` 에는 값을 넣지 않는다.
- [ ] T009 [US2] [유료 약 $0.3] 흔들림 확인 — 지금 라벨로 `disaster_alert_eval_v6` 를 평가(무료)하고, v6 를 템플릿으로 `disaster_alert_eval_v6_rerun` 을 만들어 006 quickstart 3 절차로 재클러스터링 → 평가한다. 리포트 `backend/evaluation/reports/2026-11_<날짜>-embed-baseline.md`·`…-embed-baseline-rerun.md` 를 커밋하고 차이 쌍 수를 흔들림으로 적는다.
- [ ] T010 [US2] [유료: `3large` 채우기 약 $0.8 + 재클러스터링 약 $0.3, 공개 모델은 재클러스터링만] 최종 후보마다 한 번: (1) `CREATE DATABASE disaster_alert_eval_e_<별칭> TEMPLATE disaster_alert_eval_v6`. 1024차원이면 HNSW 인덱스 삭제 → 임베딩 NULL → `vector(1024)`, `3large` 면 임베딩 NULL 만. (2) 후보 임베딩 설정으로 `EventClusteringBackfillTool --backfill.embed-only=true`, `count(*)`·`count(embedding)` 확인, 1024 사본은 HNSW 인덱스 재생성. (3) 006 재클러스터링 절차 + 후보 임베딩 설정 + `--clustering.merge-max-distance=<바로 병합 기준> --clustering.llm-fallback.distance-ceil=<LLM 후보 기준> --clustering.cross-region.similarity-floor=<1 − LLM 후보 기준>`(기준은 T005 리포트). (4) `--eval.dataset=cluster-eval-2026-11 --eval.bcubed=evaluation/datasets/bcubed-2026-05.labels.json` 로 평가해 `backend/evaluation/reports/2026-11_<날짜>-embed-<별칭>.md` 에 쓴 설정과 함께 커밋한다. 근거: research R6·R7, quickstart 4.
- [ ] T011 [US2] `backend/evaluation/reports/2026-11_<날짜>-embed-<별칭>.md` 의 후보 점수 − 기준선 점수를 T009 흔들림과 비교해 보고한다(흔들림 안이면 "차이 없음", spec FR-012). 고쳐진 쌍·새로 틀린 쌍을 원문으로 몇 개 보인다.

---

## Phase 4: 사용자 스토리 3 - 성능과 비용을 한 표로 보고 선정한다 (우선순위: P3)

**목표**: 후보별 성능·비용·운영 부담 표로 사용자가 고른다.

**독립 테스트**: 표의 비용이 단가 × 토큰으로 다시 계산되고, 점수가 리포트 파일과 같다(spec SC-004).

- [ ] T012 [US3] `specs/012-embedding-model-selection/decision.md` 를 쓴다. 후보별 한 줄: 1단계 두 지점, 2단계 점수(했으면), 1회 전환 비용·매일 비용(평가 사본 알림 62,554건과 T004·T005 의 실제 토큰으로 추정하고 추정임을 밝힘), 스키마·코드 변경, 운영 부담, 헌법 개정 필요 여부(research R9). 공개 모델이 2단계에서 이겼으면 사용자 승인 뒤 맥북에서 TEI 로 알림 1건 임베딩 시간·메모리를 재어 함께 적는다(spec FR-001a). 버린 후보마다 이유 한 줄. 사용자에게 표를 보이고, 판정과 이유(표의 숫자로)를 받아 적은 뒤 커밋한다(spec FR-014·FR-015). 운영 교체는 하지 않는다.

---

## Phase 5: 마무리

- [ ] T013 [P] `backend/evaluation/README.md` 에 측정 모드(`--eval.embedding-pairs`, `--eval.embedding-pairs-summary`, `--eval.approve`)와 TEI 실행 예시를 더한다.
- [ ] T014 끝나면 쓰지 않는 평가 사본(`disaster_alert_eval_v6_rerun`, `disaster_alert_eval_e_*`)과 TEI 컨테이너를 지울지 사용자에게 묻고 정한 대로 한다(지우기 전 대상 확인). 정리 결과를 `specs/012-embedding-model-selection/decision.md` 끝에 한 줄로 적는다.

---

## 의존성 및 실행 순서

- **T001** → **US1**: T002 → (사용자 Red 확인) → T003 → T004 → T005 → T006.
- **US2**: T006 에서 후보가 정해진 뒤. T007 → (사용자 Red 확인) → T008 → T009 → 후보마다 T010 → T011.
- **US3**: US1 만 끝났어도 가능(2단계 없이 "지금 모델 유지"). US2 를 했으면 그 뒤.
- **T013**: T003 이후 언제든. **T014**: 마지막.

### 병렬 처리 가능 지점

- T004 의 TEI 컨테이너 3개는 함께 띄울 수 있다(GPU 6GB 가 모자라면 하나씩). 측정은 같은 사본을 쓰므로 차례로 돌린다.
- T013 은 다른 작업과 병렬.
- T010 의 후보 두 개는 GPU·비용 승인 때문에 하나씩 한다.

## 구현 전략

### MVP 우선 (사용자 스토리 1만)

1. T001 연결 시험 → US1(T002~T006). 비용 1센트 미만.
2. **멈추고 확인**: 낫는 후보가 없으면 T012 로 가서 "지금 모델 유지"와 근거 쌍을 기록하고 끝낸다.

### 점진적 전달

US1(1단계 리포트) → US2(최종 후보 재클러스터링) → US3(선정 기록). 각 단계 결과가 리포트로 커밋된다.

## 참고

- 운영 DB·운영 앱·운영 `application.yml` 값은 어느 작업에서도 바꾸지 않는다(spec FR-018).
- 지표는 "N쌍 중 M쌍", 두 비율(맞게 묶은 비율·빠짐없이 묶은 비율), 점수만 쓴다(spec FR-007). 점수는 균형 정확도다(013, research R8).
- 커밋은 `commit-message` 스킬로 하고, 푸시는 사용자가 요청할 때만 한다.
