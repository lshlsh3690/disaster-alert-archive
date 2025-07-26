import React from "react";

export default function RegionStatus() {
  return (
    <div className="bg-white rounded-xl shadow-md p-6 flex flex-col gap-2">
      <div className="font-bold text-blue-700 mb-1">지역별 재난문자 현황</div>
      <div className="text-gray-700">[지도 일러스트]<br/>서울(12) 부산(8) 대구(5) 등</div>
    </div>
  );
} 