# 구현 계획: 클러스터링 관리자 대시보드

**브랜치**: `feature/clustering-admin-dashboard` | **날짜**: 2026-11-18 | **명세**: [spec.md](spec.md)

**입력**: `specs/008-clustering-admin-dashboard/spec.md`의 기능 명세

## 요약

운영에서 이미 쌓이는 두 검증 기록(00:30 구조 검사 `clustering_daily_check`, 01:00 일일 채점 `clustering_daily_score`·`clustering_daily_score_pair`)을 관리자만 보는 읽기 전용 화면으로 보여 준다. 백엔드는 `domain/event` 에 관리자 조회 API 네 개(일일 채점, 불일치 쌍, 구조 검사, 판사 버전 이력)를 더하고, 프론트엔드는 `/admin/clustering` 페이지 하나에 네 표를 둔다. 비율·분모 0·추정 비용·빈 날짜 채우기는 순수 계산 클래스에 두고, 판사 버전별 모델·일치율·단가는 한 곳(판사 버전 목록)에서 관리해 판사·리포트·대시보드가 같이 쓴다. 새 테이블·마이그레이션·LLM 호출은 없다.

## 기술 컨텍스트

**언어/버전**: Java 17(Spring Boot 3.4), TypeScript(Next.js 16 App Router)

**주요 의존성**: Spring Web·Security(`@PreAuthorize`)·Data JPA, TanStack Query, axios(`api/axios.ts` 공용 인스턴스). 새 라이브러리 없음

**저장소**: PostgreSQL — 기존 테이블만 읽음(`clustering_daily_score`, `clustering_daily_score_pair`, `clustering_daily_check`, `disaster_alert`). 마이그레이션 없음

**테스트**: JUnit 5 — 순수 계산은 단위 테스트(Red-Green), 조회 API·권한은 `@IntegrationTest`. 프론트는 Jest(api 래퍼 단위 테스트) + 필요 시 ui-checker 스크린샷

**대상 플랫폼**: 기존 운영(맥북 셀프호스팅 Docker Compose), 관리자 데스크톱 브라우저 위주

**프로젝트 유형**: web-service(backend + frontend)

**성능 목표**: 첫 화면(최근 30일) 3초 안. 불일치 쌍은 페이지당 20쌍

**제약사항**: ADMIN 권한만, 읽기 전용, 새 LLM·유료 호출 없음, 조회 범위 최대 366일

**규모/범위**: 하루 1행(점수·검사) × 최대 366일, 불일치 쌍은 하루 수십~수백(큰 재난 날 수천 가능)

## 헌법 검사

*게이트: Phase 0 research 이전에 통과해야 함. Phase 1 design 이후 재검사.*

develop 의 헌법(v1.1.0) 원칙 I~V 와, 다른 브랜치에서 개정 중인 v1.2.0 의 VI(도메인 규칙은 순수 클래스)·VII(맥락 경계) 취지를 함께 본다.

| 원칙 | 판정 | 근거 |
|---|---|---|
| I. 가독성과 단순성 | 통과 | 화면 하나·API 네 개·표 네 개. 차트는 쓰지 않음(숫자 표 기본). 새 라이브러리 없음 |
| II. 계층형 아키텍처 | 통과 | controller → service → repository. 응답은 `ApiResponse`, 오류는 `CustomException`+`ErrorCode`, 응답 DTO 는 응답 형태별 1개(목록은 내부 중첩) |
| III. 검증 가능한 변경 | 통과 | 순수 계산 Red → 사용자 확인 → Green. 권한·조회는 통합 테스트 |
| IV. 정직한 문서화 | 통과 | 화면에 "판사 점수는 정답이 아니다" 한 줄과 판사 일치율을 늘 함께 표시(spec FR-004·FR-013). 추정 비용은 "상한"으로 표시 |
| V. 한글 커밋 컨벤션 | 통과 | commit-message 스킬 |
| VI. 도메인 규칙은 순수 클래스(취지) | 통과 | 비율·분모 0·점수·추정 비용·빈 날짜 채우기·버전 기간 계산은 JPA·Spring 을 import 하지 않는 클래스(`ClusteringDashboardMetrics`, `JudgeVersionCatalog`)에. service 는 조율만 |
| VII. 맥락 경계(취지) | 통과 | 알림 원문은 `disasteralert` 맥락의 service 를 거쳐 읽는다(`event` 맥락이 `DisasterAlertRepository` 를 직접 주입하지 않음). 점수·검사 테이블은 `event` 맥락 소유라 직접 읽음 |

