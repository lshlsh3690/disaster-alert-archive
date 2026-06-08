import instance from "./axios";
import { ZEventDetail, ZEventPage, type EventDetail, type EventPage } from "@/types/events";

export type EventSearchParams = {
  active?: boolean | null;
  type?: string | null;
  region?: string | null;
  districtCode?: string | null;
  startDate?: string | null;
  endDate?: string | null;
  keyword?: string | null;
  lang?: string;
  page?: number;
  size?: number;
};

export async function searchEvents(params: EventSearchParams): Promise<EventPage> {
  const res = await instance.get("/api/v1/events", {
    params,
    headers: { "X-Auth-Required": "false" },
  });
  return ZEventPage.parse(res.data);
}

export async function fetchEvent(id: number, lang = "ko"): Promise<EventDetail> {
  const res = await instance.get(`/api/v1/events/${id}`, {
    params: { lang },
    headers: { "X-Auth-Required": "false" },
  });
  return ZEventDetail.parse(res.data);
}
