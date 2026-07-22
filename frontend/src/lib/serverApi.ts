import { ZAlert, type Alert } from "@/types/alerts";
import { ZEventDetail, type EventDetail } from "@/types/events";

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
