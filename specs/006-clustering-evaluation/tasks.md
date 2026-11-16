---

description: "006 이벤트 클러스터링 평가·검증 작업 목록"
---

# 작업 목록: 이벤트 클러스터링 평가·검증

**입력**: `specs/006-clustering-evaluation/`의 설계 문서

**전제조건**: plan.md, spec.md, research.md(10절 R1~R7), quickstart.md. data-model.md·contracts/ 는 따로 없다 — 데이터 모델은 plan.md 5절, 실행 옵션은 quickstart.md에 있다.

**테스트**: 순수 로직(사실 계산기, 재확인 조건, 판사 응답 해석)은 Red-Green으로 진행한다(CLAUDE.md 팀 컨벤션). Red는 **핵심 동작 2~4개만** 쓰고 경계값 테스트는 쓰지 않는다. Red는 컴파일·실행돼서 단언에서 실패해야 하므로 `src/main`에 틀린 기본값을 돌려주는 뼈대를 함께 둔다. Red를 쓰면 **멈추고 사용자 확인을 받은 뒤에만** Green으로 간다. `tdd-developer`를 `단계: RED` → (사용자 확인) → `단계: GREEN`으로 두 번 호출한다.

**구성**: plan.md "구현 순서와 승인 지점"의 순서를 따른다. 스토리 3(튜닝 비교)·4(일일 구조 검사)는 이미 구현돼 작업이 없다.

## 형식: `[ID] [P?] [Story] Description`

- **[P]**: 병렬 실행 가능(다른 파일, 의존성 없음)
- **[Story]**: US1 기준 고정(사실 계산기) · US2 조건에 걸리는 라벨 재확인 · US5 LLM 일일 채점
- ⛔ 표시는 사용자 확인·승인 지점이다. 그 작업은 Claude가 대신 끝내지 않는다.

## 경로 규칙

- 백엔드 소스 루트: `backend/src/main/java/com/disaster/alert/alertapi/` (아래에서 `{main}`)
- 테스트 루트: `backend/src/test/java/com/disaster/alert/alertapi/` (아래에서 `{test}`)

---

## Phase 1: 설정

**목적**: 작업 브랜치

