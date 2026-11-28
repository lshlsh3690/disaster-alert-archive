# 결정 기록: 클러스터링용 임베딩 모델 선정

**날짜**: 2026-11-20 | **명세**: [spec.md](spec.md) | **후보 선정 근거**: [candidate-selection.md](candidate-selection.md)

각 항목은 결정 · 이유 · 검토한 대안 순서다.

## R1·R2. (삭제) 1단계 쌍 거리 실험의 대상 쌍·두 지점 비교

2026-11-27 다섯 모델 모두 재클러스터링하기로 바꾸며 1단계 코드·결과·문서를 지웠다(R10). 내용은 git 이력(d94635f3 이전)에 있다.

## R3. 실험은 Java 로 한다 — 공개 모델은 TEI 컨테이너에 띄우고 지금의 Spring AI 클라이언트로 부른다

- **결정**: 공개 모델은 Hugging Face TEI(text-embeddings-inference) Docker 컨테이너로 띄운다. TEI 는 OpenAI 와 같은 형식의 `/v1/embeddings` 주소를 연다. 백엔드는 운영 클러스터링이 쓰는 Spring AI `EmbeddingModel` 빈을 그대로 쓰고, 설정만 바꿔(`spring.ai.openai.embedding.base-url`·`options.model`·`options.dimensions`) 모델을 고른다. 채우기는 기존 `EventClusteringBackfillTool --backfill.embed-only=true` 다.
- **이유**: (1) 실험이 운영과 **같은 임베딩 호출 코드**를 탄다(006 R1 의 원칙과 같다). (2) 새 라이브러리가 없다. TEI 는 개발용 컨테이너이고 앱 의존성이 아니다. (3) 공개 모델이 선정되면 운영도 같은 방식(추론 서버 + 같은 클라이언트)으로 붙일 수 있어 실험 결과가 그대로 이어진다.
- **확인(2026-11-20, TEI README)**: 지원 구조에 Qwen3(Qwen3-Embedding)·XLM-RoBERTa(multilingual-e5-large-instruct)·BERT 가 있다. 이미지: x86 CPU `cpu-1.9`, Ada(RTX 40 시리즈) `89-1.9`, arm64 CPU `cpu-arm64-1.9`. Apple Silicon 의 Metal 은 Docker 로는 안 되고 `brew install text-embeddings-inference` 로 쓴다. 지시문 옵션 `--default-prompt` 가 있다.
- **TEI 를 쓰는 근거(2026-11-26 정리)**: 후보 셋은 OpenAI API 로 부를 수 없는 공개 모델이고 Java 앱이 Hugging Face 모델을 직접 읽지 못하므로, 모델을 올려 줄 추론 서버가 필요하다.

  | 근거 | 확인 상태(2026-11-26) |
  |---|---|
  | 후보를 허깅페이스 MTEB 리더보드를 보고 정했고, 허깅페이스가 안내하는 실행 방법이 TEI 도커 컨테이너라 그대로 따른다(사용자 근거, 첫째 이유) | Qwen3-Embedding-0.6B 모델 카드에 "Text Embeddings Inference (TEI) Usage"(`docker run`) 절 확인. e5-large-instruct·BGE-m3-ko 는 카드 본문에는 없고 세 모델 모두 `text-embeddings-inference` 태그 확인 |
  | OpenAI 와 같은 `/v1/embeddings` 형식이라 운영과 같은 Spring AI 호출 코드로 잰다 | HTTP 호출 확인, Spring AI 1.0.0 `OpenAiEmbeddingProperties` 가 임베딩 `base-url`·`api-key` 를 채팅과 따로 받는 것 확인. Java 로 실제 호출은 T003 에서 확인 |
  | 후보 세 모델의 구조(Qwen3·XLM-RoBERTa·BERT)를 모두 지원 | Qwen3 기동 확인, 나머지 둘은 README 확인(11-20) |
  | 모델별 사용법(마지막 토큰 풀링, 지시문)을 서버가 처리해 Java 코드가 모델마다 갈라지지 않음 | `/info` 의 `pooling: last_token` 확인 |
  | 개발 PC 에서 충분히 빠름 | Qwen3-0.6B 200건 1.3초(13,000토큰), GPU 메모리 약 1.3GB |

  단점: 이미지·모델 다운로드 몇 GB, 한 요청 입력 수 기본 32건 제한(아래), 공개 모델이 선정되면 운영 맥북에도 추론 서버가 떠 있어야 한다(맥북은 Docker GPU 를 못 써 brew Metal 또는 CPU 이미지, 속도는 아직 안 잼). 아래 대안 (나)(다)는 직접 돌려 비교한 것이 아니라 문서로 판단했다.
