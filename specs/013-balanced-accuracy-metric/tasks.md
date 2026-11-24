# 작업 목록: 클러스터링 평가의 대표 지표를 균형 정확도로 바꾼다

**입력**: `specs/013-balanced-accuracy-metric/` 설계 문서

**전제조건**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/report-and-api.md](contracts/report-and-api.md), [quickstart.md](quickstart.md)

**테스트**: 신규 핵심 테스트는 4개다(맞게 나눔 집계, 서로 다른 배수의 구성비 불변성, 일일 집계 복원, 기준선 50%). 기존 리포트 테스트의 요약 기대값도 RED에서 새 계약으로 바꾼다. RED는 컴파일·실행되어 의도한 단언에서 실패해야 한다. **사용자가 RED를 확인하기 전에는 GREEN을 시작하지 않고, GREEN에서는 테스트를 수정하지 않는다.** 별도 경계값 테스트를 늘리지 않는다.

## 경로와 실행 규칙

- 백엔드 코드: `backend/src/main/java/com/disaster/alert/alertapi/domain/event/`
- 백엔드 테스트: `backend/src/test/java/com/disaster/alert/alertapi/domain/event/`
- 아래 백엔드 경로의 `...`는 위 패키지 루트를 뜻한다.
- 프론트엔드: `frontend/src/`
- 실행: 현재 체크아웃의 `backend/`. 평가 사본 명령은 `backend/evaluation/README.md` 공통 준비를 따른다.
- 스키마·엔티티 저장 형식 변경과 과거 데이터 백필은 없다. 태스크는 사용자 스토리별로 묶고 공유 계산을 기반 작업에 둔다.

## Phase 1: 변경 전 기준

- [x] T001 시작점 `d6cfb4ed`와 동일한 백엔드로 `disaster_alert_eval_v6`와 `--eval.labels=evaluation/datasets/cluster-eval-2026-11.json`을 사용해 `backend/evaluation/reports/013-before.md`를 만든다. 무료 평가만 수행하고 라벨·배정을 바꾸지 않는다. 리포트는 커밋하지 않는다.

## Phase 2: 기반 계산 — 사용자 RED 확인 전에는 구현하지 않는다

- [x] T002 `tdd-developer`(단계: RED)로 아래 신규 테스트 4개와 기존 리포트 기대값 변경을 작성하고, 로직 없는 뼈대와 함께 커밋한다. 근거: research R1~R7·R9, data-model 1~3절.
  - `backend/src/test/java/.../evaluation/PairCounterTest.java` 신규 1개: 사람 다른 사건·시스템 다른 이벤트 관측이 `correctlySeparated`로 집계된다.
  - `backend/src/test/java/.../evaluation/PairCountResultTest.java` 신규 2개: ① (8, 2, 1, 9)와 (16, 4, 1, 9)는 같은 사건 쌍만 2배여서 구성비가 다르지만 균형 정확도는 둘 다 85.0%다. ② 전체 100·맞게 묶음 30·과소병합 10·과병합 5·UNSURE 4·형식 실패 1을 `fromDailyCounts(...)`에 넘기면 맞게 나눔은 50이다.
  - `backend/src/test/java/.../evaluation/TrivialBaselinesTest.java` 신규 1개: 같은 평가 집합에서 두 기준선의 균형 정확도가 50.0%다.
  - `backend/src/test/java/.../evaluation/ReportFormatterTest.java`의 **기존 요약 단정들을 RED에서 수정**한다. 기존 `- 점수 ... 조화평균`·맞게 묶은 비율 줄을 새 계약의 균형 정확도 첫 줄·기준선·보조 F1로 바꾼다. 예를 들어 (39, 21, 3, 27)은 균형 정확도 77.5%, 기존 F1 76.5점이다. 기존 세 개수·F1 수치·분모 0 표시·틀린 쌍 표 회귀는 유지하며 테스트를 삭제하거나 단정을 느슨하게 하지 않는다. 단지 새 테스트 하나를 추가하고 옛 문구 단정을 남겨 두지 않는다.
  - 뼈대: `backend/src/main/java/.../evaluation/PairCountResult.java`에 `correctlySeparated`·`fromDailyCounts(...)`·`differentEventPairs()`·`separation()`·`balancedAccuracy()`를 선언하고 틀린 기본값만 반환한다. `backend/src/main/java/.../evaluation/TrivialBaselines.java`도 뼈대로 추가한다. `PairCounter`·`ClusteringDashboardService`·테스트의 생성자 호출부를 새 서명에 맞춰 컴파일되게 하지만 실제 알고리즘은 작성하지 않는다.

