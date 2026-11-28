# 사건 목록 (/events)

재난문자가 임베딩 기반 클러스터링으로 묶인 "이벤트"(`DisasterEvent`) 목록. 지역/날짜/유형/키워드로 검색하고
탭(`role=tablist`)으로 보기를 전환한다.

## Sub-features

- `events-tabs` 상단 탭(`role=tablist`/`role=tab`)으로 목록 보기를 전환한다(진행중/종료 등으로 추정 — 미확인).
- `events-region-filter` 시/도·시/군/구 select(`aria-label`이 i18n 키 `events.filter.sido`/`events.filter.sigungu`로 바인딩).
- `events-date-filter` 시작일/종료일 DatePicker.
- `events-keyword-filter` 키워드 입력(`aria-label`/`placeholder`가 `events.filter.keyword`).
- `events-pagination` 결과가 많으면 `nav[aria-label]` 페이지네이션.

## How to get to it (user POV)

- 상단 네비게이션의 "사건 목록" 메뉴.
- `/alerts/{id}` 상세에서 소속 이벤트로 넘어가는 링크(있다면 — 미확인).

## Driving it with frontend/scripts/ui-screenshot.mjs

Preconditions:

- ../SKILL.md Launch 절대로 백엔드·프론트 기동 완료.
- `curl -s http://localhost:3000/events`가 200을 반환(최초 1회 데우기).

- **(아직 안 돌림) 기본 진입.** `MSYS_NO_PATHCONV=1 node scripts/ui-screenshot.mjs /events .verify-evidence`.
- **(아직 안 돌림) API 교차 확인.** `curl "http://localhost:8080/api/v1/events?..." ` (정확한 쿼리 파라미터는
  `src/api/eventApi.ts`의 `searchEvents` 구현을 먼저 확인해야 한다 — 이번 런에서는 안 봤다).

## Gotchas

- 탭이 실제로 어떤 보기를 나누는지(진행중/종료, 유형별 등) 코드만 보고 확정하지 못했다 — drive 전에 화면에서
  직접 확인 필요.
- alerts-list.md와 같은 근본 데이터를 클러스터링한 결과이므로, 날짜 필터 기본값이 좁으면 똑같이 "데이터 없음"으로
  보일 수 있다(확인 안 됨, 가능성만 기록).
- 이 파일은 코드 리딩만으로 작성됐다 — drive를 아직 한 번도 안 돌렸다.
