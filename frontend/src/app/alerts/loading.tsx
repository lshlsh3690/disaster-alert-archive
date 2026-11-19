// /alerts는 force-dynamic 서버 컴포넌트라 page.tsx의 prefetchQuery 3개(searchCombined,
// stats, stats/sido)가 전부 끝나야 응답이 온다 — loading.tsx가 없으면 그동안 화면에 아무
// 피드백도 없다가 한 번에 훅 바뀌어서 "눌러도 반응이 없다"로 보인다. Next.js App Router는
// 이 파일이 있으면 page.tsx를 자동으로 Suspense로 감싸 즉시 이 스켈레톤을 먼저 스트리밍한다.
export default function AlertsLoading() {
  return (
    <main className="min-h-[calc(100vh-64px)] animate-pulse bg-[var(--canvas)]">
      <div className="krds-page mx-auto max-w-[1440px]">
        <div className="krds-page-head">
          <div className="space-y-2">
            <div className="h-4 w-28 rounded bg-[var(--krds-gray-10)]" />
            <div className="h-9 w-40 rounded bg-[var(--krds-gray-10)]" />
            <div className="h-5 w-72 rounded bg-[var(--krds-gray-10)]" />
          </div>
          <div className="flex items-center gap-2">
            <div className="h-11 w-28 rounded-[var(--radius-control)] bg-[var(--krds-gray-10)]" />
            <div className="h-11 w-32 rounded-[var(--radius-control)] bg-[var(--krds-gray-10)]" />
          </div>
        </div>

        <div className="grid grid-cols-1 gap-5 xl:grid-cols-[minmax(0,1fr)_380px]">
          <div className="flex min-w-0 flex-col gap-4">
            <div className="krds-card grid grid-cols-1 gap-3 p-5 sm:grid-cols-2 md:grid-cols-4">
              {/* AlertsClient.tsx의 실제 필터 폼(시도/시군구/시작일/종료일/유형/경보 단계/출처
                  7개 + 검색어 3칸 + 버튼 칸)과 배치를 맞춘다 — 스켈레톤이 실제 폼보다
                  짧으면 콘텐츠 전환 시 아래 목록/사이드바가 크게 튀는 레이아웃 점프가 생긴다
                  (ui-checker 실측: 필터 카드 86px→286.5px, 페이지 전체 605px→1335.75px). */}
              {Array.from({ length: 7 }).map((_, i) => (
                <div key={i} className="flex flex-col gap-1.5">
                  <div className="h-[22px] w-16 rounded bg-[var(--krds-gray-10)]" />
                  <div className="h-11 rounded-[var(--radius-control)] bg-[var(--krds-gray-10)]" />
                </div>
              ))}
              <div className="flex flex-col gap-1.5 sm:col-span-2 md:col-span-3">
                <div className="h-[22px] w-16 rounded bg-[var(--krds-gray-10)]" />
                <div className="h-11 rounded-[var(--radius-control)] bg-[var(--krds-gray-10)]" />
              </div>
              <div className="flex items-end gap-2 sm:col-span-2 md:col-span-1">
                <div className="h-11 flex-1 rounded-[var(--radius-control)] bg-[var(--krds-gray-10)]" />
                <div className="h-11 flex-1 rounded-[var(--radius-control)] bg-[var(--krds-gray-10)]" />
              </div>
            </div>

            <div className="krds-card flex flex-col overflow-hidden">
              <div className="flex items-center justify-between border-b border-[var(--krds-gray-10)] px-5 py-3.5">
                <div className="h-5 w-20 rounded bg-[var(--krds-gray-10)]" />
                <div className="h-5 w-14 rounded bg-[var(--krds-gray-10)]" />
              </div>
              {/* 목록 페이지 크기(size=10, alertsSearchParams.ts)와 맞춘다 */}
              {Array.from({ length: 10 }).map((_, i) => (
                <div key={i} className="flex flex-col gap-2 border-b border-[var(--krds-gray-10)] px-5 py-4">
                  <div className="flex justify-between gap-3">
                    <div className="h-5 w-1/3 rounded bg-[var(--krds-gray-10)]" />
                    <div className="h-5 w-32 rounded bg-[var(--krds-gray-10)]" />
                  </div>
                  <div className="h-4 w-full rounded bg-[var(--krds-gray-10)]" />
                  <div className="h-4 w-2/3 rounded bg-[var(--krds-gray-10)]" />
                  <div className="h-6 w-28 rounded bg-[var(--krds-gray-10)]" />
                </div>
              ))}
              {/* AlertsClient.tsx의 페이지네이션 행(이전/페이지 번호/다음)과 자리 맞춤 —
                  없으면 목록 아래가 실제 콘텐츠보다 짧아 전환 시 점프가 남는다. */}
              <div className="flex items-center justify-center gap-2 p-4">
                <div className="h-9 w-16 rounded-[var(--radius-control)] bg-[var(--krds-gray-10)]" />
                <div className="h-9 w-9 rounded-[var(--radius-control)] bg-[var(--krds-gray-10)]" />
                <div className="h-9 w-16 rounded-[var(--radius-control)] bg-[var(--krds-gray-10)]" />
              </div>
            </div>
          </div>

          <div className="flex flex-col gap-4">
            {/* 실제 KoreaMap25D 와 동일한 height="520px" + 안내 문구 한 줄 (AlertsClient.tsx) */}
            <div className="krds-card h-[560px]" />
            <div className="krds-card h-80" />
          </div>
        </div>
      </div>
    </main>
  );
}
