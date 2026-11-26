# 클러스터링 평가 — 실행하고 읽는 법

이벤트 클러스터링이 잘 묶었는지를 사람이 라벨한 알림 쌍으로 재는 도구(`EvaluationRunTool`)의 설명서다. 계산은 일부러 단순하게 두었다. 무엇을 정답으로 보는지는 [spec.md](spec.md), 왜 이렇게 재는지와 지금까지의 측정 결과는 [research.md](research.md)를 본다.

## 하는 일 한 줄
사람이 라벨한 **알림 쌍**마다 "두 알림이 **같은 이벤트**에 들어 있는가"만 본다.

## 평가 한 바퀴
튜닝 하나를 재는 순서다. 운영 DB 에서는 측정하지 않는다.

1. **평가 사본**: 운영 스냅샷을 로컬 docker Postgres 에 복사한다(현재 기준선 사본 `disaster_alert_eval_v5`, 알림 62,554건).
2. **분류 확인**: 사본의 `alert_kind` 가 채워져 있어야 한다. **평가는 반드시 분류가 채워진 사본에서 돌린다** — 비어 있으면 비사건도 클러스터링돼 산불 예방 안내가 산불 이벤트를 끝없이 이어 붙인다(research.md 3절 곁가지). 비었으면 `AlertKindBackfillTool`(프로필 `alert-kind-backfill`)로 채운다. 도구에 범위 지정 기능이 없어(`--alert-kind.limit`, `--alert-kind.threads` 뿐), 일부만 분류할 때는 대상 범위를 고르고 나머지를 `INCIDENT` 로 채우는 일을 도구를 돌리기 전에 SQL 로 한다.
3. **재클러스터링**: 클러스터링 코드나 설정을 바꿨다면 사본을 다시 묶는다. 알림의 이벤트 배정(`disaster_alert` 의 `event_` 컬럼 5개)을 `NULL` 로 지우고, `disaster_events` 를 참조하는 `disaster_event_translation`·`event_region_impact`·`region_risk_index` 를 `TRUNCATE` 한 뒤, `disaster_events` 를 `DELETE` 한다. `disaster_alert.event_id` 가 `disaster_events` 를 참조해 `disaster_events` 는 `TRUNCATE` 할 수 없다(`TRUNCATE … CASCADE` 는 `disaster_alert` 까지 비우므로 절대 쓰지 않는다). 위험도 기능은 2026-11-05 에 삭제됐지만 그 두 테이블과 외래 키는 남아 있어 지금도 비워야 한다. 그다음 `EventClusteringBackfillTool --backfill.recluster=true` 를 돌린다. 명령은 `EventClusteringBackfillTool` javadoc 의 권장 절차와 [`backend/evaluation/README.md`](../../backend/evaluation/README.md). 로그를 파일로 남긴다(틀린 쌍을 들여다볼 때 쓴다). LLM 폴백은 켠 상태가 기준이고, borderline 사고성 건은 LLM 을 부르므로 비용이 든다(분류 1건·폴백 1회 각 약 $0.0003, `gpt-4o-mini`).
4. **평가**: 아래 "실행"으로 리포트를 만든다.
5. **비교**: 튜닝 전 리포트와 diff 한다(아래 "튜닝 전후 비교").

