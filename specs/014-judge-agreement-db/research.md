# 리서치: 판사 일치율 측정 결과를 DB 에 두고 현재 라벨로 계산한다

명세의 미정 사항은 2026-11-25 사용자 답으로 모두 정해졌다(운영 반영 C, 리포트 파일 A). 아래는 설계 판단과 근거다.

## R1. 011 설계에서 두 테이블만, 열은 줄여서 가져온다

- **결정**: `judge_agreement_run`·`judge_agreement_run_pair` 이름은 011 data-model 4·5절과 같게 두되, 상태(`RUNNING/COMPLETED/ABORTED`)·비교 버전(`baseline_*`)·승인(`approved_by`, `estimated_usd`)·진행 수(`judged_pairs`)·이유별 제외 수 열은 넣지 않는다. 측정 하나에 판사 버전 하나만 기록한다.
- **이유**: 011 은 운영 화면의 재측정 버튼(앱 안 장시간 실행, 동시 실행 막기, 운영 판사와 후보 동시 측정)을 전제로 한 설계다. 이번 범위는 사람이 CLI 로 돌리고 끝에 한 번 저장하는 흐름이라, 진행 중 상태가 DB 에 남을 일이 없다(헌법 I). 이름만 같게 두면 011 을 재개할 때 열을 더하는 마이그레이션으로 이어 쓸 수 있다.
- **대안**: 011 설계 그대로 — 쓰지 않는 열과 상태 전이가 생긴다. 기각.

## R2. 사람 라벨은 측정에 복사하지 않고 현재 라벨로 계산한다

- **결정**: `judge_agreement_run_pair` 에 판사 판정만 두고, 일치율은 그 측정의 `dataset` 에 해당하는 현재 `evaluation_pair_label` 과 맞대어 계산한다(FR-002·FR-003).
- **이유**: 이 기능의 목적이 "라벨을 고치면 판사 재호출 없이 일치율이 바뀐다"(SC-003)다. 측정 당시 라벨을 복사하면 계산 기준이 둘로 갈린다. 측정 당시 값이 필요하면 그날 일일 채점 기록(`clustering_daily_score.agreement_*`)에 이미 복사돼 남는다.
- **대안**: 011 처럼 `human_label` 복사 — 감사용으로는 좋지만 지금 쓰는 곳이 없다. 기각.

## R3. 쌍은 순서 없이 맞춘다, 알림 외래 키는 걸지 않는다

- **결정**: `judge_agreement_run_pair` 는 `alert_a < alert_b` 로 정규화해 저장한다. `evaluation_pair_label` 은 a/b 를 원래 JSON 순서대로 저장하므로(덤프 SQL 주석), 계산 때 라벨 쪽을 `LEAST/GREATEST` 로 정규화해 맞춘다. `disaster_alert` 외래 키는 걸지 않는다.
- **이유**: V146 시드는 알림이 없는 DB(테스트 DB, 새 개발 DB, 라벨 덤프 전의 운영 DB 순서 무관)에서도 들어가야 한다. 측정 기록은 그때의 사실이라 알림 삭제와 묶일 이유도 없다. 알림이 지워지면 라벨 쪽이 `ON DELETE CASCADE` 로 사라지고, 그 쌍은 "라벨 없음"으로 계산에서 빠진다(명세 예외 상황).

## R4. v2 쌍별 225건은 리포트와 현재 라벨로 복원해 고정값으로 시드한다

- **결정**: 구현 때 로컬 DB(272쌍 라벨이 있는 `disaster_alert_prod` 사본)에서 아래 규칙으로 225행을 뽑아 V146 에 **고정 VALUES** 로 넣는다. 시드는 `evaluation_pair_label` 을 읽지 않는다.
  - 리포트(`backend/evaluation/reports/judge-agreement-2026-11-18-mini.md`)의 `#### A ↔ B · 사람 X · 판사 Y` 24줄(SAME→DIFF 14, SAME→UNSURE 3, DIFF→SAME 7)은 적힌 판정 Y.
  - `cluster-eval-2026-11` 라벨 중 UNSURE 가 아닌 나머지 201쌍은 판정 = 사람 라벨.
