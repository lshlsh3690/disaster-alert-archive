// app/(dashboard)/LatestAlertsSection.tsx  ← 경로는 편한 곳에
"use client";

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import instance from "@/api/axios";
import { fetchLatestAlerts } from "@/api/alertApi";
import { LatestAlert } from "@/types/alerts";


export default function LatestAlertsSection({limit = 5}: { limit?: number }) {
  const { data, isLoading, isError } = useQuery({
    queryKey: ["latest-alerts", limit],
    queryFn: () => fetchLatestAlerts(limit),
    staleTime: 60_000, // 1분 동안 캐시 유지
  });

  return (
    <div className="bg-white rounded-xl p-4 shadow">
      {isLoading && <p className="text-sm text-gray-500">불러오는 중...</p>}
      {isError && <p className="text-sm text-red-500">불러오기 실패</p>}

      <ul className="text-sm text-gray-700 space-y-1">
        {data?.map((a: LatestAlert) => (
          <li key={a.id}>
            <Link href={`/alerts/${a.id}`} className="hover:underline">
              📍 [{a.topRegion ?? "지역미상"}] {formatKST(a.createdAt)}
              {a.disasterType ? ` - ${a.disasterType}` : ""} • {a.message}
            </Link>
          </li>
        ))}

        {!isLoading && !isError && (!data || data.length === 0) && (
          <li className="text-gray-500">최근 재난 문자가 없습니다.</li>
        )}
      </ul>
    </div>
  );
}

function formatKST(iso: string) {
  const d = new Date(iso);
  return d.toLocaleString("ko-KR", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
}