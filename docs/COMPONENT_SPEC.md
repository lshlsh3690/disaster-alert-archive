# 컴포넌트 명세서 (COMPONENT_SPEC) — Disaster Alert Archive Frontend

> `frontend/src/app/`(Next.js App Router 페이지)과 `frontend/src/components/`(재사용 컴포넌트) 전체를 실제 코드 기준으로 조사해 작성했다.

## 1. 페이지 (`app/`)

### 1.1 홈 / 대시보드

| 경로 | 파일 | 설명 | 주요 데이터 훅 |
|---|---|---|---|
| `/` | `app/page.tsx` | 대시보드 홈 — 요약 KPI, 최신 알림, 전국 지도, 최근 댓글 | `useDashboardSummary`, `useLatestComments` |
| (콜로케이션) | `app/LatestAlertsSection.tsx` | 홈에서 쓰는 최신 알림 5건 리스트 | `fetchLatestAlerts`(react-query) |

### 1.2 재난 문자 (alerts)

| 경로 | 파일 | 설명 | 주요 데이터 훅 |
|---|---|---|---|
| `/alerts` | `app/alerts/page.tsx` | 검색/필터 + 페이지네이션 목록 + 폴리곤 지도 + 통계 요약 | `useSearchCombinedAlerts`, `useSigungu`, `useSidoStats`, `useAlertStats`, `useSigunguStats` |
| `/alerts/new` | `app/alerts/new/page.tsx` | 사용자 제보 작성 폼 (주소 검색 모달 포함) | `useCreateUserAlert` |
| `/alerts/:id` | `app/alerts/[id]/page.tsx` | 알림 상세 — 공식/사용자 제보, 위험도 섹션, 무한스크롤 댓글 | `useAlert`/`useUserAlert`, `useComments`, `useInfiniteComments` |
| `/alerts/:id/edit` | `app/alerts/[id]/edit/page.tsx` | 사용자 제보 수정 폼 | `useUserAlert`, `useUpdateUserAlert` |
| `/alerts/map` | `app/alerts/map/page.tsx` | **미구현 스텁** — 정적 회색 박스만 존재 | — |

### 1.3 이벤트

| 경로 | 파일 | 설명 | 주요 데이터 훅 |
|---|---|---|---|
| `/events` | `app/events/page.tsx` | 이벤트(클러스터링된 사건) 검색/필터/탭(진행중·종료·전체) | `useSearchEvents`, `useSigungu` |
| `/events/:id` | `app/events/[id]/page.tsx` | 이벤트 상세 — 알림 타임라인(날짜별 그룹/접기) | `useEvent` |

### 1.4 통계 (`/stats`) — 가장 복잡한 페이지

| 파일 | 역할 |
|---|---|
| `app/stats/page.tsx` | 메인 — KPI, 필터, CSV 내보내기, 드래그 정렬 위젯 그리드(3개 프리셋을 localStorage에 저장) |
| `app/stats/_DistributionCharts.tsx` | 스냅샷 차트: 도넛/가로바/세로바/등급카드 |
| `app/stats/_TimeCharts.tsx` | 시계열 차트: 라인/일별바/히트맵/요일별/시간대별/누적영역/누적바/비교 |
| `app/stats/_WeatherCharts.tsx` | 기상 상관 차트, 호버 고정 툴팁(`LoadingDonut` 사용) |
| `app/stats/_KpiCards.tsx` | `KpiBox`, `FilterBanner` |
| `app/stats/_WidgetCard.tsx` | 위젯 카드 쉘 — 차트 선택 디스패치, PNG 다운로드, 삭제 |
| `app/stats/_WidgetLibrary.tsx` | 위젯 추가/삭제 슬라이드 서랍 |
| `app/stats/_charts.tsx` | 공용 `EmptyChart`/`LoadingChart` 플레이스홀더 |

데이터 훅 다수 사용: `useAlertStats`, `useSidoStats`, `useSigunguStats`, `useDailyStats`, `useHourlyStats`, `useMonthlyTypeStats`, `useDailyTypeStats`, `useWeatherCorrelation` 외 기상 관련 훅들.

