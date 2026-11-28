# Disaster Alert Archive 검증 맵

이 디렉터리는 프론트엔드(Next.js) 사용자 화면의 유지되는 검증 소스다. 화면을 드라이브하기 전에 이 인덱스를 읽고,
해당 화면 파일을 레시피로 쓴다. 상단 네비게이션(대시보드/재난 문자/사건 목록/통계/커뮤니티) 기준으로 상위 5개
공개 화면만 우선 올렸다 — 로그인 뒤에만 보이는 화면(`/user/me`, `/notifications`, `/alerts/new` 등)은 OAuth2
소셜 로그인만 있어 로컬에서 자동 로그인할 방법이 없어 범위 밖이다(../SKILL.md의 Drive 절 참고).

## 기준 상태(Baseline preconditions)

- 백엔드 `http://localhost:8080`, 프론트엔드 `http://localhost:3000`가 ../SKILL.md의 Launch 절대로 떠 있을 것.
- `GET /api/v1/alerts/search?size=1` 이 200을 반환할 것(Doctor).
- 재난문자 데이터가 실제로 쌓인 DB(2026-11-28 기준 82,554건)를 쓸 것 — 빈 DB로 막 띄운 인스턴스는 아래
  화면들이 전부 "데이터 없음"으로 보여도 버그가 아니다(../SKILL.md의 Launch 절 참고).

## 드라이브 공통 규칙

- `frontend/scripts/ui-screenshot.mjs <path> <outDir>` 로 드라이브한다 — Git Bash에서는
  `MSYS_NO_PATHCONV=1`을 앞에 붙이고, 그 라우트의 첫 네비게이션 전에 같은 경로를 `curl`로 한 번 데워 둔다
  (둘 다 ../SKILL.md의 Drive 절에 있는 실제로 재현된 함정).
- 안정적인 핸들을 우선한다: `register()`가 만드는 `name` 속성(`input[name=keyword]` 등), `aria-label`, 역할(`role`).
  좌표나 DOM 순서에 기대지 않는다.
- 필터를 바꿨다면(예: 날짜 범위를 넓혔다면) 증빙을 남긴 뒤 원래 기본 상태로 되돌릴 필요는 없다 — 이 앱은
  서버 상태를 바꾸는 제출이 아니라 조회 화면이 대부분이라 되돌릴 "뮤테이션"이 없다. 글 등록(`/alerts/new`,
  커뮤니티 글쓰기)처럼 실제로 데이터를 쓰는 화면은 이 맵에 아직 없다.

## 증빙과 스킵 보고

- 캡처는 사용자 동작과 그 결과 상태를 함께 남긴다 — 마지막 화면만 찍지 않는다.
- 화면이 비어 보이면 같은 조건으로 백엔드 API를 curl로 직접 찔러 "API가 비정상"과 "진짜 데이터가 없음"을
  구분하는 JSON을 증빙에 같이 남긴다.
- 어떤 진입점을 못 돌렸으면 시도한 명령과 막힌 전제조건을 그대로 적는다 — 다른 진입점으로 돌린 걸 그 진입점을
  검증한 것처럼 적지 않는다.

## 화면 목록

- [재난문자 목록 (/alerts)](./alerts-list.md) — **2026-11-28에 실제로 drive+evidence 캡처까지 끝낸 유일한 화면.**
- [홈 대시보드 (/)](./home-dashboard.md) — 지도/오늘 통계 요약. 아직 drive 안 함(화면 구조만 파악).
- [사건 목록 (/events)](./events-list.md) — 클러스터링된 이벤트 목록. 아직 drive 안 함.
- [통계 (/stats)](./stats-dashboard.md) — 차트 대시보드. 아직 drive 안 함.