## 실행
라벨은 접속한 평가 사본 DB 의 `evaluation_pair_label` 에서 읽는다(적재 방법은 [backend/evaluation/README.md](../../backend/evaluation/README.md) 클러스터링 평가 절). 평가용 DB 사본에서 `eval` 프로필로 실행한다(OpenAI 호출 없음, 운영 DB 에는 실행하지 않는다). `backend/` 에서 루트 `.env.dev` 를 불러온 뒤 실행한다.
```
set -a; . ../.env.dev; set +a
./gradlew bootRun --args='--spring.profiles.active=eval --spring.datasource.url=jdbc:postgresql://localhost:5432/disaster_alert_eval_v5 --server.port=0 --eval.dataset=cluster-eval-2026-11 --eval.report=<리포트 경로>'
```
- `--spring.datasource.url`(사실상 필수): 평가 사본. 빼면 `.env.dev` 의 개발 DB 에 붙어 엉뚱한 배정을 읽는다. `--server.port=0` 은 실행 중인 다른 서버와 포트가 겹치지 않게 한다.
- `--eval.dataset`(필수, 또는 `--eval.pair-labels-as-of`): 평가 셋 이름. 그 DB 에 라벨이 없으면 실패한다.
- `--eval.report`(선택): 마크다운 리포트 경로. 없으면 로그로만 출력한다.
- `--eval.bcubed`(선택): B-cubed 정답 JSON 경로(예: `evaluation/datasets/bcubed-2026-05.labels.json`). 주면 리포트 맨 끝에 `## 알림 단위 점수` 절이 붙고, 안 주면 그 절이 없다. `unreviewed` 가 남았거나 형식이 틀리면 DB 를 건드리기 전에 즉시 실패한다. 이 옵션을 줘도 라벨 출처 옵션(`--eval.dataset` 또는 `--eval.pair-labels-as-of` 중 하나)은 계속 필수다.
- 리포트 머리에는 데이터셋 이름과 실행 시각만 적고, 끝나면 애플리케이션이 종료된다.
- 라벨이 DB 에 없으면(0쌍) 채점을 시작하기 전에 실패한다.

## 쌍 하나가 어떻게 세어지나
| 사람의 라벨 | 시스템 | 세는 이름 |
|---|---|---|
| 같은 사건 | 같은 이벤트 | **맞게 묶음** |
| 같은 사건 | 다른 이벤트 | **과소병합** |
| 다른 사건 | 같은 이벤트 | **과병합** |
| 다른 사건 | 다른 이벤트 | **맞게 나눔** |

## 대표 균형 정확도와 보조 지표
2026-11-24 결정([013 명세](../013-balanced-accuracy-metric/spec.md))으로 대표 점수를 F1에서 **균형 정확도**로 바꿨다. 네 개수(맞게 묶음·과소병합·과병합·맞게 나눔)에서 계산한다.

- **맞게 묶은 비율** = 맞게 묶음 ÷ (맞게 묶음 + 과병합). 시스템이 묶은 쌍 중 사람도 같은 사건이라 한 비율. 과병합이 많을수록 내려간다. (= 100 − 과병합율)
- **빠짐없이 묶은 비율** = 맞게 묶음 ÷ (맞게 묶음 + 과소병합). 사람이 같은 사건이라 한 쌍 중 시스템이 묶은 비율. 과소병합이 많을수록 내려간다. (= 100 − 과소병합율)
- **섞지 않은 비율** = 맞게 나눔 ÷ (맞게 나눔 + 과병합). 다른 사건 쌍 중 시스템도 따로 둔 비율.
- **대표: 균형 정확도** = (빠짐없이 묶은 비율 + 섞지 않은 비율) ÷ 2. 개수에서 바로 계산하고 같은 사건·다른 사건 쌍 중 한쪽 분모라도 0이면 계산 불가다.
- **보조: F1** = 맞게 묶은 비율과 빠짐없이 묶은 비율의 **조화평균** = 2 × 맞게 묶음 ÷ (2 × 맞게 묶음 + 과소병합 + 과병합). 기존 계산과 값은 유지하며 맞게 묶은 비율도 보조로 표시한다.

**대표 지표를 바꾼 이유** — 272쌍 중 모르겠음을 뺀 집합은 같은 사건 144쌍·다른 사건 81쌍이다. 전부 묶기만 해도 F1이 78.0점이어서 판단 없는 결과를 높게 읽을 수 있다. 균형 정확도는 두 클래스의 정답률을 같은 비중으로 보며, 클래스별 정답률이 같으면 쌍 구성비가 달라도 같다.

| 판단 없는 기준선 | 빠짐없이 묶은 비율 | 섞지 않은 비율 | 균형 정확도 | 보조 F1 |
|---|---:|---:|---:|---:|
| 전부 하나로 묶음 | 100% | 0% | 50.0% | 78.0점 |
| 전부 따로 둠 | 0% | 100% | 50.0% | 0점 |

