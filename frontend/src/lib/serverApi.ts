import { ZAlert, type Alert } from "@/types/alerts";
import { ZEventDetail, type EventDetail } from "@/types/events";
import { ZArchiveAlert, ZArchiveEventResolve, type ArchiveAlert, type ArchiveEventResolve } from "@/types/archive";

const API_BASE = process.env.BASE_API_URL ?? "https://api.disaster-alert-archive.co.kr";

/**
 * generateMetadata용 서버 전용 조회 — 브라우저 axios 인스턴스(쿠키/401 재발급 인터셉터)는
 * 서버 런타임에서 재사용하면 안 되므로 순수 fetch로 공개 API만 호출한다.
 * 실패 시 null을 반환해 호출부가 일반 metadata로 폴백할 수 있게 한다.
 */
export async function fetchAlertServer(id: string, lang = "ko"): Promise<Alert | null> {
  try {
    const res = await fetch(`${API_BASE}/api/v1/alerts/${id}?lang=${lang}`, {
      headers: { "X-Auth-Required": "false" },
      next: { revalidate: 3600 },
    });
    if (!res.ok) return null;
    return ZAlert.parse(await res.json());
  } catch {
    return null;
  }
}

/** 기록관 원문 기록(서버용, 메타데이터). 없는 sn·실패는 null. 원문은 바뀌지 않으므로 길게 캐시한다. */
export async function fetchArchiveAlertServer(sn: string): Promise<ArchiveAlert | null> {
  try {
    const res = await fetch(`${API_BASE}/api/v1/archive/alerts/${sn}`, {
      headers: { "X-Auth-Required": "false" },
      next: { revalidate: 3600 },
    });
    if (!res.ok) return null;
    const body = await res.json();
    return ZArchiveAlert.parse(body.data);
  } catch {
    return null;
  }
}

/** 기록관 사건 영구 링크 해석(서버용). 없는 sn·실패는 null. */
export async function resolveArchiveEventServer(sn: string): Promise<ArchiveEventResolve | null> {
  try {
    const res = await fetch(`${API_BASE}/api/v1/archive/events/${sn}`, {
      headers: { "X-Auth-Required": "false" },
      // 재클러스터링으로 사건이 바뀔 수 있어 오래 캐시하지 않는다
      next: { revalidate: 300 },
    });
    if (!res.ok) return null;
    const body = await res.json();
    return ZArchiveEventResolve.parse(body.data);
  } catch {
    return null;
  }
}

export async function fetchEventServer(id: string, lang = "ko"): Promise<EventDetail | null> {
  try {
    const res = await fetch(`${API_BASE}/api/v1/events/${id}?lang=${lang}`, {
      headers: { "X-Auth-Required": "false" },
      next: { revalidate: 3600 },
    });
    if (!res.ok) return null;
    return ZEventDetail.parse(await res.json());
  } catch {
    return null;
  }
}
