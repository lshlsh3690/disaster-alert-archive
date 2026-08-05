# 구현 계획: FCM 푸시 알림 발송

**브랜치**: `003-fcm-notification` | **날짜**: 2026-08-05 | **명세**: [spec.md](./spec.md)

**입력**: `specs/003-fcm-notification/spec.md`의 기능 명세

**참고**: 이 문서는 이미 구현·배포된 기능을 근거로 소급 작성되었다. 일반적인
`/speckit-plan` 워크플로(연구 → 설계 → 구현 계획)를 새 기능에 적용하는 대신, 기존 코드를
"as-built" 설계 결정으로 역산해 기록한다. Phase 0/1 산출물(`research.md`, `data-model.md`,
`quickstart.md`, `contracts/`)은 이번 소급 문서화 범위에서 별도로 생성하지 않았다 — 필요
시 이 plan.md와 spec.md를 근거로 후속 작업에서 추가할 수 있다.

## 요약

재난문자가 수집될 때마다(10분 주기 스케줄러 또는 관리자 수동 트리거), 해당 재난문자의
법정동 코드(및 시도 전체 파생 코드)와 일치하는 관심지역을 등록한 회원과, 동일 지역을
등록한 비로그인(게스트) FCM 토큰에게 Firebase Admin SDK로 **data-only** 푸시 메시지를
비동기 발송한다. 회원 경로는 알림 설정(`NONE`/`PUSH`/`ALARM`)과 발송 이력
(`UserNotificationLog`)을 거치는 반면, 게스트 경로는 이 두 가지가 없는 더 단순한 구조다.
(memberId, alertId) 단위 중복 발송 방지는 2026-08-03 `V115` 마이그레이션으로 회원 경로에서도
제거되어, 현재는 두 경로 모두 dedup이 없다. 프론트엔드 포그라운드(`onMessage`, Firebase JS
SDK)는 알림을 직접 `showNotification()`으로 표시하며, 백그라운드는 서비스워커
(`firebase-messaging-sw.js`)가 표준 Push API의 `push` 이벤트를 `event.waitUntil()`로
직접 처리한다 — Firebase JS SDK의 `onBackgroundMessage()`는 SDK 내부 라우팅 이슈로 push
이벤트를 못 받거나 씹는 문제가 있어 SDK를 아예 로드하지 않는 방식으로 재작성되었다. 알림
클릭 시 재난문자 상세 페이지로 라우팅한다. FCM 메시지가 data-only여야 한다는 것과,
서비스워커가 `push` 이벤트 처리를 `waitUntil()`로 감싸 비동기 작업이 끝날 때까지 살아있게
해야 한다는 것은 과거 실제 버그(중복 알림, 알림 미표시)를 낳았던 하드 제약으로 코드
주석에도 명시되어 있다.

## 기술 컨텍스트

**언어/버전**: Java 17 (Spring Boot 3.x, backend) / TypeScript (Next.js App Router, frontend)

**주요 의존성**: Firebase Admin SDK(`com.google.firebase:firebase-admin`, backend 발송),
Firebase JS SDK `firebase/messaging`(frontend 토큰 발급·수신), Spring Data JPA + QueryDSL,
Spring Security(`@PreAuthorize`), `@Async`/`@Scheduled`(Spring), React(`useEffect` 기반 훅),
Zustand(`guestFavoriteRegionsStore`), React Query(회원 관심지역 캐시 무효화)

**저장소**: PostgreSQL — `fcm_token`, `guest_fcm_region`, `notification_preference`,
`user_notification_log`, `member_favorite_region` 테이블 (Flyway
`V8__create_notification_type.sql`, `V9__create_fcm_token.sql`,
`V10__create_user_notification_log.sql`, `V102__alter_fcm_token_nullable_member.sql`,
`V103__create_guest_fcm_region.sql`). 클라이언트 측 캐시: `localStorage["fcm-token"]`.