기준선은 재클러스터링 없이 같은 유효 쌍 집합에서 계산한다. 두 클래스 중 한쪽만 있으면 기준선의 균형 정확도도 계산 불가다.

예전 리포트의 **과소병합율·과병합율**(낮을수록 좋음)은 빠짐없이 묶은 비율·맞게 묶은 비율을 뒤집은 값이라, 리포트에서 괄호 안에 함께 적는다.

> 2026-11-09의 "지표를 이 셋 밖으로 더 늘리지 않는다" 규칙은 2026-11-24 결정으로 바뀌었다. 대표는 균형 정확도, 맞게 묶은 비율과 F1은 보조다. 지난 리포트와 006 research의 F1 수치는 당시 기준의 기록으로 남기고 다시 만들지 않는다. 새 기준과 비교하려면 동일한 라벨·배정을 새 계산기로 다시 잰다.

### 예시 (쌍 6개)
| 쌍 | 사람 | 시스템 | 세는 이름 |
|---|---|---|---|
| 1 | 같은 사건 | 같은 이벤트 | 맞게 묶음 |
| 2 | 같은 사건 | 같은 이벤트 | 맞게 묶음 |
| 3 | 같은 사건 | 다른 이벤트 | 과소병합 |
| 4 | 같은 사건 | 다른 이벤트 | 과소병합 |
| 5 | 다른 사건 | 같은 이벤트 | 과병합 |
| 6 | 다른 사건 | 다른 이벤트 | 맞게 나눔 |

- 맞게 묶은 비율 = 2 ÷ (2 + 1) = 66.7%. 묶은 3쌍 중 2쌍이 맞았다. (과병합율 33.3%)
- 빠짐없이 묶은 비율 = 2 ÷ (2 + 2) = 50.0%. 같은 사건 4쌍 중 2쌍을 묶었다. (과소병합율 50.0%)
- 섞지 않은 비율 = 1 ÷ (1 + 1) = 50.0%.
- 균형 정확도 = (50.0 + 50.0) ÷ 2 = 50.0%.
- 보조 F1 = 2 × 2 ÷ (4 + 2 + 1) = 57.1점.

## 평가에서 빠지는 쌍
- **모르겠음**: 라벨이 "모르겠음"인 쌍.
- **이벤트 미배정**: 두 알림 중 하나라도 이벤트에 들어 있지 않은 쌍(비사건으로 분류되어 클러스터링에서 빠졌거나 처리되지 않은 알림). 정답을 가릴 수 없어서 빼고 건수만 센다.
- 두 건수는 리포트에 따로 적힌다. 둘 다 해당하면 모르겠음으로 센다.
- **알림 단위 점수(B-cubed)는 다르다.** `--eval.bcubed` 로 내는 점수에서 미배정 알림은 빼지 않고 혼자인 이벤트로 계산한다. 시스템이 못 묶은 알림을 빼면 못 묶을수록 점수가 오르기 때문이다. 모르겠음(분류 불가)만 뺀다.

## 리포트
```
## 요약
- 균형 정확도 77.5% — 빠짐없이 묶은 비율과 섞지 않은 비율의 평균
- 빠짐없이 묶은 비율 65.0% — 같은 사건 60쌍 중 39쌍 묶음, 21쌍 과소병합 (과소병합율 35.0%)
- 섞지 않은 비율 90.0% — 다른 사건 30쌍 중 27쌍 따로 둠, 3쌍 과병합
- 보조: 맞게 묶은 비율 92.9% (묶은 42쌍 중 39쌍), F1 76.5점
- 판단 없는 기준선: 전부 하나로 묶음 균형 정확도 50.0% · F1 80.0점 / 전부 따로 둠 50.0% · F1 0.0점
- 제외: 모르겠음 6쌍, 이벤트 미배정 0쌍
## 과소병합 쌍 / 과병합 쌍
| alertA | alertB | 유형 | 같은 시군구 | 간격(시간) | 본문 거리 | 종류 |
```
(위 숫자는 설명용 예시다.) 과소병합 쌍과 과병합 쌍에는 **이유를 계산하지 않고 사실만** 적는다.
- 유형: 두 알림의 재난 유형(다르면 "A/B").
- 같은 시군구: 두 알림이 시군구(법정동 코드 앞 5자리)를 하나라도 공유하면 "예".
- 간격(시간): 두 알림 발송 시각의 차.
- 본문 거리: 두 알림 본문 임베딩의 코사인 거리(작을수록 비슷, 병합 기준은 0.15 이하). 임베딩이 없으면 "-".
- 종류: `과소병합`(같은 사건인데 안 묶음) 또는 `과병합`(다른 사건인데 묶음). 2026-11-07 에 추가한 칸이라 그 전 리포트에는 없다. 2026-11-11 이전 리포트는 같은 뜻을 `놓침`·`잘못 묶음`으로 적었다.

