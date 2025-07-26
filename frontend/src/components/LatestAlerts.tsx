import React from "react";

export default function LatestAlerts() {
  return (
    <div className="bg-white rounded-xl shadow-md p-6 flex flex-col gap-4">
      <div className="flex items-center justify-between mb-2">
        <span className="font-bold text-lg text-blue-700">최신 재난문자</span>
        <a href="#" className="text-sm text-blue-500 font-semibold hover:underline">자세히보기 &gt;</a>
      </div>
      <div className="space-y-2 text-gray-700">
        <div>
          <span className="font-semibold text-gray-900">[서울] 2024-06-01 14:32 | 폭염주의보</span><br/>
          서울 전역에 폭염주의보가 발령되었습니다. 외출을 자제해 주세요.
        </div>
        <div>
          <span className="font-semibold text-gray-900">[부산] 2024-06-01 13:10 | 호우경보</span><br/>
          부산 지역에 호우경보가 발령되었습니다. 침수 위험 지역 접근을 삼가세요.
        </div>
        <div>
          <span className="font-semibold text-gray-900">[대구] 2024-06-01 12:00 | 미세먼지주의보</span><br/>
          대구 지역에 미세먼지주의보가 발령되었습니다. 마스크 착용을 권장합니다.
        </div>
      </div>
    </div>
  );
} 