**테스트**: 이 서브시스템(`AlertNotificationService`, `FcmSendService`, `FcmTokenService`,
`GuestFcmTokenService`, 관련 컨트롤러)에 대한 단위/통합 테스트가 코드베이스에 **존재하지
않는다** — `backend/src/test`에서 FCM/Notification 관련 테스트 클래스를 검색했으나
0건이었다. 이는 헌법 원칙 III(검증 가능한 변경) 대비 실제 격차로, 아래 헌법 검사에
기록한다.

**대상 플랫폼**: 웹(PWA, Chrome/Edge 등 데스크톱·모바일 브라우저) — Web Push API +
Firebase Cloud Messaging. 코드 내 `deviceType` 값으로 `WEB`/`ANDROID`/`IOS`를 구분하지만,
현재 저장소에는 네이티브 Android/iOS 클라이언트 코드는 없고 TWA(Trusted Web Activity,
`document.referrer.includes("android-app://")`로 판별) 환경만 `ANDROID`로 취급한다
(`frontend/src/hooks/useNotificationPermission.ts:112-113`).

**프로젝트 유형**: web-service (Spring Boot backend + Next.js frontend, 단일 저장소 내
`backend/`, `frontend/` 분리)

**성능 목표**: 코드에 명시된 성능 목표나 SLA는 없다. 발송은 `@Async`로 스케줄러 스레드를
블로킹하지 않도록 되어 있고(`AlertNotificationService.java:34`), 멀티캐스트 발송은 Firebase
제약상 최대 500 토큰/요청으로 제한된다(`FcmSendService.java:48` 주석).

**제약사항**: FCM 메시지는 반드시 data-only여야 함(webpush `notification` 필드 금지),
서비스워커는 `push` 이벤트 처리를 `event.waitUntil()`로 감싸야 함 — 둘 다 spec.md
FR-019/FR-023에 근거를 명시한 하드 제약. `fcm_token.token`은 전역 UNIQUE(V114)이므로
UPSERT 시 다른 행과의 토큰 충돌을 먼저 정리해야 함(FR-027).

**규모/범위**: 회원당 관심지역 최대 5개(관리자는 무제한, `MemberFavoriteRegionService.java:21`),
게스트 토큰당 관심지역 최대 5개(`GuestFcmTokenService.java:23`), 회원당 FCM 토큰은
디바이스 타입당 1개로 사실상 제한(UPSERT 키가 memberId+deviceType, 물리 토큰 값 자체는
전역 1행).

## 헌법 검사

*게이트: Phase 0 research 이전에 통과해야 함. Phase 1 design 이후 재검사.*

이 기능은 이미 구현되어 있으므로, 각 원칙에 대해 "설계 단계에서 준수할 것"이 아니라
"실제 코드가 준수하고 있는지"를 검사한다.

- **I. 가독성과 단순성 우선** — **준수**. `AlertNotificationService`, `FcmSendService`,
  `GuestFcmTokenService`, `FcmTokenService`의 각 메서드는 대체로 15~50줄 내외이며, 불필요한
  추상화 계층(전략 패턴, 별도 DSL 등) 없이 직접적으로 구현되어 있다. 새 외부 라이브러리
  없이 Firebase Admin SDK와 Spring 기본 기능만 사용한다.
- **II. 계층형 아키텍처 준수** — **부분 준수, 1건 위반 발견**. controller → service →
  repository 계층은 지켜지고 있고(`FcmTokenController` → `FcmTokenService`/
  `GuestFcmTokenService` → `*Repository`), 모든 API 응답이 `ApiResponse`를 사용하며, 상태
  변경은 엔티티 메서드(`updateToken`, `linkMember`, `markAsRead` 등)를 통해서만 이뤄진다
  (setter 없음). **위반**: `GuestFcmTokenService.registerGuestToken`이 관심지역 5개 초과 시
  팀 컨벤션(`CustomException` + `ErrorCode`)을 어기고 원시 `IllegalArgumentException`을
  던진다(`GuestFcmTokenService.java:37-39`). `GlobalExceptionHandler`에는 이 예외 전용
  핸들러가 없어 `@ExceptionHandler(Exception.class)`(`GlobalExceptionHandler.java:54-60`,
  "처리되지 않은 예외 — Sentry로 전송" 주석) catch-all로 떨어져 정당한 입력 검증 실패임에도
  `500 INTERNAL_SERVER_ERROR`로 응답하고 미처리 예외로 오탐(Sentry 상 버그로 분류)된다.
