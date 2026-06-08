import { useQuery, keepPreviousData } from "@tanstack/react-query";
import { searchEvents, fetchEvent, type EventSearchParams } from "@/api/eventApi";

export function useSearchEvents(params: EventSearchParams) {
  return useQuery({
    queryKey: ["events", params],
    queryFn: () => searchEvents(params),
    placeholderData: keepPreviousData,
  });
}

export function useEvent(id: number, lang = "ko") {
  return useQuery({
    queryKey: ["event", id, lang],
    queryFn: () => fetchEvent(id, lang),
    enabled: !!id,
  });
}
