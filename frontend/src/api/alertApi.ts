import { Alert, LatestAlert, Stats, ZAlert, ZLatestAlert, ZPageMeta, ZPageMetaCombined, ZRegionStat, ZStats, ZDashboardSummary, ZDailyStat, ZHourlyStat, ZMonthlyTypeStat, type DashboardSummary, type DailyStat, type HourlyStat, type MonthlyTypeStat } from "@/types/alerts";
import instance from "./axios";
import { z } from "zod";

export type AlertSearchRequest = {
  region?: string | null;
  districtCode?: string | null;
  startDate?: string | null; // ISO-8601
  endDate?: string | null; // ISO-8601
  type?: string | null;
  level?: string | null;
  keyword?: string | null;
  page?: number;
  size?: number;
  sort?: string | null;
  source?: "ALL" | "OFFICIAL" | "USER" | null;
};

export async function fetchLatestAlerts(limit = 5): Promise<LatestAlert[]> {
  const res = await instance.get("/api/v1/alerts/latest", { params: { limit } });
  const data = z.array(ZLatestAlert).parse(res.data);
  return data;
}

export async function searchAlerts(params: AlertSearchRequest) {
  const res = await instance.get("/api/v1/alerts/search?", { params });
  const data = ZPageMeta.parse(res.data);
  return data;
}

export async function searchCombinedAlerts(params: AlertSearchRequest & { source?: "ALL" | "OFFICIAL" | "USER" }) {
  const res = await instance.get("/api/v1/alerts/search/combined", { params, headers: { "X-Auth-Required": "false" } });
  const data = ZPageMetaCombined.parse(res.data);
  return data;
}

export async function fetchAlert(id: number, lang = "ko"): Promise<Alert> {
  const res = await instance.get(`/api/v1/alerts/${id}`, { params: { lang }, headers: { "X-Auth-Required": "false" } });
  const data = ZAlert.parse(res.data);
  return data;
}

export async function fetchStats(params: AlertSearchRequest): Promise<Stats> {
  const res = await instance.get("/api/v1/alerts/stats", { params });
  const data = ZStats.parse(res.data);
  return data;
}

export async function fetchLatestAlertsBySido(
  params: AlertSearchRequest
): Promise<Array<{ region: string; count: number }>> {
  const res = await instance.get("/api/v1/alerts/stats/sido", { params });
  const data = z.array(ZRegionStat).parse(res.data);
  return data;
}

export async function fetchSigunguStats(
  params: AlertSearchRequest
): Promise<Array<{ region: string; count: number }>> {
  const res = await instance.get("/api/v1/alerts/stats/sigungu", { params });
  return z.array(ZRegionStat).parse(res.data);
}

export async function fetchDailyStats(params: AlertSearchRequest): Promise<DailyStat[]> {
  const res = await instance.get("/api/v1/alerts/stats/daily", { params });
  return z.array(ZDailyStat).parse(res.data);
}

export type Sigungu = {
  name: string;          // 한국어 원문 (검색 API 전달용)
  translatedName: string | null; // 번역된 이름 (화면 표시용)
  code: string;          // 법정동 코드 (관심지역 등록용)
};

export async function fetchSigungu(sido: string, lang = "ko"): Promise<Sigungu[]> {
  const res = await instance.get("/api/v1/districts/sigungu", {
    params: { sido, lang },
  });
  return z.array(
    z.object({
      name: z.string(),
      translatedName: z.string().nullable(),
      code: z.string(),
    })
  ).parse(res.data);
}

export async function fetchHourlyStats(params: AlertSearchRequest): Promise<HourlyStat[]> {
  const res = await instance.get("/api/v1/alerts/stats/hourly", { params });
  return z.array(ZHourlyStat).parse(res.data);
}

export async function fetchMonthlyTypeStats(params: AlertSearchRequest): Promise<MonthlyTypeStat[]> {
  const res = await instance.get("/api/v1/alerts/stats/monthly-type", { params });
  return z.array(ZMonthlyTypeStat).parse(res.data);
}

export async function fetchDashboardSummary(): Promise<DashboardSummary> {
  const res = await instance.get("/api/v1/alerts/dashboard/summary");
  const data = ZDashboardSummary.parse(res.data);
  return data;
}