- **III. 검증 가능한 변경** — **미준수**. FCM 알림 발송 로직(지역 코드 파생, UPSERT 시
  토큰 충돌 정리, 알림 설정 분기 등 비즈니스 로직)에 대한 단위 테스트가 전무하다. 위
  "테스트" 항목 참고.
  기존 `docs/TEST_CASE.md` 기준으로도 낮은 커버리지가 알려진 상태이며, 이 서브시스템도
  예외가 아니다.
- **IV. 정직한 문서화** — 이 문서 자체가 이 원칙을 적용해 작성됐다. 실제 코드를 직접
  확인해 다음과 같은 미구현/고아 요소를 숨기지 않고 spec.md에 기록했다: (1)
  `NotificationPreference.minRiskScore` 미사용 필드, (2) `NotificationLog` 고아 엔티티
  (저장 호출 0건, 실제로는 `UserNotificationLog` 사용), (3) 만료 FCM 토큰 자동 정리 로직
  없음, (4) 회원 토큰 삭제 API의 소유권 검증 없음, (5) 관리자 수동 트리거 엔드포인트의
  인증 누락(spec.md FR-002 참고).
- **V. 한글 커밋 컨벤션** — 관련 최근 커밋(`9273c99 fix(security): 게스트 FCM 토큰
  등록/삭제 엔드포인트 401 오류 수정`)이 `type(scope): 한글 설명` 형식을 정확히 따른다 —
  **준수**.

**결론**: 원칙 II(1건, 예외 처리 방식)와 III(테스트 부재)에서 실질적 편차가 있다. 이 소급
문서화 작업 범위에서는 코드를 수정하지 않으므로, 두 편차는 "가정"이 아니라 향후 개선
과제로 아래 복잡도 추적 표에 근거를 남긴다.

## 프로젝트 구조

### 문서 (이 기능)

```text
specs/003-fcm-notification/
├── plan.md               # 이 파일
└── spec.md               # 기능 명세 (as-built)
```

*(research.md/data-model.md/quickstart.md/contracts/tasks.md는 이번 소급 문서화 범위에
포함하지 않음 — 기능이 이미 구현·운영 중이라 신규 설계 산출물이 필요하지 않았음)*

### 소스 코드 (저장소 루트)

