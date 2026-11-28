# 도구 계약: 임베딩 쌍 거리 실험

이 기능은 외부 API 를 새로 열지 않는다. 계약은 실험 도구의 명령줄과 출력이다. 모든 실행은 `backend/` 에서 `eval` 프로필로, 평가 사본 DB 에 붙어 한다(006 quickstart 와 같은 방식).

## 1. 공개 모델 추론 서버 (TEI, 개발용 컨테이너)

```bash
# RTX 40 시리즈 GPU 이미지. GPU 를 못 쓰면 이미지를 text-embeddings-inference:cpu-1.9 로 바꾸고 --gpus all 도 뺀다
docker run --rm --gpus all -p 8081:80 -v tei-cache:/data \
  ghcr.io/huggingface/text-embeddings-inference:89-1.9 \
  --model-id Qwen/Qwen3-Embedding-0.6B \
  --max-client-batch-size 500 \
  --default-prompt "Instruct: Given a Korean emergency alert message, retrieve alerts that report the same incident
Query: "
```

| 별칭 | `--model-id` | 포트 | `--default-prompt` | 차원 |
|---|---|---|---|---|
| `qwen3_06b` | `Qwen/Qwen3-Embedding-0.6B` | 8081 | 위 지시문 | 1024 |
| `e5_large_instruct` | `intfloat/multilingual-e5-large-instruct` | 8082 | 위 지시문 | 1024 |
| `bge_m3_ko` | `dragonkue/BGE-m3-ko` | 8083 | 없음 | 1024 |

## 2. 모델 고르기 — 명령줄 설정 + 실행별 가상 키

채우기(`--backfill.embed-only`)와 재클러스터링은 운영 클러스터링이 쓰는 Spring AI 빈을 그대로 쓰고, 명령줄 설정으로 가리키는 곳만 바꾼다. 모든 호출은 로컬 LiteLLM 프록시를 거치고, 실행마다 가상 키 하나를 쓴다(research R10, [infra/llm-usage/README.md](../../../infra/llm-usage/README.md)). 키가 그 실행의 임베딩 모델과 LLM 폴백(`gpt-4o-mini`)만 허용하고 예산 상한을 건다.

공통: `--spring.ai.openai.base-url=http://localhost:4000 --spring.ai.openai.api-key=sk-012-run-<별칭>`

| 별칭 | 덧붙이는 임베딩 설정 |
|---|---|
| `3small` (기준선) | 없음(`application.yml` 그대로, 저장 임베딩을 쓰므로 채우기 없음) |
| `3large` | `--spring.ai.openai.embedding.options.model=text-embedding-3-large` (차원 1536 그대로) |
| 공개 모델 | `--spring.ai.openai.embedding.options.model=<model-id> --spring.ai.openai.embedding.options.dimensions=1024` (프록시가 TEI 로 넘기고 `dimensions` 는 버린다 — TEI 기본이 1024) |

거리 설정(`clustering.*`)은 모든 실행에서 운영 값 그대로다(research R10).

## 3. 채우기 — 기존 `EventClusteringBackfillTool`

```bash
--spring.profiles.active=backfill --backfill.embed-only=true   # + 위 2의 공통·임베딩 설정, 후보 사본 DB 주소
```

비어 있는 임베딩만 500건씩 채운다. 끝난 뒤 `count(*)`·`count(embedding)` 을 SQL 로 확인하고, 보관 DB `embedding_store.alert_embedding` 으로 내보낸다(quickstart 2).
