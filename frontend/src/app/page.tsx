import { dehydrate, HydrationBoundary, QueryClient } from "@tanstack/react-query";
import HomeClient from "./HomeClient";
import { fetchDashboardSummary } from "@/api/alertApi";

// "오늘 발생 건수" 등 당일 통계라 빌드 시점에 한 번 정적으로 굳혀지면 안 됨 — 매 요청마다
// 서버에서 새로 렌더링(SSR)하도록 강제. 이게 없으면 Next가 이 페이지를 정적 생성(SSG)
// 대상으로 판단해 빌드 시점 값을 계속 보여줄 수 있음.
export const dynamic = "force-dynamic";

// 홈은 진입 트래픽이 가장 몰리는 페이지라, 상단 통계 카드(오늘 발생/사용자 제보 건수)를
// 서버에서 미리 받아 HydrationBoundary로 내려보낸다. useDashboardSummary()의 queryKey와
// 정확히 같은 키("dashboard-summary")로 prefetch해야 클라이언트가 그대로 캐시를 재사용한다.
// 요청마다 새 QueryClient를 만들어야 함 — lib/queryClient.ts의 싱글턴은 브라우저 전용이라
// 서버에서 재사용하면 요청 간 캐시가 섞인다.
export default async function Home() {
  const queryClient = new QueryClient();
  await queryClient.prefetchQuery({
    queryKey: ["dashboard-summary"],
    queryFn: fetchDashboardSummary,
  });

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <HomeClient />
    </HydrationBoundary>
  );
}
