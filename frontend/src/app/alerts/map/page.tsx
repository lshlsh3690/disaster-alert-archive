export default function DisasterMapPage() {
  return (
    <main className="p-6 space-y-4">
      <h1 className="text-2xl font-bold">🗺️ 재난 문자 지도</h1>
      <p className="text-gray-600">지도 위에서 지역별 재난문자 발생 현황을 시각적으로 확인할 수 있어요.</p>

      {/* 지도 삽입 */}
      <div className="h-[500px] bg-gray-100 rounded-xl flex items-center justify-center text-gray-500">
        지도 컴포넌트 (카카오맵)
      </div>
    </main>
  )
}