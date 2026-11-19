import { notFound } from "next/navigation";
import { isAxiosError } from "axios";
import AlertRecordClient from "./AlertRecordClient";
import { fetchArchiveAlert } from "@/api/archiveApi";

export const dynamic = "force-dynamic";

/** 원문 기록. sn 은 원 출처 고유번호라 재클러스터링·재수집과 무관하게 같은 원문을 가리킨다(스토리 1). */
export default async function ArchiveAlertPage({
  params,
}: {
  params: Promise<{ sn: string }>;
}) {
  const { sn: snParam } = await params;
  if (!/^\d+$/.test(snParam)) notFound();

  try {
    const record = await fetchArchiveAlert(Number(snParam));
    return <AlertRecordClient record={record} />;
  } catch (e) {
    // 수집된 적 없는 sn 은 404 페이지로. 그 밖의 오류는 그대로 올려 오류 화면을 띄운다
    if (isAxiosError(e) && e.response?.status === 404) notFound();
    throw e;
  }
}
