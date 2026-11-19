# 구현 계획: 불일치 쌍 사람 라벨링

**브랜치**: `feature/clustering-dashboard-labeling` | **날짜**: 2026-11-18 | **명세**: [spec.md](spec.md)

**입력**: `specs/009-clustering-pair-labeling/spec.md`의 기능 명세

## 요약

008 관리자 대시보드의 불일치 쌍 표에서 관리자가 쌍마다 사람 라벨(같은 사건/다른 사건/모르겠음)을 저장하게 한다. 백엔드는 `domain/event` 에 사람 라벨 테이블 하나(새 마이그레이션)와 008 컨트롤러에 엔드포인트 두 개(라벨 저장, 어려운 쌍 판사 성적)를 더하고, 불일치 쌍 응답에 현재 라벨·누가 틀렸는지·"라벨 없는 쌍만" 필터를 더한다. 평가 도구(`EvaluationRunTool`)는 라벨 파일 대신 이 테이블을 기준 시각까지 읽어 채점하는 방식을 더한다 — 라벨의 원본은 DB 한 곳이고 파일 내보내기는 두지 않는다(2026-11-18 사용자 결정). 누가 틀렸는지 판정·판사 버전별 집계·평가용 라벨 묶음 만들기는 순수 클래스에 둔다. 운영 이벤트·클러스터링 규칙·판사 프롬프트는 건드리지 않고, LLM 호출도 없다.

## 기술 컨텍스트

**언어/버전**: Java 17(Spring Boot 3.4), TypeScript(Next.js 16 App Router)

**주요 의존성**: Spring Web·Security(`@PreAuthorize`, `@AuthenticationPrincipal`)·Data JPA, Flyway, TanStack Query(`useMutation`), axios 공용 인스턴스. 새 라이브러리 없음

**저장소**: PostgreSQL — 새 테이블 `clustering_pair_label`(V142). 기존 `clustering_daily_score`·`clustering_daily_score_pair`·`disaster_alert` 는 읽기만

**테스트**: JUnit 5 — 판정·집계·평가용 라벨 묶음 순수 클래스는 단위 테스트(Red-Green), 라벨 저장·권한·집계 API 는 `@IntegrationTest`(Red-Green). 프론트는 Jest + ui-checker. 프론트 TDD 여부는 tasks 의 확인 단계에서 사용자가 정한다

**대상 플랫폼**: 기존 운영(맥북 셀프호스팅 Docker Compose), 관리자 데스크톱 브라우저 위주

**프로젝트 유형**: web-service(backend + frontend)

**성능 목표**: 라벨 저장 1초 안에 표에 반영. 집계·DB 라벨 평가는 라벨 수(수십~수백) 규모라 단순 조회로 충분

**제약사항**: ADMIN 만 쓰기, 쓰기는 라벨 저장 하나뿐, 새 LLM·유료 호출 없음, 272쌍 일치율·운영 이벤트 불변

**규모/범위**: 불일치 쌍은 하루 0~수백(큰 재난 날 수천 가능). 라벨은 사람이 매기는 만큼(하루 수십 이하 예상)

## 설계

### 1. 사람 라벨 저장 (`clustering_pair_label`, V142)

- 두 알림 쌍(`alert_a < alert_b`)마다 한 줄, `(alert_a, alert_b)` UNIQUE. 날짜를 키에 넣지 않아 같은 쌍이 여러 날 나와도 라벨은 하나다(spec FR-002).
- 열: 라벨(SAME/DIFF/UNSURE), 기준 시점(DATE), 처음 매긴 회원·시각, 마지막으로 고친 회원·시각.
- 채점 쌍·알림에 외래 키를 걸지 않는다 — 같은 날을 다시 채점하면 채점 쌍 행이 바뀔 수 있는데 라벨은 남아야 한다(spec 예외 상황). `clustering_daily_score_pair` 의 이벤트 id 와 같은 방침.
- 엔티티 `ClusteringPairLabel` + 메서드 `relabel(label, criteriaDate, memberId, now)`. 저장 service 는 쌍을 찾아 있으면 `relabel`, 없으면 새로 만든다. 첫 저장이 동시에 두 번 들어와 UNIQUE 에 걸리면 다시 찾아 `relabel` 한다(나중 저장이 남음).

### 2. 순수 판정·집계 (`DisagreementGrading`)

