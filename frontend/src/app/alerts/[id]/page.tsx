import { dehydrate, HydrationBoundary, QueryClient } from "@tanstack/react-query";
import AlertDetailClient from "./AlertDetailClient";
import { fetchAlert } from "@/api/alertApi";
import { fetchUserAlert } from "@/api/userAlertApi";

export const dynamic = "force-dynamic";

// source=USER면 useUserAlert(id), 아니면 useAlert(id, "ko")가 클라이언트에서 도는 쿼리라
// 그 둘 중 실제로 쓰일 쪽만 골라 prefetch한다 (lang은 클라이언트 초기값이 항상 "ko"라 일치).
export default async function AlertDetailPage({
  params,
  searchParams,
}: {
  params: Promise<{ id: string }>;
  searchParams: Promise<{ source?: string }>;
}) {
  const { id: idParam } = await params;
  const { source } = await searchParams;
  const id = Number(idParam);
  const isUser = (source || "OFFICIAL").toUpperCase() === "USER";

  const queryClient = new QueryClient();
  if (isUser) {
    await queryClient.prefetchQuery({
      queryKey: ["user-alert", id],
      queryFn: () => fetchUserAlert(id),
    });
  } else {
    await queryClient.prefetchQuery({
      queryKey: ["alert", id, "ko"],
      queryFn: () => fetchAlert(id, "ko"),
    });
  }

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <AlertDetailClient />
    </HydrationBoundary>
  );
}
