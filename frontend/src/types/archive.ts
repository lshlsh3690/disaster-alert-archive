import { z } from "zod";

/** 사건 영구 링크 해석 결과. eventId 는 기존 상세 API 호출용 내부 값이라 링크·화면에 쓰지 않는다. */
export const ZArchiveEventResolve = z.object({
  sn: z.number(),
  bound: z.boolean(),
  eventId: z.number().nullable(),
  representativeSn: z.number().nullable(),
  alert: z
    .object({
      alertId: z.number(),
      sn: z.number(),
      message: z.string(),
      createdAt: z.string(),
      disasterType: z.string().nullable().optional(),
      emergencyLevel: z.string().nullable().optional(),
    })
    .nullable(),
});
export type ArchiveEventResolve = z.infer<typeof ZArchiveEventResolve>;

