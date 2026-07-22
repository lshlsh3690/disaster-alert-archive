import type { MetadataRoute } from "next";

const SITE_URL = process.env.NEXT_PUBLIC_SITE_URL ?? "https://www.disaster-alert-archive.co.kr";
const API_BASE = process.env.BASE_API_URL ?? "https://api.disaster-alert-archive.co.kr";

// 최근 N건만 포함 — 전체 히스토리 백필은 2차 작업(현재 스코프 밖).
const RECENT_LIMIT = 1000;

async function recentAlertUrls(): Promise<MetadataRoute.Sitemap> {
  try {
    const res = await fetch(
      `${API_BASE}/api/v1/alerts/search/combined?source=OFFICIAL&size=${RECENT_LIMIT}&sort=createdAt,desc`,
      { headers: { "X-Auth-Required": "false" }, next: { revalidate: 3600 } },
    );
    if (!res.ok) return [];
    const page = await res.json();
    return (page.content ?? []).map((alert: { id: number; createdAt: string }) => ({
      url: `${SITE_URL}/alerts/${alert.id}`,
      lastModified: alert.createdAt,
    }));
  } catch {
    return [];
  }
}

async function recentEventUrls(): Promise<MetadataRoute.Sitemap> {
  try {
    const res = await fetch(
      `${API_BASE}/api/v1/events?size=${RECENT_LIMIT}&sort=lastAlertAt,desc`,
      { headers: { "X-Auth-Required": "false" }, next: { revalidate: 3600 } },
    );
    if (!res.ok) return [];
    const page = await res.json();
    return (page.content ?? []).map((event: { id: number; lastAlertAt: string }) => ({
      url: `${SITE_URL}/events/${event.id}`,
      lastModified: event.lastAlertAt,
    }));
  } catch {
    return [];
  }
}

export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  const staticRoutes: MetadataRoute.Sitemap = [
    { url: SITE_URL, changeFrequency: "daily", priority: 1 },
    { url: `${SITE_URL}/alerts`, changeFrequency: "hourly", priority: 0.9 },
    { url: `${SITE_URL}/events`, changeFrequency: "hourly", priority: 0.8 },
    { url: `${SITE_URL}/stats`, changeFrequency: "daily", priority: 0.6 },
    { url: `${SITE_URL}/community`, changeFrequency: "daily", priority: 0.5 },
  ];

  const [alertUrls, eventUrls] = await Promise.all([recentAlertUrls(), recentEventUrls()]);

  return [...staticRoutes, ...alertUrls, ...eventUrls];
}
