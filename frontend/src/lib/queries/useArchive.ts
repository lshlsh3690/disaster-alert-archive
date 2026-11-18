import { useQuery, keepPreviousData } from "@tanstack/react-query";
import { fetchMajorEvents } from "@/api/archiveApi";

export function useMajorEvents(year: number | null) {
  return useQuery({
    queryKey: ["archive", "majorEvents", year],
    queryFn: () => fetchMajorEvents(year),
    placeholderData: keepPreviousData,
  });
}
