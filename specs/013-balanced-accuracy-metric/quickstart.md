# 검증 안내: 클러스터링 평가의 대표 지표를 균형 정확도로

구현이 끝난 뒤 성공 기준을 확인하는 순서다. 이 문서는 검증 계획이며 아래 절차를 이미 실행했다는 뜻은 아니다. 실제 LLM 호출·DB 쓰기는 없다.

## 준비

- 로컬 Postgres와 평가 사본 `disaster_alert_eval_v6`, `.env.dev`. 공통 준비는 `backend/evaluation/README.md`를 따른다.
- 평가 DB와 라벨은 변경 전후 두 실행에서 동일하게 유지한다. 평가만 수행하며 재클러스터링하지 않는다.
- 운영 기록 검증은 일일 채점 이력이 있는 로컬 운영 사본에서 읽기 전용으로 한다. DB 스키마 변경·백필·판사 재호출은 없다.

## 1. 순수 계산과 RED 준비 (SC-001, SC-003)

```bash
cd backend
./gradlew test --tests "*PairCounterTest" --tests "*PairCountResultTest" --tests "*TrivialBaselinesTest" --tests "*ReportFormatterTest" --tests "*ClusteringDashboardMetricsTest"
```

- `PairCounterTest` 신규 1개: 다른 사건·다른 이벤트 관측을 맞게 나눈 쌍으로 센다.
- `PairCountResultTest` 신규 2개: 구성비 불변성, 기존 일일 집계 복원.
- `TrivialBaselinesTest` 신규 1개: 두 기준선이 50.0%다.
- 기존 `ReportFormatterTest`의 요약 기대값은 RED에서 새 계약으로 바꾼다. 균형 정확도 첫 줄·기준선·보조 F1을 검사하고 기존 F1 수치·틀린 쌍 표 검사는 유지한다. GREEN에서는 테스트를 수정하지 않는다.

구성비 입력은 (맞게 묶음, 과소병합, 과병합, 맞게 나눔) = (8, 2, 1, 9)와 (16, 4, 1, 9)다. 같은 사건 쌍만 2배로 늘려 구성비는 1:1 → 2:1이지만 균형 정확도는 둘 다 85.0%다. 양쪽을 같은 배수로 늘리는 입력으로 대신하지 않는다.

일일 복원 예시는 전체 100, 맞게 묶음 30, 과소병합 10, 과병합 5, UNSURE 4, 형식 실패 1 → 맞게 나눔 50이다. 판사의 모르겠음·형식 실패를 다른 사건 쌍으로 세지 않는지 확인한다.

분모 0 계약은 기존 리포트 표시 검사와 4절의 응답 확인으로 검증한다. 별도 경계값 테스트를 늘리는 작업으로 해석하지 않는다.

## 2. 리포트 회귀 (SC-002)

1. 시작점 `d6cfb4ed`에서 같은 평가 사본·라벨로 `backend/evaluation/reports/013-before.md`를 만든다.
2. 이 브랜치에서 동일한 명령으로 `013-after.md`를 만든다. `--eval.dataset` 의 같은 셋을 두 번 사용한다.
3. 원본은 그대로 두고 실행 시각 한 줄만 제거한 복사본을 만든다. 다음은 저장소 루트에서 실행하는 예시다.

```bash
python - <<'PY'
from pathlib import Path
for name in ('013-before', '013-after'):
    path = Path('backend/evaluation/reports') / f'{name}.md'
    lines = path.read_text(encoding='utf-8').splitlines(keepends=True)
    normalized = ''.join(line for line in lines if not line.startswith('- 실행 시각: '))
    path.with_name(f'{name}.normalized.md').write_text(normalized, encoding='utf-8')
PY
git diff --no-index backend/evaluation/reports/013-before.normalized.md backend/evaluation/reports/013-after.normalized.md
```

4. 정규화한 리포트의 차이는 `## 요약` 절 안에만 있어야 한다. 데이터셋 이름·틀린 쌍 표·추가 절 등은 비교에서 제외하지 않는다. `git diff`의 종료 코드 1은 차이가 있다는 뜻이므로, 코드만으로 실패 판정하지 말고 차이의 위치와 내용을 확인한다.
5. 기존 세 개수와 F1은 새 요약의 보조 항목과 **값으로** 대조한다. 예를 들어 기존 `점수 76.5점`은 새 요약의 `F1 76.5점`과 같은 값이며 문구가 같아야 하는 것은 아니다. 틀린 쌍 표는 내용·순서가 한 줄도 다르지 않아야 한다.
6. 요약에 균형 정확도·두 비율(분자/분모)·보조 지표·기준선 두 항목이 있는지 확인한다. 네 임시 파일은 확인 후 정리하고 커밋하지 않는다.

## 3. 일일 집계 복원 확인 (FR-008, FR-009, SC-004)

아래 SQL은 로컬 운영 사본을 읽기만 한다. 새 열을 만들거나 과거 기록을 갱신하지 않는다.

