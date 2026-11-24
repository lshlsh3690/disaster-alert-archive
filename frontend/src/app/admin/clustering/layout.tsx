"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useAuthStore } from "@/store/authStore";
import Button from "@/components/ui/Button";

// 클러스터링 관리자 화면들(검증 대시보드, 평가 셋 라벨)의 공통 틀. 관리자 확인과 화면 사이 탭을 여기 한 곳에 둔다.
// ADMIN 이 아니면 안내 모달 뒤 다른 화면으로 보내고 요청을 보내지 않는다 — 각 화면(children)은 ADMIN 일 때만 마운트된다.

const TABS = [
  { href: "/admin/clustering", label: "클러스터링 검증" },
  { href: "/admin/clustering/eval-labels", label: "평가 셋 라벨" },
];

/**
 * 접근할 수 없을 때 띄우는 안내 모달. 확인을 누르면 target 으로 이동한다(뒤로 가기로 다시 오지 않게 replace).
 * 브라우저 기본 dialog 를 showModal() 로 열어 포커스를 모달 안에 가두고, 문구를 접근 가능한 이름으로 쓴다.
 * 닫고 머무를 화면이 없으므로 Esc 로는 닫지 않고, 브라우저가 강제로 닫으면(Chrome 의 Esc 연타) 다시 연다.
 */
function RedirectNotice({ message, target }: { message: string; target: string }) {
  const router = useRouter();
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const dialog = ref.current;
    if (dialog && !dialog.open) dialog.showModal();
    return () => {
      if (dialog?.open) dialog.close();
    };
  }, []);
  return (
    <dialog
      ref={ref}
      aria-labelledby="redirect-notice-message"
      onCancel={(e) => e.preventDefault()}
      onClose={(e) => {
        if (e.currentTarget.isConnected && !e.currentTarget.open) e.currentTarget.showModal();
      }}
      className="m-auto w-[calc(100%-2rem)] max-w-sm space-y-4 rounded-[var(--radius-panel-card)] border-0 bg-[var(--surface)] p-6 text-center shadow-[0_10px_30px_rgba(28,39,60,0.12)] backdrop:bg-black/40"
    >
      <p id="redirect-notice-message" className="text-[var(--ink)]">{message}</p>
      <Button fullWidth onClick={() => router.replace(target)} autoFocus>
        확인
      </Button>
    </dialog>
  );
}

/** 두 화면 사이를 오가는 탭. 지금 화면은 강조하고 aria-current 를 단다. */
function AdminTabs() {
  const pathname = usePathname();
  return (
    <nav className="flex gap-1 border-b border-[var(--line)]" aria-label="클러스터링 관리자 화면">
      {TABS.map((t) => {
        const active = pathname === t.href;
        return (
          <Link
            key={t.href}
            href={t.href}
            aria-current={active ? "page" : undefined}
            className={`px-3 py-2 text-sm -mb-px border-b-2 ${
              active
                ? "border-[var(--blue)] text-[var(--blue)] font-semibold"
                : "border-transparent text-[var(--text-muted)] hover:text-[var(--ink)]"
            }`}
          >
            {t.label}
          </Link>
        );
      })}
    </nav>
  );
}

export default function ClusteringAdminLayout({ children }: { children: React.ReactNode }) {
  const user = useAuthStore((s) => s.user);
  const isInitializing = useAuthStore((s) => s.isInitializing);
  const router = useRouter();

  // 이 화면을 보던 중 로그인 상태였다가 user 가 사라지면(로그아웃) 모달 없이 바로 홈으로 보낸다.
  // 처음부터 로그인 안 한 방문(딥링크)만 아래 안내 모달을 띄운다.
  // 초기화(서버 확인)가 끝난 뒤의 user 만 센다 — localStorage 에 남은 옛 user 로 세면, 세션 만료로 들어온
  // 딥링크도 "여기서 로그아웃"으로 오인해 로그인 안내 없이 홈으로 보낸다
  const [seenUser, setSeenUser] = useState(false);
  if (!isInitializing && user && !seenUser) setSeenUser(true);
  const loggedOutHere = seenUser && !user;
  useEffect(() => {
    if (loggedOutHere) router.replace("/");
  }, [loggedOutHere, router]);

  if (isInitializing) return null;

  // 이 화면에서 로그아웃한 경우 — 헤더가 홈으로 보내므로 안내 모달 없이 아무것도 그리지 않는다(위 effect 가 보조)
  if (!user && loggedOutHere) return null;

  // 로그인 안 함 → 로그인 화면으로, 관리자 아님 → 홈으로. 안내 모달에서 확인을 누르면 이동한다
  if (!user) {
    return <RedirectNotice message="로그인이 필요한 화면입니다." target="/login" />;
  }
  if (user.role !== "ADMIN") {
    return <RedirectNotice message="관리자만 볼 수 있는 화면입니다." target="/" />;
  }

  return (
    <main className="bg-[var(--canvas)] min-h-[calc(100vh-48px)]">
      <div className="mx-auto max-w-7xl px-4 py-6 space-y-4">
        <AdminTabs />
        {children}
      </div>
    </main>
  );
}
