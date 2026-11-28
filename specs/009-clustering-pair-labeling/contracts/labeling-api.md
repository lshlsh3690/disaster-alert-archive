# API 계약: 불일치 쌍 사람 라벨링

모두 `/api/v1/admin/clustering` 아래, ADMIN 전용(`@PreAuthorize("hasRole('ADMIN')")`). 응답은 `ApiResponse` 로 감싼다(`{success, code, message, data}`). 비로그인 401, 일반 회원 403. 프론트 요청은 `X-Auth-Required: "true"` 를 붙인다(008 과 같음).

## 1. 라벨 저장 — `PUT /disagreement-labels`

요청:

```json
{ "alertA": 205, "alertB": 101, "label": "DIFF" }
```

- `label`: `SAME` | `DIFF` | `UNSURE`(필수, `@Valid`).
- 순서는 상관없다. 서버가 작은 id 를 a 로 맞춘다.
- 회원 id 는 로그인 정보에서, 기준 시점은 서버 상수에서 붙인다(요청에 없음).

응답 `data`:

```json
{ "alertA": 101, "alertB": 205, "label": "DIFF", "criteriaDate": "2026-11-13", "updatedAt": "2026-11-19T10:12:30" }
```

오류:

| 상황 | HTTP | 코드 |
|---|---|---|
| `label` 이 없거나 모르는 값, id 누락 | 400 | C001(`INVALID_REQUEST`) 또는 검증 오류 |
| 두 id 가 같음 | 400 | C001 |
| 불일치 쌍으로 기록된 적 없는 쌍 | 404 | `CLUSTERING_PAIR_NOT_FOUND` |

## 2. 불일치 쌍 — `GET /disagreements` (008 확장)

추가 파라미터: `unlabeledOnly`(boolean, 기본 false) — true 면 라벨이 없는 쌍만.

쌍마다 추가 필드(라벨이 없으면 세 필드 모두 null):

```json
{
  "humanLabel": "DIFF",
  "criteriaDate": "2026-11-13",
  "wrongSide": "SYSTEM"
}
```

- `wrongSide`: `JUDGE` | `SYSTEM` | null(라벨 없음 또는 `UNSURE`).
- 기존 필드·정렬·페이지는 008 계약 그대로.

## 3. 어려운 쌍 판사 성적 — `GET /hard-pair-grades`

파라미터 없음(지금 불일치인 쌍 중 라벨된 것 전체).

응답 `data`:

```json
{
  "labeledPairs": 5,
  "versions": [
    {
      "version": "v2",
      "model": "gpt-4o-mini",
      "judgeRight": { "numerator": 2, "denominator": 4, "percent": 50.0 },
      "systemRight": { "numerator": 2, "denominator": 4, "percent": 50.0 },
      "unsureCount": 1
    }
  ]
}
```

- `labeledPairs` 는 서로 다른 쌍 수가 아니라 "쌍×날짜" 수다 — 같은 쌍이 여러 날 불일치였으면 날짜마다 따로 판정됐으므로 날짜마다 센다(data-model.md). 화면에서는 "판정 N건"으로 보인다.
- 라벨이 없으면 `labeledPairs: 0`, `versions: []` — 화면은 "아직 라벨 없음".
- 분모 0 이면 `percent: null`.
- 버전 순서는 판사 버전 목록 순(최신 먼저). 목록에 없는 버전은 뒤, `model: null`.

## 4. 평가 도구 — DB 라벨 모드 (HTTP 아님)

```bash
./gradlew bootRun --args="--spring.profiles.active=eval \
  --spring.datasource.url=$EVAL_DB --server.port=0 \
  --eval.pair-labels-as-of=2026-11-25T00:00 \
  --eval.report=evaluation/reports/<새 리포트 이름>.md"
```

- `--eval.pair-labels-as-of`: ISO 시각(KST). 이 시각까지 저장·수정된 불일치 쌍 사람 라벨만 쓴다. `--eval.dataset`(평가 셋 라벨)과 함께 주면 오류.
- 리포트 머리의 데이터셋 이름: `불일치 쌍 사람 라벨(DB, 기준 2026-11-25T00:00, N쌍)`.
- 같은 사건·다른 사건 라벨이 0개면(모르겠음만 있어도) 채점하지 않고 오류 종료(0 이 아닌 종료 코드).
- 파일로 내보내는 HTTP 엔드포인트는 없다(research R8).
