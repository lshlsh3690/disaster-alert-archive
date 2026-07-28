import type { Metadata } from "next";
import { fetchEventServer } from "@/lib/serverApi";

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
  const event = await fetchEventServer(id);

  if (!event) {
    return {
      title: "재난 사건 상세 | 재난 안전문자 아카이브",
      description: "재난문자 묶음 단위 사건 내역을 다시보기로 확인하세요.",
    };
  }

  const period =
    event.firstAlertAt === event.lastAlertAt
      ? formatDate(event.firstAlertAt)
      : `${formatDate(event.firstAlertAt)} ~ ${formatDate(event.lastAlertAt)}`;
  const title = `${event.eventTitle} - ${event.primaryRegionName ?? ""} (${period}) | 재난 안전문자 아카이브`;
  const description = `${event.primaryRegionName ?? ""} 지역, ${period} 기간 동안 관련 재난문자 ${event.alertCount}건이 발생했습니다.`;

  return {
    title,
    description,
    openGraph: { type: "article", title, description },
  };
}

export default function EventDetailLayout({ children }: { children: React.ReactNode }) {
  return children;
}
