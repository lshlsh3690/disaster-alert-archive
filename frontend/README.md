# Frontend — Disaster Alert Archive

Next.js 16 (App Router) + React 19 기반 웹 클라이언트. 재난문자 아카이브 조회, 이벤트/위험도 지도,
통계 대시보드, 관심지역 설정과 FCM 웹 푸시 알림(PWA)을 제공한다.

> 저장소 전체 개요는 [루트 README](../README.md), 작업 시 주의점·컨벤션은 [CLAUDE.md](../CLAUDE.md) 참고.

---

## 실행

```bash
cd frontend
npm install
cp .env.local.example .env.local   # 값을 채운다
npm run dev                        # http://localhost:3000
```

백엔드가 함께 떠 있어야 한다 (레포 루트에서 `docker compose -f docker-compose.dev.yml up postgres redis`
후 `cd backend && ./gradlew bootRun`).

### 스크립트

```bash
npm run dev      # next dev --turbopack
npm run build    # next build --webpack  ← 빌드는 turbopack 이 아니라 webpack 을 쓴다
npm run start
npm run lint
npm test         # jest (testEnvironment: node)
```

### 환경변수

전체 목록은 `.env.local.example` 참고.

| 변수 | 설명 |
|------|------|
| `BASE_API_URL` | 서버 런타임(SSR/`generateMetadata`) 전용 API 주소. 브라우저에 노출되지 않음 |
| `NEXT_PUBLIC_API_URL` / `NEXT_PUBLIC_API_BASE_URL` | 브라우저(axios·OAuth 리다이렉트)에서 쓰는 API 주소 |
| `NEXT_PUBLIC_KAKAO_MAP_APP_KEY` | Kakao Map JS 키 |
| `NEXT_PUBLIC_FIREBASE_*` | Firebase 설정 6종 + `VAPID_KEY` (FCM 웹 푸시) |

---

## 구조

`src/`

```
api/                # 백엔드 도메인별 얇은 axios 래퍼 (alertApi, eventApi, riskApi, ...)
  axios.ts          # 401 → /auth/reissue 리프레시 인터셉터가 붙은 공용 인스턴스
lib/
  queries/          # React Query 조회 훅 (useAlerts, useEvents, useRisk, ...)
  mutations/        # React Query 변경 훅 (useLogin, useSignup, useCreateUserAlert, ...)
  i18n.ts           # i18next 초기화 (리소스는 constants/i18n)
  firebase.ts       # FCM 클라이언트 초기화
  kakaoMapLoader.ts kakaoGeo.ts geojsonCache.ts   # 지도·법정동 폴리곤
  serverApi.ts queryClient.ts reactQueryProvider.tsx riskScore.ts
  alertsSearchParams.ts eventsSearchParams.ts     # URL 쿼리 ↔ 필터 상태 변환
store/              # Zustand — authStore, guestFavoriteRegionsStore, languageStore, signupStore
app/                # App Router 페이지
components/         # 공통·도메인 컴포넌트 (alerts, form, layout, map, notification, providers, ui)
hooks/              # useInitAuth, useForegroundMessage, useNotificationPermission, useGuestFavoriteSync, ...
constants/          # i18n 리소스, 이벤트 유형, 언어 목록
ui/                 # 재난 유형·등급·시도 표시용 매핑 테이블 (컴포넌트가 아니라 데이터)
types/ utils/ context/
```

### 라우트

`alerts`(+`[id]` / `map` / `new`), `community`(+`[id]`), `events`(+`[id]`), `stats`(+`charts`),
`user`(+`me` / `settings` / `delete` / `[id]`), `login`, `signup`, `notifications`, `missing`, `test`.

`missing` 페이지와 `components/MissingPerson.tsx` 는 아직 하드코딩된 플레이스홀더이고 실제로 어디에도
연결돼 있지 않다. 실종자 화면을 구현할 때 이 둘을 먼저 확인할 것.

---

## 규칙

- React Query 키는 tuple 로 구성한다.
- 특별한 필요가 없다면 낙관적 업데이트는 지양한다.
- DTO 필드명은 백엔드와 정확히 일치시킨다.

---

## 알아둘 함정

- **빌드는 webpack** — `npm run dev` 만 Turbopack 이고 `npm run build` 는 `--webpack` 이다. Turbopack 에서만
  통과하는 코드를 짜지 않도록 주의.
- **서비스워커는 Firebase SDK 를 쓰지 않는다** — `public/firebase-messaging-sw.js` 는
  `onBackgroundMessage()` 가 아니라 표준 Push API 의 `push` 이벤트를 직접 파싱한다 (SDK 의 push 라우팅
  이슈로 알림을 못 받거나 씹는 문제가 있어 재작성됨). 파싱 + `showNotification()` 전체를
  **반드시 `event.waitUntil()` 로 감싸야** SW 가 처리 도중 종료되지 않는다.
- **시도 목록이 하드코딩돼 있다** — `ui/metros.ts`. 2026-07-01 광주·전남 통합처럼 행정구역이 개편되면
  백엔드 법정동 코드와 함께 여기도 갱신해야 한다.
- **i18n 보간 구분자가 기본값이 아니다** — `lib/i18n.ts` 에서 `{{ }}` 가 아니라 `{ }` 로 설정돼 있다.
  번역 문자열을 추가할 때 주의.
- **PWA 캐시** — `next.config.ts` 가 `sw.js` / `manifest.json` 에 `must-revalidate` 를 강제한다. 배포 후에도
  구버전이 남는 문제를 막기 위한 설정이니 제거하지 말 것.
- **`/api/:path*` 는 rewrite 된다** — `next.config.ts` 가 `BASE_API_URL`(미설정 시 운영 API)로 프록시한다.

---

## 스크린샷 점검

`scripts/ui-screenshot.mjs`, `scripts/auth-screenshot.mjs` — Playwright 로 여러 화면 크기에서 실제 화면을
캡처하는 스크립트. 반응형 레이아웃 확인용이며 회귀 테스트 스위트는 아니다.
