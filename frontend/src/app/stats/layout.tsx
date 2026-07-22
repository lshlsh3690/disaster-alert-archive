import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "지역별·유형별 재난 통계 | 재난 안전문자 아카이브",
  description: "선택한 기간과 지역의 재난문자 발생 통계를 차트로 한눈에 분석할 수 있습니다.",
};

export default function StatsLayout({ children }: { children: React.ReactNode }) {
  return children;
}