**체크포인트(멈춤)**: 작성자가 테스트가 단언에서 실패하는지 확인하고, 사용자에게 `./gradlew test --tests "*PairCounterTest" --tests "*PairCountResultTest" --tests "*TrivialBaselinesTest" --tests "*ReportFormatterTest"`로 RED 확인을 요청한다. 명시적인 확인을 받은 뒤에만 T003·T004를 수행한다.

- [x] T003 `tdd-developer`(단계: GREEN, 사용자 RED 확인 명시)로 `backend/src/main/java/.../evaluation/PairCounter.java`와 `PairCountResult.java`를 구현한다. `fromDailyCounts(...)`는 전체에서 기존 다섯 집계값을 빼 맞게 나눔을 복원하고, 서비스·엔티티에는 이 식을 중복하지 않는다. 비율은 개수에서 바로 계산하며 한쪽 분모가 0이면 균형 정확도는 empty다. 기존 세 개수·F1·purity·completeness·틀린 쌍 목록은 유지한다. 테스트를 수정하지 않는다.

## Phase 3: US1 — 평가 리포트 (P1, MVP)

**목표**: 대표 균형 정확도, 두 비율, 보조 지표와 기준선 두 항목을 리포트만으로 읽는다.

- [x] T004 [US1] T003과 같은 GREEN 호출에서 `backend/src/main/java/.../evaluation/TrivialBaselines.java`·`ReportFormatter.java`·`EvaluationRunTool.java`를 구현해 contracts 1절 형식으로 출력한다. 틀린 쌍 표는 손대지 않는다. 변경이 100줄 이상이면 커밋 전 `code-reviewer`에 diff·변경 줄 범위를 전달한다.
- [x] T005 [US1] T001과 같은 사본·라벨·명령으로 `backend/evaluation/reports/013-after.md`를 만든다. quickstart 2처럼 두 리포트에서 `- 실행 시각: ` 한 줄만 제거한 복사본을 `git diff --no-index`로 비교한다. 요약 밖에는 차이가 없고 틀린 쌍 표는 동일해야 한다. 기존 세 개수·F1은 값으로 대조하며, 새 요약의 대표 점수·두 비율·보조 지표·기준선도 확인한다. 원본·정규화본 네 임시 파일은 확인 후 정리하고 커밋하지 않는다.

## Phase 4: US2 — 운영 점수와 대시보드 (P2)

**목표**: 과거·신규 일일 집계를 같은 순수 규칙으로 읽고 균형 정확도를 대표로 표시한다. 새 열·마이그레이션·백필은 없다.

- [x] T006 [US2] `specs/013-balanced-accuracy-metric/quickstart.md` 3절의 읽기 전용 SQL을 로컬 운영 사본에서 실행해 기존 집계에서 맞게 나눔을 복원한다. 여섯 집계와 복원 값의 음수를 확인하고, 쌍별 기록 수가 전체 판정 수와 같은 날에만 재집계값과 대조한다. 쌍 기록 부재를 계산 불가로 처리하지 않는다. 불일치는 보정하지 말고 보고하며 적용 전에 원인을 확인한다.
- [x] T007 [US2] `backend/src/main/java/.../service/ClusteringDailyScoreService.java`의 `logSummary`를 균형 정확도 대표·두 비율·보조 F1로 바꾼다. `PairCounter`의 결과를 사용하며 기존 `ClusteringDailyScore` 엔티티·`of(...)`·`toDayRow` 저장 형식은 그대로 둔다. 새 일일 기록도 기존 여섯 집계만으로 복원할 수 있어야 한다.
- [x] T008 [US2] `backend/src/main/java/.../service/ClusteringDashboardService.java`의 `toScoreDay`가 기존 여섯 집계를 `PairCountResult.fromDailyCounts(...)`에 넘기게 하고, `backend/src/main/java/.../dto/ClusteringDashboardDailyScoresResponse.java`의 `Day`에 `correctlySeparated`·`separation`·`balancedAccuracy`를 더한다. 반올림은 `ClusteringDashboardMetrics.roundPercent`를 재사용한다. 기존 `NON_NULL` 정책에 따라 계산 불가 점수 키는 생략하되 분모 0의 Ratio 객체·분자·분모와 `percent: null`은 유지한다. 기존 score·purity·completeness와 날짜 상태의 뜻은 바꾸지 않는다.
- [x] T009 [P] [US2] `frontend/src/api/clusteringAdminApi.ts`에 `correctlySeparated?: number`, `separation?: Ratio`, `balancedAccuracy?: number`를 추가하고, `frontend/src/app/admin/clustering/_sections.tsx`의 점수 칸을 균형 정확도 → 빠짐없이 묶은 비율 → 섞지 않은 비율 → 맞게 묶은 비율 → F1로 배치한다. 생략된 점수의 `undefined`와 Ratio 내부의 null을 구분해 계산 불가를 표시하고 0점은 숨기지 않는다. `NO_RECORD`의 기록 없음 처리는 유지한다.
- [x] T010 [US2] quickstart 3·4에 따라 집계 복원값·API·화면을 대조한다. 양쪽 분모가 있는 모든 날은 점수가 있고 한쪽 분모가 없는 날은 점수 키가 없으며, 날짜별 기록 자체가 없는 날은 기록 없음이어야 한다. 쌍별 기록이 없어도 유효한 날짜별 집계가 있으면 계산한다. 검증을 위해 유료 채점이나 DB 쓰기를 수행하지 않는다.

