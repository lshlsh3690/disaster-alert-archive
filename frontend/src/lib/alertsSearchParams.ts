import { levelTextToCode } from "@/ui/level";

/**
 * /alerts 페이지의 검색 필터. URL 쿼리스트링과 1:1로 대응한다.
 * 서버 컴포넌트(page.tsx, searchParams prop)와 클라이언트 컴포넌트(AlertsClient.tsx,
 * useSearchParams())가 정확히 같은 params 객체를 만들어야 SSR prefetch 캐시가
 * 클라이언트에서 그대로 재사용된다 — 두 쪽 다 이 모듈을 통해서만 파싱/빌드한다.
 */
export type AlertsSearchForm = {
  sido?: string;
  sigungu?: string;
  startDate?: string;
  endDate?: string;
  type?: string;
  levelText?: string;
  keyword?: string;
  source?: "ALL" | "OFFICIAL" | "USER";
};

type RawSearchParams = URLSearchParams | Record<string, string | string[] | undefined>;

function readParam(sp: RawSearchParams, key: string): string | undefined {
  if (sp instanceof URLSearchParams) return sp.get(key) || undefined;
  const v = sp[key];
  return (Array.isArray(v) ? v[0] : v) || undefined;
}

/**
 * 키가 URL 에 존재하는지(값이 비어 있어도 존재로 본다).
 * "키 없음"(처음 진입)과 "키는 있는데 값이 빔"(사용자가 날짜를 지우고 검색)을 구분하는 데 쓴다.
 */
function hasParam(sp: RawSearchParams, key: string): boolean {
  if (sp instanceof URLSearchParams) return sp.has(key);
  return sp[key] !== undefined;
}

/**
 * now 의 Asia/Seoul 기준 날짜(YYYY-MM-DD).
 * 서버(Node)는 UTC 로 돌 수 있어 로컬 타임존으로 "오늘"을 구하면 브라우저(한국)와 하루 어긋나고,
 * 그러면 SSR prefetch 의 쿼리 키가 클라이언트와 달라 캐시를 재사용하지 못한다.
 * en-CA 로케일이 YYYY-MM-DD 순서로 포맷해 주므로 별도 조립이 필요 없다.
 */
export function todayKst(now: Date = new Date()): string {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Seoul",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(now);
}

/**
 * 클라이언트의 useSearchParams()와 서버의 searchParams prop 둘 다 받을 수 있다.
 * 날짜 기본값: startDate/endDate 키가 둘 다 없으면(처음 진입) 오늘(today)로 채워 오늘 알림만 보여 준다.
 * 키가 있는데 값이 비면(검색 폼에서 날짜를 지우고 제출) 채우지 않고 undefined = 전체 기간이다.
 * today 를 인자로 받는 이유는 테스트에서 실행 날짜와 무관하게 결과를 고정하기 위해서다.
 */
export function parseAlertsSearchForm(
  sp: RawSearchParams,
  today: string = todayKst(),
): AlertsSearchForm {
  const source = readParam(sp, "source");
  // 둘 중 하나라도 키가 있으면 사용자가 날짜를 직접 다룬 것이므로 기본값을 쓰지 않는다
  const useDefaultDates = !hasParam(sp, "startDate") && !hasParam(sp, "endDate");
  return {
    sido: readParam(sp, "sido"),
    sigungu: readParam(sp, "sigungu"),
    startDate: useDefaultDates ? today : readParam(sp, "startDate"),
    endDate: useDefaultDates ? today : readParam(sp, "endDate"),
    type: readParam(sp, "type"),
    levelText: readParam(sp, "levelText"),
    keyword: readParam(sp, "keyword"),
    source: source === "ALL" || source === "OFFICIAL" || source === "USER" ? source : undefined,
  };
}

/** useSearchCombinedAlerts에 넘기는 params — 목록 조회용 (page/size/sort 포함). */
export function buildAlertsListParams(f: AlertsSearchForm, page: number, size: number) {
  const levelCode = levelTextToCode(f.levelText);
  const region = f.sido && f.sigungu ? `${f.sido} ${f.sigungu}` : f.sido || undefined;
  return {
    region,
    startDate: f.startDate || undefined,
    endDate: f.endDate || undefined,
    type: f.type || undefined,
    level: levelCode,
    keyword: f.keyword || undefined,
    source: f.source || ("ALL" as const),
    page,
    size,
    sort: "createdAt,desc",
  };
}

/** useSidoStats/useAlertStats에 넘기는 params — 통계 요약용 (page/size 없음). */
export function buildAlertsStatsParams(f: AlertsSearchForm) {
  const levelCode = levelTextToCode(f.levelText);
  const region = f.sido && f.sigungu ? `${f.sido} ${f.sigungu}` : f.sido || undefined;
  return {
    region,
    startDate: f.startDate || undefined,
    endDate: f.endDate || undefined,
    type: f.type || undefined,
    level: levelCode,
    keyword: f.keyword || undefined,
    source: f.source || undefined,
  };
}
