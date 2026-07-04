// frontend/src/app/user/settings/page.tsx
"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuthStore } from "@/store/authStore";
import NotificationSettings from "@/components/notification/NotificationSettings";

export default function AccountSettingsPage() {
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
          <h1 className="text-2xl font-bold text-[var(--ink)] tracking-tight">계정 설정</h1>
          <p className="mt-1 text-[13px] text-[var(--text-muted)]">계정 정보를 확인하고 관리합니다.</p>
        </header>

        {/* 프로필 정보 */}
        <section className="space-y-3 rounded-[var(--radius-panel-card)] border border-[var(--line)] bg-[var(--surface)] p-6 shadow-[0_10px_30px_rgba(28,39,60,0.04)]">
          <h2 className="text-lg font-semibold text-[var(--ink)]">프로필 정보</h2>
          <InfoRow label="이메일" value={user.email ?? "-"} />
          <InfoRow label="닉네임" value={user.nickname ?? "-"} />
        </section>

        {/* 알림 설정 */}
        <section className="rounded-[var(--radius-panel-card)] border border-[var(--line)] bg-[var(--surface)] p-6 shadow-[0_10px_30px_rgba(28,39,60,0.04)]">
          <NotificationSettings />
        </section>

        {/* 계정 관리 */}
        <section className="space-y-3 rounded-[var(--radius-panel-card)] border border-[var(--line)] bg-[var(--surface)] p-6 shadow-[0_10px_30px_rgba(28,39,60,0.04)]">
          <h2 className="text-lg font-semibold text-[var(--ink)]">계정 관리</h2>
          <button
            type="button"
            className="w-full rounded-[var(--radius-card)] border border-[var(--line)] px-4 py-3 text-left transition-colors hover:bg-[var(--blue-soft)] hover:border-[var(--blue)] focus:outline-none focus-visible:ring-2 focus-visible:ring-[var(--blue-soft)]"
            onClick={() => alert("비밀번호 변경 기능은 준비 중입니다.")}
          >
            <div className="font-medium text-[var(--ink)]">비밀번호 변경</div>
            <div className="mt-1 text-xs text-[var(--text-muted)]">계정 비밀번호를 변경합니다.</div>
          </button>
          <button
            type="button"
            className="w-full rounded-[var(--radius-card)] border border-[#f3c7c1] px-4 py-3 text-left transition-colors hover:bg-[var(--coral-soft)] focus:outline-none focus-visible:ring-2 focus-visible:ring-[var(--coral-soft)]"
            onClick={() => alert("회원 탈퇴 기능은 준비 중입니다.")}
          >
            <div className="font-medium text-[var(--coral)]">회원 탈퇴</div>
            <div className="mt-1 text-xs text-[var(--text-muted)]">계정과 모든 데이터를 삭제합니다.</div>
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
