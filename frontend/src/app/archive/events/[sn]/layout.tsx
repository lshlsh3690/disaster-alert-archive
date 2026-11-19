import type { Metadata } from "next";
import { fetchEventServer, resolveArchiveEventServer } from "@/lib/serverApi";

const FALLBACK: Metadata = {
  title: "재난 사건 기록 | 재난 기록관",
  description: "재난문자 원문으로 남긴 재난 사건 기록입니다.",
};

function formatDate(iso: string): string {
  const d = new Date(iso);
  return `${d.getFullYear()}.${String(d.getMonth() + 1).padStart(2, "0")}.${String(d.getDate()).padStart(2, "0")}`;
}

// 검색·링크 미리보기용 메타데이터(FR-015). 같은 사건을 가리키는 sn 이 여럿일 수 있어
// canonical 은 현재 대표 알림 sn 주소로 둔다.
export async function generateMetadata({
  params,
}: {
  params: Promise<{ sn: string }>;
}): Promise<Metadata> {
  const { sn } = await params;
  const resolved = await resolveArchiveEventServer(sn);
  if (!resolved) return FALLBACK;

  if (!resolved.bound || resolved.eventId == null) {
    const message = resolved.alert?.message ?? "";
    return {
      title: `${resolved.alert?.disasterType ?? "재난문자"} 원문 기록 | 재난 기록관`,
      description: message.slice(0, 120),
    };
  }

  const event = await fetchEventServer(String(resolved.eventId));
  if (!event) return FALLBACK;

  const period =
    formatDate(event.firstAlertAt) === formatDate(event.lastAlertAt)
      ? formatDate(event.firstAlertAt)
      : `${formatDate(event.firstAlertAt)} ~ ${formatDate(event.lastAlertAt)}`;
  const title = `${event.eventTitle} (${period}) | 재난 기록관`;
  const description = `${event.primaryRegionName ?? ""} 지역, ${period} 기간 동안 관련 재난문자 ${event.alertCount}건이 발생했습니다.`;

  return {
    title,
    description,
    alternates: resolved.representativeSn ? { canonical: `/archive/events/${resolved.representativeSn}` } : undefined,
    openGraph: { type: "article", title, description },
  };
}

export default function ArchiveEventLayout({ children }: { children: React.ReactNode }) {
  return children;
}
