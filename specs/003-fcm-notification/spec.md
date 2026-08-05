# 기능 명세: FCM 푸시 알림 발송

**기능 브랜치**: `003-fcm-notification`

**생성일**: 2026-08-05

**상태**: 완료 (기존 구현 기준 사후 문서화 — speckit 도입 이전에 이미 구현·운영 중인 기능을 as-built로 기술함)

**입력**: 사용자 설명: "재난문자 수집 시 관심지역을 등록한 회원/비로그인 사용자에게 FCM으로 푸시 알림을 발송하는 서브시스템을, 이미 구현된 코드를 근거로 소급 문서화한다."

<!--
본 문서는 새 기능을 요청하는 명세가 아니라, 이미 배포되어 동작 중인 코드를 근거로 작성한
소급(as-built) 명세다. 모든 사용자 스토리·인수 시나리오·기능 요구사항은 실제 코드 동작을
`파일경로:줄번호` 형식으로 인용하여 뒷받침한다. 향후 speckit으로 이 기능을 변경할 때
기준선(baseline)으로 사용한다.
-->

## 사용자 시나리오 및 테스트 *(필수)*

### 사용자 스토리 1 - 관심지역을 등록한 회원은 재난문자 발생 시 푸시 알림을 받는다 (우선순위: P1)

로그인한 회원이 특정 지역을 관심지역으로 등록해두면, 그 지역(또는 그 지역이 속한 시도
전체)에 재난문자가 발생했을 때 웹/PWA 푸시 알림을 받는다. 이것이 이 서브시스템의 핵심
가치이며, 나머지 모든 기능(게스트 지원, 알림 설정, 발송 이력)은 이를 보조한다.

**우선순위 이유**: 회원 기반 알림 발송이 파이프라인의 기본 경로이며, 다른 모든 시나리오가
여기서 파생된다.

**독립 테스트 방법**: 회원으로 로그인해 관심지역을 1개 이상 등록한 뒤, 해당 지역 코드로
재난문자가 수집되도록 하면(또는 관리자 수동 트리거로) 등록된 FCM 토큰으로 데이터 전용
푸시 메시지가 도착하고, 알림 클릭 시 해당 재난문자 상세 페이지로 이동하는 것으로 검증 가능.

**인수 시나리오**:

1. **Given** 회원이 시군구 단위 지역(예: 법정동 코드 10자리 `2900300000`)을 관심지역으로
   등록했고 알림 설정이 `PUSH`(기본값), **When** 그 지역 코드를 포함한 재난문자가 수집되어
   `DisasterFetchScheduler`가 10분 주기로 `alertNotificationService.triggerNotification(alertId)`를
   호출하면 (`backend/.../scheduler/DisasterFetchScheduler.java:29,45`), **Then** 해당 회원의
   등록된 FCM 토큰으로 `title`/`body`/`notificationType`/`alertId`를 담은 data-only 메시지가
   발송되고 `UserNotificationLog`에 `SENT`로 기록된다
   (`backend/.../notification/service/AlertNotificationService.java:67-79,116-156`).
2. **Given** 회원이 시도 전체("전체")를 관심지역으로 등록해 시도 코드가 0으로 패딩된 형태로
   저장되어 있고(`2900000000`), **When** 그 시도에 속한 특정 구(`2900300000`)의 재난문자가
   수집되면, **Then** 시스템은 알림 대상 재난문자의 시군구 코드로부터 시도 코드(앞 2자리)를
   추출해 `00000000`으로 패딩한 파생 코드를 함께 검색 조건에 포함하므로
   (`AlertNotificationService.java:58-65`) 이 회원도 알림을 받는다.
3. **Given** 동일 회원이 이미 동일 `alertId`에 대해 알림을 받은 이력이 있으면(`UserNotificationLog`
   존재), **When** `triggerNotification`이 같은 alertId로 다시 호출되면(예: 관리자 수동
   재트리거), **Then** `existsByMemberIdAndAlertId` 체크에 의해 중복 발송을 하지 않고 조용히
   반환한다 (`AlertNotificationService.java:118-121`).