먼저 다음 조회 결과가 0행인지 확인한다. 행이 나오면 집계가 유효하다는 전제가 깨진 것이므로 원인을 확인한 뒤 다음 검증으로 넘어간다.

```sql
SELECT score_date, pair_count, correctly_merged, missed, over_merged,
       unsure_count, format_failure_count,
       pair_count - correctly_merged - missed - over_merged
                  - unsure_count - format_failure_count AS derived_count
FROM clustering_daily_score
WHERE least(pair_count, correctly_merged, missed, over_merged, unsure_count,
            format_failure_count,
            pair_count - correctly_merged - missed - over_merged
                       - unsure_count - format_failure_count) < 0;
```

```sql
WITH derived AS (
    SELECT s.*,
           pair_count - correctly_merged - missed - over_merged
                      - unsure_count - format_failure_count AS correctly_separated_derived
    FROM clustering_daily_score s
)
SELECT score_date, pair_count, correctly_separated_derived,
       CASE WHEN correctly_merged + missed > 0
                  AND over_merged + correctly_separated_derived > 0
            THEN round((correctly_merged::numeric / (correctly_merged + missed)
                      + correctly_separated_derived::numeric
                        / (over_merged + correctly_separated_derived)) * 50, 1)
       END AS balanced_accuracy
FROM derived ORDER BY score_date;
```

- 기존 여섯 집계값과 복원 값이 음수가 아닌지 먼저 확인한다. 불일치가 있으면 0으로 보정하지 말고 원인을 조사해 적용 전에 보고한다.
- 양쪽 분모가 양수인 모든 날에서 SQL 값과 API의 `correctlySeparated`·`balancedAccuracy`가 일치해야 한다. 한쪽 분모가 0이면 SQL 점수는 NULL, API에서는 점수 키 생략이다.
- 쌍별 기록이 없는 날도 유효한 날짜별 집계가 있으면 복원할 수 있다.

쌍별 기록이 **완전한 날만** 다음 조회로 교차 검증한다. 기록이 없거나 일부만 남은 날의 재집계값으로 기존 날짜별 집계를 덮어쓰지 않는다.

```sql
WITH pairs AS (
    SELECT score_date, count(*) AS stored_pair_count,
           count(*) FILTER (WHERE judge_verdict = 'DIFF' AND NOT system_same_event) AS recount
    FROM clustering_daily_score_pair GROUP BY score_date
)
SELECT s.score_date,
       s.pair_count - s.correctly_merged - s.missed - s.over_merged
                    - s.unsure_count - s.format_failure_count AS derived_count,
       coalesce(p.recount, 0) AS recount
FROM clustering_daily_score s
LEFT JOIN pairs p USING (score_date)
WHERE coalesce(p.stored_pair_count, 0) = s.pair_count
ORDER BY s.score_date;
```

- 이 조회의 모든 행에서 `derived_count = recount`여야 한다. 빠진 날짜는 쌍별 기록 검증 대상이 아닐 뿐, 점수 계산 불가를 뜻하지 않는다.
- 일일 로그는 균형 정확도를 대표로, F1을 보조로 보이는지 확인한다. 이를 확인하려고 유료 일일 채점을 다시 실행하지 않는다.

## 4. 대시보드와 계산 불가 응답 (FR-003, FR-010, SC-004)

- 날짜별 점수 칸 순서는 균형 정확도 → 빠짐없이 묶은 비율 → 섞지 않은 비율 → 맞게 묶은 비율 → F1이다. 비율은 분자·분모와 함께 보이고 판사 일치율은 유지한다.
- 날짜별 기록이 있으면 복원한 `correctlySeparated`와 `separation` 객체가 있다. 0은 실제 개수이므로 숨기지 않는다.
- 다른 사건 쌍이 없으면 `separation`은 `{numerator: 0, denominator: 0, percent: null}`이며 `balancedAccuracy` 키는 없다. 계산되는 completeness는 그대로 보인다.
- 같은 사건 쌍이 없으면 completeness의 `percent`는 null이고 `balancedAccuracy` 키는 없다. 계산되는 separation은 그대로 보인다.
- 유효 쌍이 없을 때도 균형 정확도 키는 없고, 기록 없는 날은 날짜·상태만 온다. 화면은 계산 불가와 기록 없음을 구분한다.
- `balancedAccuracy?: number`의 `undefined`를 처리해 "계산할 수 없음"으로 보인다. truthy 검사로 0점까지 숨기지 않는다. 최상위의 생략된 키와 Ratio 내부의 `percent: null`을 구분한다.
- 실제 날짜에 해당 상황이 없으면 같은 집계의 로컬 입력으로 응답·표시를 확인하며, 운영 기록은 수정하지 않는다.

## 5. 문서 (SC-005)

```bash
grep -rn "조화평균\|점수(조화" specs/006-clustering-evaluation specs/008-clustering-admin-dashboard backend/evaluation/README.md
```

남은 곳이 보조 지표 설명이나 과거 기록인지 확인한다. 대표 지표 서술은 균형 정확도이며, 006의 지표 제한 규칙이 2026-11-24 결정으로 바뀌었다고 설명해야 한다.
