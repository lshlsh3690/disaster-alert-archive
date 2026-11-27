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
- 실행: `backend/` 에서 `./gradlew bootRun --args='--spring.profiles.active=eval --spring.datasource.url=jdbc:postgresql://localhost:5432/<사본> …'`(006 quickstart 와 같은 방식)

---

## Phase 1: 설정

- [x] T001 연결 시험(2026-11-26 완료, 결과 research R3). TEI 컨테이너 하나(`Qwen/Qwen3-Embedding-0.6B`, 포트 8081)를 [contracts/experiment-cli.md](contracts/experiment-cli.md) 1절 명령으로 띄우고 `curl` 로 `/v1/embeddings` 가 1024차원을 돌려주는지 확인한다(`dimensions` 를 받는지 포함). GPU 이미지(`89-1.9`)가 안 뜨면 `cpu-1.9` 로 바꾼다. Spring AI 1.0.0 `OpenAiEmbeddingProperties` 가 `spring.ai.openai.embedding.base-url`·`api-key` 를 채팅과 따로 받는지 jar 에서 확인한다. 결과를 `specs/012-embedding-model-selection/research.md` R3·R4 에 적는다. 따로 받지 못하면 대안을 R3 에 적고 사용자에게 알린 뒤 멈춘다.

---

## Phase 2: (삭제) 1단계 쌍 거리 실험

2026-11-27 사용자 결정으로 다섯 모델 모두 재클러스터링하기로 바꾸며, 1단계 측정 코드·테스트·결과·문서를 모두 지웠다(옛 T002~T006 완료 후 삭제, git 이력: fd67f476·67a78d85·77611dda, research R10).

---

## Phase 3: 사용자 스토리 2 - 다섯 모델 모두 실제 클러스터링을 다시 돌려 점수를 비교한다 (우선순위: P2)

**목표**: 기준선(`3small`)과 후보 4개를 운영과 같은 거리 기준(0.15·0.40·0.6)으로 모두 재클러스터링하고, 같은 형식으로 점수를 낸다. 임베딩은 보관 DB 에 남겨 재사용하고, 실행마다 LiteLLM 가상 키로 비용을 나눠 기록한다(research R10).

**독립 테스트**: 기준선 재실행의 흔들림(v6 기존 배정 대비 점수 차)과 후보 점수 차가 나란히 리포트에 있고, 실행별 실제 비용이 가상 키 기록과 같다.

- [x] T007 [US2] (2026-11-27 확정 — 둘 다 나빠지지 않아야 나아짐, 화재도 따로 세어 둘 중 하나라도 늘면 탈락, research R10) 판정 기준의 미정 항목(최소 개선 폭, 과병합이 늘면 탈락 여부, 화재가 나빠지면 탈락 여부, 흔들림을 넘었을 때 성능·비용 맞바꿈)을 사용자와 정해 `specs/012-embedding-model-selection/research.md` R10 에 적는다. **재클러스터링 결과를 보기 전에** 정한다(spec FR-014).
- [x] T008 [US2] (2026-11-27 완료) 백필 묶음 크기 200 → 500(2026-11-27 사용자 결정): `backend/src/main/java/com/disaster/alert/alertapi/domain/event/tool/EventClusteringBackfillTool.java` 의 `EMBED_BATCH_SIZE` 와 javadoc, contracts 1절 TEI 명령과 quickstart 의 `--max-client-batch-size` 를 500 으로. TEI 컨테이너 3개를 `--max-client-batch-size 500` 으로 다시 띄운다(모델 캐시 재사용). 상수 변경이라 Red-Green 대상 아님.
- [x] T009 [US2] (2026-11-27 완료 — 허용 모델 임베딩 성공·허용 밖 모델 거부 확인, `gpt-4o-mini` 실호출은 유료라 T010 첫 실행에서 확인) 무료 준비: (1) `infra/llm-usage/litellm-config.yaml` 에 `gpt-4o-mini`(LLM 폴백)를 등록하고 프록시 재시작. (2) R10 표대로 가상 키 5개 발급(키 값 지정, 허용 모델, 예산 상한) — `/key/generate`. (3) 보관 DB `embedding_store` 와 표 `alert_embedding` 생성(R10). (4) 키 하나로 임베딩·`gpt-4o-mini` 를 각 1회 불러 허용 모델·기록이 맞는지, 허용 밖 모델이 거부되는지 확인.
- [x] T010 [US2] (2026-11-27 완료 — v6 과소 16·과병 11, 재실행 과소 17·과병 12(화재 8·8), 새로 틀린 2쌍 모두 LLM 구간 → 흔들림 각 1쌍, LLM 폴백 961회 $0.049) [유료 약 $0.3] 기준선: 지금 라벨로 v6 기존 배정을 평가(무료) → `CREATE DATABASE disaster_alert_eval_e_3small TEMPLATE disaster_alert_eval_v6` → 006 quickstart 3 재클러스터링(프록시 주소 + 키 `sk-012-run-3small`, 거리 설정 운영 값) → `--eval.dataset=cluster-eval-2026-11 --eval.bcubed=evaluation/datasets/bcubed-2026-05.labels.json` 평가. 리포트 `backend/evaluation/reports/2026-11_<날짜>-embed-baseline-v6.md`·`…-embed-3small.md` 를 커밋하고, 두 점수 차이를 흔들림으로 적는다. LLM 폴백 판정 수·실패 수가 프록시를 거치기 전과 같은 수준인지 확인한다.
- [ ] T011 [US2] [유료: `3large` 채우기 약 $0.8 + 재클러스터링 약 $0.3, 공개 모델은 재클러스터링만] 후보 4개마다 한 번(실행 전 예상 비용을 보이고 승인): (1) `CREATE DATABASE disaster_alert_eval_e_<별칭> TEMPLATE disaster_alert_eval_v6`. (2) 보관 DB 에 그 모델 행이 있으면 `COPY` 로 가져와 `disaster_alert.embedding` 갱신, 없으면 HNSW 인덱스 삭제 → 임베딩 NULL → (1024 모델이면) `vector(1024)` → v6 에서 비어 있던 2,172건은 임시 0 벡터로 채우기에서 빼기(기준선과 같은 60,382건, quickstart 2) → **채우기 전 비교 확인**(같은 20건을 직접 호출·프록시로 채워 같은 알림 두 �ector 거리 0.01 이하, quickstart 2·research R11) → `--backfill.embed-only=true`(프록시 + 그 키) → `count(*)`·`count(embedding)`, 저장 차원·프록시 기록의 모델 대조 → `(alert_id, embedding)` 을 보관 DB 로 `COPY`. (3) HNSW 인덱스 재생성. (4) 재클러스터링(프록시 + 그 키, 거리 설정 운영 값). (5) T010 과 같은 옵션으로 평가, `backend/evaluation/reports/2026-11_<날짜>-embed-<별칭>.md` 커밋. 근거: research R6·R10, quickstart 4.
- [ ] T012 [US2] 다섯 리포트의 점수·두 비율을 T010 흔들림과 나란히 보고한다(흔들림 안이면 "차이 없음", spec FR-012). 가상 키별 실제 비용(채우기·LLM 폴백)과 고쳐진 쌍·새로 틀린 쌍 원문 몇 개를 함께 보인다. 눈금이 다른 모델(e5 등)은 research R10 의 거리 분포로 이유를 밝힌다.

