'use client'

import Link from 'next/link'

export default function DashboardPage() {
  return (
    <main className="p-6 space-y-10">
      {/* 상단 요약 카드 */}
      <section className="grid grid-cols-2 sm:grid-cols-4 gap-4">
        <SummaryCard title="오늘 재난문자" value="8건" />
        <SummaryCard title="사용자 보고" value="3건" />
        <SummaryCard title="누적 재보 수" value="112건" />
        <SummaryCard title="커뮤니티 참여" value="84회" />
      </section>

      {/* 최신 재난 문자 */}
      <section>
        <SectionHeader title="📩 최신 재난 문자" href="/disasters" />
        <div className="bg-white rounded-xl p-4 shadow">
          <ul className="text-sm text-gray-700 space-y-1">
            <li>📍 [서울] 2024-06-01 12:30 - 누강읍 태풍 경보 발령</li>
            <li>📍 [부산] 2024-06-01 11:00 - 호우주의보 발령</li>
            <li>📍 [대전] 2024-06-01 09:10 - 미세먼지 주의보</li>
          </ul>
        </div>
      </section>

      {/* 지역별 재난 문자 지도 */}
      <section>
        <SectionHeader title="🗺️ 지역별 재난 문자 지도" href="/disasters/map" />
        <div className="h-[300px] bg-gray-200 rounded-xl flex items-center justify-center text-gray-500">
          지도 컴포넌트 자리
        </div>
      </section>

      {/* 재난 통계 */}
      <section>
        <SectionHeader title="📊 재난 통계 요약" href="/stats" />
        <div className="grid grid-cols-2 gap-4">
          <div className="bg-white rounded-xl p-4 shadow">
            <div className="text-gray-600 text-sm">별송된 총 문자 건수</div>
            <div className="text-xl font-bold">404</div>
          </div>
          <div className="bg-white rounded-xl p-4 shadow">
            <div className="text-gray-600 text-sm">전년 대비 증가율</div>
            <div className="text-xl font-bold">+40%</div>
          </div>
        </div>
      </section>

      {/* 커뮤니티 인기 글 */}
      <section>
        <SectionHeader title="💬 인기 커뮤니티 게시글" href="/community" />
        <div className="bg-white rounded-xl p-4 shadow text-sm text-gray-800 space-y-2">
          <p>🗨️ 재난 문자 받으신 분 계신가요? - 댓글 5</p>
          <p>🗨️ 과거 문자 어디서 볼 수 있죠? - 댓글 3</p>
        </div>
      </section>
    </main>
  )
}

function SummaryCard({ title, value }: { title: string; value: string }) {
  return (
    <div className="bg-white p-4 rounded-xl shadow text-center">
      <div className="text-gray-500 text-sm mb-1">{title}</div>
      <div className="text-2xl font-bold text-gray-900">{value}</div>
    </div>
  )
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
  )
}