# 도구 계약: 임베딩 쌍 거리 실험

이 기능은 외부 API 를 새로 열지 않는다. 계약은 실험 도구의 명령줄과 출력이다. 모든 실행은 `backend/` 에서 `eval` 프로필로, 평가 사본 DB 에 붙어 한다(006 quickstart 와 같은 방식).

## 1. 공개 모델 추론 서버 (TEI, 개발용 컨테이너)

```bash
# RTX 40 시리즈 GPU 이미지. GPU 를 못 쓰면 이미지를 text-embeddings-inference:cpu-1.9 로
docker run --rm --gpus all -p 8081:80 -v tei-cache:/data \
  ghcr.io/huggingface/text-embeddings-inference:89-1.9 \
  --model-id Qwen/Qwen3-Embedding-0.6B \
  --max-client-batch-size 200 \
  --default-prompt "Instruct: Given a Korean emergency alert message, retrieve alerts that report the same incident
Query: "
```

| 별칭 | `--model-id` | 포트 | `--default-prompt` | 차원 |
|---|---|---|---|---|
| `qwen3_06b` | `Qwen/Qwen3-Embedding-0.6B` | 8081 | 위 지시문 | 1024 |
| `e5_large_instruct` | `intfloat/multilingual-e5-large-instruct` | 8082 | 위 지시문 | 1024 |
| `bge_m3_ko` | `dragonkue/BGE-m3-ko` | 8083 | 없음 | 1024 |

## 2. 모델 고르기 — 임베딩 설정만 바꾼다

**1단계 측정(3절)은 이 설정을 쓰지 않는다** (2026-11-27 사용자 결정, 실행 명령이 너무 길어져서). 후보마다 별칭·주소·모델·차원·단가를 `EmbeddingCandidates.ALL`(`backend/.../domain/event/evaluation/EmbeddingCandidates.java`)에 하드코딩하고, 측정기가 그 값으로 운영과 같은 `OpenAiEmbeddingModel` 을 만들어 부른다. 후보를 바꿀 때는 그 목록을 직접 고친다.

아래 명령줄 설정은 **2단계 채우기(4절)** 용이다. 운영 클러스터링이 쓰는 Spring AI `EmbeddingModel` 빈을 그대로 쓰고, 가리키는 곳만 바꾼다. 채팅(LLM 폴백)은 바꾸지 않는다.

| 별칭 | 덧붙이는 명령줄 설정 |
|---|---|
| `3small` (기준선) | 없음(`application.yml` 그대로) |
| `3large` | `--spring.ai.openai.embedding.options.model=text-embedding-3-large` (차원 1536 그대로) |
| 공개 모델 | `--spring.ai.openai.embedding.base-url=http://localhost:<포트> --spring.ai.openai.embedding.api-key=unused --spring.ai.openai.embedding.options.model=<model-id> --spring.ai.openai.embedding.options.dimensions=1024` |

임베딩 전용 `base-url`·`api-key` 가 채팅 설정과 따로 먹는지, TEI 가 `dimensions` 를 받는지는 첫 연결 시험에서 확인한다(research R3).

## 3. 1단계 측정 모드 — `EvaluationRunTool`

라벨은 평가 사본 DB 의 평가 셋(`--eval.dataset`, `evaluation_pair_label` 테이블)에서 읽는다.

```bash
# (a) 기준선 저장 거리: 평가 사본에 저장된 임베딩으로 거리만 잰다 (호출 없음)
--eval.dataset=cluster-eval-2026-11 --eval.embedding-pairs=stored --eval.report=<폴더>

# (b) 모델 하나로 다시 임베딩해 거리를 잰다 (주소·모델·차원은 EmbeddingCandidates 에서, 명령줄 설정 없음)
--eval.dataset=... --eval.embedding-pairs=<별칭> --eval.report=<폴더> [--eval.approve-paid-embedding]

# (c) 모은 거리 파일로 두 지점 비교 리포트를 만든다 (호출 없음)
#     값 없이 주면 EmbeddingCandidates.ALL 전체, 일부만이면 =stored,3small,qwen3_06b 처럼
--eval.dataset=... --eval.embedding-pairs-summary --eval.report=<폴더>
```

- 대상 쌍은 research R1 규칙으로 고른다. 유형 목록은 `clustering.regional-types`·`clustering.sustained-types` 설정에서 읽는다.
- 임베딩 입력은 평가 사본의 `disaster_alert.message` 다(운영 클러스터링과 같은 값).
- 모르는 별칭은 쓸 수 있는 별칭 목록과 함께 오류로 끝난다.
- (b)는 후보가 OpenAI 일 때 대상 알림 수·예상 토큰(글자 수 상한)·예상 비용만 출력하고 끝낸다. `--eval.approve-paid-embedding` 이 있어야 호출한다(spec FR-016).
- (a)(b)의 출력은 `<폴더>/embedding-pairs-<별칭>.csv`: `alert_a, alert_b, label, type, relation, distance`.
- (c)의 출력은 `<폴더>/embedding-pairs-summary.md`. 형식은 [data-model.md](../data-model.md) 2절. `3small` 과 `stored` 의 쌍별 거리 차이가 0.01 을 넘으면 리포트 맨 위에 "기준선 재현 실패 — 후보 비교 무효"를 적고 종료 코드 1.
- `--eval.embedding-pairs` 와 `--eval.embedding-pairs-summary`, 그리고 기존 `--eval.recheck`·`--eval.judge-agreement` 는 함께 쓸 수 없다.

## 4. 2단계 채우기 — 기존 `EventClusteringBackfillTool`

```bash
--backfill.embed-only=true   # + 위 2의 후보 모델 설정, 후보 사본 DB 주소
```

비어 있는 임베딩만 배치로 채운다. 끝난 뒤 `count(*)`·`count(embedding)` 을 SQL 로 확인한다(research R6).
