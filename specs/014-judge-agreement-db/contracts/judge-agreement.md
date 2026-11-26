# 계약: 판사 일치율

## 1. 관리자 API — `GET /api/v1/admin/clustering/judge-versions` (변경분)

기존 계약은 [008 contracts](../../008-clustering-admin-dashboard/contracts/admin-clustering-api.md) 의 `GET /judge-versions`. 이 기능은 필드 두 개를 더하고 두 필드의 뜻을 바꾼다. 경로·권한·응답 래퍼(`ApiResponse`)는 그대로다.

```json
{
  "versions": [
    {
      "version": "v2", "model": "gpt-4o-mini", "measuredOn": "2026-11-18",
      "sameAgreement": { "numerator": 127, "denominator": 141, "percent": 90.1 },
      "diffAgreement": { "numerator": 74, "denominator": 81, "percent": 91.4 },
      "recomputable": true, "unmeasuredPairs": 0,
      "firstScoredDate": "2026-11-18", "lastScoredDate": "2026-11-25", "scoredDays": 8
    },
    {
      "version": "v1", "model": "gpt-4o", "measuredOn": "2026-11-17",
      "sameAgreement": { "numerator": 124, "denominator": 142, "percent": 87.3 },
      "diffAgreement": { "numerator": 77, "denominator": 80, "percent": 96.3 },
      "recomputable": false, "unmeasuredPairs": null,
      "firstScoredDate": "2026-11-17", "lastScoredDate": "2026-11-17", "scoredDays": 1
    }
  ]
}
```

| 필드 | 변경 | 뜻 |
|---|---|---|
| `sameAgreement` / `diffAgreement` | 출처 변경 | 코드 상수 → 그 버전 최신 측정의 쌍별 판정 × **현재** 사람 라벨(`recomputable=true`), 또는 측정에 저장된 합계(`false`) |
| `measuredOn` | 출처 변경 | 코드 상수 → 최신 측정의 `finished_at` 날짜(KST) |
| `recomputable` | 새 필드 | 라벨이 바뀌면 다시 계산되는 값인가. 합계만 있는 측정이면 false |
| `unmeasuredPairs` | 새 필드 | 셋에 SAME/DIFF 라벨이 있는데 그 측정에 판정이 없는 쌍 수. `recomputable=false` 면 null |

- 카탈로그에 있는데 측정 기록이 없는 버전, 또는 계산할 수 없는 버전(셋 라벨 0행): `measuredOn`·`sameAgreement`·`diffAgreement`·`recomputable`·`unmeasuredPairs` 가 null. 채점 기간 필드는 그대로 채운다.
- 정렬은 지금처럼 카탈로그 순서(최신 먼저), 기록에만 있는 버전은 뒤에.

### 화면(008 판사 버전 이력 표)

- 일치율 칸은 기존 `formatRatio` 그대로(분모 0 이면 `-`).
- `recomputable=false` 면 일치율 옆에 "합계만(라벨 변경 미반영)" 표시.
- `unmeasuredPairs > 0` 이면 "측정에 없는 쌍 N" 표시.

## 2. 일일 채점이 쓰는 값

- `ClusteringDailyScoreService.score(date)` 는 채점 시작 때 `JudgeAgreementService.latest(ClusteringJudge.PROMPT_VERSION)` 를 한 번 읽어 그날 행의 `agreement_*` 4열에 복사한다(저장 형식 그대로).
- 비어 있으면(측정 없음, 셋 라벨 0행) 판사를 부르지 않고 warn 을 남기고 돌아간다. 그날 행을 만들지 않으므로 7일 안의 다음 실행이 다시 시도한다.

## 3. 측정 도구 — `--eval.judge-agreement` (CLI, `eval` 프로필)

```bash
./gradlew bootRun --args="--spring.profiles.active=eval \
  --spring.datasource.url=$EVAL_DB --server.port=0 \
  --eval.dataset=cluster-eval-2026-11 --eval.judge-agreement"
```

| 항목 | 계약 |
|---|---|
| 비용 | **유료**(사람 UNSURE 를 뺀 쌍마다 판사 1회). 실행 전 사용자 명시 승인 |
| 라벨 출처 | `--eval.dataset` 만 받는다. 측정 기록의 `dataset` 은 그 값이다. `--eval.pair-labels-as-of`(불일치 쌍 라벨)와 함께 주면 판사를 부르기 전에 오류로 끝낸다 — 불일치 쌍은 판사·시스템이 갈린 어려운 쌍만 모은 것이라 버전 일치율과 뜻이 다르고, 그 성적은 대시보드 "어려운 쌍 판사 성적"이 따로 보인다 |
| 저장 | 판정을 모두 마친 뒤 측정 1행 + 쌍별 행을 한 트랜잭션으로 접속한 DB 에 저장(`origin='CLI'`). 도중에 멈추면 아무것도 저장하지 않는다 |
| 출력 | 콘솔 로그: 대상 쌍 수, 교차표, 같은 사건·다른 사건 일치율(분자/분모), 판사 UNSURE·형식 실패·호출 실패 수, 토큰·추정 비용, 저장된 측정 id |
| 리포트 파일 | 없음. `--eval.report` 를 함께 주면 판사를 부르기 전에 오류로 끝낸다 |
| 운영 반영 | 로컬에서 잰 측정은 고정값 시드 마이그레이션으로 운영에 넣는다(research R11) |
