import { notFound } from "next/navigation";
import { isAxiosError } from "axios";
import { dehydrate, HydrationBoundary, QueryClient } from "@tanstack/react-query";
import EventDetailClient from "@/app/events/[id]/EventDetailClient";
import UnboundAlertClient from "./UnboundAlertClient";
import { resolveArchiveEvent } from "@/api/archiveApi";
import { fetchEvent } from "@/api/eventApi";

export const dynamic = "force-dynamic";

/**
 * 사건 영구 링크. sn 알림이 "지금" 속한 사건을 이 주소 그대로 그린다(리다이렉트 없음) —
 * 사건 ID 는 백필 때 바뀌므로 /events/{id} 로 보내면 그 주소가 다시 영구하지 않게 된다.
 */
export default async function ArchiveEventPage({
  params,
}: {
  params: Promise<{ sn: string }>;
}) {
  const { sn: snParam } = await params;
  if (!/^\d+$/.test(snParam)) notFound();

  let resolved;
  try {
    resolved = await resolveArchiveEvent(Number(snParam));
  } catch (e) {
    // 수집된 적 없는 sn 은 404 페이지로. 그 밖의 오류는 그대로 올려 오류 화면을 띄운다
    if (isAxiosError(e) && e.response?.status === 404) notFound();
    throw e;
  }

  // 사건에 묶이지 않은 알림(비사건 분류 등)은 오류가 아니라 정상 상태 — 안내와 원문 요약을 보여준다
  if (!resolved.bound || resolved.eventId == null) {
    return <UnboundAlertClient alert={resolved.alert} />;
  }

  const eventId = resolved.eventId;
  const queryClient = new QueryClient();
  await queryClient.prefetchQuery({
    queryKey: ["event", eventId, "ko"],
    queryFn: () => fetchEvent(eventId, "ko"),
  });

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <EventDetailClient eventId={eventId} />
    </HydrationBoundary>
  );
}
