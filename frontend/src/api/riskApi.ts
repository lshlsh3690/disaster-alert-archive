import instance from "./axios";
import { z } from "zod";
import {
  ZAlertRisk, ZRegionRiskDetail, ZRiskHistoryPoint,
  type AlertRisk, type RegionRiskDetail, type RiskHistoryPoint,
} from "@/types/risk";

/**
 * 재난문자 단건이 속한 이벤트의 지역별 위험도 영향 조회.
 * 아직 이벤트 클러스터링/위험도 계산 전이면 백엔드가 204를 반환 → null.
 */
export async function fetchAlertRisk(alertId: number): Promise<AlertRisk | null> {
  const res = await instance.get(`/api/v1/regions/alerts/${alertId}/risk`, {
    headers: { "X-Auth-Required": "false" },
  });
  if (res.status === 204 || !res.data) return null;
  return ZAlertRisk.parse(res.data);
}

/**
 * 시군구 현재 위험도 (감쇠·확산 반영 0~1).
 * 주: alertRisk 와 달리 ApiResponse({success, data, ...})로 래핑되어 내려옴.
 */
export async function fetchRegionRisk(regionCode: string): Promise<RegionRiskDetail> {
  const res = await instance.get(`/api/v1/regions/${regionCode}/risk`, {
    headers: { "X-Auth-Required": "false" },
  });
  return ZRegionRiskDetail.parse(res.data.data);
}

/** 시군구 위험도 시계열 (최근 days 일). */
export async function fetchRegionRiskHistory(regionCode: string, days = 7): Promise<RiskHistoryPoint[]> {
  const res = await instance.get(`/api/v1/regions/${regionCode}/risk/history`, {
    params: { days },
    headers: { "X-Auth-Required": "false" },
  });
  return z.array(ZRiskHistoryPoint).parse(res.data.data);
}
