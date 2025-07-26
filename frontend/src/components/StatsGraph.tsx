import React from "react";

export default function StatsGraph() {
  return (
    <div className="bg-white rounded-xl shadow-md p-6 flex flex-col gap-4">
      <div className="flex items-center justify-between mb-2">
        <span className="font-bold text-blue-700">재난안전문자 통계 그래프</span>
        <a href="#" className="text-sm text-blue-500 font-semibold hover:underline">자세히보기 &gt;</a>
      </div>
      <div className="text-xs text-gray-600 mt-2">[그래프 예시]<br/>2024-06-01: 12건<br/>2024-06-02: 8건<br/>2024-06-03: 15건</div>
    </div>
  );
} 