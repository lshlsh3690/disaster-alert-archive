export default function DisasterListPage() {
  return (
    <main className="p-6 space-y-6">
      <h1 className="text-2xl font-bold">📂 재난 문자 아카이브</h1>
      <p className="text-gray-600 mb-4">과거 수신된 모든 재난 문자 목록입니다. 지역, 날짜, 키워드로 검색할 수 있어요.</p>

      {/* 🔍 검색 필터, 날짜선택, 지역필터 등 */}
      <div className="bg-white rounded-xl p-4 shadow">[ 검색 필터 바 ]</div>

      {/* ✅ 리스트 */}
      <div className="bg-white rounded-xl p-4 shadow space-y-2">
        <p>[서울] 2024-06-01 - 태풍 경보</p>
        <p>[부산] 2024-06-01 - 폭우 주의보</p>
      </div>
    </main>
  )
}