- **연결 시험 결과(2026-11-26, T001)**: `text-embeddings-inference:89-1.9`(1.9.4) + `--gpus all` 로 Qwen3-0.6B 기동. `/v1/embeddings` 가 1024차원을 돌려주고 `dimensions`·`encoding_format` 을 받는다. **한 요청의 입력은 기본 32건까지**인데 `EventClusteringBackfillTool` 은 200건씩 보내므로 TEI 에 `--max-client-batch-size 200` 을 줘야 한다(200건 요청 성공 확인).
- **11-20 에 구현 때 확인하기로 한 것(위 결과로 해결)**: Spring AI 1.0 OpenAI 클라이언트가 TEI 에 `dimensions` 를 보내도 되는지(1024 모델은 `--spring.ai.openai.embedding.options.dimensions=1024` 로 맞춘다), 임베딩 전용 `api-key` 를 따로 줄 수 있는지(TEI 는 키가 필요 없고, 채팅 LLM 폴백은 진짜 OpenAI 키를 써야 한다). 안 되면 그때 결정 기록을 고친다.
- **대안**: (가) Python + sentence-transformers — 이 plan 의 첫 판이었다. 운영과 다른 코드로 재게 돼 2026-11-20 사용자 지시로 기각. (나) Spring AI Transformers(ONNX) — 모델마다 ONNX 변환이 필요하고, Qwen3 의 마지막 토큰 풀링을 맞추기 어렵다. (다) Ollama — 후보 중 공식 배포가 있는 것이 일부뿐이라(BGE-m3-ko 없음) 변환이 필요하다.

## R4. 실행 환경과 비용

