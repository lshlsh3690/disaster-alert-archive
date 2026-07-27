import { dehydrate, HydrationBoundary, QueryClient } from "@tanstack/react-query";
import AlertsClient from "./AlertsClient";
import { searchCombinedAlerts, fetchStats, fetchLatestAlertsBySido } from "@/api/alertApi";
import { parseAlertsSearchForm, buildAlertsListParams, buildAlertsStatsParams } from "@/lib/alertsSearchParams";

export const dynamic = "force-dynamic";

// 목록 페이지는 필터가 URL 쿼리스트링 + react-hook-form 상태로 관리되고 있어서, 서버가
// prefetch할 params와 클라이언트가 첫 렌더에 쓰는 params가 정확히 같아야 캐시가 재사용된다.
// 그래서 파싱/빌드 로직을 alertsSearchParams.ts로 공유 — 이 파일과 AlertsClient.tsx가
// 똑같은 함수를 호출해 같은 queryKey를 만든다. page=0/size=10은 페이지네이션이 URL에
// 없고 항상 0부터 시작하는 클라이언트 상태라 서버도 동일하게 가정.
export default async function AlertsPage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const sp = await searchParams;
  const form = parseAlertsSearchForm(sp);
  const listParams = buildAlertsListParams(form, 0, 10);
  const statsParams = buildAlertsStatsParams(form);

  const queryClient = new QueryClient();
  await Promise.all([
    queryClient.prefetchQuery({
      queryKey: ["alerts-combined", listParams, "ko"],
      queryFn: () => searchCombinedAlerts(listParams, "ko"),
    }),
    queryClient.prefetchQuery({
      queryKey: ["alert-stats-sido", statsParams],
      queryFn: () => fetchLatestAlertsBySido(statsParams),
    }),
    queryClient.prefetchQuery({
      queryKey: ["alert-stats", statsParams],
      queryFn: () => fetchStats(statsParams),
    }),
  ]);

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <AlertsClient />
    </HydrationBoundary>
  );
}