## Phase 5: US3 — 평가 문서 (P3)

- [x] T011 [P] [US3] `specs/006-clustering-evaluation/spec.md`의 FR-011·FR-016과 `quickstart.md`의 "점수 하나와 그 재료 둘"·지표 제한 문장을 2026-11-24 결정에 맞춘다. 기준선 표·이유를 설명하고 006 research의 과거 수치는 기록으로 남긴다.
- [x] T012 [P] [US3] `specs/008-clustering-admin-dashboard/spec.md`·`quickstart.md`의 점수 표시와 `contracts/admin-clustering-api.md`의 새 필드·계산 불가 시 키 생략 규칙을 갱신한다. `backend/evaluation/README.md`의 리포트 읽는 법과 실행 시각 정규화 비교 절차도 맞춘다.

## Phase 6: 마무리

- [x] T013 `specs/013-balanced-accuracy-metric/quickstart.md` 1~5의 결과를 취합해 전체 요구사항을 확인하고 변경이 생긴 검증만 다시 수행한다. 전체 변경은 `code-reviewer`에 한 번 검토받되 T003·T004에서 이미 본 구현은 빼고 추가 변경만 전달한다. 결과를 사용자에게 보고하며 커밋·푸시·PR은 사용자가 요청할 때 한다.

## 의존성 및 실행 순서

- T001은 변경 전에 완료한다.
- T002 → **사용자 RED 확인** → T003·T004 순서다. T004는 T003과 같은 GREEN 호출에서 한다.
- US1의 T005는 T004 뒤다. US2는 T003 뒤 T006 → T007 → T008·T009 → T010 순서이며 US1과 독립이다.
- T008과 T009는 contracts 2·3절을 기준으로 병렬 진행할 수 있다. T011·T012도 서로 다른 문서라 병렬 가능하다.
- 문서는 실제 형식이 정해진 뒤 갱신하고 T013은 모든 스토리 뒤에 수행한다.

## 구현 전략

1. MVP는 US1(T001~T005)이다. 균형 정확도 평가 리포트를 먼저 얻는다.
2. US2는 기존 저장 형식을 유지하고 운영 로그·조회·화면만 맞춘다. 배포는 사용자이며 DB 마이그레이션은 없다.
3. US3은 현재 형식과 계산 불가 규칙을 문서에 반영한다.

## 실행 기록

- T001 완료(2026-11-24): 백엔드 소스가 `d6cfb4ed`와 같은 `d1da6286` 체크아웃에서 기준 리포트를 생성했다. `compileJava`와 평가 실행은 종료 코드 0으로 끝났다.
- 로컬 평가 DB에는 `cluster-eval-2026-11` DB 라벨 셋이 없으므로 같은 272쌍 파일을 사용했다. T005도 동일한 파일을 사용한다.
- 라벨 파일 SHA-256: `40576d31ac90deb7e68dad9a41ebe0dfef6360e6a9819daff9ab726310b297fa`.
- 평가 옵션은 `eval` 프로필, 평가 사본 URL, `spring.datasource.hikari.read-only=true`, `spring.flyway.enabled=false`, `server.port=0`, 위 `eval.labels`와 `eval.report`다. 판사 호출 옵션을 사용하지 않았다.
- 리포트는 로컬 검증 자료이며 커밋하지 않는다. 실제 튜닝 수치는 이 문서에 복사하지 않는다.
- T002 완료(2026-11-24): RED 커밋 `9d1a29c353643adc309d3ffa92614f4460a62db1`. 신규 핵심 테스트 4개와 기존 리포트 기대값을 작성하고 계산 로직 없는 뼈대 및 생성자 호환 변경만 포함했다.
- `compileJava compileTestJava` 성공. 대상 네 클래스의 테스트 14개 중 7개 통과·7개 단언 실패·오류 0·건너뜀 0을 확인했다. 두 묶음 단언의 내부 원인은 AssertionError이며 NPE·NoSuchElementException은 없다.
- 실행 명령(Windows, backend): `.\gradlew.bat test --tests "*PairCounterTest" --tests "*PairCountResultTest" --tests "*TrivialBaselinesTest" --tests "*ReportFormatterTest"`.
- RED 완료 시점에는 사용자 확인을 기다렸다. 이후 사용자 요청으로 RED 커밋을 풀어 동일한 8개 파일을 스테이징했으며, 2026-11-24 사용자가 GREEN 진행을 명시했다. T002에는 기능 선택 상태·명세 문서·기준 리포트를 포함하지 않았다.

