import { z } from "zod";

/** 연도별 주요 사건 한 항목. 순위·점수 필드는 일부러 없다(FR-013). */
export const ZArchiveMajorEvent = z.object({
  representativeSn: z.number(),
  eventTitle: z.string(),
  primaryDisasterType: z.string().nullable().optional(),
  primaryRegionName: z.string().nullable().optional(),
  firstAlertAt: z.string(),
  lastAlertAt: z.string(),
  alertCount: z.number(),
});
export type ArchiveMajorEvent = z.infer<typeof ZArchiveMajorEvent>;

export const ZArchiveMajorEvents = z.object({
  year: z.number().nullable(),
  years: z.array(z.number()),
  events: z.array(ZArchiveMajorEvent),
});
export type ArchiveMajorEvents = z.infer<typeof ZArchiveMajorEvents>;

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

/** 원문 기록(스토리 1). 인용·공유 링크는 sn 으로 만든다. agency 는 본문에 발신 기관 표기가 없으면 null. */
export const ZArchiveAlert = z.object({
  sn: z.number(),
  alertId: z.number(),
  message: z.string(),
  createdAt: z.string(),
  modifiedDate: z.string().nullable().optional(),
  disasterType: z.string().nullable().optional(),
  emergencyLevel: z.string().nullable().optional(),
  originalRegion: z.string().nullable().optional(),
  inEvent: z.boolean(),
  agency: z.object({ label: z.string(), name: z.string().nullable().optional() }).nullable(),
});
export type ArchiveAlert = z.infer<typeof ZArchiveAlert>;
