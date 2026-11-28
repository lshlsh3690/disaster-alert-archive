# 통계 (/stats)

`/alerts`에서 걸린 필터 조건을 URL 쿼리로 이어받아, 유형별/경보단계별/지역별/일별/시간대별/날씨 상관 등
다양한 차트 위젯으로 분석 결과를 보여주는 대시보드. 클라이언트 컴포넌트(`"use client"`)이고 `useSearchParams()`로
`sido·sigungu·startDate·endDate·type·level·keyword·source`를 읽는다.

## Sub-features

- `stats-type-level` 유형별·경보단계별 집계(총 건수).
- `stats-region` 시/도별, (시/도 선택 시) 시/군/구별 건수.
- `stats-timeseries` 일별 건수 시계열, 요일×시간대 히트맵.
- `stats-type-trend` 월별/일별 유형 누적(stacked) 위젯.
- `stats-yoy-compare` 전년/금년 비교(해당 위젯이 있을 때만).
- `stats-weather-correlation` 날씨·재난 상관 데이터(기간 7일 이하일 때는 시간별 버전).

## How to get to it (user POV)

- `/alerts` 화면의 "통계 보기" 버튼(쿼리 파라미터를 들고 `/stats`로 이동).
- 직접 URL(`/stats?sido=...&startDate=...&...`)로 진입.

## Driving it with frontend/scripts/ui-screenshot.mjs

Preconditions:

- ../SKILL.md Launch 절대로 백엔드·프론트 기동 완료.
- `curl -s http://localhost:3000/stats`가 200을 반환(최초 1회 데우기).

- **(아직 안 돌림) 기본 진입.** `MSYS_NO_PATHCONV=1 node scripts/ui-screenshot.mjs /stats .verify-evidence`.
- **(아직 안 돌림) 필터 이어받기.** `/alerts`에서 지역/날짜를 고르고 "통계 보기"를 눌러 `/stats`로 넘어갔을 때
  같은 조건의 쿼리 파라미터가 URL에 그대로 있는지 확인.

## Gotchas

- 위젯이 많아 전부 같은 조건(`sido`/`startDate`/`endDate`/`type`/`level`/`keyword`/`source`)에 enabled 조건부로
  걸려 있다 — alerts-list.md와 같은 "오늘만" 기본 날짜 필터 문제가 여기도 그대로 이어질 가능성이 높다(미확인).
- 날씨 상관 위젯은 기간이 7일 이하일 때만 시간별 버전으로 바뀐다 — 긴 기간/짧은 기간을 각각 한 번씩 드라이브해야
  두 버전을 다 봤다고 할 수 있다.
- 이 파일은 코드 리딩만으로 작성됐다 — drive를 아직 한 번도 안 돌렸다.