### GREEN 검증 (2026-11-24)

- 사용자 명시 승인 후 T003·T004·T007·T008·T009를 구현했다. 테스트 파일과 기존 RED 인덱스 8개는 변경하지 않았다. GREEN 제품·문서 변경은 작업 트리에만 두며 커밋·푸시·PR은 하지 않았다.
- `compileJava compileTestJava` 성공. evaluation·dashboard 관련 순수 테스트 14클래스 48개 통과(실패·오류·건너뜀 0). 프론트 `tsc --noEmit --incremental false`와 API 래퍼 Jest 2개도 통과했다.
- T005: 동일한 평가 사본 `disaster_alert_eval_v6`와 T001의 라벨 파일·해시로 평가 종료 코드 0. 실행 시각 한 줄만 제거한 `git diff --no-index`에서 차이는 요약 절 안에만 있었다. 기존 세 개수·F1·제외 건수와 틀린 쌍 표·다른 머리글은 동일했고 두 기준선의 균형 정확도는 50.0%였다. 실제 시스템 점수는 문서에 복사하지 않았다.
- 평가 실행은 DB 읽기 전용·Flyway 비활성화·eval 프로필이며 판사 일치율 옵션은 사용하지 않았다. 외부 자격증명을 가짜 값으로 대체하고 AI 공통·chat·embedding 주소를 로컬 미사용 포트로 제한했다. 유료 API 호출과 DB 쓰기는 없었다.
- T006: 로컬 운영 사본 `disaster_alert_prod`의 2026-11-17 일일 집계 1행을 읽기 전용 트랜잭션으로 확인했다. 기존 여섯 집계와 복원 값의 음수는 0건, 쌍별 기록이 완전한 1일의 범주별 재집계 불일치는 0건이었다. 기록을 보정하지 않았다.
- T010: 실제 응답 매핑 메서드와 Jackson으로 위 1일 집계 및 SAME만·DIFF만·판정 0·균형 정확도 0·제외만·기록 없음의 입력을 확인했다. 계산 불가 `balancedAccuracy` 키 생략, Ratio의 `percent:null`·분자·분모 유지, 0점 유지, NO_RECORD의 날짜·상태만 직렬화가 모두 맞았다. 쌍별 기록 조회 없이 집계만 전달했다.
- Playwright로 실행 중인 로컬 화면을 확인했다. 실제 매핑 응답과 대표 입력 7행을 사용했고 브라우저 API는 가짜 응답으로 처리했다. 지표 열 순서·0점·계산 불가·기록 없음·분자/분모를 확인했으며 페이지 오류는 0건이었다. 데스크톱·모바일 스크린샷도 직접 확인했다. 기존 개발 서버는 종료하지 않았다.
- T011·T012: 006 지표 조항·quickstart, 008 화면·API 계약·quickstart, 평가 실행 README를 갱신했다. 지난 리포트와 006 research의 과거 수치는 수정하지 않았다.
- T013: code-reviewer가 핵심 평가 5파일과 후속 일일 점수·대시보드 5파일을 나눠 검토했고 두 번 모두 특이사항 없음이었다. 문서 링크·변경 공백·RED 인덱스 보존을 메인 에이전트가 확인했다. 등록된 구현 전후 확장 훅은 없었다.
- 비교용 원본·정규화 리포트 4개는 확인 후 현재 체크아웃에서 정리했다. 일회성 검증 코드·비공개 결과는 무시되는 build 및 .ui-check 폴더에만 남겼다. 운영 배포는 수행하지 않았다.
