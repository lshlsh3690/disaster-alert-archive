import type { Metadata } from "next";
import { fetchAlertServer } from "@/lib/serverApi";

function formatDate(iso: string): string {
  const d = new Date(iso);
  return `${d.getFullYear()}.${String(d.getMonth() + 1).padStart(2, "0")}.${String(d.getDate()).padStart(2, "0")}`;
}

export async function generateMetadata({
  params,
}: {
  params: Promise<{ id: string }>;
}): Promise<Metadata> {
  const { id } = await params;
  const alert = await fetchAlertServer(id);

  if (!alert) {
    return {
      title: "재난문자 상세 | 재난 안전문자 아카이브",
      description: "지역별 재난 안전문자 내역을 다시보기로 확인하세요.",
    };
  }

  const region = alert.regionNames[0] ?? alert.originalRegion ?? "";
  const title = `${alert.disasterType ?? "재난"}문자 - ${region} (${formatDate(alert.createdAt)}) | 재난 안전문자 아카이브`;
  const description = alert.message.slice(0, 150);

  return {
    title,
    description,
    openGraph: { type: "article", title, description },
  };
}

export default function AlertDetailLayout({ children }: { children: React.ReactNode }) {
  return children;
}
