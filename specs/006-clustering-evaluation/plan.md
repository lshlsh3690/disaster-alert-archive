# 구현 계획: 이벤트 클러스터링 평가·검증

**브랜치**: `006-clustering-evaluation` (미생성 — 구현을 시작할 때 `develop`에서 `feature/` 접두사로 분기) | **날짜**: 2026-11-13 | **명세**: [spec.md](spec.md)

**입력**: `specs/006-clustering-evaluation/spec.md`의 기능 명세

## 요약

명세의 스토리 5개 중 3(튜닝 비교)·4(일일 구조 검사)는 이미 동작한다(as-built, 아래 "이미 있는 것"). 새로 만들 것은 셋이다.

1. **스토리 1 — 기준 고정**: 문서 작업은 2026-11-13 끝났다(`CLUSTERING_CRITERIA.md`에 G·H 통합, I 추가, 적용 순서 절). 남은 것은 코드가 아니라 **판정 사실 계산기**다 — 기준 중 판단이 필요 없는 부분(어느 시군구 문자인가, 두 문자의 시군구 관계, 간격)을 기준 문서 정의대로 계산하는 순수 로직. 이 계산기를 스토리 2와 5가 같이 쓴다.
2. **스토리 2 — 조건에 걸리는 라벨만 재확인**: 평가 도구(`EvaluationRunTool`)에 재확인 후보를 뽑는 모드를 더한다. 272쌍과 5월 B-cubed에서 D·E·G·I 조건에 걸리는 쌍·알림을 사실 계산기로 찾아 목록으로 내고, 다시 판정하는 것은 사람이 한다.
3. **스토리 5 — LLM 일일 채점**: 백엔드 안에 스케줄러를 하나 더 둔다. 전날 사건 알림이 낀 판정 대상 쌍마다 판사(규칙으로 계산한 사실 + LLM 판단, `gpt-4o`, 온도 0)가 같은/다른 사건을 판정하고, 기존 `PairCounter`로 운영 점수를 계산해 날짜별로 저장한다. 판사 일치율은 같은 판사 코드를 272쌍에 돌리는 평가 도구 모드로 잰다(유료, 승인 후).

과거 전수 채점은 하지 않는다(FR-020). FR-021 범위 밖 항목은 다루지 않는다.

## 기술 컨텍스트

**언어/버전**: Java 17, Spring Boot 3.x (기존 백엔드). 새 Python 스크립트는 만들지 않는다 — research.md 10절 R1.

**주요 의존성**: Spring AI(`ChatModel` + `OpenAiChatOptions`로 호출별 모델 지정, `OpenAiTranslationClient`와 같은 방식), JdbcTemplate, 기존 평가 패키지(`domain/event/evaluation`의 `PairCounter`·`LabelJson`·`BCubedLabelJson`·`AssignmentReader`). 새 라이브러리 없음.

**저장소**: PostgreSQL. 새 테이블 2개(`clustering_daily_score`, `clustering_daily_score_pair`), 새 Flyway 마이그레이션 1개(작성 시점의 다음 번호, 현재 최신 V138). 읽는 표: `disaster_alert`, `disaster_alert_region`, `legal_district`, `region_adjacency`.

**테스트**: JUnit 5. 사실 계산기·재확인 조건·판사 응답 해석은 순수 단위 테스트(Red-Green, CLAUDE.md 규칙대로 Red 후 사용자 확인). 판사 LLM은 테스트에서 목킹. DB 조회·저장은 `@IntegrationTest`. 실제 OpenAI 호출 테스트는 만들지 않는다 — 일치율 측정 자체가 그 역할이다.

**대상 플랫폼**: 운영 맥북 셀프호스팅의 백엔드 컨테이너(기존과 동일).

**프로젝트 유형**: web-service(백엔드만. 프론트엔드 변경 없음).

**성능 목표**: 일일 채점은 하루 판정 쌍 수십 개 규모라 성능 목표를 두지 않는다. 수집 루프를 막지 않도록 별도 실행기(`@Async`)에서 돈다.

