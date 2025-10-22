"use client";
import dynamic from "next/dynamic";

const KakaoMetroMap = dynamic(
  () => import("@/components/map/KakaoMetroMap"), // 경로 alias가 없다면 상대경로로 변경
  { ssr: false }
);

export const metadata = {
  title: "재난 지도 | 재난 문자 아카이브",
};

export default function DisastersMapPage() {
  return (
    <main className="p-6">
      <h1 className="text-xl font-semibold mb-4">지역별 재난 문자 지도</h1>
      <KakaoMetroMap />
    </main>
  );
}