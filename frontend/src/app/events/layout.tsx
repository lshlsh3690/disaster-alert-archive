import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "재난 사건 모아보기 | 재난 안전문자 아카이브",
  description: "재난문자 묶음 단위 사건 내역을 유형·지역·기간·키워드로 검색할 수 있습니다.",
};

export default function EventsLayout({ children }: { children: React.ReactNode }) {
  return children;
}