위반 없음 → 복잡도 추적 절 생략.

## 설계

### 1. 판사 버전 목록 (한 곳에서 관리)

지금 버전별 일치율은 `ClusteringJudge.agreementOf` 의 switch 에, 모델은 주석에, 단가는 `JudgeAgreementReport` 상수에 흩어져 있다. 대시보드가 "버전·모델·일치율·단가"를 함께 보여 줘야 하므로 순수 클래스 `JudgeVersionCatalog` 하나로 모은다.

- 버전마다: 버전 이름, 모델, 272쌍 일치율(같은 사건·다른 사건 분자/분모), 입력·출력 단가(USD/1M 토큰), 측정일.
- `ClusteringJudge.agreementOf`·`MODEL` 과 `JudgeAgreementReport` 단가는 이 목록을 읽게 바꾼다(동작 동일). 이렇게 하면 버전을 올릴 때 한 곳만 고친다.

### 2. 순수 계산 (`ClusteringDashboardMetrics`)

- 비율: 분자·분모를 그대로 들고, 분모 0 이면 "-"(백분율 없음). 0% 로 바꾸지 않는다(spec FR-003).
- 점수: 기존 `PairCountResult` 의 정의(맞게 묶은 비율·빠짐없이 묶은 비율의 조화평균)를 그대로 쓴다 — 지표 정의를 둘로 만들지 않음(006 FR-011).
- 추정 비용: 그날 판사 버전의 단가 × 토큰. 상한(캐시 할인 미반영).
- 빈 날짜 채우기: 요청 범위의 모든 날을 만들고, 기록이 없는 날은 "기록 없음", 기록은 있으나 판정 0쌍인 날은 "판정 0쌍"으로 구별(spec FR-005).
- 버전 기간: 날짜별 기록의 `judge_version` 에서 버전마다 첫날·마지막 날. 기록이 없는 버전은 "채점 기록 없음".

### 3. 백엔드 API (`domain/event`)

- `ClusteringAdminController` — `@RequestMapping("/api/v1/admin/clustering")`, `@PreAuthorize("hasRole('ADMIN')")`(기존 `AdminController`·`WeatherAdminController` 와 같은 방식). 엔드포인트 네 개는 [contracts/admin-clustering-api.md](contracts/admin-clustering-api.md).
- `ClusteringDashboardService` — 저장소 조회 + 순수 계산 조율. 조회 범위 검증(시작 ≤ 끝, 최대 366일, 위반 시 `ErrorCode`).
- 저장소: 기존 세 저장소에 조회 메서드만 더함(범위 조회, 불일치 쌍 페이지·종류 필터, 버전별 첫날/마지막 날 집계).
- 알림 원문: `DisasterAlertService` 에 "id 목록 → 원문·발송 시각·유형 요약" 조회 메서드를 하나 더해 그 service 를 거친다. 없는 id 는 결과에서 빠지고, 화면은 "원문 없음"으로 보인다.
- 쓰기 엔드포인트는 만들지 않는다.

### 4. 프론트엔드

