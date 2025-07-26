import React from "react";

export default function MissingPerson() {
  return (
    <div className="bg-white rounded-xl shadow-md p-6 flex flex-col gap-4">
      <div className="flex items-center justify-between mb-2">
        <span className="font-bold text-blue-700">실종자 정보</span>
        <a href="#" className="text-sm text-blue-500 font-semibold hover:underline">자세히보기 &gt;</a>
      </div>
      <div className="bg-gray-100 rounded-lg p-4 text-sm text-gray-700">
        [사진]<br/>
        이름: 김OO (남, 12세)<br/>
        실종일: 2024-06-01<br/>
        장소: 서울 강남구<br/>
        특이사항: 파란 점퍼 착용<br/>
        <div className="mt-2 underline cursor-pointer text-blue-500">[더보기]</div>
      </div>
    </div>
  );
} 