- 정답 = 사람 라벨. 판사가 맞음 ⇔ 판사 판정 == 라벨. 시스템이 맞음 ⇔ (시스템이 같은 이벤트에 넣었는가) == (라벨이 SAME).
- 불일치 쌍에서는 둘 중 정확히 하나만 맞는다 → `WrongSide` = JUDGE / SYSTEM. 라벨이 UNSURE 면 판정 없음(null).
- 판사 버전별 집계: 버전마다 판사가 맞은 쌍/라벨 쌍(UNSURE 제외), 시스템이 맞은 쌍/라벨 쌍, UNSURE 수. 비율은 008 의 `ClusteringDashboardMetrics.ratio` 를 그대로 쓴다(분모 0 → 백분율 없음).
- 지금 불일치로 남아 있는 쌍만 센다(spec 예외 상황: 재채점으로 불일치가 아니게 된 쌍의 라벨은 남기되 집계에서 뺀다).
- 평가용 라벨 묶음(`PairLabelSnapshot`, `evaluation` 패키지): 저장된 라벨 행 중 `updated_at ≤ 기준 시각` 인 것만 골라 `(alert_a, alert_b)` 순으로 정렬해 `LabelJson.LabeledPair` 목록을 만든다. 순서가 늘 같아야 두 리포트의 틀린 쌍 표를 비교할 수 있다(006 FR-013).
- 기준 시점 상수 `ClusteringCriteria.VERSION_DATE = 2026-11-13`(경계 사례 마지막 변경일). `CLUSTERING_CRITERIA.md` 를 바꿀 때 이 상수도 바꾼다고 그 문서 머리와 상수 javadoc 에 적는다.

### 3. 백엔드 API (`ClusteringAdminController` 에 추가)

자세한 모양은 [contracts/labeling-api.md](contracts/labeling-api.md).

- `PUT /api/v1/admin/clustering/disagreement-labels` — `{alertA, alertB, label}`. 순서는 서버가 작은 id 를 a 로 맞춘다. 그 쌍이 불일치 쌍으로 기록돼 있지 않으면 404(`CLUSTERING_PAIR_NOT_FOUND`). 회원 id 는 `@AuthenticationPrincipal(expression = "id")`.
- `GET /api/v1/admin/clustering/disagreements` (008) — 쌍마다 `humanLabel`·`criteriaDate`·`wrongSide` 추가, 파라미터 `unlabeledOnly`(기본 false) 추가.
- `GET /api/v1/admin/clustering/hard-pair-grades` — 판사 버전별 어려운 쌍 성적.
- 008 spec FR-011 을 "쓰기는 사람 라벨 저장만 허용"으로 고친다(spec FR-015).

### 4. 평가 도구가 DB 라벨을 읽음 (`EvaluationRunTool`)

- 새 옵션 `--eval.pair-labels-as-of=<ISO 시각>`. `--eval.labels`(파일) 대신 주며 둘을 함께 주면 오류. 주면 평가 사본의 `clustering_pair_label` 을 읽어 `PairLabelSnapshot` 으로 라벨 목록을 만든 뒤, 나머지 채점·리포트는 파일 모드와 똑같이 돈다.
- 리포트 이름 자리(데이터셋 이름)에 "불일치 쌍 사람 라벨(DB, 기준 시각 …, N쌍)"을 쓴다(spec FR-013).
- 기준 시각까지 라벨이 0개면 채점하지 않고 오류로 끝낸다(spec FR-014).
- 덮어쓰기 구조라 기준 시각 뒤에 고친 라벨은 옛 값으로 돌아가지 않고 빠진다(research R11). 리포트의 라벨 수가 이전 리포트와 다르면 그 차이를 감안해 비교한다.
- `backend/evaluation/README.md` 클러스터링 평가 절에 DB 라벨 모드 실행 예시를 더한다.

### 5. 프론트엔드 (`app/admin/clustering/_sections.tsx`)

- 불일치 쌍 표: 쌍마다 같은 사건 / 다른 사건 / 모르겠음 버튼(현재 라벨 강조), "판사가 틀림"/"시스템이 틀림" 표시, "라벨 없는 쌍만" 체크.
- 저장은 `useMutation`, 낙관적 업데이트 없이 성공 후 불일치 쌍·판사 성적 쿼리를 무효화(팀 컨벤션). 실패하면 이유를 보이고 이전 라벨 유지.
- 판사 버전 이력 아래에 "어려운 쌍(불일치 쌍 사람 라벨 기준) 판사 성적" 표. 라벨이 없으면 "아직 라벨 없음".

### 6. 하지 않는 것