**제약사항**:
- 유료 호출: 일일 채점은 최초 도입 승인으로 갈음한다(FR-019). 일치율 재측정은 실행마다 승인을 받는다(사람 UNSURE 를 뺀 227쌍 약 $0.86, 2026-11-17 실측).
- 판사에게 **시스템 설정값(시군구 키, 72시간, 임계값)과 시스템 배정(어느 이벤트에 넣었는지)을 넘기지 않는다** — 넘기면 판사가 시스템을 닮아 점수가 저절로 오른다.
- 판사 프롬프트 버전마다 일치율이 있어야 채점한다(FR-018). 측정되지 않은 버전이면 채점하지 않고 warn을 남긴다.
- 로그 레벨: 불일치 쌍은 정상 흐름이므로 info 이하. 채점 자체가 실패했을 때만 error.
- 운영 DB는 이 계획의 구현·검증 중에 읽기 전용으로만 조회한다. 재확인 후보 추출은 평가 사본에서 한다.

**규모/범위**: 하루 사건 알림 약 7~12건, 판정 쌍 약 10~30개(과거 1.2만 건 → 2.2만 쌍 비율로 추정), 하루 비용 약 $0.01~0.05로 잡았으나 **2026-11-17 평가 사본 실측은 큰 사건 날 훨씬 크다**: 쌍당 약 $0.0038(입력 약 1,500토큰), 2025-03-25(경북 산불) 12,122쌍 약 $46 · 2025-07-17(호우) 3,436쌍 약 $13, 조용한 날은 17쌍 약 $0.06(같은 이벤트 쌍이 알림 수의 제곱으로 늘어난다). **상한은 두지 않는다**(2026-11-17 사용자 결정) — 나중에 모델별 성능·비용을 비교해 다시 정한다. 272쌍 셋, 5월 B-cubed 250건.

## 헌법 검사

*게이트: Phase 0 research 이전에 통과해야 함. Phase 1 design 이후 재검사.*

| 원칙 | 판정 | 근거 |
|---|---|---|
| I. 가독성과 단순성 | 통과 | 새 라이브러리 없음. 점수 계산은 기존 `PairCounter` 재사용, 일일 스케줄러는 `ClusteringDailyCheckScheduler`와 같은 모양. 관리자 화면·API 엔드포인트를 만들지 않는다(불일치 쌍은 SQL 한 번으로 본다) |
| II. 계층형 아키텍처 | 통과 | scheduler → service → repository/JdbcTemplate. 컨트롤러 없음(API를 열지 않음) |
| III. 검증 가능한 변경 | 통과 | 순수 로직은 단위 테스트, LLM은 목킹, DB 경계는 `@IntegrationTest` |
| IV. 정직한 문서화 | 통과 | 판사 일치율을 점수 옆에 항상 저장·표시. 사실 계산기가 못 정하는 경우(시군구가 아닌 기관의 장소)는 "모름"으로 넘기고 LLM이 판단한다고 명시 |
| V. 한글 커밋 | 통과 | 기존 스킬 사용 |
| 기술 스택 | 통과 | 스키마 변경은 새 Flyway 파일로만. AI는 Spring AI 경유 |

**Phase 1 이후 재검사**: 위 판정 그대로. 복잡도 추적 대상 없음.

## 이미 있는 것 (as-built, 이 계획에서 바꾸지 않음)

- **스토리 3 — 튜닝 비교**: `EvaluationRunTool`(`--eval.labels`, `--eval.report`, `--eval.bcubed`), `PairCounter`, `BCubedScorer`, `ReportFormatter`. 실행·읽는 법은 [quickstart.md](quickstart.md).
- **스토리 4 — 일일 구조 검사**: `ClusteringDailyCheckScheduler`(00:30 KST, 최근 7일 재검사, `clusteringCheckExecutor`) → `ClusteringDailyCheckService` → `clustering_daily_check`(V135).

## 설계

### 1. 판정 사실 계산기 (스토리 1 → 2·5 공용)

기준 문서의 D·E·I와 시군구 관계를 그대로 옮긴 순수 로직이다. **판정(같은/다른 사건)을 내지 않고 사실만 낸다.**

