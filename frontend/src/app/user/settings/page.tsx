// frontend/src/app/user/settings/page.tsx
"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuthStore } from "@/store/authStore";
import NotificationSettings from "@/components/notification/NotificationSettings";
import { useI18n } from "@/hooks/useI18n";

export default function AccountSettingsPage() {
  const t = useI18n();
  const router = useRouter();
  const user = useAuthStore((s) => s.user);

  useEffect(() => {
    if (!user) router.push("/login");
  }, [user, router]);

  if (!user) return null;

  return (
    <main className="bg-[var(--canvas)] min-h-[calc(100vh-48px)]">
      <div className="mx-auto max-w-3xl px-4 py-8 space-y-5">
        <header>
          <h1 className="text-2xl font-bold text-[var(--ink)] tracking-tight">{t("userSettings.title")}</h1>
          <p className="mt-1 text-[13px] text-[var(--text-muted)]">{t("userSettings.description")}</p>
        </header>

        {/* 프로필 정보 */}
        <section className="space-y-3 rounded-[var(--radius-panel-card)] border border-[var(--line)] bg-[var(--surface)] p-6 shadow-[0_10px_30px_rgba(28,39,60,0.04)]">
          <h2 className="text-lg font-semibold text-[var(--ink)]">{t("userSettings.profileInfo")}</h2>
          <InfoRow label={t("userSettings.email")} value={user.email ?? "-"} />
          <InfoRow label={t("userSettings.nickname")} value={user.nickname ?? "-"} />
        </section>

        {/* 알림 설정 */}
        <section className="rounded-[var(--radius-panel-card)] border border-[var(--line)] bg-[var(--surface)] p-6 shadow-[0_10px_30px_rgba(28,39,60,0.04)]">
          <NotificationSettings />
        </section>

        {/* 계정 관리 */}
        <section className="space-y-3 rounded-[var(--radius-panel-card)] border border-[var(--line)] bg-[var(--surface)] p-6 shadow-[0_10px_30px_rgba(28,39,60,0.04)]">
          <h2 className="text-lg font-semibold text-[var(--ink)]">{t("userSettings.accountManagement")}</h2>
          <button
            type="button"
            className="w-full rounded-[var(--radius-card)] border border-[var(--line)] px-4 py-3 text-left transition-colors hover:bg-[var(--blue-soft)] hover:border-[var(--blue)] focus:outline-none focus-visible:ring-2 focus-visible:ring-[var(--blue-soft)]"
            onClick={() => alert(t("userSettings.changePasswordComingSoon"))}
          >
            <div className="font-medium text-[var(--ink)]">{t("userSettings.changePassword")}</div>
            <div className="mt-1 text-xs text-[var(--text-muted)]">{t("userSettings.changePasswordDesc")}</div>
          </button>
          <button
            type="button"
            className="w-full rounded-[var(--radius-card)] border border-[#f3c7c1] px-4 py-3 text-left transition-colors hover:bg-[var(--coral-soft)] focus:outline-none focus-visible:ring-2 focus-visible:ring-[var(--coral-soft)]"
            onClick={() => alert(t("userSettings.deleteAccountComingSoon"))}
          >
            <div className="font-medium text-[var(--coral)]">{t("userSettings.deleteAccount")}</div>
            <div className="mt-1 text-xs text-[var(--text-muted)]">{t("userSettings.deleteAccountDesc")}</div>
          </button>
        </section>
      </div>
    </main>
  );
}

function InfoRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between gap-4 border-b border-[var(--line)] py-2 last:border-0">
      <span className="text-[13px] text-[var(--text-muted)]">{label}</span>
      <span className="min-w-0 truncate text-[13px] font-medium text-[var(--text-body)]">{value}</span>
    </div>
  );
}
