// hooks/useAlerts.ts
import { useQuery, keepPreviousData } from "@tanstack/react-query";
import { fetchLatestAlerts, searchAlerts, fetchAlert, fetchStats, type AlertSearchRequest, fetchLatestAlertsBySido, fetchSigunguStats, searchCombinedAlerts, fetchDashboardSummary, fetchSigungu, fetchDailyStats, fetchHourlyStats, fetchMonthlyTypeStats, fetchWeatherCorrelation, fetchWeatherByType, fetchWeatherByRegion, fetchAlertWeather } from "@/api/alertApi";
import { fetchUserAlert, fetchUserAlerts } from "@/api/userAlertApi";

export function useLatestAlerts(limit = 5) {
  return useQuery({
    queryKey: ["latest-alerts", limit],
    queryFn: () => fetchLatestAlerts(limit),
    staleTime: 60_000,
  });
}

export function useSearchAlerts(params: AlertSearchRequest) {
  return useQuery({
    queryKey: ["alerts", params],
    queryFn: () => searchAlerts(params),
    placeholderData: keepPreviousData,
  });
}

export function useSearchCombinedAlerts(params: AlertSearchRequest & { source?: "ALL" | "OFFICIAL" | "USER" }) {
  return useQuery({
    queryKey: ["alerts-combined", params],
    queryFn: () => searchCombinedAlerts(params),
    placeholderData: keepPreviousData,
  });
}

export function useAlert(id: number, lang = "ko") {
  return useQuery({
    queryKey: ["alert", id, lang],
    queryFn: () => fetchAlert(id, lang),
    enabled: !!id,
  });
}

export function useUserAlert(id: number) {
  return useQuery({
    queryKey: ["user-alert", id],
    queryFn: () => fetchUserAlert(id),
    enabled: !!id,
  });
}

export function useAlertStats(params: AlertSearchRequest) {
  return useQuery({
    queryKey: ["alert-stats", params],
    queryFn: () => fetchStats(params),
    placeholderData: keepPreviousData,
    staleTime: 60_000,
  });
}

export function useSidoStats(params: AlertSearchRequest) {
  return useQuery({
    queryKey: ["alert-stats-sido", params],
    queryFn: () => fetchLatestAlertsBySido(params),
    placeholderData: keepPreviousData,
  });
}

export function useSigunguStats(params: AlertSearchRequest, enabled = true) {
  return useQuery({
    queryKey: ["alert-stats-sigungu", params],
    queryFn: () => fetchSigunguStats(params),
    placeholderData: keepPreviousData,
    enabled,
  });
}

export function useSigungu(sido: string | undefined, lang = "ko") {
  return useQuery({
    queryKey: ["sigungu", sido, lang],
    queryFn: () => fetchSigungu(sido!, lang),
    enabled: !!sido,
    staleTime: Infinity,
  });
}

export function useUserAlerts(params: { page?: number; size?: number; mine?: boolean }) {
  return useQuery({
    queryKey: ["user-alerts", params],
    queryFn: () => fetchUserAlerts(params),
    placeholderData: keepPreviousData,
  });
}

export function useDailyStats(params: AlertSearchRequest) {
  return useQuery({
    queryKey: ["alert-stats-daily", params],
    queryFn: () => fetchDailyStats(params),
    placeholderData: keepPreviousData,
    staleTime: 60_000,
  });
}

export function useHourlyStats(params: AlertSearchRequest) {
  return useQuery({
    queryKey: ["alert-stats-hourly", params],
    queryFn: () => fetchHourlyStats(params),
    placeholderData: keepPreviousData,
    staleTime: 60_000,
  });
}

export function useMonthlyTypeStats(params: AlertSearchRequest) {
  return useQuery({
    queryKey: ["alert-stats-monthly-type", params],
    queryFn: () => fetchMonthlyTypeStats(params),
    placeholderData: keepPreviousData,
    staleTime: 60_000,
  });
}

export function useDashboardSummary() {
  return useQuery({
    queryKey: ["dashboard-summary"],
    queryFn: () => fetchDashboardSummary(),
    staleTime: 30_000,
  });
}

export function useWeatherCorrelation(params: AlertSearchRequest) {
  return useQuery({
    queryKey: ["weather-correlation", params],
    queryFn: () => fetchWeatherCorrelation(params),
    placeholderData: keepPreviousData,
    staleTime: 60_000,
  });
}

export function useWeatherByType(params: AlertSearchRequest) {
  return useQuery({
    queryKey: ["weather-by-type", params],
    queryFn: () => fetchWeatherByType(params),
    placeholderData: keepPreviousData,
    staleTime: 60_000,
    retry: 1,
  });
}

export function useWeatherByRegion(params: AlertSearchRequest, groupBy: "sido" | "sigungu") {
  return useQuery({
    queryKey: ["weather-by-region", params, groupBy],
    queryFn: () => fetchWeatherByRegion(params, groupBy),
    placeholderData: keepPreviousData,
    staleTime: 60_000,
    retry: 1,
  });
}

export function useAlertWeather(id: number) {
  return useQuery({
    queryKey: ["alert-weather", id],
    queryFn: () => fetchAlertWeather(id),
    enabled: !!id,
    staleTime: Infinity,
  });
}
