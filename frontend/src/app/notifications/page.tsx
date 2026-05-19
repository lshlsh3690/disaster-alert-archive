"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { useAuthStore } from "@/store/authStore";
import {
  getMyNotificationLogs,
  markNotificationAsRead,
  NotificationLogItem,
  NotificationLogPage,
} from "@/api/notificationLogApi";

const LEVEL_BADGE: Record<string, string> = {
  안전안내: "bg-blue-100 text-blue-700",
  긴급재난: "bg-orange-100 text-orange-700",
  위급재난: "bg-red-100 text-red-700",
};

function formatDate(dateStr: string) {
  const d = new Date(dateStr);
  return d.toLocaleString("ko-KR", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
}

export default function NotificationsPage() {
  const router = useRouter();
  const user = useAuthStore((s) => s.user);

  const [data, setData] = useState<NotificationLogPage | null>(null);
  const [loading, setLoading] = useState(true);
  const [currentPage, setCurrentPage] = useState(0);

  useEffect(() => {
    if (!user) {
      router.push("/login");
    }
  }, [user, router]);

  useEffect(() => {
    if (!user) return;
    setLoading(true);
    getMyNotificationLogs(currentPage, 20)
      .then((res) => setData(res.data))
      .catch(() => setData(null))
      .finally(() => setLoading(false));
  }, [user, currentPage]);

  const handleRead = async (item: NotificationLogItem) => {
    if (item.isRead) return;
    await markNotificationAsRead(item.id);
    setData((prev) => {
      if (!prev) return prev;
      return {
        ...prev,
        unreadCount: Math.max(0, prev.unreadCount - 1),
        content: prev.content.map((n) =>
          n.id === item.id ? { ...n, isRead: true } : n
        ),
      };
    });
  };

  if (!user) return null;

  return (
    <main className="max-w-2xl mx-auto p-4 space-y-4">
      <header className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">알림 수신 이력</h1>
          <p className="text-sm text-gray-500 mt-1">내가 받은 푸시 알림 목록입니다.</p>
        </div>
        {data && data.unreadCount > 0 && (
          <span className="bg-red-500 text-white text-xs font-bold px-2 py-1 rounded-full">
            읽지 않음 {data.unreadCount}
          </span>
        )}
      </header>

      {loading ? (
        <div className="flex justify-center py-16 text-gray-400">불러오는 중...</div>
      ) : !data || data.content.length === 0 ? (
        <div className="bg-white rounded-xl shadow p-10 text-center text-gray-400">
          <p className="text-3xl mb-3">🔔</p>
          <p className="font-medium">수신된 알림이 없습니다.</p>
          <p className="text-sm mt-1">재난 알림을 구독하면 이곳에 기록이 남습니다.</p>
        </div>
      ) : (
        <>
          <ul className="space-y-2">
            {data.content.map((item) => (
              <li key={item.id}>
                <button
                  onClick={() => handleRead(item)}
                  className={`w-full text-left bg-white rounded-xl shadow px-4 py-3 transition hover:shadow-md border-l-4 ${
                    item.isRead ? "border-gray-200" : "border-blue-500"
                  }`}
                >
                  <div className="flex items-start justify-between gap-2">
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 mb-1 flex-wrap">
                        {item.emergencyLevel && (
                          <span
                            className={`text-xs font-semibold px-2 py-0.5 rounded-full ${
                              LEVEL_BADGE[item.emergencyLevel] ?? "bg-gray-100 text-gray-600"
                            }`}
                          >
                            {item.emergencyLevel}
                          </span>
                        )}
                        {item.disasterType && (
                          <span className="text-xs text-gray-500">{item.disasterType}</span>
                        )}
                        {!item.isRead && (
                          <span className="text-xs font-bold text-blue-600">NEW</span>
                        )}
                      </div>
                      <p className="text-sm text-gray-800 leading-snug line-clamp-2">
                        {item.message ?? "알림 내용 없음"}
                      </p>
                      {item.originalRegion && (
                        <p className="text-xs text-gray-400 mt-1 truncate">{item.originalRegion}</p>
                      )}
                    </div>
                    <time className="text-xs text-gray-400 shrink-0 mt-0.5">
                      {formatDate(item.sentAt)}
                    </time>
                  </div>
                  <div className="mt-2">
                    <Link
                      href={`/alerts/${item.alertId}`}
                      onClick={(e) => e.stopPropagation()}
                      className="text-xs text-blue-500 hover:underline"
                    >
                      상세 보기 →
                    </Link>
                  </div>
                </button>
              </li>
            ))}
          </ul>

          {data.totalPages > 1 && (
            <div className="flex justify-center gap-2 pt-2">
              <button
                onClick={() => setCurrentPage((p) => Math.max(0, p - 1))}
                disabled={currentPage === 0}
                className="px-3 py-1 text-sm border rounded disabled:opacity-40 hover:bg-gray-50"
              >
                이전
              </button>
              <span className="px-3 py-1 text-sm text-gray-600">
                {currentPage + 1} / {data.totalPages}
              </span>
              <button
                onClick={() => setCurrentPage((p) => Math.min(data.totalPages - 1, p + 1))}
                disabled={currentPage >= data.totalPages - 1}
                className="px-3 py-1 text-sm border rounded disabled:opacity-40 hover:bg-gray-50"
              >
                다음
              </button>
            </div>
          )}
        </>
      )}
    </main>
  );
}
