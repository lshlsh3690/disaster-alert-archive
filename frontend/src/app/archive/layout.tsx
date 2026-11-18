import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "재난 기록관 | 재난 안전문자 아카이브",
  description: "연도별 주요 재난 사건을 재난문자 원문과 함께 기록합니다. 사건마다 바뀌지 않는 영구 링크로 인용할 수 있습니다.",
};

export default function ArchiveLayout({ children }: { children: React.ReactNode }) {
  return children;
}
