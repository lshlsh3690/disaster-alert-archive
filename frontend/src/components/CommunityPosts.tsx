import React from "react";

export default function CommunityPosts() {
  return (
    <div className="bg-white rounded-xl shadow-md p-6 flex flex-col gap-2">
      <div className="font-bold text-blue-700 mb-1">커뮤니티 최신 글</div>
      <div className="text-gray-700">
        [재난문자 공유] 오늘 서울 폭염 문자 받으신 분? 우산 챙기세요!<br/><br/>
        [대구 미세먼지] 마스크 어디서 사는 게 좋을까요?
      </div>
      <a href="#" className="text-right text-sm text-blue-500 font-semibold hover:underline mt-2">자세히보기 &gt;</a>
    </div>
  );
} 