| 사실 | 계산 방법 | 기준 |
|---|---|---|
| 발신 기관 | 본문 끝 `[...]` 안의 문자열. 없으면 "없음" | D·I |
| 발신 기관 종류 | 시군구 기관(`…시청`·`…군청`·`…구청`, `…시`·`…군`·`…구`, 그 아래 부서·대책본부 포함 — 예: `산청군상하수도과`, `안동시 재난안전대책본부`) / 시군구가 아닌 기관(철도·경찰청·도청·관리본부 등) | D·I |
| 이 문자의 시군구 | 시군구 기관이면 기관 이름 → `legal_district` 시군구. 인천 옛 구청(`인천서구청` 등)은 옛 구청 이름 그대로 하나의 시군구로 본다(E). 시군구가 아닌 기관이면 **"모름(문자 속 장소로 판단)"** — 장소를 본문에서 뽑는 것은 규칙으로 안정적이지 않아 LLM에 맡긴다(I) | D·E·I |
| 수신 지역 수 | `disaster_alert_region`의 시군구 개수(2개 이상이면 다지역 발송) | D |
| 두 문자의 시군구 관계 | 둘 다 시군구가 정해졌으면 같음 / `region_adjacency` 이웃 / 그 밖. 하나라도 모름이면 "모름" | A |
| 간격 | 두 문자 발송 시각 차이 | A·G의 "같은 비 동안" 판단 재료(판단은 LLM) |
| 이름이 같은 시설 후보 | 두 본문에 같이 나오는 시설 이름 토큰(`…교`, `…천`, `…강`, `…지하차도`, `…터널`, `…저수지`). 후보일 뿐이고 같은 시설인지는 LLM이 판단 | G |

"같은 비 동안", "같은 사고인가", "새 발생인가(C)"는 사실이 아니라 판단이라 계산하지 않는다.

### 2. 재확인 후보 추출 (스토리 2)

`EvaluationRunTool`에 `--eval.recheck=<규칙 목록, 예: D,E,G,I>` 모드를 더한다. 평가 사본에서 돌린다.

| 규칙 | 걸리는 조건(사실 계산기 기준) |
|---|---|
| D | 쌍의 한쪽이라도 수신 시군구 2개 이상 |
| E | 한쪽이라도 발신 기관이 인천 옛 구청 |
| G | 이름이 같은 시설 후보가 있음 |
| I | 한쪽이라도 발신 기관이 시군구가 아님 |

- 272쌍은 쌍 단위로, 5월 B-cubed는 알림 단위로(그 알림이 든 정답 사건 전체를 다시 봄) 후보를 낸다.
- 출력: 규칙별 후보 목록(알림 id, 발송 시각, 유형, 발신 기관, 본문, 현재 라벨). 쌍 번호만 내지 않는다.
- 2026-11-13 대략 추정(272쌍 JSON 기준): 다지역 발송 쪽 89, 인천 18, 시군구가 아닌 기관 약 21(겹침 포함). 실제 수는 구현 후 도구로 센다.
- 다시 판정한 결과는 기존 방식대로 라벨 파일과 루브릭 "라벨 결과" 절에 남기고(FR-008), 라벨 셋의 기준 시점을 2026-11-13으로 적는다(FR-006).
- 이 규칙-조건 표는 기준을 바꿀 때마다 같이 고친다(FR-007). 표의 위치는 이 plan과 quickstart.

### 3. 판사 (스토리 5와 일치율 측정 공용)

- **입력**: 두 문자 각각의 발송 시각·유형·수신 지역·본문 + 1절의 사실(발신 기관과 종류, 이 문자의 시군구, 두 문자의 시군구 관계, 간격, 같은 시설 후보). 시스템 설정·배정은 넣지 않는다.
- **프롬프트**: `CLUSTERING_CRITERIA.md` 1절(원칙 1~3, 경계 사례 A~G·I, 적용 순서)을 옮긴다. few-shot 예시 없음. 사실 블록은 "규칙으로 계산한 값이며 판정이 아니다"라고 밝힌다. 기준 문서의 "이 순서로도 답이 갈리지 않으면 기준의 결함(라벨링을 멈춘다)"은 판사에게 "기준끼리 부딪칠 때만 UNSURE"로 옮긴다 — 본문 단서가 적다는 이유로 UNSURE 를 고르게 하면 그만큼 채점 대상이 소리 없이 줄어든다.
- **모델**: `gpt-4o`, 온도 0, 최대 토큰 5. 응답이 정확히 `SAME`/`DIFF`/`UNSURE` 한 단어일 때만 받고, 그 밖은 "형식 실패"로 따로 센다(UNSURE로 바꾸지 않음).
- **버전**: 프롬프트 본문이 바뀌면 버전 문자열을 올린다. 버전마다 272쌍 일치율(같은 사건 쌍·다른 사건 쌍 각각 분자/분모)을 코드 상수로 둔다. 상수가 없는 버전이면 일일 채점을 하지 않는다.
- **일치율 측정**: `EvaluationRunTool`에 `--eval.judge-agreement` 모드를 더한다. 운영 채점과 **같은 판사 코드**로 272쌍(UNSURE 제외)을 판정해 교차표·일치율·형식 실패 수·토큰 비용을 리포트로 낸다. 유료라 실행마다 사용자 승인을 받는다. 결과를 보고 사람이 버전 상수를 갱신한다.

