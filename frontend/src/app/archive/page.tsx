import { dehydrate, HydrationBoundary, QueryClient } from "@tanstack/react-query";
import ArchiveClient from "./ArchiveClient";
import { fetchMajorEvents } from "@/api/archiveApi";

export const dynamic = "force-dynamic";

/** "2026" 처럼 숫자로만 된 year 만 받는다. 그 밖의 값은 무시하고 최근 연도로 보여준다. */
function parseYear(raw: string | string[] | undefined): number | null {
  const v = Array.isArray(raw) ? raw[0] : raw;
  return v && /^\d{4}$/.test(v) ? Number(v) : null;
}

// 연도는 주소(?year=)에 둔다 — 특정 연도 목록을 그대로 공유·인용할 수 있게.
// 서버가 prefetch 한 queryKey 와 클라이언트 첫 렌더의 queryKey 가 같아야 캐시가 재사용된다.
export default async function ArchivePage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const year = parseYear((await searchParams).year);

  const queryClient = new QueryClient();
  await queryClient.prefetchQuery({
    queryKey: ["archive", "majorEvents", year],
    queryFn: () => fetchMajorEvents(year),
  });

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <ArchiveClient year={year} />
    </HydrationBoundary>
  );
}
