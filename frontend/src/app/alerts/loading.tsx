// /alerts는 force-dynamic 서버 컴포넌트라 page.tsx의 prefetchQuery 3개(searchCombined,
// stats, stats/sido)가 전부 끝나야 응답이 온다 — loading.tsx가 없으면 그동안 화면에 아무
// 피드백도 없다가 한 번에 훅 바뀌어서 "눌러도 반응이 없다"로 보인다. Next.js App Router는
// 이 파일이 있으면 page.tsx를 자동으로 Suspense로 감싸 즉시 이 스켈레톤을 먼저 스트리밍한다.
export default function AlertsLoading() {
  return (
    <main className="bg-[var(--canvas)] min-h-[calc(100vh-48px)] animate-pulse">
      <div className="mx-auto max-w-7xl space-y-4 px-4 py-8 sm:px-6 sm:space-y-6">
        <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
          <div className="space-y-2">
            <div className="h-6 w-40 rounded bg-[var(--line)]" />
            <div className="h-4 w-64 rounded bg-[var(--line)]" />
          </div>
          <div className="flex items-center gap-2">
            <div className="h-9 w-24 rounded-[var(--radius-control)] bg-[var(--line)]" />
            <div className="h-9 w-24 rounded-[var(--radius-control)] bg-[var(--line)]" />
          </div>
        </div>

        <div className="grid grid-cols-1 gap-4 sm:gap-6 xl:grid-cols-[minmax(0,1fr)_340px]">
          <div className="flex min-w-0 flex-col gap-4 sm:gap-6">
            <div className="grid grid-cols-1 gap-3 rounded-[var(--radius-panel-card)] border border-[var(--line)] bg-[var(--surface)] p-4 shadow-[0_10px_30px_rgba(28,39,60,0.04)] sm:grid-cols-2 md:grid-cols-4">
              {/* AlertsClient.tsx의 실제 필터 폼(시도/시군구/시작일/종료일/유형/등급/출처
                  7개 + 키워드 2칸 + 버튼 행)과 필드 개수를 맞춘다 — 스켈레톤이 실제 폼보다
                  짧으면 콘텐츠 전환 시 아래 목록/사이드바가 크게 튀는 레이아웃 점프가 생긴다
                  (ui-checker 실측: 필터 카드 86px→286.5px, 페이지 전체 605px→1335.75px). */}
              {Array.from({ length: 7 }).map((_, i) => (
                <div key={i} className="flex flex-col gap-1">
                  <div className="h-3 w-16 rounded bg-[var(--line)]" />
                  <div className="h-9 rounded-[var(--radius-control)] bg-[var(--line)]" />
                </div>
              ))}
              <div className="col-span-full flex flex-col gap-1 sm:col-span-2">
                <div className="h-3 w-16 rounded bg-[var(--line)]" />
                <div className="h-9 rounded-[var(--radius-control)] bg-[var(--line)]" />
              </div>
              <div className="col-span-full flex justify-end gap-2">
                <div className="h-9 w-16 rounded-[var(--radius-control)] bg-[var(--line)]" />
                <div className="h-9 w-20 rounded-[var(--radius-control)] bg-[var(--line)]" />
              </div>
            </div>

            <div className="flex flex-col gap-3 rounded-[var(--radius-panel-card)] border border-[var(--line)] bg-[var(--surface)] p-4 shadow-[0_10px_30px_rgba(28,39,60,0.04)]">
              {/* 목록 페이지 크기(size=10, alertsSearchParams.ts)와 맞춘다 */}
              {Array.from({ length: 10 }).map((_, i) => (
                <div key={i} className="flex items-start gap-3 border-b border-[var(--line)] pb-3 last:border-0 last:pb-0">
                  <div className="h-5 w-5 shrink-0 rounded-full bg-[var(--line)]" />
                  <div className="flex-1 space-y-2">
                    <div className="h-4 w-1/3 rounded bg-[var(--line)]" />
                    <div className="h-3 w-full rounded bg-[var(--line)]" />
                    <div className="h-3 w-2/3 rounded bg-[var(--line)]" />
                  </div>
                </div>
              ))}
              {/* AlertsClient.tsx:397-413의 페이지네이션 행(이전/N of M/다음)과 자리 맞춤 —
                  없으면 목록 아래가 실제 콘텐츠보다 짧아 전환 시 점프가 남는다. */}
              <div className="mt-auto flex items-center justify-between border-t border-[var(--line)] px-2 pt-3">
                <div className="h-8 w-16 rounded-[var(--radius-control)] bg-[var(--line)]" />
                <div className="h-4 w-10 rounded bg-[var(--line)]" />
                <div className="h-8 w-16 rounded-[var(--radius-control)] bg-[var(--line)]" />
              </div>
            </div>
          </div>

          <div className="flex flex-col gap-4">
            {/* 실제 KakaoPolygonMap과 동일한 mapHeight="520px" (AlertsClient.tsx:422) */}
            <div className="h-[520px] rounded-[var(--radius-panel-card)] border border-[var(--line)] bg-[var(--surface)] p-4 shadow-[0_10px_30px_rgba(28,39,60,0.04)]" />
            <div className="h-64 rounded-[var(--radius-panel-card)] border border-[var(--line)] bg-[var(--surface)] p-4 shadow-[0_10px_30px_rgba(28,39,60,0.04)]" />
          </div>
        </div>
      </div>
    </main>
  );
}
