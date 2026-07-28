import type { EventSearchParams } from "@/api/eventApi";

/**
 * /events 페이지의 검색 필터. URL 쿼리스트링과 1:1로 대응한다.
 * alertsSearchParams.ts와 같은 이유로 존재 — 서버(page.tsx)와 클라이언트(EventsClient.tsx)가
 * 정확히 같은 params를 만들어야 SSR prefetch 캐시가 클라이언트에서 그대로 재사용된다.
 */
export type EventsSearchForm = {
  sido: string;
  sigungu: string;
  type: string;
  startDate: string;
  endDate: string;
  keyword: string;
};

export type EventsActiveTab = "active" | "past" | "all";

export const EVENTS_EMPTY_FORM: EventsSearchForm = {
  sido: "",
  sigungu: "",
  type: "",
  startDate: "",
  endDate: "",
  keyword: "",
};

type RawSearchParams = URLSearchParams | Record<string, string | string[] | undefined>;

function readParam(sp: RawSearchParams, key: string): string | undefined {
  if (sp instanceof URLSearchParams) return sp.get(key) || undefined;
  const v = sp[key];
  return (Array.isArray(v) ? v[0] : v) || undefined;
}

/** react-hook-form의 defaultValues/reset()에 그대로 넣을 수 있도록 빈 문자열 기반. */
export function parseEventsSearchForm(sp: RawSearchParams): EventsSearchForm {
  return {
    sido: readParam(sp, "sido") ?? "",
    sigungu: readParam(sp, "sigungu") ?? "",
    type: readParam(sp, "type") ?? "",
    startDate: readParam(sp, "startDate") ?? "",
    endDate: readParam(sp, "endDate") ?? "",
    keyword: readParam(sp, "keyword") ?? "",
  };
}

export function parseEventsTab(sp: RawSearchParams): EventsActiveTab {
  const a = readParam(sp, "active");
  if (a === "true") return "active";
  if (a === "false") return "past";
  return "all";
}

export function parseEventsPage(sp: RawSearchParams): number {
  const p = Number.parseInt(readParam(sp, "page") ?? "", 10);
  return Number.isNaN(p) || p < 0 ? 0 : p;
}

/** useSearchEvents에 넘기는 params. */
export function buildEventsListParams(
  f: EventsSearchForm,
  tab: EventsActiveTab,
  page: number,
  size: number,
  lang: string
): EventSearchParams {
  const active = tab === "active" ? true : tab === "past" ? false : undefined;
  const region = f.sido && f.sigungu ? `${f.sido} ${f.sigungu}` : f.sido || undefined;
  return {
    active,
    type: f.type || undefined,
    region,
    startDate: f.startDate || undefined,
    endDate: f.endDate || undefined,
    keyword: f.keyword || undefined,
    lang,
    page,
    size,
  };
}
