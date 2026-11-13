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
 * Red 단계 뼈대 — 구현은 Green 에서.
 * now 의 Asia/Seoul 기준 날짜(YYYY-MM-DD)를 돌려줘야 하지만 지금은 틀린 기본값("")만 돌려준다.
 */
export function todayKst(now: Date = new Date()): string {
  void now;
  return "";
}

/**
 * 클라이언트의 useSearchParams()와 서버의 searchParams prop 둘 다 받을 수 있다.
 * Red 단계 뼈대 — today 는 받기만 하고 아직 쓰지 않는다(날짜 기본값 처리는 Green 에서).
 */
export function parseAlertsSearchForm(
  sp: RawSearchParams,
  today: string = todayKst(),
): AlertsSearchForm {
  void today;
  const source = readParam(sp, "source");
  return {
    sido: readParam(sp, "sido"),
    sigungu: readParam(sp, "sigungu"),
    startDate: readParam(sp, "startDate"),
    endDate: readParam(sp, "endDate"),
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
