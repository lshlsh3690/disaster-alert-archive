import instance from "./axios";
import { ZArchiveEventResolve, type ArchiveEventResolve } from "@/types/archive";

/** 사건 영구 링크(/archive/events/{sn}) 해석 — sn 알림이 지금 속한 사건. 없는 sn 은 404 로 throw. */
export async function resolveArchiveEvent(sn: number): Promise<ArchiveEventResolve> {
  const res = await instance.get(`/api/v1/archive/events/${sn}`, {
    headers: { "X-Auth-Required": "false" },
  });
  return ZArchiveEventResolve.parse(res.data.data);
}