리포트를 볼 때는 전체 쌍 수 대비 계산에 쓰인 쌍 수("제외" 줄)를 함께 본다. 리포트는 `backend/evaluation/reports/` 에 둔다. 지표 변경 전 F1 기준점은 `2026-11_2026-11-11-recheck.md`(78.5점)이고, 호우 72h 튜닝 결과는 `2026-11_2026-11-12-rain72.md`(90.2점)다. 2026-11-09 이전 리포트는 지운 예전 셋으로 잰 기록이라 이 셋과 직접 비교하지 않는다(그중 `*_2026-10-19.md` 세 개가 이 도구로 잰 첫 기준점, 그 전 `before.md`·`after_v0.md` 등은 옛 형식).

## 튜닝 전후 비교
**점수나 비율의 변화로 개선 여부를 판단하지 않는다.** 같은 셋을 반복해 쓰므로 쌍 단위로 비교하는 편이 훨씬 민감하다(근거: research.md 2절).

두 원본은 보존하고 비교용 복사본에서 `- 실행 시각: `으로 시작하는 한 줄만 제거한 뒤 `git diff --no-index <이전 정규화본>.md <새 정규화본>.md`로 비교한다. 틀린 쌍 표는 라벨 파일 순서로 나오므로, `-` 줄은 이번에 사라진 쌍, `+` 줄은 새로 틀린 쌍이다. 평가에서 빠진 쌍도 표에서 사라지므로 "제외" 줄이 같은지 먼저 확인한다. 013 지표 변경만 검증할 때는 요약 밖에 차이가 없어야 하고 기존 세 개수·F1 값과 틀린 쌍 표가 같아야 한다. 절차는 [013 quickstart 2절](../013-balanced-accuracy-metric/quickstart.md). 라벨을 고친 뒤 다시 쟀다면 라벨 변경과 배정 변경으로 바뀐 몫을 나눠 보고한다.

## 틀린 쌍을 들여다보는 법
리포트의 틀린 쌍은 사실만 적혀 있고 **왜 그렇게 됐는지는 적히지 않는다.** 원인은 사람이 따로 찾는다. 판정 순간을 기록하던 `clustering_trace` 는 2026-11-08 에 들어냈으므로(research.md 4절) 쓸 수 있는 수단은 둘뿐이다.

1. **재클러스터링 로그** — 알림마다 탄 경로와 병합 결과가 찍힌다. 후보 이벤트와 거리(`event=… dist=…`)는 **임베딩 경로(일반 후보 검색)에서만** 찍힌다. 동물 경로(`clusterAnimal`)는 후보 수와 선택된 이벤트만 남기고 후보 목록·거리는 남기지 않으므로 로그를 보존해도 동물 후보는 확인할 수 없다. 재클러스터링할 때 로그를 파일로 남겨 두면 그 실행에 한해 판정을 되짚을 수 있다.
2. **후보 검색을 SQL 로 재현** — 나중에 들어온 알림 기준으로 "같은 시군구 + 후보 윈도우(168h) 안 이벤트"를 거리순으로 다시 뽑아 본다. `findTopCandidates` 처럼 윈도우는 **이벤트의 마지막 알림 시각**(판정 시점 이전)으로 검사하고 broadcast 이벤트는 뺀다. 그래도 그 순간 실제로 본 후보가 아니라 **지금 이벤트 배정으로 다시 계산한 값**이라는 한계가 있다.

