import { z } from "zod";

export const ZRegion = z.object({
    code: z.string(),
    name: z.string(),
});

export const ZAlert = z.object({
  id: z.number(),
  sn: z.number().optional(),
  message: z.string(),
  createdAt: z.string(),
  emergencyLevelText: z.string().nullable().optional(),
  disasterType: z.string().nullable().optional(),
  originalRegion: z.string().nullable().optional(),
  regionNames: z.array(z.string()).default([]),
  translatedMessage: z.string().nullable().optional(),
  translatedDisasterType: z.string().nullable().optional(),
  translatedRegionNames: z.array(z.string()).nullable().optional(),
  language: z.string().nullable().optional(),
});

export type Alert = z.infer<typeof ZAlert>;

export const ZCombinedAlert = ZAlert.extend({
  source: z.enum(["OFFICIAL", "USER"]).optional(),
});
export type CombinedAlert = z.infer<typeof ZCombinedAlert>;

export const ZPageMeta = z.object({
  content: z.array(ZAlert),
  totalElements: z.number(),
  totalPages: z.number(),
  number: z.number(), // current page (0-based)
  size: z.number(),
});

export const ZPageMetaCombined = z.object({
  content: z.array(ZCombinedAlert),
  totalElements: z.number(),
  totalPages: z.number(),
  number: z.number(),
  size: z.number(),
});


export const ZRegionStat = z.object({ region: z.string(), count: z.number() });
export const ZLevelStat  = z.object({ level: z.string().nullable(), count: z.number() });
export const ZTypeStat   = z.object({ type: z.string().nullable(), count: z.number() });
export const ZDailyStat  = z.object({ date: z.string(), count: z.number() });
export type DailyStat = z.infer<typeof ZDailyStat>;

export const ZHourlyStat = z.object({ dayOfWeek: z.number(), hour: z.number(), count: z.number() });
export type HourlyStat = z.infer<typeof ZHourlyStat>;

export const ZMonthlyTypeStat = z.object({ month: z.string(), type: z.string(), count: z.number() });
export type MonthlyTypeStat = z.infer<typeof ZMonthlyTypeStat>;

export const ZStats = z.object({
  totalCount: z.number(),
  regionStats: z.array(ZRegionStat),
  levelStats: z.array(ZLevelStat),
  typeStats: z.array(ZTypeStat),
});

export type Stats = z.infer<typeof ZStats>;

export const ZLatestAlert = z.object({
  id: z.number(),
  message: z.string(),
  createdAt: z.string(),
  topRegion: z.string().optional(),     // 서버에서 대표 지역명 내려주면 사용
  disasterType: z.string().nullable().optional(),
});
export type LatestAlert = z.infer<typeof ZLatestAlert>;

export const ZDashboardSummary = z.object({
  todayOfficialCount: z.number(),
  todayUserCount: z.number(),
  totalUserCount: z.number(),
  totalCombinedCount: z.number(),
});
export type DashboardSummary = z.infer<typeof ZDashboardSummary>;