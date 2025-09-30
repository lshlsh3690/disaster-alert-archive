"use client";

import Link from "next/link";
import LatestAlertsSection from "./LatestAlertsSection";
import KakaoMetroMap from "@/components/map/KakaoMetroMap";

export default function DashboardPage() {
  return (
    <main className="p-6 space-y-10">
      {/* 상단 요약 카드 */}
      <section className="grid grid-cols-2 sm:grid-cols-3 gap-4">
        <SummaryCard title="오늘 재난문자" value="8건" />
        <SummaryCard title="사용자 보고" value="3건" />
        <SummaryCard title="누적 제보 수" value="112건" />
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
        <SectionHeader title="💬 최신 커뮤니티 게시글" href="/community" />
        <div className="bg-white rounded-xl p-4 shadow text-sm text-gray-800 space-y-2">
          <p>🗨️ 재난 문자 받으신 분 계신가요? - 댓글 5</p>
          <p>🗨️ 과거 문자 어디서 볼 수 있죠? - 댓글 3</p>
          <p>🗨️ 과거 문자 어디서 볼 수 있죠? - 댓글 3</p>
          <p>🗨️ 과거 문자 어디서 볼 수 있죠? - 댓글 3</p>
          <p>🗨️ 과거 문자 어디서 볼 수 있죠? - 댓글 3</p>
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