---

## Phase 4: 사용자 스토리 3 - 성능과 비용을 한 표로 보고 선정한다 (우선순위: P3)

**목표**: 후보별 성능·비용·운영 부담 표로 사용자가 고른다.

**독립 테스트**: 표의 비용이 가상 키 기록·단가 × 토큰과 같고, 점수가 리포트 파일과 같다(spec SC-004·SC-007).

- [ ] T013 [US3] `specs/012-embedding-model-selection/decision.md` 를 쓴다. 모델별 한 줄: 재클러스터링 점수와 흔들림 대비 차이, 1회 전환 비용·매일 비용(평가 사본 알림 62,554건과 실제 토큰으로 추정하고 추정임을 밝힘), 실행별 실제 비용(가상 키), 스키마·코드 변경, 운영 부담, 헌법 개정 필요 여부(research R9). 공개 모델이 이겼으면 사용자 승인 뒤 맥북에서 TEI 로 알림 1건 임베딩 시간·메모리를 재어 함께 적는다(spec FR-001a). T007 에서 정한 판정 기준으로 판정 초안을 적고, 사용자 판정과 이유를 받아 적은 뒤 커밋한다(spec FR-014·FR-015). 운영 교체는 하지 않는다.

---

## Phase 5: 마무리

- [ ] T014 [P] `backend/evaluation/README.md` 에 보관 DB 재사용, 프록시·가상 키로 채우기·재클러스터링하는 예시를 더한다.
- [ ] T015 끝나면 평가 사본(`disaster_alert_eval_e_*`), 보관 DB(`embedding_store`), TEI 컨테이너, 가상 키를 남길지 사용자에게 묻고 정한 대로 한다(지우기 전 대상 확인). 결과를 `decision.md` 끝에 한 줄로 적는다.

---

## 의존성 및 실행 순서

- **T001** 완료. 1단계(옛 T002~T006)는 삭제.
- **US2**: T007(판정 기준, 결과 보기 전) → T008·T009(무료 준비, 병렬 가능) → T010(기준선, 흔들림) → 후보마다 T011 → T012.
- **US3**: T012 뒤 T013.
- **T014**: T011 이후 언제든. **T015**: 마지막.

### 병렬 처리 가능 지점

- T008 과 T009 는 서로 독립이다.
- T011 의 공개 모델 채우기는 TEI 컨테이너 3개가 함께 떠 있으면 차례로 바로 돈다. 재클러스터링은 LLM 비용 승인 때문에 하나씩 한다.

## 구현 전략

T007 판정 기준 확정 → 무료 준비 → 기준선 → 후보 4개 → 보고 → 선정 기록. 각 실행의 리포트와 비용이 커밋·프록시 기록으로 남는다.

## 참고

- 운영 DB·운영 앱·운영 `application.yml` 값과 운영 코드는 어느 작업에서도 바꾸지 않는다(spec FR-010·FR-018). 거리 기준은 운영 값 그대로다(R10, 옛 T007·T008 병합 거리 설정화는 취소).
- 지표는 "N쌍 중 M쌍", 두 비율(맞게 묶은 비율·빠짐없이 묶은 비율), 점수만 쓴다(spec FR-007). 점수는 균형 정확도다(013, research R8).
- 커밋은 `commit-message` 스킬로 하고, 푸시는 사용자가 요청할 때만 한다.
