import { parseAlertsSearchForm, todayKst } from "./alertsSearchParams";

describe("parseAlertsSearchForm 의 날짜 기본값", () => {
  // /alerts 에 처음 들어오면(URL 에 날짜 키가 없으면) 오늘 알림만 보여 주기로 한 결정을 고정한다.
  // Red 에서는 뼈대가 URL 값을 그대로 돌려줘 startDate/endDate 가 undefined 라 "2026-11-13" 기대와 어긋나 실패한다.
  it("날짜 키가 URL 에 아예 없으면 시작일·종료일이 모두 오늘이다", () => {
    // sido 만 있고 startDate/endDate 키 자체가 없는 URL = 처음 들어온 상황
    const sp = new URLSearchParams("sido=경기도");

    // today 를 직접 넘겨 실행 날짜와 무관하게 결과를 고정한다
    const form = parseAlertsSearchForm(sp, "2026-11-13");

    // 서버(SSR)와 브라우저가 같은 날짜를 써야 prefetch 캐시가 재사용되므로 둘 다 today 여야 한다
    expect(form.startDate).toBe("2026-11-13");
    expect(form.endDate).toBe("2026-11-13");
    // 날짜 외 필터는 그대로 유지된다
    expect(form.sido).toBe("경기도");
  });

  // 사용자가 날짜를 지우고 검색하면 "전체 기간"이 돼야 한다. 키는 있는데 값만 빈 경우를
  // "키 없음"과 구분하지 못하면 지워도 다시 오늘로 채워져 전체 기간을 볼 수 없다.
  // Red 에서는 뼈대가 빈 값을 undefined 로 돌려줘 이 테스트는 이미 통과할 수 있다 — 나머지 케이스가 Green 의 회귀를 막는다.
  it("날짜 키는 있는데 값이 비어 있으면 오늘로 채우지 않고 전체 기간(undefined)이다", () => {
    // 검색 폼이 날짜를 비운 채 제출하면 ?startDate=&endDate= 형태가 된다
    const sp = new URLSearchParams("startDate=&endDate=");

    const form = parseAlertsSearchForm(sp, "2026-11-13");

    // undefined = 날짜 조건 없음 = 전체 기간
    expect(form.startDate).toBeUndefined();
    expect(form.endDate).toBeUndefined();
  });

  // 사용자가 직접 고른 기간은 오늘 기본값에 덮이면 안 된다.
  it("날짜가 URL 에 있으면 그 값을 그대로 쓴다", () => {
    // today(2026-11-13)와 다른 날짜를 써서 기본값이 섞여 들어오는지 드러나게 한다
    const sp = new URLSearchParams("startDate=2026-11-01&endDate=2026-11-10");

    const form = parseAlertsSearchForm(sp, "2026-11-13");

    expect(form.startDate).toBe("2026-11-01");
    expect(form.endDate).toBe("2026-11-10");
  });
});

describe("todayKst", () => {
  // 서버는 UTC 로 돌 수 있어 "오늘"을 서버 시간으로 구하면 브라우저(한국)와 하루 어긋난다.
  // Red 에서는 뼈대가 빈 문자열("")을 돌려줘 "2026-11-13" 기대와 어긋나 실패한다.
  it("UTC 로는 전날이어도 한국 시간 기준 날짜를 돌려준다", () => {
    // 2026-11-12T16:00Z = 한국 시간(UTC+9) 2026-11-13 01:00 — UTC 날짜(12일)와 KST 날짜(13일)가 갈리는 시각
    const now = new Date("2026-11-12T16:00:00Z");

    expect(todayKst(now)).toBe("2026-11-13");
  });
});
