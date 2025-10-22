"use client";

import Link from "next/link";
import LatestAlertsSection from "./LatestAlertsSection";
import { useDashboardSummary } from "@/lib/queries/useAlerts";
import KakaoMetroMap from "@/components/KakaoMetroMap";
import { useLatestComments } from "@/lib/queries/useComments";

export default function DashboardPage() {
  const { data } = useDashboardSummary();
  const latestComments = useLatestComments(5);
  return (
    <main className="p-6 space-y-10">
      {/* 상단 요약 카드 */}
      <section className="grid grid-cols-2 sm:grid-cols-3 gap-4">
        <SummaryCard title="오늘 재난문자" value={`${data?.todayOfficialCount ?? 0}건`} />
        <SummaryCard title="오늘 사용자 제보" value={`${data?.todayUserCount ?? 0}건`} />
        <SummaryCard title="누적 사용자 제보 수" value={`${data?.totalUserCount ?? 0}건`} />
      </section>

      {/* 최신 재난 문자 */}
      <section>
        <SectionHeader title="📩 최신 재난 문자" href="/alerts" />
        <LatestAlertsSection limit={5} />
      </section>

      {/* 지역별 재난 문자 지도 */}
      <section>
        <SectionHeader title="🗺️ 지역별 재난 문자 지도" href="/disasters/map" />
        <KakaoMetroMap todayOnly zoomable={false} />
      </section>

      {/* 커뮤니티 인기 글 */}
      <section>
        <SectionHeader title="💬 최신 댓글" />
        <div className="bg-white rounded-xl p-4 shadow text-sm text-gray-800 space-y-2">
          {latestComments.isLoading && <div className="text-sm text-gray-500">불러오는 중...</div>}
          {latestComments.data?.map((c) => (
            <div key={c.id} className="flex items-start justify-between gap-3">
              <div className="flex-1">
                <div className="text-gray-800 line-clamp-1">{c.content}</div>
                <div className="text-xs text-gray-500">{c.authorNickname ?? "익명"} · {new Date(c.createdAt).toLocaleString()}</div>
              </div>
            </div>
          ))}
          {latestComments.data && latestComments.data.length === 0 && (
            <div className="text-sm text-gray-500">최근 댓글이 없습니다.</div>
          )}
        </div>
      </section>
    </main>
  );
}

function SummaryCard({ title, value }: { title: string; value: string }) {
  return (
    <div className="bg-white p-4 rounded-xl shadow text-center">
      <div className="text-gray-500 text-sm mb-1">{title}</div>
      <div className="text-2xl font-bold text-gray-900">{value}</div>
    </div>
  );
}

function SectionHeader({ title, href }: { title: string; href?: string }) {
  return (
    <div className="flex items-center justify-between mb-2">
      <h2 className="text-lg font-bold">{title}</h2>
      {href && (
        <Link href={href} className="text-sm text-blue-600 hover:underline">
          전체 보기 →
        </Link>
      )}
    </div>
  );
}
