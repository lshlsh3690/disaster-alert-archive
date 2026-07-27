import { dehydrate, HydrationBoundary, QueryClient } from "@tanstack/react-query";
import EventsClient from "./EventsClient";
import { searchEvents } from "@/api/eventApi";
import { buildEventsListParams, parseEventsPage, parseEventsSearchForm, parseEventsTab } from "@/lib/eventsSearchParams";

export const dynamic = "force-dynamic";

// alerts 목록과 동일한 이유로 파싱/빌드 로직을 eventsSearchParams.ts로 공유 — 서버가
// prefetch하는 params와 클라이언트 첫 렌더의 params가 정확히 같아야 캐시가 재사용된다.
// lang은 서버가 알 수 없는 클라이언트 저장 값이라 기본값 "ko"로 prefetch(다른 언어 사용자는
// 캐시 미스로 정상 재요청 — 가속 효과만 없을 뿐 깨지지 않음, 상세 페이지들과 동일 패턴).
export default async function EventsPage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const sp = await searchParams;
  const form = parseEventsSearchForm(sp);
  const tab = parseEventsTab(sp);
  const page = parseEventsPage(sp);
  const params = buildEventsListParams(form, tab, page, 10, "ko");

  const queryClient = new QueryClient();
  await queryClient.prefetchQuery({
    queryKey: ["events", params],
    queryFn: () => searchEvents(params),
  });

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <EventsClient />
    </HydrationBoundary>
  );
}
