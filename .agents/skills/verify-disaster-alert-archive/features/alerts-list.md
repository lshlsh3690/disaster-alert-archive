# 재난문자 목록 (/alerts)

과거 수신된 모든 재난문자를 지역/날짜/유형/경보단계/출처/키워드로 검색하는 목록 화면. 공식 수집분과 사용자
제보(`source=ALL|OFFICIAL|USER`)를 함께 보여주고, 옆에 지역별 건수를 보여주는 폴리곤 지도가 같이 뜬다.

## Sub-features

- `alerts-default-view` 진입 시 시작일=종료일=오늘로 기본 필터가 걸려 그 날 수신분만 보인다.
- `alerts-region-filter` 시/도 선택 시 시/군/구 select가 활성화되고 그 하위 목록으로 좁혀진다.
- `alerts-keyword-filter` 키워드 검색어로 본문을 필터링한다.
- `alerts-reset` "초기화" 버튼으로 모든 필터를 지운다.
- `alerts-pagination` 결과가 10건을 넘으면 페이지네이션 nav가 보인다.
- `alerts-stats-summary` 우측 패널에 총 발생 건수·최다 발생 지역·유형·경보단계별 건수가 목록과 같은 조건으로 집계된다.

## How to get to it (user POV)

- 상단 네비게이션의 "재난 문자" 메뉴.
- 홈(`/`) 대시보드에서 상세 목록으로 넘어가는 링크.
- 직접 URL(`/alerts?sido=...&startDate=...&...`)로 필터가 걸린 상태로 진입.

## Driving it with frontend/scripts/ui-screenshot.mjs + curl

Preconditions:

- ../SKILL.md Launch 절대로 백엔드·프론트 기동 완료.
- `curl -s http://localhost:3000/alerts`가 200을 반환(최초 1회 "데우기").

- **기본 진입.** `MSYS_NO_PATHCONV=1 node scripts/ui-screenshot.mjs /alerts .verify-evidence` (frontend/ 에서 실행).
  6개 프로필(iphone-se/iphone-12/galaxy-s24/tablet-768/desktop-1280/desktop-1920) 스크린샷이 `.verify-evidence/`에 저장된다.
  2026-11-28 런에서는 기본 날짜 필터가 "오늘"이라 목록이 "데이터 없음"으로 보였다 — **이건 버그가 아니라
  오늘자 수신분이 없다는 뜻**이다(Gotchas 참고).
- **API로 실데이터 교차 확인.** `curl -s "http://localhost:8080/api/v1/alerts/search/combined?startDate=2020-01-01&endDate=2026-12-31&size=3" -o .verify-evidence/alerts-search-combined.json`.
  2026-11-28 런에서 200 OK, 1814바이트, 실제 재난문자 레코드 3건(`content[].message` 채워짐)을 확인 — 화면이
  비어 보인 건 필터 탓이지 API/DB 단절이 아니라는 증거.
- **(아직 안 돌림) 지역 필터.** `select[name=sido]`에서 옵션 선택 → `select[name=sigungu]`가 `disabled` 해제되는지 확인.
- **(아직 안 돌림) 키워드 필터.** `input[name=keyword]`에 입력 후 "검색" 버튼 클릭 → 목록이 좁혀지는지 확인.
- **(아직 안 돌림) 페이지네이션.** 날짜 범위를 넓혀 10건 넘게 만든 뒤 `nav[aria-label*=pagination]`가 보이는지 확인.

## Gotchas

- **기본 날짜 필터가 "오늘"이다.** 오늘 수신된 재난문자가 없으면(흔함 — 재난문자는 매일 오지 않는다) 목록이
  항상 비어 보인다. "데이터가 있는지" 증명하려면 시작일/종료일을 넓히거나(예: 2020-01-01~오늘) API를 직접 찔러야 한다.
- **Git Bash에서 `/alerts`를 그대로 넘기면 MSYS가 경로를 변환해 버린다** — `MSYS_NO_PATHCONV=1` 필수(../SKILL.md 참고).
- **dev 서버를 막 띄운 직후 그 라우트의 첫 Playwright 네비게이션은 `networkidle` 30초 타임아웃으로 실패할 수 있다** —
  먼저 curl로 한 번 데워라.
- `/actuator/health`로 "떠 있는지" 확인하려 하지 마라 — 인증이 걸려 401이 난다(SecurityConfig, 제품 버그 아님,
  ../SKILL.md Doctor 절 참고). `GET /api/v1/alerts/search?size=1`을 대신 써라.
