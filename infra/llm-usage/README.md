# LLM 사용량 기록 (로컬 전용)

모델 비교 실험(예: specs/012 임베딩 모델 선정)의 호출을 **LiteLLM 프록시**로 모아, 모델별 토큰·비용을 **Grafana** 에서 본다.
운영에는 아직 쓰지 않는다(운영 도입은 별도 작업, 2026-11-27 결정).

```
평가 도구 ──▶ LiteLLM 프록시(:4000) ──▶ OpenAI / TEI 컨테이너(:8081~8083)
                 │ 호출 1건마다 1행
                 ▼
         Postgres 의 litellm DB ("LiteLLM_SpendLogs") ◀── Grafana(:3001)
                 │
                 └─ /metrics/ ◀── Prometheus(:9090) — 보조(재시작하면 카운터가 0부터)
```

## 띄우기

저장소 루트에서. `DATABASE_URL` 을 `.env.dev` 의 `POSTGRES_USER`·`POSTGRES_PASSWORD` 로 만들기 때문에 `--env-file` 이 필요하다.

```bash
# 처음 한 번: 기록용 데이터베이스를 앱 DB 와 따로 만든다(LiteLLM 이 첫 기동 때 자기 표를 만든다)
docker exec postgres sh -c 'psql -U "$POSTGRES_USER" -d postgres -c "create database litellm"'

docker compose --env-file .env.dev -f docker-compose.dev.yml --profile llm-usage up -d litellm prometheus grafana
```

TEI 후보를 쓸 때는 TEI 컨테이너도 떠 있어야 한다(specs/012 contracts 1절). 평소처럼 `up postgres redis` 만 하면 이 셋은 뜨지 않는다.

| 화면 | 주소 | 로그인 |
|---|---|---|
| Grafana 대시보드 "LLM 사용량 (로컬)" | http://127.0.0.1:3001 | admin / admin (처음 접속 때 바꾸라고 나온다) |
| LiteLLM 관리 화면 | http://127.0.0.1:4000/ui | admin / `sk-local-llm-usage` |
| Prometheus | http://127.0.0.1:9090 | 없음 |

포트는 모두 `127.0.0.1` 에만 열린다. 프록시 키 `sk-local-llm-usage` 는 로컬 전용이며, 진짜 OpenAI 키는 프록시 컨테이너만 가진다(`.env.dev` 의 `OPENAI_API_KEY`).

## 모델 추가

1. [litellm-config.yaml](litellm-config.yaml) 의 `model_list` 에 모델을 더한다. `model_name` 은 앱이 요청에 넣는 모델 이름과 같아야 한다.
   - OpenAI 계열: `model: openai/<모델>`, `api_key: os.environ/OPENAI_API_KEY`. 단가는 LiteLLM 내장표를 쓴다.
   - 로컬(TEI 등 OpenAI 형식 서버): `api_base: http://host.docker.internal:<포트>/v1`, `input_cost_per_token: 0`.
     TEI 는 `dimensions` 를 보내면 LiteLLM 이 거부하므로 `drop_params: true` 를 둔다.
2. `docker restart litellm`.
3. 그 모델을 쓸 실행 키가 있으면 허용 모델에 넣는다(아래 "실행별 가상 키").

## 읽는 법

- **비용**은 LiteLLM 내장 단가표 × 응답의 실제 토큰이다. 실제 청구 금액의 원본은 OpenAI·Anthropic 대시보드다. 로컬 모델은 0.
- **토큰 수를 모델끼리 바로 비교하지 않는다.** 토크나이저가 회사·모델마다 달라 같은 입력이라도 토큰 수가 다르다
  (2026-11-27 같은 알림 274건: Qwen 22,319 · e5 20,035 · BGE 13,459). 모델끼리는 같은 입력의 비용으로 비교한다.
- 실험은 실행마다 **가상 키**를 따로 쓴다(아래). 기록의 `metadata.user_api_key_alias` 에 키 별칭이 남아, 대시보드 맨 위 "가상 키" 와
  "실행(가상 키) × 모델" 표로 실행별 비용을 본다. 요청에 `x-litellm-tags` 헤더를 붙이면 "실험 태그" 로도 거를 수 있다.
- 실패한 호출도 `status <> 'success'` 로 남는다(토큰 0). 요청·응답 본문은 저장하지 않는다(`store_prompts_in_spend_logs: false`).

## 직접 조회

```sql
-- psql -U <사용자> -d litellm
select t as tag, model_group, count(*) calls, sum(prompt_tokens) tokens, sum(spend) spend
  from "LiteLLM_SpendLogs", jsonb_array_elements_text(request_tags) t
 where status = 'success' and t not like 'User-Agent%'
 group by 1, 2 order by 1, 2;
```

`"startTime"` 은 UTC 다.

## 확인한 것 (2026-11-27, `ghcr.io/berriai/litellm:main-stable`)

- 같은 알림 274건을 직접(TEI) 임베딩한 거리와 프록시 경유 거리가 쌍별 최대 0.0004 이내로 같았고, 기록 토큰이 응답 사용량과 같았다(Qwen·e5·BGE).
- `3small`·`3large` 의 프록시 계산 비용(`spend`)이 토큰 × 단가와 같았다($0.000438, $0.002844).
- 가상 키가 허용 밖 모델 호출을 거부했다.
- Prometheus 지표(`litellm_input_tokens_metric_total` 등)는 라이선스 없이 나왔다. 엔드포인트는 프록시 키가 있어야 열린다.

## 실행별 가상 키

실험 실행마다 관리 키로 가상 키를 발급해 쓴다. 키마다 허용 모델과 예산 상한을 걸어, 상한을 넘는 호출은 프록시가 막는다.
키 값은 로컬 전용이라 정해서 발급한다.

```bash
curl -s http://127.0.0.1:4000/key/generate -H "Authorization: Bearer sk-local-llm-usage" -H "Content-Type: application/json"   -d '{"key":"sk-012-run-qwen3_06b","key_alias":"012-run-qwen3_06b","models":["Qwen/Qwen3-Embedding-0.6B","gpt-4o-mini"],"max_budget":0.5}'
```

앱은 `--spring.ai.openai.base-url=http://localhost:4000 --spring.ai.openai.api-key=<가상 키>` 로 부른다. 012 의 키 5개는 specs/012 research R10 표.