4. **Given** 회원의 알림 설정이 `NONE`이면, **When** 관심지역 알림 대상에 포함되더라도,
   **Then** FCM 발송을 하지 않는다 (`AlertNotificationService.java:124-130`).
5. **Given** 회원이 알림 설정을 한 번도 저장한 적이 없으면(`NotificationPreference` 레코드
   없음), **When** 알림 대상이 되면, **Then** 기본값 `PUSH`로 취급해 발송한다
   (`AlertNotificationService.java:124-127`, `NotificationPreference.java:32`).
6. **Given** 회원이 등록한 FCM 토큰이 1개면, **When** 발송하면, **Then** 단건 발송 API
   (`FirebaseMessaging.send`)를, 2개 이상이면 멀티캐스트 발송 API(`sendEachForMulticast`)를
   사용한다 (`AlertNotificationService.java:142-146`, `FcmSendService.java:19-46,49-72`).

---

### 사용자 스토리 2 - 비로그인(게스트) 사용자도 관심지역 알림을 받는다 (우선순위: P2)

로그인하지 않은 방문자도 브라우저 알림 권한만 허용하면 관심지역을 등록하고 재난문자
푸시를 받을 수 있다. 로그인을 강제하지 않고도 핵심 가치(위험 지역 알림)를 제공하기 위한
경로다.

**우선순위 이유**: 서비스 진입 장벽을 낮추는 핵심 차별점이지만, 로그인 사용자 알림 경로
없이는 독립적으로 존재할 이유가 없으므로 P2로 둔다.

**독립 테스트 방법**: 로그아웃 상태에서 브라우저 알림 권한을 허용하고 관심지역을 선택하면
프론트가 자동으로 게스트 FCM 토큰과 지역 코드를 서버에 등록하고, 해당 지역 재난문자
발생 시 해당 브라우저에 알림이 표시되는 것으로 검증 가능.

**인수 시나리오**:

1. **Given** 비로그인 사용자가 브라우저에서 FCM 토큰을 발급받고 관심지역(최대 5개)을
   선택하면, **When** 프론트엔드가 `POST /api/v1/fcm-token/guest`를 호출하면
   (`frontend/src/api/guestFcmApi.ts:4-15`, `frontend/src/hooks/useNotificationPermission.ts:46-53,111-129`),
   **Then** 인증 없이(permitAll) 요청이 처리되어 `fcm_token` 테이블에 `member=null`로
   토큰이 저장되고 `guest_fcm_region`에 지역 코드가 저장된다
   (`SecurityConfig.java:58-62`, `FcmTokenController.java:44-50`,
   `GuestFcmTokenService.java:33-67`).
2. **Given** 게스트가 이미 등록한 관심지역이 있는 상태에서, **When** 관심지역을 다시
   등록(변경)하면, **Then** 기존 `guest_fcm_region` 레코드를 모두 삭제한 뒤 새 목록으로
   전량 교체한다(부분 추가가 아님) (`GuestFcmTokenService.java:57-66`).
3. **Given** 게스트가 등록하려는 FCM 토큰이 이미 다른 회원 계정에 연결되어 있으면,
   **When** 게스트 등록을 시도하면, **Then** 해당 토큰에 대해서는 게스트 등록을 건너뛴다
   (`GuestFcmTokenService.java:44-46`).
4. **Given** 게스트 관심지역 코드 목록이 발생한 재난문자의 (파생 시도코드 포함) 지역 코드
   집합과 겹치면, **When** `triggerNotification`이 실행되면, **Then** 해당 게스트 토큰들로
   data-only FCM이 발송된다 — 이 경로는 회원 경로와 달리 알림 설정(`NotificationType`)
   확인이나 `UserNotificationLog` 중복 방지 로직을 거치지 않는다
   (`AlertNotificationService.java:82,89-114`).
