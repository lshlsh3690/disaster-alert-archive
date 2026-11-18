import instance from "./axios";
import { ZArchiveEventResolve, ZArchiveMajorEvents, type ArchiveEventResolve, type ArchiveMajorEvents } from "@/types/archive";

/** 연도별 주요 사건. year 를 빼면 사건이 있는 가장 최근 연도. 응답은 ApiResponse 로 감싸져 온다. */
export async function fetchMajorEvents(year?: number | null): Promise<ArchiveMajorEvents> {
  const res = await instance.get("/api/v1/archive/major-events", {
    params: year ? { year } : undefined,
    headers: { "X-Auth-Required": "false" },
  });
  return ZArchiveMajorEvents.parse(res.data.data);
}

/** 사건 영구 링크(/archive/events/{sn}) 해석 — sn 알림이 지금 속한 사건. 없는 sn 은 404 로 throw. */
export async function resolveArchiveEvent(sn: number): Promise<ArchiveEventResolve> {
  const res = await instance.get(`/api/v1/archive/events/${sn}`, {
    headers: { "X-Auth-Required": "false" },
  });
  return ZArchiveEventResolve.parse(res.data.data);
}