- **확인(2026-11-25)**: 리포트의 24줄이 모두 위 형식으로 읽히고, 현재 라벨 구성(SAME 144 / DIFF 81 / UNSURE 47)과 리포트 교차표(SAME→SAME 127 · DIFF 14 · UNSURE 3, DIFF→DIFF 74 · SAME 7)가 맞는다. 11-18 측정 때 라벨과 현재 DB 라벨은 같다(2026-11-24 JSON·DB 272쌍 대조).
- **판정 분포(시드 검증값)**: SAME 134(127+7) · DIFF 88(14+74) · UNSURE 3, 합 225.
- **재측정하지 않는 이유**: 11-18 이후 일일 채점 기록에 127/141·74/81 이 복사돼 있다. 다시 재면 온도 0 이어도 한두 쌍이 달라져 DB 의 v2 값과 이미 저장된 날의 값이 어긋난다. 복원은 무료이고 정확히 같다.
- **뽑는 방법**: 일회성 SQL(리포트 24쌍을 VALUES 로 넣고 라벨과 LEFT JOIN, `COALESCE(listed.verdict, label)`)로 결과를 받아 마이그레이션 파일에 붙인다. Python 스크립트는 쓰지 않는다(평가 도구는 Java/SQL 원칙).

## R5. v1 은 합계만 넣는다

- **결정**: v1 측정 행은 `has_pair_verdicts = false` 와 합계 열 `legacy_same_num/den`, `legacy_diff_num/den`(124/142·77/80)만 갖고 쌍별 행이 없다. 합계 열은 이 경우에만 채우고, 쌍별 판정이 있는 측정에서는 NULL 이다(CHECK 제약).
- **이유**: v1 숫자는 측정 뒤 라벨 재확인 두 번을 반영해 손으로 다시 센 값이라 리포트 쌍 목록과 맞지 않을 수 있다. v1 은 더 쓰지 않는 버전이라 버전 이력 표시용 합계면 충분하다.

## R6. 측정 기록의 버전 이름은 카탈로그 기준이다

- 11-18 리포트 머리에는 "판사 프롬프트 버전 v1 · 모델 gpt-4o-mini" 라고 적혀 있다. 측정 당시 코드의 버전 표기가 아직 v1 이었고, mini 로 바꾼 뒤 카탈로그에서 v2 로 이름 붙였다(같은 지시문 + mini). 시드는 `judge_version = 'v2'`, `model = 'gpt-4o-mini'` 로 넣고 마이그레이션 주석에 이 차이를 적는다.
- 측정 시각(`finished_at`)은 리포트가 커밋된 시각을 쓴다: v1 `2026-11-17 14:30:28`(c2ecbfa1), v2 `2026-11-18 09:06:05`(8ad95f19). 토큰·비용은 리포트 값(v1 343,825 / 459, $0.864, v2 340,740 / 453, $0.051).

## R7. 계산 규칙은 순수 클래스 하나에 둔다

- **결정**: `domain/event/judge/JudgeAgreementCalculator`(JPA·Spring 없음, 헌법 VI). 입력은 쌍별 판정 목록(정규화된 a, b, 판정)과 현재 라벨 맵(정규화된 쌍 → SAME/DIFF/UNSURE). 출력은 `Agreement`(기존 `ClusteringJudge.Agreement` 재사용) + 판사 UNSURE·형식 실패·호출 실패 수 + 측정에 없는 라벨 쌍 수 + 라벨이 사라진 판정 수.
- 규칙(지금 `JudgeAgreementCounter` 와 같음): 사람 UNSURE 는 계산에서 뺀다. 사람 SAME 쌍 중 판사 SAME/DIFF 만 같은 사건 분모, 그중 SAME 이 분자. DIFF 도 같은 방식. 판사 UNSURE·형식 실패·호출 실패는 분모에서 빼고 따로 센다.
- **기존 `JudgeAgreementCounter` 와의 관계**: Counter 는 측정 중 교차표·리포트용으로 스냅숏까지 들고 있다. 리포트가 없어지므로(R10) Counter 의 교차표는 콘솔 요약에만 쓰고, 일치율 자체는 측정 직후에도 계산기로 다시 계산해 같은 규칙 하나만 남긴다(Counter 정리는 tasks 에서 판단 — 남는 사용처가 콘솔 요약뿐이면 계산기로 대체).

