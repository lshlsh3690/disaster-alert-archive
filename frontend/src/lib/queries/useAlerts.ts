// hooks/useAlerts.ts
import { useQuery, keepPreviousData } from "@tanstack/react-query";
import { fetchLatestAlerts, searchAlerts, fetchAlert, fetchStats, type AlertSearchRequest, fetchLatestAlertsBySido, searchCombinedAlerts } from "@/api/alertApi";
import { fetchUserAlert } from "@/api/userAlertApi";
import { fetchUserAlerts } from "@/api/userAlertApi";

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

export function useAlert(id: number) {
  return useQuery({
    queryKey: ["alert", id],
    queryFn: () => fetchAlert(id),
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

export function useUserAlerts(params: { page?: number; size?: number; mine?: boolean }) {
  return useQuery({
    queryKey: ["user-alerts", params],
    queryFn: () => fetchUserAlerts(params),
    placeholderData: keepPreviousData,
  });
}
