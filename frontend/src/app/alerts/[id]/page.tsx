// app/alerts/[id]/page.tsx
"use client";

import { useAlert } from "@/lib/queries/useAlerts";
import { useParams } from "next/navigation";

export default function AlertDetailPage() {
  const params = useParams<{ id: string }>();
  const id = Number(params.id);
  const { data, isLoading } = useAlert(id);

  if (isLoading) return <main className="p-6">불러오는 중...</main>;
  if (!data) return <main className="p-6">데이터가 없습니다.</main>;

  return (
    <main className="p-6 space-y-4">
      <h1 className="text-xl font-semibold">📨 재난 문자 상세</h1>
      <div className="bg-white rounded-xl shadow p-4 space-y-2">
        <div className="text-sm text-gray-500">{new Date(data.createdAt).toLocaleString()}</div>
        <div className="text-lg">{data.message}</div>
        <div className="text-sm text-gray-600">유형: {data.disasterType ?? "-"}</div>
        <div className="text-sm text-gray-600">레벨: {data.emergencyLevelText ?? "-"}</div>
        <div className="text-sm text-gray-600">지역: {data.originalRegion ?? "-"}</div>
        <div className="text-xs text-gray-400">SN: {data.sn ?? "-"}</div>
      </div>
    </main>
  );
}