## R8. 버전별 일치율 조회와 "계산할 수 없음"

- **최신 측정**: 그 버전의 측정 중 `finished_at` 이 가장 늦은 것(같으면 id 큰 것).
- **계산할 수 없음(Optional.empty)**: 그 버전에 측정이 없거나, 쌍별 판정이 있는 측정인데 그 `dataset` 의 라벨이 DB 에 하나도 없을 때. 일일 채점은 이때 지금처럼 판사를 부르지 않고 warn 후 건너뛴다(FR-007, 다음 실행이 다시 채점).
- 분모가 0 이 되는 경우(라벨은 있는데 매칭이 하나도 없음)는 값으로 돌려주고, 화면은 기존 규칙대로 `-` 를 보인다.

## R9. 카탈로그에는 버전·모델·단가만 남긴다

- `JudgeVersionCatalog.Entry` 에서 `sameNum…diffDen`, `measuredOn` 을 지운다. 순서(최신 먼저)·모델·단가·`estimateUsd` 는 그대로라 `DisagreementGrading`·`ClusteringDashboardMetrics` 는 바뀌지 않는다.
- `ClusteringJudge.agreementOf` 를 지우고 `Agreement` 레코드는 남긴다. javadoc 의 측정 근거 설명은 V146 주석과 이 research 로 옮긴다.

## R10. 측정 도구는 리포트 파일 대신 DB 에 저장한다

- **결정**(명세 FR-011, 사용자 결정 A): `JudgeAgreementRunner.run` 이 판정을 모두 마친 뒤 `JudgeAgreementService.saveRun(...)` 으로 측정 1행 + 쌍별 행을 **한 트랜잭션**에 저장하고, 콘솔에 교차표·일치율·비용 요약을 로그로 남긴다. `JudgeAgreementReport` 는 삭제한다.
- 판정 도중에는 DB 에 쓰지 않는다 — 중간에 멈추면 기록이 남지 않아 다시 잰다(일일 채점과 같은 원칙, 이미 쓴 비용은 버려짐).
- `--eval.judge-agreement` 와 `--eval.report` 를 함께 주면 오류로 끝낸다(조용히 무시하면 파일이 생긴 줄 안다 — #358 의 `--eval.labels` 처리와 같은 이유).
- 측정은 접속한 DB 에 저장된다. 운영 반영은 R11.

## R11. 운영 반영: 측정은 시드, 라벨은 덤프

- **결정**(사용자 결정 C): v1·v2 측정은 V146 시드로 운영에 들어간다. 272쌍 라벨은 사용자가 배포 전에 `pair_label_data_2026_11.sql` 을 DBeaver 로 운영에 넣는다. 이후 새 버전을 재면 로컬 DB 의 측정 행을 고정값 시드 마이그레이션으로 만들어 운영에 넣는다.
- 배포 순서가 어긋나 운영에 라벨이 없으면 R8 대로 그날 채점을 비용 없이 건너뛰고, 7일 안의 다음 실행이 다시 채점한다(라벨을 넣은 다음 날 자동으로 메워짐).

## R12. 테스트 범위(Red 는 핵심만)

- Red(순수): `JudgeAgreementCalculatorTest` 3~4개 — 현재 라벨로 분자·분모 계산 / 라벨이 바뀐 쌍이 다른 분모로 옮겨감 / 측정에 없는 라벨 쌍은 분모 밖에서 따로 셈 / 판사 UNSURE·형식 실패·호출 실패는 분모 밖.
- 통합(`@IntegrationTest`, Green 단계): V146 시드 분포(SAME 134·DIFF 88·UNSURE 3, v1 합계 행)를 확인한다. 서비스 연결은 테스트 트랜잭션 안에 작은 측정(쌍 몇 개)과 그 쌍의 알림·라벨을 직접 넣어 확인한다 — 테스트 DB 에는 알림이 없고 `evaluation_pair_label` 은 알림 외래 키가 있어 272쌍 전체를 넣기 어렵다.
- 실제 v2 값 127/141·74/81 재현(SC-002)과 라벨 변경 반영(SC-003)은 272쌍 라벨이 있는 로컬 사본에서 quickstart 로 확인한다.
- 판사 호출은 어떤 테스트에서도 하지 않는다.
