import { z } from "zod";

/** 이벤트 → 법정동 영향 점수 (시간 감쇠 전 baseScore). */
export const ZRegionImpact = z.object({
  regionCode: z.string(), // 법정동 코드 (앞 5자리 = 시군구 SIG_CD)
  impactScore: z.number(),
});
export type RegionImpact = z.infer<typeof ZRegionImpact>;

/** GET /api/v1/regions/alerts/{alertId}/risk — 재난문자 단건 위험도. */
export const ZAlertRisk = z.object({
  alertId: z.number(),
  eventId: z.number(),
  eventTitle: z.string().nullable().optional(),
  disasterType: z.string().nullable().optional(),
  regionImpacts: z.array(ZRegionImpact).default([]),
});
export type AlertRisk = z.infer<typeof ZAlertRisk>;

/** 기여 이벤트 (지역 상세 설명용). */
export const ZContributingEvent = z.object({
  eventId: z.number(),
  eventTitle: z.string().nullable().optional(),
  disasterType: z.string().nullable().optional(),
  impactScore: z.number(),
});

/**
 * GET /api/v1/regions/{regionCode}/risk — 시군구 현재 위험도.
 * riskScore 는 시간 감쇠 + 인접 확산 반영 후 0~1 정규화된 effective 점수.
 */
export const ZRegionRiskDetail = z.object({
  regionCode: z.string(),
  riskScore: z.number(),
  topEventId: z.number().nullable().optional(),
  contributingEvents: z.array(ZContributingEvent).default([]),
  updatedAt: z.string().nullable().optional(),
});
export type RegionRiskDetail = z.infer<typeof ZRegionRiskDetail>;

/** GET /api/v1/regions/{regionCode}/risk/history — 위험도 시계열 (0~1 정규화). */
export const ZRiskHistoryPoint = z.object({
  snapshotAt: z.string(),
  riskScore: z.number(),
});
export type RiskHistoryPoint = z.infer<typeof ZRiskHistoryPoint>;
