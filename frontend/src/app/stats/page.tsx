export default function StatsSummaryPage() {
  return (
    <main className="p-6 space-y-6">
      <h1 className="text-2xl font-bold">📊 재난 통계 요약</h1>
      <p className="text-gray-600">재난 문자 수, 제보 건수, 커뮤니티 활동량 등을 종합적으로 보여줍니다.</p>

      {/* 지표 카드 */}
      <div className="grid grid-cols-2 gap-4">
        <div className="bg-white rounded-xl p-4 shadow">총 문자 수: 872건</div>
        <div className="bg-white rounded-xl p-4 shadow">월 평균: 73건</div>
      </div>
    </main>
  )
}