5. **Given** 게스트가 알림 수신을 중단하려고 하면, **When** `DELETE /api/v1/fcm-token/guest`를
   호출하면(인증 불필요), **Then** 해당 토큰의 게스트 지역 레코드가 모두 삭제되고, 그
   토큰이 아직 회원과 연결되지 않은 경우에 한해 `fcm_token` 레코드 자체도 삭제된다
   (`SecurityConfig.java:61-62`, `GuestFcmTokenService.java:85-92`).

---

### 사용자 스토리 3 - 게스트가 로그인하면 게스트 알림 상태가 회원 계정으로 이관된다 (우선순위: P3)

비로그인 상태에서 알림을 설정해두고 이후 로그인한 사용자는, 별도 재설정 없이 같은
브라우저의 FCM 토큰이 그대로 회원 알림 경로로 전환된다.

**우선순위 이유**: 게스트 경로(스토리 2)가 존재해야 의미가 있는 보조 시나리오이며, 실패해도
게스트 알림이 그대로 유지되므로 치명적이지 않다.

**독립 테스트 방법**: 게스트로 관심지역·FCM 토큰을 등록한 뒤 로그인하면, 로그인 직후
`POST /api/v1/fcm-token/guest/link` 호출로 토큰이 회원과 연결되고, 이후 알림은 게스트
경로가 아닌 회원 경로(관심지역 기준)로만 발송되는 것으로 검증 가능.

**인수 시나리오**:

1. **Given** 로그인 직후이고 `localStorage`에 게스트 FCM 토큰이 남아 있으면, **When**
   `useGuestFavoriteSync` 훅이 1회 실행되면, **Then** 관심지역을 서버 관심지역 목록과
   비교해 서버에 없는 지역만 회원 관심지역으로 추가하고, `linkGuestFcmToken`을 호출한다
   (`frontend/src/hooks/useGuestFavoriteSync.ts:21-54`).
2. **Given** `POST /api/v1/fcm-token/guest/link` 요청이 인증된 상태로 도착하면, **When**
   해당 토큰이 아직 회원에 연결되지 않은 `fcm_token` 레코드라면, **Then** 그 토큰을 요청한
   회원 ID와 연결하고, 해당 토큰의 `guest_fcm_region` 레코드를 모두 삭제한다
   (`FcmTokenController.java:53-61`, `GuestFcmTokenService.java:72-80`) — 이후 이 토큰은
   `sendToMember` 경로에서 `FcmTokenRepository.findAllByMemberId`로 조회되어 발송 대상이 된다.
3. **Given** 연결하려는 토큰이 이미 다른 회원에 연결되어 있으면, **When** 링크를 시도하면,
   **Then** 아무 동작도 하지 않는다(무시) (`GuestFcmTokenService.java:73-74`).

---

### 사용자 스토리 4 - 알림 클릭 시 해당 재난문자 상세로 이동한다 (우선순위: P3)

수신한 푸시 알림을 클릭하면 관련 재난문자 상세 페이지로 이동하며, 이미 열려 있는 탭이
있으면 새 창을 띄우지 않고 그 탭을 재사용한다.

**우선순위 이유**: 알림 발송 자체(P1/P2)가 선행되어야 의미가 있는 UX 보조 기능이다.

**독립 테스트 방법**: 알림을 수신한 뒤 클릭하여 `/alerts/{alertId}`로 이동하는지, 이미 그
사이트 탭이 열려 있을 때 새 탭이 아니라 기존 탭이 포커스되는지로 검증 가능.

**인수 시나리오**:

1. **Given** 서비스워커가 background 상태에서 FCM data 메시지를 수신하면, **When**
   `onBackgroundMessage` 핸들러가 실행되면, **Then** `alertId`가 있으면
   `/alerts/{alertId}`, 없으면 `/`를 `data.url`로 담아 `showNotification()`을 **반드시
   await하여** 표시한다 (`frontend/public/firebase-messaging-sw.js:17-45`) — await하지
   않으면 서비스워커가 알림 표시 전에 종료되어 알림이 아예 뜨지 않을 수 있다.