- 사람 라벨로 운영 이벤트 합치기·쪼개기, 임계값·규칙 자동 조정, 판사 프롬프트 자동 수정(spec FR-016).
- 판사 판정으로 라벨 미리 채우기(spec FR-018).
- 라벨 변경 이력 테이블, 평가자 간 일치도, 일치 쌍(판사=시스템) 라벨링.
- 새 LLM 호출.

## 프로젝트 구조

### 문서 (이 기능)

```text
specs/009-clustering-pair-labeling/
├── spec.md
├── plan.md              # 이 파일
├── research.md          # 결정 기록
├── data-model.md        # 테이블·판정 규칙·집계
├── quickstart.md        # 검증 시나리오
├── contracts/
│   └── labeling-api.md
├── checklists/requirements.md
└── tasks.md             # /speckit-tasks 에서 생성
```

### 소스 코드

```text
backend/src/main/resources/db/migration/V142__create_clustering_pair_label.sql

backend/src/main/java/com/disaster/alert/alertapi/
├── domain/event/
│   ├── model/ClusteringPairLabel.java                  # 엔티티 + relabel
│   ├── repository/ClusteringPairLabelRepository.java
│   ├── repository/ClusteringDailyScorePairRepository.java  # 라벨 없는 쌍만·라벨된 불일치 쌍 조회 추가
│   ├── evaluation/ClusteringCriteria.java              # 기준 시점 상수(순수)
│   ├── dashboard/DisagreementGrading.java              # 누가 틀렸는지·버전별 집계(순수)
│   ├── evaluation/PairLabelSnapshot.java               # 기준 시각까지 라벨 → 평가용 라벨 목록(순수)
│   ├── evaluation/EvaluationRunTool.java               # --eval.pair-labels-as-of 옵션 추가
│   ├── service/ClusteringPairLabelService.java         # 라벨 저장
│   ├── service/ClusteringDashboardService.java         # 불일치 쌍 응답 확장·성적
│   ├── controller/ClusteringAdminController.java       # 엔드포인트 2개 추가, 불일치 쌍 파라미터 추가
│   └── dto/                                            # 요청 1개, 응답 2개, 불일치 쌍 응답 필드 추가
└── domain/common/exception/ErrorCode.java              # CLUSTERING_PAIR_NOT_FOUND

backend/src/test/java/.../domain/event/
├── dashboard/DisagreementGradingTest.java              # 순수 단위(Red-Green)
├── evaluation/PairLabelSnapshotTest.java               # 순수 단위(Red-Green)
└── controller/ClusteringAdminLabelingTest.java         # @IntegrationTest: 저장·권한·성적

frontend/src/
├── api/clusteringAdminApi.ts                           # 저장·성적 함수, 타입 추가
├── lib/queries/useClusteringAdmin.ts                   # 성적 훅
├── lib/mutations/useSaveDisagreementLabel.ts           # 라벨 저장
└── app/admin/clustering/_sections.tsx                  # 라벨 버튼·필터·성적 표

backend/evaluation/README.md                            # DB 라벨 모드 실행 예시
```

**구조 결정**: 라벨은 채점 결과에 붙는 사람 판단이라 채점 테이블과 같은 `event` 맥락에 둔다. 저장(쓰기) service 는 008 의 조회 service 와 나눠, 조회 service 가 읽기 전용(`@Transactional(readOnly = true)`)으로 남게 한다.

## 구현 순서

| 단계 | 내용 | 승인 지점 |
|---|---|---|
| 1 | `DisagreementGrading` + `ClusteringCriteria` + `PairLabelSnapshot` 순수 판정·집계·라벨 묶음 — Red | **사용자 Red 확인** |
| 2 | 1의 Green | — |
| 3 | 마이그레이션·엔티티 뼈대 + 라벨 저장·권한·불일치 쌍 확장·성적 통합 테스트 — Red | **사용자 Red 확인** |
| 4 | 3의 Green(service·저장소·DTO·ErrorCode) + 평가 도구 DB 라벨 모드·README | 코드 리뷰(100줄 이상) |
| 5 | 프론트 라벨 버튼·필터·성적 | 프론트 TDD 여부 **사용자 확인** |
| 6 | 008 spec FR-011 수정, CLUSTERING_CRITERIA.md 머리에 기준 시점 상수 안내 | — |
| 7 | 운영 배포 후 불일치 쌍이 생기면 라벨링 확인, 평가 사본에서 DB 라벨 평가 확인 | 사용자 확인 |
