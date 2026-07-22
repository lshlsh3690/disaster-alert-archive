import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "재난문자 다시보기 · 지역별 재난 안전문자 내역 | 재난 안전문자 아카이브",
  description: "과거 수신된 모든 재난 안전문자를 지역·날짜·키워드로 검색하고 다시볼 수 있습니다.",
};

export default function AlertsLayout({ children }: { children: React.ReactNode }) {
  return children;
}
