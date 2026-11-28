# 홈 대시보드 (/)

진입 트래픽이 가장 몰리는 첫 화면. 오늘 발생 건수·사용자 제보 건수 같은 당일 요약 통계와 지역별 지도를 보여준다.
`page.tsx`가 `fetchDashboardSummary`를 SSR로 미리 받아 `HydrationBoundary`로 내려보낸다(`force-dynamic`이라
빌드 시점 값이 굳지 않는다).

## Sub-features

- `home-summary-cards` 오늘 발생 건수·사용자 제보 건수 등 요약 카드.
- `home-map-view` 지역별 건수를 보여주는 폴리곤 지도, `role=radiogroup`인 세그먼트로 보기 전환(전국/시도/시군구 등으로 추정 — 미확인).
- `home-nav` 상단 네비게이션에서 다른 화면(재난 문자/사건 목록/통계/커뮤니티)으로 이동.

## How to get to it (user POV)

- 루트 URL(`/`) 직접 진입 — 로그인 불필요.
- 다른 화면에서 로고/홈 메뉴 클릭.

## Driving it with frontend/scripts/ui-screenshot.mjs

Preconditions:

- ../SKILL.md Launch 절대로 백엔드·프론트 기동 완료.
- `curl -s http://localhost:3000/`가 200을 반환(최초 1회 "데우기", alerts-list.md에서 실제로 필요했던 것과 동일 함정).

- **(아직 안 돌림) 기본 진입.** `MSYS_NO_PATHCONV=1 node scripts/ui-screenshot.mjs / .verify-evidence`. 요약 카드 숫자와
  `curl http://localhost:8080/api/v1/alerts/dashboard/summary` 응답을 대조해야 한다.
- **(아직 안 돌림) 지도 보기 전환.** `role=radiogroup`(`aria-label`은 `dashboard.mapViewLabel` i18n 키 — 실제 렌더링된
  한글 라벨은 화면에서 직접 확인 필요) 안의 옵션을 클릭해 지도가 다시 그려지는지 확인.

## Gotchas

- `export const dynamic = "force-dynamic"`이라 매 요청마다 서버에서 다시 렌더링한다 — 정적 캐시된 화면을 본 게
  아닌지 헷갈리지 말 것(빌드된 프로덕션 번들이 아니라 `npm run dev`로 떠 있는 한 문제되지 않는다).
- "오늘 발생 건수"는 당일 기준이라, `/alerts`와 마찬가지로 오늘 수신분이 없으면 0으로 보여도 정상이다.
- 이 파일은 코드 리딩만으로 작성됐다 — 실제 셀렉터(지도 보기 전환 라벨 등)는 drive 전에 화면에서 직접 확인해야 한다.