2. **Given** 사용자가 알림을 클릭하면, **When** `notificationclick` 이벤트가 발생하면,
   **Then** 이미 열려 있는 탭이 있으면 `client.navigate()`로 대상 URL로 이동 후 포커스,
   실패 시 `client.focus()`로 폴백, 열린 탭이 없으면 `clients.openWindow()`로 새 창을 연다
   (`firebase-messaging-sw.js:47-78`).
3. **Given** 앱이 포그라운드(활성 탭)에 있는 상태에서 FCM 메시지를 수신하면, **When**
   `onMessage` 콜백이 실행되면, **Then** 브라우저 알림 권한이 `granted`일 때만 서비스워커의
   `showNotification()`(가능하면) 또는 `Notification` 생성자로 동일하게 알림을 표시한다
   (`frontend/src/hooks/useForegroundMessage.ts:12-41`).

---

### 예외 상황

- 재난문자에 유효한 법정동 코드가 하나도 없으면(`regionCodes.isEmpty()`), 알림 대상 조회
  자체를 하지 않고 즉시 종료한다 (`AlertNotificationService.java:51`).
- `triggerNotification` 전체가 예외를 던지면(예: 알림 조회 실패) 로그만 남기고 스케줄러의
  나머지 파이프라인(클러스터링 등)에 영향을 주지 않는다 (`AlertNotificationService.java:36-37,84-86`,
  호출부는 `@Async`이므로 실패가 `DisasterFetchScheduler`를 멈추지 않는다).
- 개별 회원 발송 중 예외가 발생해도 다른 회원 발송은 계속 진행된다 — 회원 단위로
  `try/catch`가 걸려 있다 (`AlertNotificationService.java:116-161`).
- 게스트 발송 전체가 예외를 던지면(예: 토큰 조회 실패) 로그만 남기고 트리거 자체는
  실패로 처리되지 않는다 (`AlertNotificationService.java:89,111-113`).
- FCM 발송이 `UNREGISTERED`/`INVALID_ARGUMENT` 에러로 실패(만료/무효 토큰)해도 해당
  토큰을 DB에서 자동으로 삭제하는 정리 로직은 없다 — 로그만 남긴다
  (`FcmSendService.java:35-45`). 만료된 토큰은 사용자가 재로그인/재방문해 토큰을 갱신
  등록(UPSERT)하기 전까지 계속 발송 대상에 남는다.
- 게스트가 관심지역을 6개 이상 등록하려 하면 `IllegalArgumentException`을 던져 등록을
  거부한다 (`GuestFcmTokenService.java:37-39`, `MAX_GUEST_REGIONS = 5`).
- `notificationType`이 `NONE`이면 서비스워커/포그라운드 핸들러 모두 알림을 표시하지 않고
  조기 반환한다 (`firebase-messaging-sw.js:20`, `useForegroundMessage.ts:14`) — 다만 서버가
  이 값으로 `NONE`을 보내는 경로는 게스트 발송(`NotificationType.PUSH.name()` 고정,
  `AlertNotificationService.java:106,109`)에는 없고, 회원 발송도 이미 `NONE`이면 서버 단에서
  발송 자체를 하지 않으므로(`AlertNotificationService.java:130`) 클라이언트의 `NONE` 분기는
  현재 코드 경로상 도달하지 않는 방어 코드로 보인다.

## 요구사항 *(필수)*

### 기능 요구사항

- **FR-001**: 시스템은 재난문자 공공데이터를 10분 주기로 수집한 직후, 새로 저장된 각
  재난문자마다 알림 발송을 비동기로 트리거해야 한다(MUST)
  (`backend/.../scheduler/DisasterFetchScheduler.java:29,41-45`).
- **FR-002**: 시스템은 관리자용 수동 트리거 엔드포인트(`POST /api/v1/admin/trigger-notification/{alertId}`,
  `POST /api/v1/admin/trigger-fetch`)로 임의의 alertId에 대해 알림 발송을 재실행할 수 있게
  지원해야 한다(MUST) (`global/controller/AdminController.java:24-35`). 이 엔드포인트들은
  `/api/v1/admin/**`이 `permitAll`로 설정되어 있어 **인증 없이** 누구나 호출할 수 있다
  (`global/config/SecurityConfig.java:57`) — 별도 인증/인가가 없다는 점을 실제 동작으로
  기록한다.
