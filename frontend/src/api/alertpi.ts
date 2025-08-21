import { Alert, LatestAlert, Stats, ZAlert, ZLatestAlert, ZPageMeta, ZRegionStat, ZStats } from "@/types/alerts";
import instance from "./axios";
import { z } from "zod";

export type AlertSearchRequest = {
  region?: string | null;
  districtCode?: string | null;
  startDate?: string | null; // ISO-8601
  endDate?: string | null; // ISO-8601
  type?: string | null;
  level?: string | null;
  keywords?: string | null;
  page?: number;
  size?: number;
  sort?: string | null;
};

export async function fetchLatestAlerts(limit = 5): Promise<LatestAlert[]> {
  const res = await instance.get("/api/v1/alerts/latest", { params: { limit } });
  const data = z.array(ZLatestAlert).parse(res.data);
  return data;
}

export async function searchAlerts(params: AlertSearchRequest) {
  const res = await instance.get("/api/v1/alerts/search", { params });
  const data = ZPageMeta.parse(res.data);
  return data;
}

export async function fetchAlert(id: number): Promise<Alert> {
  const res = await instance.get(`/api/v1/alerts/${id}`);
  const data = ZAlert.parse(res.data);
  return data;
}

export async function fetchStats(params: AlertSearchRequest): Promise<Stats> {
  const res = await instance.get("/api/v1/alerts/stats", { params });
  const data = ZStats.parse(res.data);
  return data;
}

export async function fetchLatestAlertsBySido(params: AlertSearchRequest): Promise<Array<{ region: string; count: number }>> {
  const res = await instance.get("/api/v1/alerts/stats/sido", { params });
  const data = z.array(ZRegionStat).parse(res.data);
  return data;
}