```sql
-- 먼저 들어온 알림이 속한 정답 이벤트
SELECT event_id FROM disaster_alert WHERE disaster_alert_id = <먼저 알림 id>;

-- 나중 알림 기준으로 같은 시군구·168h 안 후보를 거리순으로 재현
WITH t AS (SELECT embedding, created_at FROM disaster_alert WHERE disaster_alert_id = <나중 알림 id>),
     s AS (SELECT DISTINCT left(legal_district_code,5) c FROM disaster_alert_region WHERE disaster_alert_id = <나중 알림 id>)
SELECT a.event_id, round(min(a.embedding <=> (SELECT embedding FROM t))::numeric,4) AS dist
FROM disaster_alert a
JOIN disaster_alert_region r ON r.disaster_alert_id = a.disaster_alert_id
JOIN disaster_events e ON e.id = a.event_id
WHERE e.is_broadcast = false
  AND left(r.legal_district_code,5) IN (SELECT c FROM s)
  AND a.created_at < (SELECT created_at FROM t)
  AND a.embedding IS NOT NULL
  -- 이벤트의 마지막 알림(판정 시점 이전)이 168h 안인가 = 168h 안에 알림이 하나라도 있는가
  AND EXISTS (SELECT 1 FROM disaster_alert a2
              WHERE a2.event_id = a.event_id
                AND a2.created_at < (SELECT created_at FROM t)
                AND a2.created_at > (SELECT created_at FROM t) - interval '168 hours')
GROUP BY a.event_id ORDER BY dist LIMIT 3;
```

정답 이벤트가 이 SQL 의 상위 3개(실제 검색의 상위 3개와 같은 범위)에 있어도 **이것만으로 판정 단계 실패를 확정하지 마라** — 이 결과는 판정 당시의 후보를 증명하지 못한다. 재클러스터링 로그로 정답 이벤트가 실제 검색 후보였음을 확인한 경우에만 검색이 아니라 판정(임계값·LLM 폴백·가드)에서 막힌 것으로 본다. 그 다음은 코드를 읽어야 한다 — 기록이 있어도 "무엇을 봤는가"까지만 알 수 있고 "왜 그 가드에 걸렸는가"는 코드에만 있다(2026-11-07 거제 화재 사례에서 실제로 그랬다).

## 데이터셋
`backend/evaluation/datasets/` 에 있다. 셋을 어떻게 뽑고 매겼는지는 각 셋의 기록 문서에 있고, 지운 예전 셋의 이력은 research.md 7절.

| 파일 | 내용 |
|---|---|
| `cluster-eval-2026-11.json` | **유일한 쌍 평가 셋**(272쌍, 같은 사건 144 / 다른 사건 81 / 모르겠음 47 — 2026-11-17 재확인 뒤). 사본 `disaster_alert_eval_v5` 에서 잰다. 구성·판정 기준·라벨 변경 기록은 `.rubric.md` |
| `bcubed-2026-05.labels.json` | B-cubed 정답(2026-05 한 달 사건 알림 전부를 사건별로 나눔). `--eval.bcubed` 로 넘긴다 |
| `bcubed-2025-08.labeling.html` | 2025-08 B-cubed 라벨링 화면(291건). 2026-11-13 기준 불안정으로 중단 |
| `alert-kind-*.json` | 사건/비사건 분류기 평가용 사람 라벨(개발 150·검증 100·테스트 100·R 100, 재판정 54건 `alert-kind-recheck-labels.json` 이 우선). 결과는 research.md 8절 |

쌍 셋 JSON 은 배열이고, 항목마다 `q`(문항 번호), `label`(SAME·DIFF·UNSURE), `relation`, `gap`, `a`·`b`(각각 `id`, `at`, `type`, `region`, `message`)가 있다. 평가 계산에는 `a.id`·`b.id`·`label` 만 쓰고 나머지는 사람이 판정을 대조하려고 둔 필드다. 데이터셋 이름은 파일명에서 확장자를 뗀 것이다.