- **FR-003**: 시스템은 알림 대상 지역 코드 집합을 계산할 때, 재난문자의 시군구 단위(10자리)
  법정동 코드 각각에 대해 앞 2자리(시도)를 취하고 나머지를 `0`으로 채운 시도 전체 코드를
  파생시켜 원본 코드 목록에 합쳐야 한다(MUST) — 시도 전체를 관심지역으로 등록한 사용자를
  포함하기 위함 (`AlertNotificationService.java:56-65`).
- **FR-004**: 시스템은 파생 코드를 포함한 지역 코드 집합과 일치하는 `MemberFavoriteRegion`을
  가진 모든 회원 ID를 조회해야 한다(MUST) (`AlertNotificationService.java:67-72`,
  `MemberFavoriteRegionRepository.findByIdLegalDistrictCodeIn`).
- **FR-005**: 시스템은 회원별로 동일한 `alertId`에 대해 두 번 이상 발송하지 않아야 한다(MUST)
  — `UserNotificationLog`에 (memberId, alertId) 존재 여부로 판정한다
  (`AlertNotificationService.java:118-121`, `UserNotificationLogRepository.existsByMemberIdAndAlertId`).
- **FR-006**: 시스템은 회원의 `NotificationPreference.notificationType`이 `NONE`이면 해당
  회원에게 발송하지 않아야 한다(MUST) (`AlertNotificationService.java:124-130`).
- **FR-007**: 시스템은 회원의 알림 설정 레코드가 없으면 기본값 `PUSH`로 간주해 발송해야
  한다(MUST) (`AlertNotificationService.java:124-127`; 엔티티 기본값도 `PUSH`,
  `NotificationPreference.java:32`).
- **FR-008**: 시스템은 회원에게 등록된 FCM 토큰이 없으면 발송을 건너뛰어야 한다(MUST)
  (`AlertNotificationService.java:132-139`).
- **FR-009**: 시스템은 토큰 수에 따라 단건(`FirebaseMessaging.send`) 또는 멀티캐스트
  (`sendEachForMulticast`, 최대 500개) API를 선택해 사용해야 한다(MUST)
  (`AlertNotificationService.java:142-146`, `FcmSendService.java:19-72`).
- **FR-010**: 시스템은 회원 발송 결과(성공/실패)와 알림 타입을 `UserNotificationLog`에
  `SENT`/`FAILED` 상태로 기록해야 한다(MUST) (`AlertNotificationService.java:148-156`).
- **FR-011**: 시스템은 알림 발송 파이프라인(트리거 전체, 회원 단위, 게스트 단위)에서 발생하는
  예외를 각 단계별로 잡아 로깅만 하고 상위 스케줄러의 나머지 처리(번역, 클러스터링 등)를
  중단시키지 않아야 한다(MUST) (`AlertNotificationService.java:36-37,84-86,111-113,116,158-160`).
- **FR-012**: 시스템은 비로그인 사용자가 인증 없이 FCM 토큰과 최대 5개의 관심지역 코드를
  등록할 수 있게 해야 한다(MUST) (`GuestFcmTokenService.java:23,33-39`,
  `SecurityConfig.java:61`).
- **FR-013**: 시스템은 게스트 토큰 등록 시, 등록하려는 토큰이 이미 회원에 연결되어 있으면
  게스트 등록(재사용)을 건너뛰어야 한다(MUST) (`GuestFcmTokenService.java:42-46`).
- **FR-014**: 시스템은 게스트 관심지역 등록을 부분 추가가 아닌 전체 교체(기존 삭제 후
  재삽입) 방식으로 처리해야 한다(MUST) (`GuestFcmTokenService.java:57-66`).