### 4. 일일 채점 (스토리 5)

- **스케줄러**: 매일 01:00 KST(일일 구조 검사 00:30 뒤), `@Async`로 별도 실행기. 최근 7일 중 **기록이 없는 날만** 채점한다(같은 날을 다시 채점해 돈을 두 번 쓰지 않음, 빠진 날은 메움). 단 **채점 시작일(도입일)을 설정으로 두고 그 날짜 이전은 찾지 않는다** — 첫 실행에는 기록이 하나도 없어 하한이 없으면 도입 전 7일까지 유료 호출하게 되고, 이는 FR-020(도입 시점부터만 채점)과 어긋난다.
- **판정 대상 쌍(FR-016)**: 그날 발송된 사건 알림 X마다, X보다 앞선 7일(168시간) 안의 사건 알림 Y(①②에 모두 적용, 같은 이벤트 쌍도 168시간을 넘으면 대상이 아니다) 중 ① 시스템이 X와 같은 이벤트에 넣은 것 ② 같은 유형이고 시군구가 같거나 이웃인데 다른 이벤트에 있는 것. 평가 셋을 뽑은 틀(같은 유형, 168시간, 같은 시군구 또는 이웃)과 같게 맞춰, 272쌍으로 잰 일치율을 운영 쌍에 그대로 붙일 수 있게 한다.
- **점수**: 판사 판정을 라벨로 보고 기존 `PairCounter.count()`에 넣어 맞게 묶은 비율·빠짐없이 묶은 비율·점수, 과병합·과소병합 쌍을 얻는다. UNSURE·형식 실패는 분모에서 빼고 수를 따로 저장한다.
- **저장**: 아래 데이터 모델. 판정한 쌍은 모두 저장하고, 불일치 쌍은 시스템 이벤트 id와 함께 남겨 SQL 한 번으로 두 알림 원문과 시스템 이벤트를 볼 수 있게 한다(SC-007, 화면을 만들지 않음).
- **로그**: 날짜별 요약 한 줄(info) — "판정 N쌍, 맞게 묶은 비율 a/b, 빠짐없이 묶은 비율 c/d, 점수 s, 판사 일치율(버전 v) 같은 사건 x/y·다른 사건 z/w". 채점 실패는 error.

### 5. 데이터 모델

**`clustering_daily_score`** — 날짜당 한 줄

| 컬럼 | 뜻 |
|---|---|
| `score_date` (PK, date) | 채점한 날(그날 발송된 사건 알림 기준) |
| `incident_count` | 그날 사건 알림 수 |
| `pair_count` | 판정한 쌍 수 |
| `correctly_merged`, `over_merged`, `missed` | `PairCountResult` 그대로 |
| `unsure_count`, `format_failure_count` | 분모에서 뺀 쌍 |
| `judge_version` | 판사 프롬프트 버전 |
| `agreement_same_num`, `agreement_same_den`, `agreement_diff_num`, `agreement_diff_den` | 그 버전의 272쌍 일치율(당시 값을 복사해 둠) |
| `prompt_tokens`, `completion_tokens` | 비용 확인용 |
| `scored_at` | 채점 시각 |

비율·점수는 저장하지 않고 위 숫자에서 계산한다(분모를 잃지 않기 위해).

**`clustering_daily_score_pair`** — 판정한 쌍마다 한 줄

| 컬럼 | 뜻 |
|---|---|
| `id` (PK) | |
| `score_date` (FK) | |
| `alert_a`, `alert_b` | 알림 id(`alert_a < alert_b`) |
| `judge_verdict` | `SAME`/`DIFF`/`UNSURE`/`FORMAT_FAILURE` |
| `system_same_event` | 시스템이 같은 이벤트에 넣었는가 |
| `event_a`, `event_b` | 채점 시점의 시스템 이벤트 id(이후 재클러스터링돼도 당시 상태를 남김) |
| `disagreement` | `OVER_MERGED`/`MISSED`/null |

