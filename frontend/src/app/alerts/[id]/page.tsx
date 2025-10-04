"use client";

import { useAlert, useUserAlert } from "@/lib/queries/useAlerts";
import { useParams, useRouter, useSearchParams } from "next/navigation";
import { useDeleteUserAlert } from "@/lib/mutations/useDeleteUserAlert";
import { useAuthStore } from "@/store/authStore";

export default function AlertDetailPage() {
  const params = useParams<{ id: string }>();
  const sp = useSearchParams();
  const router = useRouter();
  const id = Number(params.id);
  const source = (sp.get("source") || "OFFICIAL").toUpperCase();
  const isUser = source === "USER";
  const { data: offData, isLoading: offLoading } = useAlert(isUser ? 0 : id);
  const { data: userData, isLoading: userLoading } = useUserAlert(isUser ? id : 0);
  const data = isUser ? userData : offData;
  const isLoading = isUser ? userLoading : offLoading;
  const { mutateAsync: deleteUser } = useDeleteUserAlert();
  const authUser = useAuthStore((s) => s.user);
  const canEdit = isUser && authUser && (
    authUser.role === "ADMIN" || authUser.memberId === (userData as any)?.createdById
  );

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
        <div className="text-sm text-gray-600">지역: {Array.isArray((data as any).regionNames) && (data as any).regionNames.length > 0 ? (data as any).regionNames.join(", ") : (data as any).originalRegion ?? "-"}</div>
        {!(isUser) && <div className="text-xs text-gray-400">SN: {(data as any).sn ?? "-"}</div>}
        {canEdit && (
          <div className="pt-2 flex gap-2">
            <button
              className="px-3 py-1 rounded bg-blue-600 text-white"
              onClick={() => router.push(`/alerts/${id}/edit?source=USER`)}
            >
              수정
            </button>
            <button
              className="px-3 py-1 rounded bg-red-600 text-white"
              onClick={async () => {
                if (!confirm("정말 삭제하시겠습니까?")) return;
                await deleteUser(id);
                router.push("/alerts?page=0");
              }}
            >
              삭제
            </button>
          </div>
        )}
      </div>
    </main>
  );
}