- **FR-015**: 시스템은 로그인 시 게스트 FCM 토큰을 인증된 회원과 연결하고, 연결된 토큰의
  게스트 지역 레코드를 삭제해야 한다(MUST) (`FcmTokenController.java:53-61`,
  `GuestFcmTokenService.java:72-80`).
- **FR-016**: 시스템은 (파생 시도코드를 포함한) 지역 코드 집합과 일치하는 `guest_fcm_region`
  레코드의 토큰들에게 FCM을 발송해야 한다(MUST) — 이 경로는 회원 경로와 달리 알림 타입
  설정이나 중복 발송 방지를 거치지 않고 항상 `NotificationType.PUSH`로 발송한다
  (`AlertNotificationService.java:82,89-114`).
- **FR-017**: 시스템은 게스트 FCM 토큰 등록(`POST /api/v1/fcm-token/guest`)과 삭제
  (`DELETE /api/v1/fcm-token/guest`) 엔드포인트를 인증 없이 접근 가능하게 해야 한다(MUST)
  (`SecurityConfig.java:58-62`; 컨트롤러 메서드에도 `@PreAuthorize`가 없음,
  `FcmTokenController.java:44-50,64-70`). *(2026-07-31 커밋 `9273c99`으로 보안 설정 누락이
  수정되기 전에는 이 두 엔드포인트가 `anyRequest().authenticated()`에 걸려 항상 401을
  반환하던 실제 결함이 있었다.)*
- **FR-018**: 시스템은 게스트-회원 연결 엔드포인트(`POST /api/v1/fcm-token/guest/link`)는
  반드시 인증을 요구해야 한다(MUST) (`FcmTokenController.java:53-61`, `@PreAuthorize("isAuthenticated()")`).
- **FR-019**: 시스템은 모든 FCM 발송 메시지를 data-only로만 구성해야 하며, 최상위(webpush)
  `notification` 필드를 설정해서는 안 된다(MUST NOT `.setNotification()`을 `Message`/
  `MulticastMessage` 빌더에 호출) (`FcmSendService.java:13-16,22-29,54-61`). **근거**:
  webpush `notification` 페이로드가 있으면 Firebase JS SDK가 서비스워커에서 알림을 자동으로
  1회 표시하고, 커스텀 `onBackgroundMessage` 핸들러가 동일 메시지를 다시 표시해 알림이
  중복(두 번째는 제목·본문이 비어 있는 빈 알림)으로 뜨는 실제 발생했던 버그이며, 코드 주석에
  재발 방지 목적으로 명시되어 있다 (`FcmSendService.java:13-16`).
- **FR-020**: 시스템은 예외적으로 `AndroidConfig.setNotification()`(채널ID, 사운드, 진동,
  우선순위)은 사용해도 된다(MAY) — 이는 최상위 webpush notification 페이로드와는 별개로
  Android 네이티브 FCM SDK가 자체적으로 알림을 표시할 때 쓰는 채널 설정이라 FR-019가 막는
  "중복 표시" 문제와 무관하다 (`FcmSendService.java:74-97`).
- **FR-021**: 시스템은 `notificationType`이 `ALARM`이면 Android 채널 `disaster_alarm`,
  `MAX` 우선순위, 진동 패턴(`[0,200,100,200]`)을 사용하고, 그 외에는 `disaster_push` 채널을
  사용해야 한다(MUST) (`FcmSendService.java:75-96`).
- **FR-022**: 시스템은 FCM 발송 실패 시 `UNREGISTERED`/`INVALID_ARGUMENT` 에러코드를 만료/
  무효 토큰으로 판별해 로그를 남겨야 한다(MUST). 다만 해당 토큰을 DB에서 자동 삭제하는
  로직은 구현되어 있지 않다(로그만 남기고 반환) (`FcmSendService.java:35-45`).
- **FR-023**: 프론트엔드 서비스워커(`firebase-messaging-sw.js`)의 `onBackgroundMessage`
  핸들러는 `self.registration.showNotification()` 호출을 반드시 `await`해야 한다(MUST)
  (`firebase-messaging-sw.js:17,44`). **근거**: await하지 않으면 서비스워커가 알림 표시
  완료 전에 종료(terminate)될 수 있어 알림이 아예 표시되지 않는 문제가 발생할 수 있다.