### 1.5 커뮤니티

| 경로 | 파일 | 설명 |
|---|---|---|
| `/community` | `app/community/page.tsx` | NOTICE/FREE 탭, 목록 + 인라인 작성 폼 (NOTICE는 ADMIN만 작성 가능) |
| `/community/:id` | `app/community/[id]/page.tsx` | **미구현 스텁** — 정적 텍스트만 존재 |

### 1.6 계정/사용자

| 경로 | 파일 | 설명 |
|---|---|---|
| `/login` | `app/login/page.tsx` | 이메일 로그인 + 소셜 로그인 버튼 |
| `/signup` | `app/signup/page.tsx` + `SignupForm.tsx` | 이메일 인증 → 비밀번호 → 닉네임 중복확인 흐름 |
| `/user/me` | `app/user/me/page.tsx` | 내 정보 조회 |
| `/user/:id` | `app/user/[id]/page.tsx` | **미구현 스텁** |
| `/user/delete` | `app/user/delete/page.tsx` | **미구현 스텁** — "탈퇴 버튼 구현 예정" |
| `/user/settings` | `app/user/settings/page.tsx` | 프로필, 알림 설정(`NotificationSettings`), 비밀번호 변경/탈퇴는 "준비 중" 알림만 |
| `/user/settings/regions` | `app/user/settings/regions/page.tsx` | 관심지역 관리(최대 5개), 게스트 localStorage → 로그인 시 서버 동기화 |

### 1.7 기타

| 경로 | 파일 | 설명 |
|---|---|---|
| `/missing` | `app/missing/page.tsx` | **미구현 스텁** — 정적 텍스트만 존재 (관련 컴포넌트 `components/MissingPerson.tsx`는 고아 코드, 아래 3절 참고) |
| `/notifications` | `app/notifications/page.tsx` | 알림 수신 이력, 읽음 처리, 페이지네이션(20건) |
| `/test` | `app/test/page.tsx` | ⚠️ **개발용 임시 페이지** — `KakaoPolygonTest` 렌더링만 함, 네비게이션에 연결 안 됨 |

`app/layout.tsx`는 루트 레이아웃 — PWA 메타/폰트, `Header`/`Footer`/`NotificationPermissionBanner`를 전역 렌더링.

## 2. 재사용 컴포넌트 (`components/`)

### 2.1 alerts/
| 컴포넌트 | Props | 사용처 |
|---|---|---|
| `AlertRiskSection` | `{ alertId, alertCreatedAt? }` | `alerts/[id]` — 위험도 분석 패널, `AlertRiskMap` 동적 임포트(`ssr:false`) |
| `ReportButton` | 없음 | `alerts` — 미로그인 시 `/login`, 로그인 시 `/alerts/new`로 이동 |

### 2.2 form/ — react-hook-form 필드 컴포넌트
| 컴포넌트 | Props | 사용처 |
|---|---|---|
| `EmailInput` | `{ formMethods, disabled?, showVerificationUI?, defaultValue? }` | signup(인증코드 UI 포함), login |
| `CodeInput` | `{ formMethods }` | signup — 이메일 인증 코드 입력 |
| `PasswordInput` | `{ formMethods, name, showVerificationUI? }` | login, `PasswordInputGroup` |
| `PasswordInputGroup` | `{ formMethods, showVerificationUI? }` | signup — 비밀번호+확인 2개, 일치 여부 스토어 저장 |
| `NicknameInput` | `{ formMethods, disabled?, defaultValue? }` | signup — 600ms 디바운스 중복확인 |

공통 하위: `ui/InputStatusMessage`(필드 에러/유효/대기 상태 메시지), `ui/Button`(로딩 상태 지원).

### 2.3 layout/
| 컴포넌트 | 설명 |
|---|---|
| `Header` | 전역 헤더 — 데스크톱/모바일 네비게이션, 언어 선택, 로그인/유저 드롭다운(알림·설정·로그아웃), 로그아웃 시 게스트 FCM 토큰 정리 |
| `Footer` | 전역 푸터 |