유일 제약: (`score_date`, `alert_a`, `alert_b`).

## 프로젝트 구조

### 문서 (이 기능)

```text
specs/006-clustering-evaluation/
├── spec.md              # 요구사항
├── plan.md              # 이 파일 (데이터 모델·인터페이스 포함 — 별도 data-model.md·contracts/ 를 만들지 않음)
├── research.md          # 결정·측정 기록. 이 plan 의 결정은 10절
├── quickstart.md        # 실행·읽는 법. 구현 후 검증 시나리오는 마지막 절
├── checklists/requirements.md
└── tasks.md             # /speckit-tasks 에서 생성
```

인터페이스는 외부 API가 없고 평가 도구의 실행 옵션 두 개(`--eval.recheck`, `--eval.judge-agreement`)뿐이라 contracts 대신 quickstart "실행" 절에 적는다.

### 소스 코드

```text
backend/src/main/java/com/disaster/alert/alertapi/domain/event/
├── evaluation/
│   ├── AlertPlaceFacts            # 1절 사실 계산기 (순수)
│   ├── PlaceDirectory, PlaceDirectoryBuilder, PlaceDirectoryLoader  # 사실 계산기가 쓰는 기관·시군구·이웃 표
│   ├── RecheckCandidateFinder     # 2절 규칙-조건 매칭 (순수)
│   ├── RecheckRunner              # 2절 --eval.recheck 모드
│   ├── JudgeInputAssembler        # 판사 입력 조립 (일치율 측정·일일 채점 공용)
│   ├── JudgeAgreementCounter, JudgeAgreementResult  # 3절 일치율 집계 (순수)
│   ├── JudgeAgreementRunner, JudgeAgreementReport   # 3절 --eval.judge-agreement 모드·리포트
│   └── EvaluationRunTool          # 모드 2개 추가
├── judge/
│   ├── ClusteringJudge            # 3절 프롬프트 조립·호출·응답 해석, 버전·일치율 상수
│   └── JudgeVerdict
├── model/ClusteringDailyScore, ClusteringDailyScorePair
├── repository/ClusteringDailyScoreRepository, ClusteringDailyScorePairRepository
├── service/ClusteringDailyScoreService     # 4절 대상 쌍 수집·판정·PairCounter·저장
└── scheduler/ClusteringDailyScoreScheduler # 01:00 KST, CLUSTERING_DAILY_SCORE_START_DATE 가 비면 꺼짐

backend/src/main/resources/db/migration/V139__create_clustering_daily_score.sql
backend/src/test/java/.../domain/event/{evaluation,judge,service}/  # 단위·통합 테스트
```

2026-11-17 구현에 맞춰 고쳤다. 클래스 이름이 바뀌면 이 표를 고친다.

**구조 결정**: 기존 백엔드 `domain/event` 아래에 둔다. 평가 도구와 일일 채점이 사실 계산기와 판사를 같이 써야 해서 한 모듈에 있어야 한다.

## 구현 순서와 승인 지점

| 순서 | 일 | 승인 |
|---|---|---|
| 1 | 사실 계산기(Red → 사용자 확인 → Green) | — |
| 2 | 재확인 후보 추출 모드 → 평가 사본에서 실행 → 후보 목록 | — |
| 3 | **사람이 후보를 다시 판정**, 라벨·루브릭 갱신, 기준 시점 기록 | 사용자 작업 |
| 4 | 판사(프롬프트를 새 기준으로) + 일치율 측정 모드 | — |
| 5 | 고친 272쌍으로 일치율 측정(약 $0.86, 2026-11-17 실측) → 버전 상수 기록 | **유료 — 실행 전 승인** |
| 6 | 일일 채점 테이블·서비스·스케줄러 | — |
| 7 | 운영 배포(조용한 날 약 $0.06, 큰 사건 날 수십 달러 — 기술 컨텍스트 "규모/범위" 참고, 상한 없음) | **유료 — 도입 승인(FR-019)** |
| 8 | 8월 B-cubed 라벨링 재개(FR-010) | 사용자 판단 |

3이 5보다 먼저인 이유: 272쌍 라벨이 A~C 기준인 채로 새 기준 판사의 일치율을 재면 기준이 다른 두 대상을 비교하는 셈이다.

## 복잡도 추적

헌법 검사 위반 없음.