- 페이지 `app/admin/clustering/page.tsx`(클라이언트 컴포넌트). `authStore` 의 `role !== "ADMIN"` 이면 "관리자만 볼 수 있습니다" 안내만 보이고 API 를 부르지 않는다(백엔드도 403).
- 영역 네 개(위에서 아래로): 판사 점수 설명 한 줄 → 날짜별 일일 채점 표(날짜 범위 선택, 기본 최근 30일) → 불일치 쌍 표(날짜·종류 필터, 페이지) → 구조 검사 표 → 판사 버전 이력 표.
- 계산 가능한 비율은 `a/b` 를 먼저, 백분율은 괄호로(분모 0 은 "-" 하나). "-"·"기록 없음"·"판정 0쌍"을 서로 다른 표시로.
- `api/clusteringAdminApi.ts`(axios 래퍼) + `lib/queries/useClusteringAdmin.ts`(React Query, 튜플 키 예: `["admin", "clustering", "daily-scores", from, to]`). DTO 필드명은 백엔드와 같게.
- 관리자에게만 보이는 진입 링크를 기존 사용자 메뉴에 하나 둔다(위치는 구현 때 기존 메뉴 구조를 보고 정함).

### 5. 하지 않는 것

- 채점 실행·재실행, 라벨 수정, 설정 변경(읽기 전용, spec FR-011).
- 추세 그래프(숫자 표 기본. 필요해지면 따로).
- 새 테이블·마이그레이션, 새 LLM 호출.
- 옛 판정 기록(`clustering_trace`) 기반 화면(2026-11-09 삭제된 설계).

## 프로젝트 구조

### 문서 (이 기능)

```text
specs/008-clustering-admin-dashboard/
├── spec.md
├── plan.md              # 이 파일
├── research.md          # 결정 기록
├── data-model.md        # 화면이 쓰는 데이터와 계산 규칙
├── quickstart.md        # 검증 시나리오
├── contracts/
│   └── admin-clustering-api.md
├── checklists/requirements.md
└── tasks.md             # /speckit-tasks 에서 생성
```

### 소스 코드

```text
backend/src/main/java/com/disaster/alert/alertapi/
├── domain/event/
│   ├── controller/ClusteringAdminController.java      # 관리자 조회 API 4개
│   ├── service/ClusteringDashboardService.java        # 조회·계산 조율
│   ├── dashboard/ClusteringDashboardMetrics.java      # 순수 계산(비율·점수·비용·빈 날짜·버전 기간)
│   ├── judge/JudgeVersionCatalog.java                 # 버전별 모델·일치율·단가(판사·리포트·대시보드 공용)
│   ├── dto/ClusteringDashboard*Response.java          # 응답 형태별 DTO
│   └── repository/ClusteringDaily{Score,ScorePair,Check}Repository.java  # 조회 메서드 추가
├── domain/disasteralert/service/DisasterAlertService.java  # id 목록 → 원문 요약 조회 추가
└── domain/common/exception/ErrorCode.java             # 조회 범위 오류 코드 추가

backend/src/test/java/.../domain/event/
├── dashboard/ClusteringDashboardMetricsTest.java      # 순수 단위(Red-Green)
├── judge/JudgeVersionCatalogTest.java                 # 순수 단위
└── controller/ClusteringAdminControllerTest.java      # @IntegrationTest: 권한·조회

frontend/src/
├── app/admin/clustering/page.tsx
├── api/clusteringAdminApi.ts (+ .test.ts)
└── lib/queries/useClusteringAdmin.ts
```

**구조 결정**: 점수·검사 테이블과 판사가 모두 `domain/event` 소유라 API·service 도 거기 둔다. 알림 원문만 `disasteralert` 맥락 service 를 거친다.

## 구현 순서

| 단계 | 내용 | 승인 지점 |
|---|---|---|
| 1 | `JudgeVersionCatalog` + `ClusteringDashboardMetrics` 순수 계산 — Red | **사용자 Red 확인** |
| 2 | 1의 Green, 판사·리포트가 버전 목록을 읽도록 이전 | — |
| 3 | 조회 API 4개 + 권한 — 통합 테스트 Red | **사용자 Red 확인** |
| 4 | 3의 Green(service·저장소·DTO·알림 원문 조회) | 코드 리뷰(100줄 이상) |
| 5 | 프론트 페이지·api·query — Red→Green(같은 규칙) | **사용자 Red 확인** |
| 6 | 운영 배포 후 관리자 계정으로 화면 확인(읽기만) | 사용자 확인 |
