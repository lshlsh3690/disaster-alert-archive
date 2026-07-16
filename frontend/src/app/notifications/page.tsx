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
import { useI18n } from "@/hooks/useI18n";
import { useLanguageStore } from "@/store/languageStore";

const LEVEL_BADGE: Record<string, string> = {
  안전안내: "bg-[var(--blue-soft)] text-[var(--blue)]",
  긴급재난: "bg-[#fff1e3] text-[#c1670c]",
  위급재난: "bg-[var(--coral-soft)] text-[#c0473b]",
};

const LANG_LOCALE: Record<string, string> = { ko: "ko-KR", en: "en-US", zh: "zh-CN", ja: "ja-JP" };

function formatDate(dateStr: string, locale: string) {
  const d = new Date(dateStr);
  return d.toLocaleString(locale, {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
}

export default function NotificationsPage() {
  const t = useI18n();
  const locale = LANG_LOCALE[useLanguageStore((s) => s.language)] ?? "ko-KR";
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
    <main className="bg-[var(--canvas)] min-h-[calc(100vh-48px)]">
      <div className="mx-auto max-w-2xl px-4 py-8 space-y-4">
      <header className="flex items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-[var(--ink)]">{t("notificationHistory.title")}</h1>
          <p className="mt-1 text-[13px] text-[var(--text-muted)]">{t("notificationHistory.description")}</p>
        </div>
        {data && data.unreadCount > 0 && (
          <span className="shrink-0 rounded-[var(--radius-pill)] bg-[var(--coral)] px-2.5 py-1 text-xs font-bold text-white">
            {t("notificationHistory.unread")} {data.unreadCount}
          </span>
        )}
      </header>

      {loading ? (
        <div className="flex justify-center py-16 text-[var(--text-subtle)]">{t("loading")}</div>
      ) : !data || data.content.length === 0 ? (
        <div className="rounded-[var(--radius-panel-card)] border border-[var(--line)] bg-[var(--surface)] p-10 text-center">
          <svg className="mx-auto mb-3 text-[var(--text-subtle)]" width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9" />
            <path d="M10.3 21a1.94 1.94 0 0 0 3.4 0" />
          </svg>
          <p className="font-medium text-[var(--text-body)]">{t("notificationHistory.empty")}</p>
          <p className="mt-1 text-[13px] text-[var(--text-muted)]">{t("notificationHistory.emptyDesc")}</p>
        </div>
      ) : (
        <>
          <ul className="space-y-2">
            {data.content.map((item) => (
              <li key={item.id}>
                <button
                  onClick={() => handleRead(item)}
                  className={`w-full rounded-[var(--radius-card)] border border-l-4 bg-[var(--surface)] px-4 py-3 text-left shadow-[0_10px_30px_rgba(28,39,60,0.04)] transition-shadow hover:shadow-[0_12px_28px_rgba(28,39,60,0.08)] focus:outline-none focus-visible:ring-2 focus-visible:ring-[var(--blue-soft)] ${
                    item.isRead ? "border-[var(--line)] border-l-[var(--line)]" : "border-[var(--line)] border-l-[var(--coral)]"
                  }`}
                >
                  <div className="flex items-start justify-between gap-2">
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 mb-1 flex-wrap">
                        {item.emergencyLevel && (
                          <span
                            className={`text-[11px] font-semibold px-2 py-0.5 rounded-[var(--radius-pill)] ${
                              LEVEL_BADGE[item.emergencyLevel] ?? "bg-[#eef1f5] text-[var(--text-muted)]"
                            }`}
                          >
                            {t(`levels.${item.emergencyLevel}`, { defaultValue: item.emergencyLevel })}
                          </span>
                        )}
                        {item.disasterType && (
                          <span className="text-xs text-[var(--text-muted)]">{t(`disasterTypes.${item.disasterType}`, { defaultValue: item.disasterType })}</span>
                        )}
                        {!item.isRead && (
                          <span className="text-[11px] font-bold text-[var(--coral)]">NEW</span>
                        )}
                      </div>
                      <p className="text-[13px] text-[var(--text-body)] leading-snug line-clamp-2">
                        {item.message ?? t("notificationHistory.noMessage")}
                      </p>
                      {item.originalRegion && (
                        <p className="text-xs text-[var(--text-subtle)] mt-1 truncate">{item.originalRegion}</p>
                      )}
                    </div>
                    <time className="text-xs text-[var(--text-subtle)] shrink-0 mt-0.5">
                      {formatDate(item.sentAt, locale)}
                    </time>
                  </div>
                  <div className="mt-2">
                    <Link
                      href={`/alerts/${item.alertId}`}
                      onClick={(e) => e.stopPropagation()}
                      className="text-xs font-medium text-[var(--blue)] hover:underline"
                    >
                      {t("notificationHistory.viewDetail")}
                    </Link>
                  </div>
                </button>
              </li>
            ))}
          </ul>

          {data.totalPages > 1 && (
            <div className="flex items-center justify-center gap-2 pt-2">
              <button
                onClick={() => setCurrentPage((p) => Math.max(0, p - 1))}
                disabled={currentPage === 0}
                className="rounded-[var(--radius-control)] border border-[var(--line)] bg-[var(--surface)] px-3 py-1.5 text-sm text-[var(--text-body)] transition-colors hover:bg-[var(--blue-soft)] disabled:opacity-40 disabled:hover:bg-[var(--surface)]"
              >
                {t("notificationHistory.prev")}
              </button>
              <span className="px-3 py-1 text-sm text-[var(--text-muted)]">
                {currentPage + 1} / {data.totalPages}
              </span>
              <button
                onClick={() => setCurrentPage((p) => Math.min(data.totalPages - 1, p + 1))}
                disabled={currentPage >= data.totalPages - 1}
                className="rounded-[var(--radius-control)] border border-[var(--line)] bg-[var(--surface)] px-3 py-1.5 text-sm text-[var(--text-body)] transition-colors hover:bg-[var(--blue-soft)] disabled:opacity-40 disabled:hover:bg-[var(--surface)]"
              >
                {t("notificationHistory.next")}
              </button>
            </div>
          )}
        </>
      )}
      </div>
    </main>
  );
}
