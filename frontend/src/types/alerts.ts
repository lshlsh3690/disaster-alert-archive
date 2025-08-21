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
});

export type Alert = z.infer<typeof ZAlert>;

export const ZPageMeta = z.object({
  content: z.array(ZAlert),
  totalElements: z.number(),
  totalPages: z.number(),
  number: z.number(), // current page (0-based)
  size: z.number(),
});


export const ZRegionStat = z.object({ region: z.string(), count: z.number() });
export const ZLevelStat  = z.object({ level: z.string().nullable(), count: z.number() });
export const ZTypeStat   = z.object({ type: z.string().nullable(), count: z.number() });

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