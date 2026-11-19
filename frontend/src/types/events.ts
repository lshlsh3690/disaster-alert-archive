import { z } from "zod";

export const ZEventAlertItem = z.object({
  alertId: z.number(),
  sequenceNo: z.number().nullable().optional(),
  message: z.string(),
  disasterType: z.string().nullable().optional(),
  emergencyLevelText: z.string().nullable().optional(),
  createdAt: z.string(),
  regionNames: z.array(z.string()).default([]),
  translatedMessage: z.string().nullable().optional(),
  translatedType: z.string().nullable().optional(),
  translatedRegionNames: z.array(z.string()).nullable().optional(),
  language: z.string().nullable().optional(),
});
export type EventAlertItem = z.infer<typeof ZEventAlertItem>;

export const ZEvent = z.object({
  id: z.number(),
  eventTitle: z.string(),
  primaryDisasterType: z.string().nullable().optional(),
  primaryRegionName: z.string().nullable().optional(),
  primaryRegionCode: z.string().nullable().optional(),
  active: z.boolean(),
  firstAlertAt: z.string(),
  lastAlertAt: z.string(),
  alertCount: z.number(),
  translatedTitle: z.string().nullable().optional(),
  language: z.string().nullable().optional(),
});
export type Event = z.infer<typeof ZEvent>;

export const ZEventDetail = ZEvent.extend({
  timeline: z.array(ZEventAlertItem),
  // 기록관 영구 링크(/archive/events/{representativeSn})용. 백엔드 배포 순서와 무관하게 깨지지 않게 optional
  representativeSn: z.number().nullable().optional(),
});
export type EventDetail = z.infer<typeof ZEventDetail>;

export const ZEventPage = z.object({
  content: z.array(ZEvent),
  totalElements: z.number(),
  totalPages: z.number(),
  number: z.number(),
  size: z.number(),
});
export type EventPage = z.infer<typeof ZEventPage>;