## 이 도구가 하지 않는 것
- **왜 놓쳤는지(검색 단계인지 판정 단계인지) 계산하지 않는다.** 어느 단계가 막혔는지는 필요할 때 별도로 조사한다.
- **클러스터링을 다시 하지 않는다.** DB 에 저장된 이벤트 배정을 읽을 뿐이다. 클러스터링 코드를 바꿨다면 먼저 재클러스터링(백필)으로 새 스냅샷을 만든 뒤 그 DB 를 평가해야 바뀐 결과가 나온다. 평가와 재클러스터링은 일부러 분리했다.
- **분류기는 재지 않는다.** 사건/비사건 분류는 `AlertKindEvalTool`(프로필 `alert-kind-eval`)이 라벨 셋의 알림을 실제 `AlertKindClassifier` 로 분류해(저장 안 함) 사람 라벨과 대조하고 로그로 남긴다. 특보·안내는 비사건으로 합쳐 센다. `--alert-kind-eval.labels=<라벨 json>` `--alert-kind-eval.override=alert-kind-recheck-labels.json`. 검증 셋 1회 약 $0.03.

## 구현 후 검증 시나리오 ([plan.md](plan.md), 아직 구현 전)

재확인 후보 추출과 판사 일치율 측정 모드는 2026-11-17 구현·실행됐다. 일일 채점은 2026-11-13 계획 단계이며 아직 없다 — 구현하면 실제 옵션·출력에 맞게 고친다.

**재확인 후보 추출** (`--eval.recheck`, 무료 — 2026-11-17 구현)
```
./gradlew bootRun --args='--spring.profiles.active=eval --spring.datasource.url=jdbc:postgresql://localhost:5432/disaster_alert_eval_v5 --server.port=0 --eval.dataset=cluster-eval-2026-11 --eval.bcubed=evaluation/datasets/bcubed-2026-05.labels.json --eval.recheck=D,E,G,I --eval.report=<후보 목록 경로>'
```
- `--eval.recheck` 를 주면 채점하지 않고 후보 목록만 낸다. `--eval.bcubed` 는 선택(주면 B-cubed 절이 붙음). 수신 시군구 코드는 라벨 JSON 에 없어 사본 DB 의 `disaster_alert_region` 에서 읽는다.
- 출력: 규칙별 건수 요약 → 규칙별 후보. 272쌍은 쌍마다 두 알림의 id·발송 시각·유형·발신 기관(종류)·수신 시군구 수·본문과 현재 라벨(G 는 같은 시설 후보 이름도). B-cubed 는 걸린 알림이 든 묶음(정답 사건 / 혼자 알림 / 분류 불가) 전체를 알림 줄로 내고 걸린 알림에 **걸림** 표시, G 는 걸린 쌍(`a(묶음) ↔ b(묶음) · 같은 시설 후보`)도 낸다. B-cubed 의 D·E·I 는 알림 하나로, G 는 같은 유형 알림끼리의 쌍으로 판정하며, 분류 불가 알림도 판정한다(새 기준으로는 나뉠 수 있어서).
- 2026-11-17 첫 실행(`backend/evaluation/reports/recheck-2026-11-17.md`): 272쌍 중 D 31 · E 6 · G 15 · I 24쌍(겹침 빼면 55쌍), B-cubed 알림 250건(분류 불가 20 포함) 중 D 36 · E 4 · G 28 · I 53건(묶음 18 · 4 · 17 · 24개). G 에는 "인천" 같은 도시 이름 후보가 섞인다(받아들인 한계).

