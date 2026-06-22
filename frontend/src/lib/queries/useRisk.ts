import { useQuery } from "@tanstack/react-query";
import { fetchAlertRisk, fetchRegionRisk, fetchRegionRiskHistory } from "@/api/riskApi";

/** 재난문자 단건 위험도. 클러스터링 전이면 data === null. */
export function useAlertRisk(alertId: number, enabled = true) {
  return useQuery({
    queryKey: ["alert-risk", alertId],
    queryFn: () => fetchAlertRisk(alertId),
    enabled: enabled && !!alertId,
    staleTime: 60_000,
    retry: 1,
  });
}

/** 시군구 현재 위험도 (감쇠·확산 반영). */
export function useRegionRisk(regionCode: string | undefined, enabled = true) {
  return useQuery({
    queryKey: ["region-risk", regionCode],
    queryFn: () => fetchRegionRisk(regionCode!),
    enabled: enabled && !!regionCode,
    staleTime: 60_000,
    retry: 1,
  });
}

/** 시군구 위험도 시계열. */
export function useRegionRiskHistory(regionCode: string | undefined, days = 7, enabled = true) {
  return useQuery({
    queryKey: ["region-risk-history", regionCode, days],
    queryFn: () => fetchRegionRiskHistory(regionCode!, days),
    enabled: enabled && !!regionCode,
    staleTime: 5 * 60_000,
    retry: 1,
  });
}