### 2.4 map/
| 컴포넌트 | Props | 사용처 | 비고 |
|---|---|---|---|
| `AlertRiskMap` | `{ impacts, mapHeight? }` | `AlertRiskSection` | 이벤트 지역 영향도 히트맵 |
| `KakaoMetroMap` | `{ todayOnly?, zoomable?, selectedSido?, sigunguStats?, ... }` | 홈(`/`) | 시/도 단위 버블 오버레이, 시군구 드릴다운 지원 |
| `KakaoPolygonMap` | `{ params?, mapHeight?, showSidebar?, externalSido?, onSidoSelect? }` | `/alerts` | 가장 복잡한 지도 — 시도→시군구 드릴다운 위험도 단계 색상, 실시간 기상 연동(Open-Meteo) |
| `KakaoPolygonTest` | 없음 | `/test`만 | ⚠️ `KakaoPolygonMap`과 거의 동일하나 `mockDanger()` 가짜 데이터 사용 — 실험용 프로토타입 |

### 2.5 notification/
| 컴포넌트 | 설명 |
|---|---|
| `NotificationPermissionBanner` | 전역 하단 배너 — 로그인 사용자/관심지역 등록 게스트에게 알림 권한 요청 |
| `NotificationSettings` | 알림 유형(NONE/PUSH/ALARM) 설정 — `@/api` 훅 패턴을 안 쓰고 `fetch`를 직접 호출(다른 컴포넌트와 패턴 불일치) |

### 2.6 ui/
| 컴포넌트 | 설명 | 사용처 |
|---|---|---|
| `Button` | 로딩 상태 지원 범용 버튼 | signup, form 컴포넌트들 |
| `InputStatusMessage` | 폼 필드 상태 메시지 | 모든 form/ 컴포넌트 |
| `LoadingDonut` | 진행률 SVG 인디케이터 | `stats/_WeatherCharts.tsx`만 |
| `OAuthButton` | `{ provider: "google"\|"kakao"\|"naver" }` | login, signup |

## 3. 발견된 이슈 — 미구현/고아 코드

### 3.1 미구현 스텁 페이지 (정적 텍스트만 존재)
- `/alerts/map` — "지도 컴포넌트(카카오맵)" 문구만 있는 회색 박스
- `/community/:id` — 게시글 상세 미구현
- `/user/:id` — 사용자 정보 페이지 미구현
- `/user/delete` — 탈퇴 버튼 미구현
- `/missing` — 실종자 정보 페이지 미구현 (PRD 5.8 기능 자체는 백엔드에 있으나 프론트 화면이 없음)

### 3.2 고아 컴포넌트 — 어디서도 import되지 않음 (삭제 후보)
| 파일 | 비고 |
|---|---|
| `components/MissingPerson.tsx` | 하드코딩된 목업 카드, `/missing`에서도 미사용 |
| `components/dashboard/LatestAlerts.tsx` | `app/LatestAlertsSection.tsx`로 대체됨 |
| `components/dashboard/StatsGraph.tsx` | `/stats` 페이지로 대체됨 |
| `components/dashboard/RegionStatus.tsx` | `KakaoMetroMap`/`KakaoPolygonMap`으로 대체됨 |
| `components/dashboard/CommunityPosts.tsx` | `/community` 페이지로 대체됨 |

다섯 개 모두 하드코딩된 한글 목업 텍스트만 있는 초기 프로토타입으로 보이며, 실제 데이터 연동 버전으로 교체된 뒤 삭제되지 않고 남아있다. 삭제 전 팀 확인 필요.

### 3.3 개발용 임시 코드
- `app/test/page.tsx` + `components/map/KakaoPolygonTest.tsx` — 네비게이션에 연결되지 않은 개발 스크래치 페이지. `KakaoPolygonMap.tsx`(실제 사용)와 로직이 대량 중복됨.

## 4. 참고
- [PRD.md](./PRD.md), [TRD.md](./TRD.md), [REQUIREMENTS.md](./REQUIREMENTS.md), [API_SPEC.md](./API_SPEC.md), [DATA_MODEL.md](./DATA_MODEL.md)
