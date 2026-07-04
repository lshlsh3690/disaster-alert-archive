// frontend/src/components/Header.tsx
"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useAuthStore } from "@/store/authStore";
import { useLanguageStore } from "@/store/languageStore";
import { useEffect, useRef, useState } from "react";
import { logoutApi } from "@/api/authApi";
import { deleteGuestFcmToken } from "@/api/guestFcmApi";
import { useRouter } from "next/navigation";
import { useInitAuth } from "@/hooks/useInitAuth";
import { LANGUAGES, LangCode } from "@/constants/language";
import { useI18n } from "@/hooks/useI18n";
import { useForegroundMessage } from "@/hooks/useForegroundMessage";

export default function Header() {
  useForegroundMessage();
  const router = useRouter();
  const pathname = usePathname();

  useInitAuth();

  const isLoggedIn = useAuthStore((state) => state.user !== null);
  const logout = useAuthStore((state) => state.logout);
  const nickname = useAuthStore((state) => state.user?.nickname);

  const language = useLanguageStore((state) => state.language);
  const setLanguage = useLanguageStore((state) => state.setLanguage);

  const [open, setOpen] = useState<boolean>(false);
  const [mobileMenuOpen, setMobileMenuOpen] = useState<boolean>(false);
  const dropdownRef = useRef<HTMLDivElement>(null);

  const t = useI18n();

  const menu = [
    { name: t.nav.dashboard, href: "/" },
    { name: t.nav.alerts, href: "/alerts" },
    { name: t.nav.events, href: "/events" },
    { name: t.nav.stats, href: "/stats" },
    { name: t.nav.community, href: "/community" },
  ];

  const isActive = (href: string) => {
    const matches = (h: string) => pathname === h || pathname.startsWith(h + "/");
    if (!matches(href)) return false;
    const hasMoreSpecific = menu.some((m) => m.href.length > href.length && matches(m.href));
    return !hasMoreSpecific;
  };

  const handleLangChange = (lang: LangCode) => {
    setLanguage(lang);
    // 로그인 상태면 추후 DB 저장 API 호출 추가
  };

  const handleLogout = () => {
    logoutApi()
      .then(() => {
        logout();
        const token = localStorage.getItem("fcm-token");
        localStorage.removeItem("fcm-token");
        if (token) deleteGuestFcmToken(token);
        setOpen(false);
        router.push("/");
      })
      .catch((error) => {
        console.error("로그아웃 실패:", error);
      });
  };

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  return (
    <header className="bg-[var(--surface)] border-b border-[var(--line)] px-4 sm:px-6 py-3">
      <div className="flex items-center justify-between">
        {/* 왼쪽: 로고 + 언어 선택 */}
        <div className="flex items-center gap-3">
          <Link href="/" className="flex items-center gap-2">
            <span className="text-xl font-bold text-[var(--blue)] tracking-tight">{t.appName}</span>
          </Link>
          <select
            value={language}
            onChange={(e) => handleLangChange(e.target.value as LangCode)}
            className="text-sm border border-[var(--line)] rounded-[var(--radius-control)] px-2 py-1.5 text-[var(--text-body)] bg-[var(--surface)] cursor-pointer hover:border-[var(--blue)] focus:outline-none focus:border-[var(--blue)] focus:ring-2 focus:ring-[var(--blue-soft)]"
          >
            {LANGUAGES.map(({ code, label }) => (
              <option key={code} value={code}>{label}</option>
            ))}
          </select>
        </div>

        {/* 데스크톱 네비게이션 */}
        <nav className="hidden md:flex items-center gap-1 text-sm">
          {menu.map(({ name, href }) => (
            <Link
              key={href}
              href={href}
              className={`px-3 py-2 rounded-[var(--radius-control)] transition-colors hover:text-[var(--blue)] hover:bg-[var(--blue-soft)] ${
                isActive(href) ? "font-semibold text-[var(--blue)] bg-[var(--blue-soft)]" : "text-[var(--text-body)]"
              }`}
            >
              {name}
            </Link>
          ))}

          <span className="mx-1 h-5 w-px bg-[var(--line)]" aria-hidden="true"></span>

          {isLoggedIn ? (
            <>
              <Link
                href="/user/settings/regions"
                className="px-3 py-2 rounded-[var(--radius-control)] text-[var(--text-muted)] transition-colors hover:text-[var(--blue)] hover:bg-[var(--blue-soft)]"
              >
                {t.nav.favoriteRegions}
              </Link>
              <div className="relative" ref={dropdownRef}>
                <button
                  onClick={() => setOpen((prev) => !prev)}
                  className="px-3 py-2 rounded-[var(--radius-control)] font-medium text-[var(--blue)] transition-colors hover:bg-[var(--blue-soft)]"
                >
                  {nickname ?? t.nav.user} ▾
                </button>
                {open && (
                  <div className="absolute right-0 mt-2 w-44 bg-[var(--surface)] border border-[var(--line)] rounded-[var(--radius-compact)] shadow-[0_10px_30px_rgba(28,39,60,0.08)] text-sm z-50 overflow-hidden">
                    <Link href="/notifications" className="block px-4 py-2.5 text-[var(--text-body)] hover:bg-[var(--blue-soft)] hover:text-[var(--blue)]" onClick={() => setOpen(false)}>
                      알림 이력
                    </Link>
                    <Link href="/user/settings" className="block px-4 py-2.5 text-[var(--text-body)] hover:bg-[var(--blue-soft)] hover:text-[var(--blue)]" onClick={() => setOpen(false)}>
                      {t.nav.settings}
                    </Link>
                    <button onClick={handleLogout} className="w-full text-left px-4 py-2.5 text-[var(--coral)] hover:bg-[var(--coral-soft)]">
                      {t.nav.logout}
                    </button>
                  </div>
                )}
              </div>
            </>
          ) : (
            <>
              <Link
                href="/user/settings/regions"
                className="px-3 py-2 rounded-[var(--radius-control)] text-[var(--text-muted)] transition-colors hover:text-[var(--blue)] hover:bg-[var(--blue-soft)]"
              >
                {t.nav.favoriteRegions}
              </Link>
              <Link href="/login" className="px-3 py-2 rounded-[var(--radius-control)] font-medium text-[var(--blue)] transition-colors hover:bg-[var(--blue-soft)]">
                {t.nav.login}
              </Link>
            </>
          )}
        </nav>

        {/* 모바일 햄버거 버튼 */}
        <button
          className="md:hidden p-2 rounded-[var(--radius-control)] text-[var(--text-muted)] hover:bg-[var(--blue-soft)] hover:text-[var(--blue)]"
          onClick={() => setMobileMenuOpen((prev) => !prev)}
          aria-label="메뉴 열기"
        >
          {mobileMenuOpen ? (
            <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          ) : (
            <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6h16M4 12h16M4 18h16" />
            </svg>
          )}
        </button>
      </div>

      {/* 모바일 드롭다운 메뉴 */}
      {mobileMenuOpen && (
        <nav className="md:hidden mt-3 pb-2 border-t border-[var(--line)] pt-3 space-y-1 text-sm">
          {menu.map(({ name, href }) => (
            <Link
              key={href}
              href={href}
              className={`block px-3 py-2.5 rounded-[var(--radius-control)] hover:bg-[var(--blue-soft)] hover:text-[var(--blue)] ${
                isActive(href) ? "font-semibold text-[var(--blue)] bg-[var(--blue-soft)]" : "text-[var(--text-body)]"
              }`}
              onClick={() => setMobileMenuOpen(false)}
            >
              {name}
            </Link>
          ))}
          <div className="flex items-center gap-2 px-3 py-2.5">
            <span className="text-[var(--text-muted)]">{t.dashboard.langLabel}:</span>
            <select
              value={language}
              onChange={(e) => handleLangChange(e.target.value as LangCode)}
              className="text-sm border border-[var(--line)] rounded-[var(--radius-control)] px-2 py-1 text-[var(--text-body)] bg-[var(--surface)]"
            >
              {LANGUAGES.map(({ code, label }) => (
                <option key={code} value={code}>{label}</option>
              ))}
            </select>
          </div>
          {isLoggedIn ? (
            <>
              <Link href="/notifications" className="block px-3 py-2.5 rounded-[var(--radius-control)] text-[var(--text-body)] hover:bg-[var(--blue-soft)] hover:text-[var(--blue)]" onClick={() => setMobileMenuOpen(false)}>
                알림 이력
              </Link>
              <Link href="/user/settings/regions" className="block px-3 py-2.5 rounded-[var(--radius-control)] text-[var(--text-body)] hover:bg-[var(--blue-soft)] hover:text-[var(--blue)]" onClick={() => setMobileMenuOpen(false)}>
                {t.nav.favoriteRegions}
              </Link>
              <Link href="/user/settings" className="block px-3 py-2.5 rounded-[var(--radius-control)] text-[var(--text-body)] hover:bg-[var(--blue-soft)] hover:text-[var(--blue)]" onClick={() => setMobileMenuOpen(false)}>
                {t.nav.settings}
              </Link>
              <button
                onClick={() => { handleLogout(); setMobileMenuOpen(false); }}
                className="block w-full text-left px-3 py-2.5 rounded-[var(--radius-control)] text-[var(--coral)] hover:bg-[var(--coral-soft)]"
              >
                {t.nav.logout}
              </button>
            </>
          ) : (
            <>
              <Link href="/user/settings/regions" className="block px-3 py-2.5 rounded-[var(--radius-control)] text-[var(--text-body)] hover:bg-[var(--blue-soft)] hover:text-[var(--blue)]" onClick={() => setMobileMenuOpen(false)}>
                {t.nav.favoriteRegions}
              </Link>
              <Link href="/login" className="block px-3 py-2.5 rounded-[var(--radius-control)] text-[var(--blue)] font-medium hover:bg-[var(--blue-soft)]" onClick={() => setMobileMenuOpen(false)}>
                {t.nav.login}
              </Link>
            </>
          )}
        </nav>
      )}
    </header>
  );
}