```text
backend/src/main/java/com/disaster/alert/alertapi/
├── domain/notification/
│   ├── controller/
│   │   ├── FcmTokenController.java              # 회원/게스트 토큰 등록·삭제·연결
│   │   ├── NotificationLogController.java       # 회원 알림함 조회/읽음 처리
│   │   └── NotificationPreferenceController.java# 회원 알림 설정 조회/변경
│   ├── service/
│   │   ├── AlertNotificationService.java         # 발송 대상 판정 + 트리거 진입점
│   │   ├── FcmSendService.java                   # Firebase Admin SDK 발송 (data-only)
│   │   ├── FcmTokenService.java                  # 회원 토큰 UPSERT/삭제
│   │   ├── GuestFcmTokenService.java              # 게스트 토큰/지역 등록·연결·삭제
│   │   ├── NotificationLogService.java            # (UserNotificationLog 기반) 알림함
│   │   └── NotificationPreferenceService.java     # 알림 설정 조회/변경
│   ├── model/
│   │   ├── FcmToken.java / GuestFcmRegion.java
│   │   ├── NotificationPreference.java / NotificationType.java
│   │   ├── UserNotificationLog.java               # 실제 사용되는 발송 이력
│   │   └── NotificationLog.java                   # 고아 엔티티 (미사용)
│   ├── dto/  FcmTokenDtos.java / NotificationLogDtos.java / NotificationPreferenceDtos.java
│   └── repository/ (JpaRepository 6종)
├── domain/member/
│   ├── model/MemberFavoriteRegion*.java           # 알림 대상 판정의 기준 테이블
│   ├── repository/MemberFavoriteRegionRepository.java
│   └── service/MemberFavoriteRegionService.java
├── domain/disasteralert/model/DisasterAlert*.java # 알림 트리거의 소스(법정동 코드 목록)
├── scheduler/DisasterFetchScheduler.java          # 10분 주기 수집→번역→알림트리거→클러스터링
└── global/
    ├── config/SecurityConfig.java                 # 게스트 엔드포인트 permitAll 규칙
    └── controller/AdminController.java            # 수동 발송 트리거(permitAll)

frontend/
├── public/firebase-messaging-sw.js                # 백그라운드 수신 + 표시 + 클릭 라우팅
└── src/
    ├── lib/firebase.ts                            # Firebase JS SDK 초기화, getToken/onMessage
    ├── hooks/
    │   ├── useNotificationPermission.ts            # 권한 요청, 토큰 발급/등록, localStorage 캐시
    │   ├── useForegroundMessage.ts                 # 포그라운드 onMessage 표시
    │   └── useGuestFavoriteSync.ts                 # 로그인 시 게스트→회원 이관
    ├── api/guestFcmApi.ts                          # 게스트 토큰 등록/연결/삭제 fetch 래퍼
    └── store/guestFavoriteRegionsStore.ts           # 게스트 관심지역 로컬 상태(Zustand)
```

**구조 결정**: 표준 web-service 구조(`backend/` + `frontend/`)를 그대로 따른다. 백엔드는
`domain/notification`을 중심으로 controller → service → repository 3계층이며, 알림 대상
판정에 필요한 `domain/member`(관심지역)와 `domain/disasteralert`(알림 원본)를 참조한다.
프론트엔드는 별도 "알림" 라우트/페이지 없이 전역 훅(`hooks/`)과 서비스워커로 구현되어
있어 `app/` 트리와 독립적이다. Option 1(단일 프로젝트)/Option 3(모바일)은 해당하지
않으므로 제거했다.

## 복잡도 추적

> 헌법 검사에서 위반 사항이 있어 정당화가 필요한 경우에만 작성 — 여기서는 "정당화"가
> 아니라 소급 문서화 과정에서 발견된 실제 편차를 기록한다 (수정은 이 작업 범위 밖).

| 위반 사항 | 실제 동작 | 비고 |
|-----------|------------|-------------------------------------|
| 원칙 II: 임의 예외 사용 | `GuestFcmTokenService.registerGuestToken`이 관심지역 5개 초과 시 `CustomException`+`ErrorCode`가 아닌 원시 `IllegalArgumentException`을 던져, `GlobalExceptionHandler`의 catch-all(`Exception.class`)로 떨어지고 500 + Sentry 오탐으로 처리됨 (`GuestFcmTokenService.java:37-39`, `GlobalExceptionHandler.java:54-60`) | 이 spec 범위에서 수정하지 않음. 별도 fix 작업 후보로 남김 |
| 원칙 III: 테스트 부재 | `AlertNotificationService`/`FcmSendService`/`FcmTokenService`/`GuestFcmTokenService`에 단위 테스트 없음 | 지역 코드 파생(FR-003), UPSERT 토큰 충돌 정리(FR-027), 알림 설정 분기(FR-006/007) 등 순수 로직부터 우선 테스트 추가가 유효한 후속 과제 |
