import { dehydrate, HydrationBoundary, QueryClient } from "@tanstack/react-query";
import EventDetailClient from "./EventDetailClient";
import { fetchEvent } from "@/api/eventApi";

export const dynamic = "force-dynamic";

// 상세 페이지는 id가 라우트 파라미터에서 바로 나오고(폼 상태 없음) 홈과 난이도가 비슷해
// 목록 페이지보다 먼저 SSR prefetch를 적용했다. lang은 서버가 알 수 없는 클라이언트
// localStorage(zustand persist) 값이라 항상 기본값 "ko"로 prefetch — 사용자가 다른 언어를
// 저장해뒀다면 클라이언트에서 캐시 미스로 정상적으로 재요청됨(가속 효과만 없을 뿐 깨지지 않음).
export default async function EventDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id: idParam } = await params;
  const id = Number(idParam);

  const queryClient = new QueryClient();
  await queryClient.prefetchQuery({
    queryKey: ["event", id, "ko"],
    queryFn: () => fetchEvent(id, "ko"),
  });

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <EventDetailClient />
    </HydrationBoundary>
  );
}
