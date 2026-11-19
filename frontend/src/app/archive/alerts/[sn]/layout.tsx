import type { Metadata } from "next";
import { fetchArchiveAlertServer } from "@/lib/serverApi";

// 검색·링크 미리보기용 메타데이터(FR-015). 원문은 sn 하나에 하나뿐이라 canonical 은 이 주소 그대로다.
export async function generateMetadata({
  params,
}: {
  params: Promise<{ sn: string }>;
}): Promise<Metadata> {
  const { sn } = await params;
  const record = await fetchArchiveAlertServer(sn);
  if (!record) {
    return {
      title: "재난문자 원문 기록 | 재난 기록관",
      description: "재난문자 원문을 바뀌지 않는 링크로 남긴 기록입니다.",
    };
  }

  const date = record.createdAt.slice(0, 10).replace(/-/g, ".");
  const sender = record.agency?.label ? `${record.agency.label} ` : "";
  const title = `${sender}${record.disasterType ?? "재난"}문자 원문 (${date}) | 재난 기록관`;
  const description = record.message.slice(0, 150);

  return {
    title,
    description,
    alternates: { canonical: `/archive/alerts/${record.sn}` },
    openGraph: { type: "article", title, description },
  };
}

export default function ArchiveAlertLayout({ children }: { children: React.ReactNode }) {
  return children;
}