- **FR-024**: 시스템은 `notificationType`이 `NONE`인 메시지를 수신하면 서비스워커/포그라운드
  핸들러 모두 알림을 표시하지 않아야 한다(MUST) (`firebase-messaging-sw.js:18-20`,
  `useForegroundMessage.ts:13-14`).
- **FR-025**: 시스템은 알림 클릭 시, 이미 열려 있는 앱 탭이 있으면 그 탭을 대상 URL로
  이동시켜 포커스하고, 없으면 새 창을 열어야 한다(MUST) (`firebase-messaging-sw.js:47-78`).
- **FR-026**: 시스템은 앱이 포그라운드(활성 탭)에 있을 때도 FCM `onMessage` 콜백으로 알림을
  표시해야 한다(MUST), 단 브라우저 알림 권한이 `granted`가 아니면 표시하지 않는다(MUST)
  (`useForegroundMessage.ts:12,20-40`).
- **FR-027**: 회원 FCM 토큰 등록은 (memberId, deviceType) 단위로 UPSERT되어야 한다(MUST) —
  동일 디바이스 타입으로 재등록 시 새 토큰 값으로 갱신한다 (`FcmTokenService.java:22-40`,
  `FcmTokenRepository.findByMemberIdAndDeviceType`).
- **FR-028**: 회원 FCM 토큰 삭제(`DELETE /api/v1/fcm-token`)는 토큰 값만으로 삭제되며,
  요청자가 해당 토큰의 소유 회원인지 별도로 검증하지 않는다 — 실제 구현된 동작을 그대로
  기록한다 (`FcmTokenController.java:34-41`, `FcmTokenService.java:43-45`,
  `FcmTokenRepository.deleteByToken`).

### 주요 엔티티 *(데이터가 관련된 경우 포함)*

- **FcmToken** (`fcm_token`): 회원 또는 게스트(‘member’ 컬럼 null 허용)의 디바이스 FCM
  토큰. `deviceType`(WEB/ANDROID/IOS)별로 회원당 최대 1개 UPSERT (`FcmToken.java`).
- **GuestFcmRegion** (`guest_fcm_region`): 비로그인 FCM 토큰과 법정동 코드의 다대다 매핑
  (토큰당 최대 5개 행). `MemberFavoriteRegion`의 게스트용 대응물이지만 회원 테이블과
  독립적인 별도 엔티티다 (`GuestFcmRegion.java`).
- **MemberFavoriteRegion**: 회원-법정동 코드 매핑(회원당 최대 5개, 관리자는 무제한).
  알림 대상 회원 조회의 기준 테이블 (`member/model/MemberFavoriteRegion.java`,
  `member/service/MemberFavoriteRegionService.java:21`).
- **NotificationPreference** (`notification_preference`): 회원별 알림 방식(`NONE`/`PUSH`/
  `ALARM`)과 `minRiskScore`(기본 0)를 저장하지만, `minRiskScore`는 발송 로직·수정 API
  어디에서도 참조되지 않는 미사용 필드다 (`NotificationPreference.java`,
  `NotificationPreferenceDtos.java:11`, `NotificationPreferenceService.java`).
- **UserNotificationLog** (`user_notification_log`): 회원 단위 발송 이력(SENT/FAILED, 읽음
  여부). 중복 발송 방지 판정과 회원용 알림함 조회(`NotificationLogController`)의 근거
  테이블이다 (`UserNotificationLog.java`).
- **NotificationLog** (`notification_log`): 동일한 목적으로 보이는 별도 엔티티가 존재하지만,
  코드베이스 전체에서 저장(`save`) 호출이 한 곳도 없는 고아(orphan) 엔티티다 — 실제 알림
  이력은 전부 `UserNotificationLog`를 사용한다 (`notification/model/NotificationLog.java`,
  `notification/repository/NotificationLogRepository.java`). 후속 정리가 필요한 대상으로
  기록한다.