- [x] T001 `develop`에서 `feature/clustering-evaluation` 브랜치를 만든다(2026-11-16, PR #329 머지 커밋 `90a2d1d2` 기준)

---

## Phase 2: 기반 작업

없음. 새 라이브러리·설정 클래스가 필요 없고, 공용 로직(사실 계산기)은 US1 자체다.

---

## Phase 3: 사용자 스토리 1 — 판정 사실 계산기 (우선순위: P1) 🎯 MVP

**목표**: 기준 문서 D·E·I와 시군구 관계를 기준 정의 그대로 계산해, 판정 없이 사실만 내는 순수 로직(plan.md 1절). US2·US5가 같이 쓴다.

**독립 테스트 방법**: 알림 원문·수신 지역·이웃 목록을 넣으면 발신 기관, 기관 종류, 이 문자의 시군구(시군구가 아닌 기관이면 "모름"), 두 문자의 시군구 관계, 간격, 같은 시설 후보가 기준 문서대로 나온다. DB·Spring 없이 JUnit만으로 돈다.

### 사용자 스토리 1 테스트 (Red) ⚠️

- [ ] T002 [US1] `{main}/domain/event/evaluation/AlertPlaceFacts.java`에 사실 계산기 뼈대를 만든다 — 입력 레코드(본문, 발송 시각, 유형, 수신 시군구 코드 목록)와 결과 레코드(발신 기관, 기관 종류 `SIGUNGU`/`NON_SIGUNGU`/`NONE`, 이 문자의 시군구 또는 모름, 시군구 관계 `SAME`/`ADJACENT`/`OTHER`/`UNKNOWN`, 간격, 같은 시설 후보 목록). 시군구 이름→코드와 이웃 관계는 생성자로 받는 조회 함수/맵으로 주입해 DB 없이 돌게 한다. 모든 메서드는 틀린 기본값(빈 값, `UNKNOWN`)을 돌려준다
- [ ] T003 [US1] `{test}/domain/event/evaluation/AlertPlaceFactsTest.java`에 핵심 동작 2~4개만 Red로 쓴다: (1) 본문 끝 `[산청군상하수도과]` → 시군구 기관, 시군구=산청군(D) (2) `[한국철도공사]` 같은 시군구가 아닌 기관 → 시군구 "모름"(I) (3) `[인천서구청]` → 옛 구청 이름 그대로 하나의 시군구(E) (4) 두 본문에 `임천교`가 같이 나오면 같은 시설 후보로 잡힌다(G). 경계값(괄호 없음, 공백 변형 등)은 쓰지 않는다
- [ ] T004 [US1] `./gradlew test --tests "*AlertPlaceFactsTest"`로 단언에서 실패하는 것을 확인하고, 뼈대(T002)와 테스트(T003)를 `commit-message` 스킬로 커밋한다(tdd-developer RED 자동 커밋)
- [ ] T005 ⛔ [US1] **멈춘다.** 사용자에게 테스트 파일 경로와 실행 방법을 알리고 "Red 확인했다"/"Green 진행해"를 기다린다

### 사용자 스토리 1 구현 (Green)

- [ ] T006 [US1] `{main}/domain/event/evaluation/AlertPlaceFacts.java`에 T003을 통과시키는 최소 구현을 쓴다 — 발신 기관 추출(본문 끝 `[...]`), 기관 종류 판정, 시군구 이름 매칭(E는 인천 옛 구청 이름 유지), 시군구 관계(이웃 조회), 간격, 시설 이름 토큰(`…교`·`…천`·`…강`·`…지하차도`·`…터널`·`…저수지`) 교집합. 테스트 파일은 고치지 않는다
- [ ] T007 [US1] `{main}/domain/event/evaluation/`에 `legal_district`·`region_adjacency`를 JdbcTemplate로 읽어 T002의 조회 함수/맵을 만들어 주는 로더를 둔다(평가 도구·일일 채점이 같이 씀). 시도 코드 파생은 CLAUDE.md 법정동 절의 중복 지점과 같은 방식(`substring(0,2)`)을 따른다
- [ ] T008 [US1] 변경이 100줄 이상이면 `code-reviewer`에 `git diff`와 변경 줄 범위를 넣어 검토받고 결과를 사용자에게 보고한다

**체크포인트**: 사실 계산기가 단위 테스트로 동작한다. US2·US5를 시작할 수 있다.

---

## Phase 4: 사용자 스토리 2 — 조건에 걸리는 라벨만 재확인 (우선순위: P1)

**목표**: 기준이 바뀌었을 때 272쌍·5월 B-cubed에서 D·E·G·I 조건에 걸리는 라벨만 뽑아 사람이 다시 본다(plan.md 2절, FR-006~FR-008).

**독립 테스트 방법**: 평가 사본에서 `--eval.recheck=D,E,G,I`를 돌리면 규칙별 후보 목록(알림 id, 발송 시각, 유형, 발신 기관, 본문, 현재 라벨)이 나오고, 조건에 안 걸리는 라벨은 목록에 없다.

### 사용자 스토리 2 테스트 (Red) ⚠️

- [ ] T009 [US2] `{main}/domain/event/evaluation/RecheckCandidateFinder.java`에 뼈대를 만든다 — 입력: 규칙 목록 + 쌍(또는 알림)별 `AlertPlaceFacts` 결과, 출력: 규칙별 걸린 항목. 빈 목록을 돌려준다
- [ ] T010 [US2] `{test}/domain/event/evaluation/RecheckCandidateFinderTest.java`에 핵심 동작 2~3개만 Red로 쓴다: (1) 한쪽 수신 시군구가 2개 이상인 쌍은 D 후보 (2) 한쪽 발신 기관이 시군구가 아니면 I 후보 (3) 조건에 하나도 안 걸리는 쌍은 어느 목록에도 없다
- [ ] T011 [US2] 단언 실패를 확인하고 `commit-message` 스킬로 커밋한다
- [ ] T012 ⛔ [US2] **멈춘다.** 사용자 Red 확인을 기다린다

### 사용자 스토리 2 구현 (Green)

- [ ] T013 [US2] `{main}/domain/event/evaluation/RecheckCandidateFinder.java`에 plan.md 2절 규칙-조건 표(D: 수신 시군구 2개 이상, E: 인천 옛 구청, G: 같은 시설 후보, I: 시군구가 아닌 기관)를 구현한다
- [ ] T014 [US2] `{main}/domain/event/evaluation/EvaluationRunTool.java`에 `--eval.recheck=<규칙 목록>` 모드를 더한다 — 272쌍(`backend/evaluation/datasets/cluster-eval-2026-11.json`)은 쌍 단위, 5월 B-cubed(`bcubed-2026-05.labels.json`)는 알림 단위(그 알림이 든 정답 사건 전체)로 후보를 내고, 알림 id·발송 시각·유형·발신 기관·본문·현재 라벨을 출력한다(쌍 번호만 내지 않음)
- [ ] T015 [US2] `specs/006-clustering-evaluation/quickstart.md` "구현 후 검증 시나리오"의 재확인 명령을 실제 옵션·출력 경로에 맞게 고친다
- [ ] T016 [US2] 평가 사본(운영 아님)에서 `--eval.recheck=D,E,G,I`를 실행해 규칙별 후보 수를 사용자에게 보고한다(2026-11-13 추정: 다지역 89, 인천 18, 시군구가 아닌 기관 약 21)
- [ ] T017 ⛔ [US2] **사용자가 후보를 다시 판정한다.** Claude는 판정 화면·목록 준비만 돕고 라벨을 정하지 않는다
- [ ] T018 [US2] 사용자 판정 결과를 `backend/evaluation/datasets/cluster-eval-2026-11.json`·`bcubed-2026-05.labels.json`에 반영하고, `backend/evaluation/datasets/cluster-eval-2026-11.rubric.md` "라벨 결과" 절에 바뀐 항목·이유·전후 점수(분모 포함)를 적고, 두 셋의 기준 시점을 2026-11-13으로 기록한다(FR-006, FR-008)

**체크포인트**: 라벨 셋이 새 기준 시점으로 맞춰졌다. 판사 일치율을 잴 수 있다.

---

## Phase 5: 사용자 스토리 5 — LLM 일일 채점 (우선순위: P2)

**목표**: 매일 전날 사건 알림의 판정 대상 쌍을 판사(규칙 사실 + LLM)가 판정해 운영 점수와 불일치 쌍을 날짜별로 저장하고, 점수 옆에 판사 일치율을 둔다(plan.md 3·4·5절, FR-016~FR-020).

**독립 테스트 방법**: 판사 LLM을 목킹한 상태에서 하루치 알림을 넣으면 판정 대상 쌍이 FR-016 틀대로 모이고, `PairCounter`로 계산한 숫자와 쌍별 판정이 `clustering_daily_score`·`clustering_daily_score_pair`에 저장된다. 일치율 상수가 없는 판사 버전이면 채점하지 않는다.

### 판사 (plan.md 3절)

- [ ] T019 [US5] `{main}/domain/event/judge/JudgeVerdict.java`(`SAME`/`DIFF`/`UNSURE`/`FORMAT_FAILURE`)와 `{main}/domain/event/judge/ClusteringJudge.java` 뼈대를 만든다 — 응답 해석 메서드는 항상 `UNSURE`를, 버전별 일치율 조회는 빈 값을 돌려준다
- [ ] T020 [US5] `{test}/domain/event/judge/ClusteringJudgeTest.java`에 핵심 동작 2~3개만 Red로 쓴다: (1) 응답이 정확히 `SAME`이면 `SAME` (2) 한 단어가 아닌 응답은 `UNSURE`가 아니라 `FORMAT_FAILURE` (3) 프롬프트에 시스템 설정값·배정(이벤트 id)이 들어가지 않는다
- [ ] T021 [US5] 단언 실패를 확인하고 `commit-message` 스킬로 커밋한다
- [ ] T022 ⛔ [US5] **멈춘다.** 사용자 Red 확인을 기다린다
- [ ] T023 [US5] `{main}/domain/event/judge/ClusteringJudge.java`를 구현한다 — `CLUSTERING_CRITERIA.md` 1절(원칙 1~3, A~G·I, 적용 순서)을 옮긴 프롬프트, 두 문자 정보 + `AlertPlaceFacts` 사실 블록("규칙으로 계산한 값이며 판정이 아니다" 명시, I는 "모름"), Spring AI `ChatModel` + `OpenAiChatOptions`로 `gpt-4o`·온도 0·최대 토큰 5, 정확한 한 단어만 받기, 프롬프트 버전 문자열과 버전별 일치율 상수(분자/분모)
- [ ] T024 [US5] `{main}/domain/event/evaluation/JudgeAgreementRunner.java`와 `EvaluationRunTool`의 `--eval.judge-agreement` 모드를 만든다 — 같은 `ClusteringJudge`로 272쌍(UNSURE 라벨 제외)을 판정해 교차표, 같은 사건/다른 사건 일치율(분자/분모), 형식 실패 수, 토큰 수를 리포트로 낸다
- [ ] T025 ⛔ [US5] **유료 — 실행 전 승인.** 사용자에게 비용(약 $0.32)을 알리고 명시적 "진행해"를 받은 뒤에만 T024 모드를 실행한다. 결과를 보고하고, 사용자가 정한 대로 `ClusteringJudge`의 버전 일치율 상수를 갱신한다

### 일일 채점 (plan.md 4·5절)

- [ ] T026 [US5] `backend/src/main/resources/db/migration/V{작성 시점 다음 번호, 현재 V139}__create_clustering_daily_score.sql`로 `clustering_daily_score`(score_date PK 등 plan.md 5절 컬럼)와 `clustering_daily_score_pair`(유일 제약 `score_date, alert_a, alert_b`, `alert_a < alert_b` 체크)를 만든다
- [ ] T027 [P] [US5] `{main}/domain/event/model/ClusteringDailyScore.java`·`ClusteringDailyScorePair.java` 엔티티와 `{main}/domain/event/repository/ClusteringDailyScoreRepository.java`를 만든다(상태 변경은 엔티티 메서드로만)
- [ ] T028 [US5] `{main}/domain/event/service/ClusteringDailyScoreService.java`를 만든다 — 그날 사건 알림 X마다 앞선 168시간 안의 Y 중 ①같은 이벤트 ②같은 유형·같은/이웃 시군구·다른 이벤트를 모으고, 판사 판정을 라벨로 `PairCounter.count()`에 넣어 점수를 얻고, UNSURE·형식 실패는 분모에서 빼 따로 세고, 쌍별 판정·당시 이벤트 id·불일치 종류(`OVER_MERGED`/`MISSED`)를 저장한다. 판사 버전에 일치율 상수가 없으면 채점하지 않고 warn. 날짜별 요약 한 줄은 info, 채점 실패는 error
- [ ] T029 [US5] `{main}/domain/event/scheduler/ClusteringDailyScoreScheduler.java`를 `ClusteringDailyCheckScheduler`와 같은 모양으로 만든다 — 매일 01:00 KST, `{main}/global/config/AsyncConfig.java`의 별도 실행기, 최근 7일 중 기록 없는 날만 채점하되 채점 시작일(도입일) 설정 이전은 찾지 않는다(R7). 시작일 설정 키를 추가하면 `application.yml`과 루트 `.env.example`을 같이 갱신한다
- [ ] T030 [US5] `{test}/domain/event/service/ClusteringDailyScoreServiceTest.java`에 `@IntegrationTest`로 판사를 목킹한 채 하루치 채점이 두 테이블에 저장되는지 확인한다(DB 필요 — 로컬 docker compose Postgres)
- [ ] T031 [US5] `code-reviewer`에 `git diff`와 변경 줄 범위를 넣어 검토받고 결과를 사용자에게 보고한다(로직·마이그레이션 변경, 100줄 이상)
- [ ] T032 ⛔ [US5] **유료 — 운영 도입 승인(FR-019).** 매일 약 $0.01~0.05임을 알리고 승인을 받은 뒤 PR(develop 대상, `pr-message` 스킬) → 머지 → 배포한다. 푸시와 CodeRabbit 요청은 작업 단위 끝에 한 번만

**체크포인트**: 운영에서 매일 01:00 점수가 쌓이고, 불일치 쌍을 quickstart의 SQL 한 번으로 본다.

---

## Phase 6: 마무리

- [ ] T033 [P] `specs/006-clustering-evaluation/plan.md` 소스 구조 표를 실제 클래스 이름에 맞게 고친다(바뀐 경우)
- [ ] T034 [P] `CLAUDE.md` 이벤트 클러스터링 절에 일일 채점(01:00, 판사·일치율 표시)을 한 줄로 더하고 상세는 `specs/006-clustering-evaluation/`을 가리킨다(기준·수치를 다시 쓰지 않음)
- [ ] T035 배포 다음 날 운영 DB를 읽기 전용으로 조회해 `clustering_daily_score` 첫 줄과 불일치 쌍 SQL(quickstart)을 확인하고 보고한다
- [ ] T036 ⛔ 8월 B-cubed 라벨링 재개 여부는 사용자가 정한다(FR-010). 재개 전에 G 예외의 비이행성(PR #329 리뷰, 후속으로 미룸)을 정한다

---

## 의존성 및 실행 순서

### Phase 의존성

- **US1(Phase 3)**: 바로 시작. US2·US5를 막는다(사실 계산기 공용)
- **US2(Phase 4)**: US1 Green 이후
- **US5 판사(T019~T025)**: US1 Green 이후 코드 작성 가능. 단 **T025 일치율 측정은 T018(라벨 재확인) 이후** — 272쌍이 A~C 기준인 채로 새 기준 판사를 재면 기준이 다른 두 대상을 비교하게 된다
- **US5 일일 채점(T026~T032)**: T023(판사) 이후. T032 배포는 T025(버전 일치율 상수) 이후 — 상수가 없으면 채점이 돌지 않는다
- **마무리**: T035는 T032 이후, T036은 T018 이후

### 각 스토리 내부

- Red(뼈대+테스트) → 커밋 → ⛔ 사용자 확인 → Green
- 엔티티·마이그레이션 → 서비스 → 스케줄러

### 병렬 처리 가능 지점

- US1 Green 이후 US2(T009~)와 US5 판사 코드(T019~T024)는 파일이 겹치지 않아 병렬 가능 — 단 `EvaluationRunTool.java`는 T014·T024가 같이 고치므로 순서대로 한다
- T027(엔티티)은 T026(마이그레이션)과 같이 쓸 수 있다
- T033·T034는 서로 독립

---

## 병렬 처리 예시

```text
# US1 Green 이후, 각각 Red 단계를 따로 진행:
tdd-developer 단계: RED — RecheckCandidateFinder (T009~T011)
tdd-developer 단계: RED — ClusteringJudge 응답 해석 (T019~T021)
```

---

## 구현 전략

### MVP (US1만)

1. T002~T005 Red → 사용자 확인
2. T006~T008 Green → 리뷰
3. 사실 계산기가 단위 테스트로 동작하면 멈추고 확인

### 점진적 전달

1. US1 → 사실 계산기
2. US2 → 후보 목록 → **사람이 재판정** → 라벨 셋 기준 시점 2026-11-13
3. US5 판사 → **승인 후** 일치율 측정 → 버전 상수
4. US5 일일 채점 → **도입 승인** → 배포 → 다음 날 확인
5. 8월 라벨링 재개는 사용자 판단

---

## 참고

- 유료 호출(T025 일치율 측정, T032 운영 도입)은 방향 동의가 아니라 그 작업에 대한 명시적 "진행해"가 있어야 실행한다(FR-019, SC-008)
- 운영 DB는 구현·검증 중 읽기 전용으로만 조회한다. 재확인 후보 추출은 평가 사본에서 한다
- 커밋은 `commit-message` 스킬로. tdd-developer RED 커밋 외에는 사용자가 요청할 때만
- 범위 밖(FR-021: 월간 사람 감사, 제약 규칙, 평가 셋 분할, 층별 지표, recall@k, 판사 통과 기준)은 작업으로 만들지 않는다
