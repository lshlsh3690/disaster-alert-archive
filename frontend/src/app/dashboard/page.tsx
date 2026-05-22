"use client";

import Link from "next/link";
import LatestAlertsSection from "./LatestAlertsSection";
import { useDashboardSummary } from "@/lib/queries/useAlerts";
import KakaoMetroMap from "@/components/KakaoMetroMap";
import { useLatestComments } from "@/lib/queries/useComments";
import { useI18n } from "@/hooks/useI18n";

export default function DashboardPage() {
  const { data } = useDashboardSummary();
  const latestComments = useLatestComments(5);
  const t = useI18n();


  return (
    <main className="p-3 sm:p-6 space-y-6 sm:space-y-10">
      {/* 상단 요약 카드 */}
      <section className="grid grid-cols-2 sm:grid-cols-3 gap-3 sm:gap-4">
        <SummaryCard
          title={t.dashboard.todayAlerts}
          value={`${data?.todayOfficialCount ?? 0}${t.dashboard.count}`}
        />
        <SummaryCard
          title={t.dashboard.todayUserReports}
          value={`${data?.todayUserCount ?? 0}${t.dashboard.count}`}
        />
        <SummaryCard
          title={t.dashboard.totalUserReports}
          value={`${data?.totalUserCount ?? 0}${t.dashboard.count}`}
        />
      </section>

      {/* 최신 재난 문자 */}
      <section>
        <SectionHeader title={t.dashboard.latestAlerts} href="/alerts" />
        <LatestAlertsSection limit={5} />
      </section>

      {/* 지역별 재난 문자 지도 */}
      <section>
        <SectionHeader title={t.dashboard.alertMap} href="/alerts/map" />
        <div className="px-[25%]">
          <KakaoMetroMap todayOnly zoomable={false} mapHeight="500px" nationalLevel={12} />
        </div>
      </section>

      {/* 커뮤니티 인기 글 */}
      <section>
        <SectionHeader title={t.dashboard.latestComments} href="/community"/> 
        <div className="bg-white rounded-xl p-4 shadow text-sm text-gray-800 space-y-2">
          {latestComments.isLoading && <div className="text-sm text-gray-500">{t.loading}</div>}
          {latestComments.data?.map((c) => (
            <div key={c.id} className="flex items-start justify-between gap-3">
              <div className="flex-1">
                <div className="text-gray-800 line-clamp-1">{c.content}</div>
                <div className="text-xs text-gray-500">{c.authorNickname ?? t.dashboard.anonymous} · {new Date(c.createdAt).toLocaleString()}</div>
              </div>
            </div>
          ))}
          {latestComments.data && latestComments.data.length === 0 && (
            <div className="text-sm text-gray-500">{t.dashboard.noComments}</div>
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
      <div className="text-xl sm:text-2xl font-bold text-gray-900">{value}</div>
    </div>
  );
}

function SectionHeader({ title, href }: { title: string; href?: string }) {
  const t = useI18n();
  return (
    <div className="flex items-center justify-between mb-2">
      <h2 className="text-lg font-bold">{title}</h2>
      {href && (
        <Link href={href} className="text-sm text-blue-600 hover:underline">
          {t.dashboard.viewAll}
        </Link>
      )}
    </div>
  );
}