- **개발 PC**: NVIDIA RTX 4050 Laptop(6GB), Docker. TEI GPU 이미지(`89-1.9`)로 0.6B 모델을 돌린다. Docker 의 GPU 사용이 안 되면 CPU 이미지(`cpu-1.9`)로 돌린다(6만 건 채우기에 시간이 더 걸린다).
- **단가(2026-11-20, OpenAI 가격표 https://developers.openai.com/api/docs/pricing)**: `text-embedding-3-small` $0.02, `text-embedding-3-large` $0.13 / 100만 입력 토큰.
- **재클러스터링(2026-11-27 R10: 다섯 모델 모두)**: 평가 사본 알림 62,554건 재임베딩(`3-large` 약 $0.8 상한, 공개 모델 0원), 재클러스터링 LLM 폴백 1회 약 $0.3(2026-11-12 호우 튜닝 980회 실측) × 5회. 총 약 $2.2~2.3.
- **승인**: `embed-only` 채우기(`3-large`)와 재클러스터링은 예상 비용을 보이고 사용자가 명시 승인한 뒤에만 돌린다(spec FR-016). 실행별 가상 키의 예산 상한이 그 금액을 넘는 호출을 막는다(R10).

## R5. 모델 입력 형식

- **결정**: 백엔드는 지금처럼 알림 본문을 그대로 보낸다(가공·정규화 없음). 모델 카드가 요구하는 지시문은 TEI 의 `--default-prompt` 로 서버에서 붙인다. 지시문 문장은 하나로 고정하고 바꿔 가며 재지 않는다.
  - `intfloat/multilingual-e5-large-instruct`, `Qwen/Qwen3-Embedding-0.6B`: `--default-prompt "Instruct: Given a Korean emergency alert message, retrieve alerts that report the same incident
Query: "`. 대칭 비교라 모든 문장에 같은 지시문이 붙는다.
  - `dragonkue/BGE-m3-ko`: 지시문 없음. OpenAI 두 모델: 해당 없음.
- **차원**: `3-large` 는 `dimensions=1536`(스키마 그대로). 공개 모델은 기본 1024.
- **이유**: 지시문은 이 모델들의 정상 사용법이고, 본문 가공(2026-10-11 "정규화 계층 안 만듦")과 다르다. 서버에서 붙이면 Java 코드가 모델마다 갈라지지 않는다. 지시문을 여러 개 시험하면 272쌍에 맞춘 튜닝이 된다.
- **운영 교체 시**: 같은 서버 옵션을 운영 추론 서버에도 줘야 같은 거리가 나온다. 전환 작업 목록(FR-013)에 넣는다.

## R6. 2단계 재클러스터링 — 기존 백필 도구로 사본 임베딩을 채운 뒤 재클러스터링한다 (2026-11-27 R10 으로 보완: 채운 임베딩은 보관 DB 에 내보내 재사용, 호출은 프록시·실행별 가상 키)

- **결정**: 후보마다 평가 사본(`disaster_alert_eval_v6`)을 템플릿으로 새 사본을 만들고, 임베딩을 모두 비운다. 임베딩 설정을 후보 모델로 바꿔 `EventClusteringBackfillTool --backfill.embed-only=true` 로 채운 뒤(비어 있는 행만 배치로 임베딩), 같은 설정으로 기존 재클러스터링 절차(006 quickstart 3)를 돌린다.
- **근거(코드)**: `embed-only` 는 `embedding IS NULL` 인 알림을 배치로 `EventClusteringService.storeEmbeddings` 에 넘긴다. 재클러스터링의 `ensureEmbedding`·`storeEmbeddingBestEffort` 는 저장된 임베딩이 있으면 다시 부르지 않는다. 새 채우기 코드가 필요 없다.
- **1024차원 사본**: V104(512 → 1536)와 같은 순서다. HNSW 인덱스 삭제 → 임베딩 NULL → 컬럼 `vector(1024)` → `embed-only` 로 채우기 → 인덱스 재생성(`hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64)`). 채우기 전에 인덱스를 지워야 대량 갱신이 빠르다(2026-11-19 실측: 인덱스가 있으면 건당 약 1.6ms).
- **안전장치**: 채운 뒤 `count(embedding) = count(*)`(본문이 빈 알림 제외)를 확인한다. 재클러스터링도 같은 임베딩 설정으로 돌리므로, 혹시 빈 행이 있어도 같은 후보 모델로 채워진다.
- **대안**: 별도 채우기 도구 — 같은 일을 하는 기존 모드가 있어 기각.

## R7. 코드에 고정된 병합 거리 0.15 를 설정으로 뺀다 (기본값 그대로) — 2026-11-27 취소(R10: 거리 기준을 운영 값으로 고정해 설정화가 필요 없다)

- **결정**: `EventClusteringService` 의 지역 변수 `mergeMaxDistance = 0.15` 를 `@Value("${clustering.merge-max-distance:0.15}")` 필드로 바꾼다. 운영 설정은 건드리지 않으므로 운영 동작은 그대로다. 재클러스터링 때 `--clustering.merge-max-distance=<그 모델의 바로 병합 기준>` 으로 넘긴다. LLM 폴백 상한(`clustering.llm-fallback.distance-ceil`)과 동물 경로 유사도 하한(`clustering.cross-region.similarity-floor`)은 이미 설정이라 명령줄로 넘긴다.
- **이유**: 2단계가 후보 모델 척도의 기준으로 재클러스터링하려면 이 값을 바꿀 수 있어야 한다(spec FR-010). 기본값이 같아 클러스터링 규칙 변경이 아니다(spec FR-018).
- **TDD**: 기본값이 0.15 로 유지되는지와, 설정한 값으로 병합 여부가 갈리는지를 Red-Green 으로 확인한다(CLAUDE.md 팀 컨벤션). CLAUDE.md 이벤트 클러스터링 절의 "설정이 아니라 코드에 고정" 문구도 함께 고친다.

## R8. 흔들림 크기(spec FR-012)

- **결정**: 기준선을 한 번 더 재클러스터링해(같은 설정, LLM 폴백 켬) 기존 v6 결과와 고쳐진 쌍·새로 틀린 쌍을 비교한다. 서로 다른 쌍 수를 흔들림으로 쓴다. 후보의 점수 차가 그 범위 안이면 "차이 없음"이다.
- **근거**: LLM 폴백은 온도 0 이어도 판정이 조금씩 달라진다. 2026-11-12 호우 튜닝에서 비호우 쌍이 ±1 로 바뀐 전례가 있다.
- **점수의 정의(2026-11-26 갱신)**: 013 에서 대표 지표가 F1 에서 균형 정확도로 바뀌었다. 2단계의 "점수"는 균형 정확도이고 두 비율·F1 은 보조다. 위의 90.2점은 F1 이다.
- **기준선 점수**: 272쌍 라벨이 2026-11-17 에 바뀌었으므로, 지금 라벨로 v6 를 다시 평가한 값(무료)을 기준선 점수로 쓴다. 2026-11-12 의 90.2점과 다를 수 있다.

## R9. 선정 표의 비용·운영 부담 항목 (spec FR-013)

후보마다 아래를 적는다. 값은 1·2단계 실측으로 채운다.

| 항목 | OpenAI `3-large` | 공개 모델 |
|---|---|---|
| 단가 | $0.13 / 100만 토큰 | 0원(자원 비용만) |
| 운영 재임베딩 | 운영 알림 수 × 평균 토큰 × 단가 | 0원, 맥북에서 걸리는 시간 |
| 매일 비용 | 하루 새 알림 × 평균 토큰 × 단가 | 0원 |
| 스키마 | 변경 없음(1536) | `vector(1024)` 마이그레이션 + 인덱스 재생성 + 재임베딩 중 배포 지연 |
| 코드 | 모델 이름·`dimensions` 설정 | 추론 서빙(Ollama·TEI·ONNX 중 하나) + Spring 연동 + 지시문 |
| 운영 부담 | 없음 | 맥북 메모리·속도(새 알림 1건 임베딩 시간이 수집 주기 1분을 위협하면 실격, spec FR-001a) |
| 헌법 | 해당 없음 | 기술 스택 제약 개정(임베딩 = OpenAI) |
| 임계값 재튜닝 | 필요(척도 변화) | 필요 |

- 운영 알림 수는 평가 사본(알림 62,554건)으로 추정하고 추정임을 밝힌다. 비용이 몇십 센트 단위라 운영 DB 를 따로 조회해도 결론이 바뀌지 않는다(2026-11-20 작업 최소화).
- 맥북 속도는 공개 모델이 2단계에서 기준선보다 나을 때만 잰다. 운영 기기라 사용자 승인 뒤에 한다.

## R10. 2026-11-27 방향 전환 — 다섯 모델 모두 재클러스터링, 거리 기준은 운영 값 고정, 임베딩 보관·재사용, 실행별 가상 키

사용자 결정의 흐름: (1) 1단계 결과를 받은 뒤 "성능 측정 기준부터 마련해야 한다" → (2) "1·2단계로 거르지 말고 실제 클러스터링으로 한 번에 확인하자, 임베딩은 표에 저장해 재사용" → (3) 거리 기준은 A(운영 값 그대로) → (4) 기준선 포함 5회, 별도 로컬 DB 보관, 모델마다 사본 → (5) LiteLLM 가상 키를 실행마다 하나씩.

- **다섯 모델 모두 재클러스터링**: 쌍 거리(삭제한 1단계)는 대리 지표다. 클러스터링은 새 알림을 같은 시군구·시간 창 안의 기존 이벤트 전체(라벨에 없는 알림 수만 건 포함)와 비교하고, 연쇄 병합·LLM 폴백·후보 검색 순서가 결과를 바꾼다. 비용이 작아(아래) 거르지 않고 모두 돌린다. 기준선(`3small`)도 같은 코드·같은 방식으로 돌린다 — v6 기존 배정은 운영에서 여러 코드 버전으로 쌓였을 수 있어, 그대로 비교하면 모델 외 차이가 섞인다. 기준선 재실행과 v6 기존 배정의 점수 차이를 흔들림으로 쓴다(R8).
- **거리 기준 A(운영 값 고정)**: 0.15·0.40·동물 0.6 을 모든 모델에 그대로 쓴다. 검토한 대안: B 라벨 쌍에서 "다른 사건을 기준선과 같은 수만 붙이는 거리"(삭제한 1단계 방식) — 기준을 정한 라벨로 다시 채점해 후보 점수가 부풀고, 바로 병합 지점은 다른 사건 2쌍에 좌우된다. C 라벨로 여러 값 훑기 — 임계값 훑기 금지·과적합. D 라벨 없는 후보 쌍 분포에서 같은 백분위 위치로 맞추기 — 라벨 독립·안정적이지만 사용자가 A 를 골랐다. A 의 대가: 2026-11-27 1단계에서 잰 것처럼(137쌍, 결과는 지움 — git 이력 77611dda) 모델마다 거리 눈금이 달라(다른 사건 거리 중앙값 3small 0.387 · 3large 0.337 · qwen 0.359 · e5 0.062 · bge 0.397), 눈금이 크게 다른 모델(e5)은 0.15 로 거의 모든 쌍을 바로 병합해 과병합으로 불리하게 나올 수 있다. 결과를 읽을 때 이 분포로 이유를 밝힌다. 이득: 코드 변경(R7)이 필요 없고, 라벨로 기준을 정하지 않아 같은 272쌍으로 채점해도 편향이 없다.
- **임베딩 보관(별도 로컬 DB)**: 같은 Postgres 컨테이너의 DB `embedding_store`, 표 `alert_embedding(alert_id bigint, model text, dimensions int, embedding vector, created_at timestamptz, primary key (alert_id, model))`. `embedding` 은 차원을 정하지 않은 `vector` 라 1536·1024 를 한 표에 담는다(검색하지 않으므로 인덱스 없음). 운영 스키마(Flyway)에 넣지 않는다 — 실험 데이터이고, 평가 사본을 지웠다 만들어도 남아야 한다. 처음 채울 때는 기존 `embed-only`(R6)로 그 모델의 사본을 채운 뒤 `(alert_id, embedding)` 을 `COPY` 로 보관 DB 에 내보낸다. 같은 모델을 다시 쓸 때는 보관 DB 에서 `COPY` 로 가져와 사본의 `disaster_alert.embedding` 을 갱신한다(API 호출 없음). `3small` 은 평가 사본에 이미 저장된 임베딩이라 보관하지 않아도 된다. 규모: 62,554건 × 4모델 ≈ 25만 행, 약 1~1.5GB.
- **모델마다 사본**: `CREATE DATABASE disaster_alert_eval_e_<별칭> TEMPLATE disaster_alert_eval_v6`. 사본 하나 2.7GB, 5개 약 14GB(2026-11-27 C 드라이브 여유 523GB). 틀린 쌍의 이벤트 배정을 나중에 다시 볼 수 있다.
- **실행별 가상 키(LiteLLM, `infra/llm-usage/`)**: 재클러스터링 중 모델 호출은 임베딩(빈 행이 있을 때만)과 LLM 폴백(`EventLLMDecisionService`, 공용 채팅 모델 `gpt-4o-mini`)뿐이다 — 번역·분류기는 재클러스터링 경로에 없다(2026-11-27 코드 확인). 채팅 호출에는 태그 헤더를 붙이기 어려워, 실행마다 키를 달리해 채우기·LLM 폴백 비용을 실행별로 나눈다. 키는 로컬 전용 프록시(127.0.0.1)라 값을 정해 발급한다.

  | 키 별칭(값) | 실행 | 허용 모델 | 예산 상한 |
  |---|---|---|---|
  | `sk-012-run-3small` | 기준선 재클러스터링 | text-embedding-3-small, gpt-4o-mini | $0.5 |
  | `sk-012-run-3large` | 채우기 + 재클러스터링 | text-embedding-3-large, gpt-4o-mini | $1.5 |
  | `sk-012-run-qwen3_06b` | 채우기 + 재클러스터링 | Qwen/Qwen3-Embedding-0.6B, gpt-4o-mini | $0.5 |
  | `sk-012-run-e5_large_instruct` | 채우기 + 재클러스터링 | intfloat/multilingual-e5-large-instruct, gpt-4o-mini | $0.5 |
  | `sk-012-run-bge_m3_ko` | 채우기 + 재클러스터링 | dragonkue/BGE-m3-ko, gpt-4o-mini | $0.5 |

  실행 명령에 `--spring.ai.openai.base-url=http://localhost:4000 --spring.ai.openai.api-key=<키>` 와 그 모델의 `--spring.ai.openai.embedding.options.model=…`·`dimensions=…` 를 준다. 상한에 걸려도 실행은 멈추지 않는다 — LLM 판정 호출이 거부되면 `EventLLMDecisionService` 가 예외를 병합 실패로 바꿔 새 이벤트를 만들고, 재클러스터링은 `오류=0` 으로 끝날 수 있다. 그래서 실행마다 그 키의 실패 호출이 0건인지 확인하고, 있으면 그 사본 결과를 버리고 다시 돌린다(quickstart 2절 7). 2026-11-27 다섯 실행 모두 실패 호출 0건이었다.
- **예상 비용**: `3large` 채우기 약 $0.7~0.8, 재클러스터링 LLM 폴백 회당 약 $0.3(모델마다 LLM 에 가는 쌍 수가 달라 조금씩 다름) × 5회. 합계 약 $2.2~2.3. 실행마다 예상 비용을 먼저 보이고 승인 뒤 실행한다(FR-016).
- **모델 비교 기준(사용자 확정)**: (1) 기준선(`3small` 재실행)과 비교해 **과병합·과소병합 둘 다 늘지 않고, 적어도 하나가 줄어야 "나아짐"**이다. 하나가 줄고 다른 하나가 늘면 더 묶거나 덜 묶은 맞바꿈일 뿐이라 "나아짐"이 아니다 — 거리 기준을 운영 값으로 고정해(A) 눈금이 다른 모델은 덜 가리고도 더 묶어 균형 정확도가 오를 수 있기 때문이다(사용자: "과병합이 늘면 과소병합이 준다는 채점이 이상하다"). (2) 줄어든 차이는 흔들림(기준선 재실행 대 v6 기존 배정)보다 커야 인정한다. (3) **(1)·(2)는 유형이 화재인 쌍을 뺀 나머지 쌍으로 센다.** 화재는 문구 틀이 같고 번지만 다른 경우가 많아, 번지가 다른 화재가 묶인 쌍은 어느 모델에서든 원문과 함께 모아 클러스터링 로직(번지·주소 비교) 수정 작업의 입력으로 넘긴다(로직 수정은 012 밖). (4) 균형 정확도는 "나아짐"을 통과한 모델들 사이의 순위에만 쓴다. (5) 통과한 모델이 없으면 결론은 "지금 모델 유지"다. 성능과 비용의 맞바꿈은 통과한 모델이 있을 때 선정 표를 보고 사용자가 정한다. 리포트에는 272쌍 균형 정확도·두 비율·"N쌍 중 M쌍"·2026-05 B-cubed 점수를 함께 적는다.

## R11. LLM 사용량 기록에 LiteLLM 프록시를 고른 이유 (2026-11-27)

모델 비교 때 회사·모델별 토큰·비용을 화면으로 보고, 실행별 비용을 나누고, 유료 호출에 상한을 걸려고 게이트웨이를 들였다(사용자 결정, 로컬만·운영은 추후). 구성은 `infra/llm-usage/`.

**고른 이유 (사용자 판단, 2026-11-27)**

- **무료가 가장 중요했다.** 라이선스 없이 셀프 호스팅으로 쓸 수 있어야 했다.
- **OpenAI 와 같은 인터페이스로 여러 모델을 묶어 준다.** 지금 OpenAI 플랫폼에서 발급한 API 키로 임베딩 모델도 OpenAI 모델을 쓰고 있어서, 같은 OpenAI 형식 그대로 프록시로 바꿔 붙이는 전환이 가장 낫다고 봤다(앱은 주소·키만 바꾼다).
- **Grafana·Prometheus 는 게이트웨이의 트래픽 문제에 대비해 미리 둔 것이다.** LLM 게이트웨이를 호출 경로에 두면 트래픽 관련 트러블슈팅(지연, 실패, 몰림)이 생길 것으로 예상해, 그것까지 볼 수 있게 일단 로컬에 설정만 해 두었다. 지금 실험의 사용량 원본은 Grafana 가 읽는 DB 기록이고, Prometheus 지표는 그 대비용이다.
- 실행마다 가상 키를 따로 발급해 쓰는 방식도 사용자 제안이다(R10).

**검증한 것 — 위 판단을 뒷받침하는 사실(작성: Claude, 같은 날 확인)**

필요 조건(이 프로젝트 기준):

1. 로컬 셀프 호스팅·무료(라이선스 키 없이).
2. OpenAI 형식 프록시 — Spring AI OpenAI 클라이언트의 `base-url` 만 바꿔 붙는다(앱 코드 변경 없음).
3. 로컬 TEI(OpenAI 형식 `/v1/embeddings`)로도 보내고, **임베딩 호출**도 기록한다.
4. 실행별 키에 **허용 모델과 예산 상한**을 걸어 승인 밖 유료 호출을 막는다(spec FR-016·FR-020).
5. 30초 안팎으로 끝나는 배치 도구 호출도 빠짐없이 남는다(호출마다 DB 에 1행).
6. 가볍다 — 개발 PC, 이미 있는 Postgres 컨테이너를 재사용.

후보 비교:

| 후보 | 방식 | 판단 |
|---|---|---|
| **LiteLLM 프록시** (`ghcr.io/berriai/litellm:main-stable`) | 프록시. 호출마다 `LiteLLM_SpendLogs` 1행(Postgres) | **채택.** 컨테이너 1개 + 기존 Postgres 의 DB 하나. 2026-11-27 직접 확인: TEI 3개·OpenAI·`gpt-4o-mini` 라우팅, 임베딩 토큰·비용 기록(`3small`·`3large` 비용이 토큰×단가와 같음), 가상 키의 허용 모델 제한(허용 밖 모델 거부)과 예산 상한 설정이 라이선스 없이 동작, Prometheus 지표(`litellm_input_tokens_metric_total` 등)도 나옴. 한계: TEI 모델에 `dimensions` 를 보내면 거부해 `drop_params` 필요, Prometheus 카운터는 재시작하면 0부터, 일부 기능의 라이선스 경계가 버전마다 다르다는 자료가 있다(검색 땐 Prometheus 지표를 Enterprise 로 적은 자료가 있었으나 이 버전에서는 나왔다) |
| Portkey AI Gateway | 프록시(MIT) | 보류. 예산 한도를 2.0 에서 오픈소스로 넣는다는 발표가 있으나 pre-release 이고, 다른 공식 문서·엔터프라이즈 페이지는 키 예산을 엔터프라이즈 기능으로 적는다 — 조건 4 를 무료로 쓸 수 있는지 확인되지 않았다 |
| Helicone | 관측(+ 별도 AI Gateway) | 기각. 셀프 호스팅이 Web·Worker·Jawn·Supabase·ClickHouse 5개 구성, 최소 4코어·8GB. 프록시는 별도 AI Gateway 를 함께 띄워야 한다(Jawn 의 프록시 경로 제거). 셀프 호스팅의 예산 상한은 확인하지 못했다 — 조건 6·4 |
| Langfuse | 기록 수집(트레이싱), 호출 경로 밖 | 이번엔 기각, **운영 도입 때 재검토 후보.** 셀프 호스팅이 web·worker·Postgres·ClickHouse·Redis·S3 로 무겁고, 앱에 OpenTelemetry 계측을 더해야 하며, 호출을 막는 예산 상한은 게이트웨이 기능이라 해당 없다 — 조건 2·4·6. 장점은 호출 경로에 끼지 않아 운영 장애 지점을 늘리지 않는 것 |
| 호스팅형(Cloudflare AI Gateway, OpenRouter 등) | 외부 서비스 경유 | 기각. 로컬 TEI(localhost)로 보낼 수 없고, 요청이 외부 서비스를 거친다 — 조건 1·3 |
| 직접 구현(Spring 에서 사용량 표에 기록) | 앱 코드 | 기각. 단가표·키별 예산 차단·화면을 새로 만들어야 한다 — 조건 2·6. 같은 일을 하는 도구가 있다 |

**검토한 반론과 판단 (2026-11-27)** — 선택 이유를 받아 적은 뒤, 시니어 입장의 반론을 받고 사용자가 답했다.

| 반론 | 사용자 판단 / 대응 |
|---|---|
| 들어가는 돈은 0 이지만 구성·검증에 반나절이 들었고, 지켜본 금액은 지금까지 $0.003·012 전체 약 $2.5 라 도구가 더 비싸다 | **나에게는 시간보다 돈이 더 중요하다.** 유지 |
| 지금 후보는 OpenAI·TEI 모두 원래 OpenAI 형식이라 "묶어 준다"는 이점이 지금은 작다 | **OpenAI 만이 아니라 앞으로 여러 회사 모델을 통합 관리해야 해서, 확장 가능성을 보고 골랐다.** 유지 |
| `main-stable` 태그는 받을 때마다 바뀌고, 무료 기능 경계가 버전마다 움직인다 | 이미지를 검증한 1.111.0 의 digest 로 고정(`docker-compose.dev.yml`). 올릴 때 이 절의 확인을 다시 한다 |
| 프록시가 요청을 조용히 고칠 수 있다(`drop_params` 로 TEI 의 `dimensions` 를 버림) — 설정을 실수해도 에러 대신 다른 조건으로 돈 결과가 나올 수 있다 | (1) `drop_params` 는 TEI 세 모델에만, 전역으로 켜지 않는다(OpenAI 모델은 잘못된 파라미터가 에러로 난다). (2) 후보마다 큰 채우기 전에 같은 20건을 직접 호출과 프록시로 채워 같은 알림의 두 벡터 거리가 0.01 이하인지 본다(quickstart 2, tasks T011). (3) 채운 뒤 저장 벡터 차원과 프록시 기록의 `model_group`·`api_base` 가 의도한 모델인지 대조한다. 가상 키의 허용 모델이 다른 모델 호출을 막는다 |
| 형식이 다른 회사(Claude 등)를 OpenAI 형식으로 번역해 부르면 고유 API 로 쓸 때와 결과가 달라질 수 있다 | 012 의 임베딩 후보는 모두 원래 OpenAI 형식이라 해당 없음. 다른 회사 모델을 비교할 때(예: LLM 판사·번역) — 번역 경로 대신 LiteLLM 의 고유 형식 통과(pass-through) 경로를 쓰고 앱은 그 회사의 Spring AI 모듈로 부른다(통과 경로의 범위·비용 기록은 **그때 문서와 실측으로 확인**). 비교 전에 같은 질문 20~30개를 고유 API 직접 호출과 프록시 경유로 보내 결과가 같은지 먼저 본다 |
| Grafana·Prometheus 는 아직 없는 문제에 대한 대비이고, 알림 없는 대시보드는 열어 볼 때만 쓸모 있다 | 게이트웨이를 호출 경로에 둘 때의 트래픽 트러블슈팅 대비로 로컬에 설정만 해 둔 것(위 사용자 판단). 운영 도입 때 알림과 함께 다시 설계한다 |

**운영 도입 때(추후)**: 프록시를 운영 호출 경로(수집 루프의 임베딩, LLM 폴백, 번역, 일일 채점)에 끼우면 프록시 장애가 곧 수집·클러스터링 장애가 된다. 그래서 운영은 LiteLLM 을 그대로 쓸지, 호출 경로 밖의 Langfuse 로 갈지를 다시 비교한다. 이때 위 "확인되지 않았다" 항목(Portkey 예산, Helicone 예산, Langfuse 의 새 AI Gateway)도 그 시점 문서로 다시 확인한다.

**출처(2026-11-27 확인)**: LiteLLM Enterprise 기능 목록 https://docs.litellm.ai/docs/proxy/enterprise · LiteLLM Prometheus https://docs.litellm.ai/docs/proxy/prometheus · Portkey Gateway 2.0 https://portkey.ai/blog/gateway-2-0/ · Portkey 기능 비교 https://docs.portkey.ai/docs/product/product-feature-comparison · Helicone 셀프 배포 https://docs.helicone.ai/getting-started/self-deploy · Langfuse 셀프 호스팅 https://langfuse.com/self-hosting · Langfuse ClickHouse https://langfuse.com/resources/engineering/clickhouse-for-self-hosted-langfuse