**판사 일치율 측정** (`--eval.judge-agreement`, **유료 약 $0.86(사람 UNSURE 를 뺀 227쌍, 2026-11-17 실측. 리포트 금액은 캐시 할인을 뺀 상한) — 실행 전 승인**, 2026-11-17 구현)
```
./gradlew bootRun --args='--spring.profiles.active=eval --spring.datasource.url=jdbc:postgresql://localhost:5432/disaster_alert_eval_v5 --server.port=0 --eval.dataset=cluster-eval-2026-11 --eval.judge-agreement --eval.report=<리포트 경로>'
```
- 대상: 재확인을 마친 272쌍에서 사람 UNSURE 를 뺀 쌍. 운영 채점과 같은 `ClusteringJudge`(gpt-4o-mini, 온도 0 — 2026-11-18 전까지 gpt-4o)를 쌍마다 순차로 부르므로 `OPENAI_API_KEY` 가 필요하고 호출마다 요금이 든다. 채점은 하지 않고(`--eval.bcubed` 는 무시) 이 모드만 돈다. 두 알림의 발송 시각·유형·본문과 수신 시군구는 사본 DB 에서 읽고, 수신 지역 이름은 `legal_district` 의 시군구 행 이름(예: "충청북도 청주시")이다.
- 출력(마크다운): 프롬프트 버전·모델·대상 쌍 수 → 같은 사건 쌍 일치율 `a/b (%)`·다른 사건 쌍 일치율 `c/d (%)`와 분모에서 빠진 쌍(판사 UNSURE·형식 실패·호출 실패 수) → 교차표(사람 라벨 × 판사 판정, 형식 실패는 UNSURE 와 별도 열) → 토큰 합과 추정 비용(gpt-4o-mini 단가 입력 $0.15/1M·출력 $0.60/1M, 모델을 바꾸면 `JudgeAgreementReport` 단가도 고친다) → **불일치 쌍 목록**(두 알림의 id·발송 시각·유형·본문 앞 100자, 사람 라벨·판사 판정) → 판사가 답하지 못한 쌍(UNSURE·형식 실패) 목록 → 호출 실패 쌍(id·사유).
- 사람 UNSURE 쌍은 판정하지 않는다(교차표·일치율·토큰에 없음). 판사 호출이 예외로 끝난 쌍은 그 쌍만 호출 실패로 따로 세고 나머지는 계속한다(형식 실패와 섞지 않는다).
- 결과를 보고 판사 버전 상수를 갱신한다. 2026-11-17 첫 실행(v1, 227쌍, $0.864)을 같은 날 두 차례 라벨 재확인 뒤 라벨로 다시 센 값 같은 사건 124/142 · 다른 사건 77/80 을 v1 상수로 뒀다(`backend/evaluation/reports/judge-agreement-2026-11-17.md`). 2026-11-18 같은 프롬프트를 gpt-4o-mini 로 잰 값 같은 사건 127/141 · 다른 사건 74/81($0.051)을 v2 상수로 두고 판사를 mini 로 바꿨다(`backend/evaluation/reports/judge-agreement-2026-11-18-mini.md`). 2026-11-11 2차(87.6%·95.1%)는 기준과 라벨이 달라 같은 범위 비교가 아니다.

**일일 채점**
- 판정 대상 쌍과 점수: 개발 DB에 하루치 사건 알림을 두고 판사를 목킹한 통합 테스트로 확인한다(대상 쌍 수, `PairCounter` 결과, 저장된 줄).
- 운영에서 불일치 쌍 보기(읽기 전용):
```sql
SELECT p.judge_verdict, p.disagreement, p.event_a, p.event_b,
       a.disaster_alert_id AS a_id, a.created_at AS a_at, a.message AS a_msg,
       b.disaster_alert_id AS b_id, b.created_at AS b_at, b.message AS b_msg
FROM clustering_daily_score_pair p
JOIN disaster_alert a ON a.disaster_alert_id = p.alert_a
JOIN disaster_alert b ON b.disaster_alert_id = p.alert_b
WHERE p.score_date = DATE '2026-12-01' AND p.disagreement IS NOT NULL;
```
- 확인: 일치율 상수가 없는 판사 버전이면 채점이 돌지 않고 warn만 남는지. 하루를 빼먹은 뒤 다음 실행이 그날만 채우고 이미 있는 날은 다시 판정하지 않는지.