## 성공 기준 *(필수)*

<!--
  본 시스템에는 SLA, 발송 성공률, 지연시간 등을 실측하는 모니터링/지표 수집 로직이
  코드베이스 내에 존재하지 않는다(로그(`log.info`/`log.error`) 이외의 별도 계측 없음).
  따라서 아래 항목은 "측정된 지표"가 아니라 코드 구조상 성립하는 규약을 서술한다.
-->

### 측정 가능한 결과

- **SC-001**: 현재 구현에는 발송 성공률, p95 지연시간, 알림 도달률 등을 계측하는 코드가
  없다 — Firebase Admin SDK 호출 결과를 `log.info`/`log.error`로만 남기며
  (`FcmSendService.java:32,36,64-65,69`), 별도 메트릭/대시보드로 집계되지 않는다. 실측된
  성공 기준을 제시할 수 없으므로 지표를 지어내지 않고 이 사실을 그대로 기록한다.
- **SC-002**: 알림 트리거는 재난문자 수집 스케줄러(10분 주기)에 종속되어 실행되며
  (`DisasterFetchScheduler.java:29`), 발송 자체는 `@Async`로 비동기 실행되어 스케줄러의
  다음 작업(클러스터링 등)을 블로킹하지 않는다(`AlertNotificationService.java:34-35`) — 이는
  실측 지연시간이 아니라 코드 구조상의 설계 사실이다.
- **SC-003**: 회원 경로는 (memberId, alertId) 단위로 중복 발송이 발생하지 않음을 코드
  로직으로 보장한다(`AlertNotificationService.java:118-121`) — 단, 게스트 경로에는 동일한
  중복 방지 로직이 없다(FR-016).

## 가정

- FCM 메시지는 항상 data-only여야 한다는 것이 이 서브시스템 전체의 하드 제약이다(FR-019).
  이 제약을 깨는 변경(최상위 `notification` 필드 재도입)은 과거 실제로 발생했던 중복 알림
  버그를 재발시킨다.
- 서비스워커의 `showNotification()` 호출은 반드시 `await`되어야 한다는 것도 하드 제약이다
  (FR-023). 이를 어기면 알림이 아예 표시되지 않을 수 있다.
- 회원 알림 대상 판정은 `MemberFavoriteRegion`(관심지역)만을 기준으로 하며, 위험도 점수
  (`domain/risk`)나 재난 유형별 구독 설정 등 다른 조건은 발송 여부에 전혀 반영되지 않는다
  — `NotificationPreference.minRiskScore` 필드가 존재하지만 미사용임을 확인했다(주요 엔티티
  섹션 참고).
- 게스트 알림 경로는 회원 알림 경로보다 기능이 적다(알림 타입 선택 불가, 발송 이력 없음,
  중복 방지 없음)는 것이 의도된 단순화인지, 향후 보강 대상인지는 코드만으로는 판단할 수
  없다.
- 알림 트리거는 클러스터링 이전, 원본 `DisasterAlert` 저장 시점에 개별 재난문자 단위로
  발생한다(`DisasterFetchScheduler.java:39-47`의 순서: 저장 → 번역 → **알림 트리거** →
  클러스터링 → cross-region). 즉 "이벤트(`DisasterEvent`)" 단위가 아니라 "원본 알림
  (`DisasterAlert`)" 단위로 알림이 나가며, 같은 사건이 여러 건의 원본 알림으로 신고되면
  (클러스터링으로 나중에 하나의 이벤트로 합쳐지더라도) 관심지역이 일치하는 사용자는 그
  각각의 원본 알림마다 별도로 알림을 받을 수 있다. 이는 `alertId` 단위 중복 방지(FR-005)와는
  별개의 특성이다.
- `/api/v1/admin/**`가 `permitAll`인 것(FR-002)은 이 기능 문서화 과정에서 발견한 기존
  보안 설정이며, 이 스펙 작성 범위에서 수정하지 않았다 — 별도 이슈로 남긴다.
