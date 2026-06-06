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
    { name: t.nav.dashboard, href: "/dashboard" },
    { name: t.nav.alerts, href: "/alerts" },
    { name: t.nav.stats, href: "/stats" },
    { name: t.nav.community, href: "/community" },
  ];

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
    <header className="bg-white shadow px-4 sm:px-6 py-3">
      <div className="flex items-center justify-between">
        <Link href="/" className="text-xl font-bold text-blue-500">
          {t.appName}
        </Link>

        {/* 데스크톱 네비게이션 */}
        <nav className="hidden md:flex items-center gap-4 text-sm text-gray-700">
          {menu.map(({ name, href }) => (
            <Link
              key={href}
              href={href}
              className={`hover:text-blue-600 ${pathname.startsWith(href) ? "font-bold text-blue-600" : ""}`}
            >
              {name}
            </Link>
          ))}
          <div className="flex items-center gap-1">
            <span className="text-sm text-gray-500">{t.dashboard.langLabel}:</span>
            <select
              value={language}
              onChange={(e) => handleLangChange(e.target.value as LangCode)}
              className="text-sm border border-gray-300 rounded px-2 py-1 text-gray-700 bg-white cursor-pointer hover:border-blue-400 focus:outline-none focus:border-blue-500"
            >
              {LANGUAGES.map(({ code, label }) => (
                <option key={code} value={code}>{label}</option>
              ))}
            </select>
          </div>
          {isLoggedIn ? (
            <div className="flex items-center gap-3">
              <Link href="/user/settings/regions" className="text-sm text-gray-600 hover:text-blue-600 hover:underline">
                {t.nav.favoriteRegions}
              </Link>
              <div className="relative" ref={dropdownRef}>
              <button
                onClick={() => setOpen((prev) => !prev)}
                className="text-sm font-medium text-blue-600 hover:underline"
              >
                {nickname ?? t.nav.user} ▾
              </button>
              {open && (
                <div className="absolute right-0 mt-2 w-40 bg-white border rounded shadow text-sm z-50">
                  <Link href="/notifications" className="block px-4 py-2 hover:bg-gray-50" onClick={() => setOpen(false)}>
                    알림 이력
                  </Link>
                  <Link href="/user/settings" className="block px-4 py-2 hover:bg-gray-50" onClick={() => setOpen(false)}>
                    {t.nav.settings}
                  </Link>
                  <button onClick={handleLogout} className="w-full text-left px-4 py-2 hover:bg-gray-50 text-red-500">
                    {t.nav.logout}
                  </button>
                </div>
              )}
              </div>
            </div>
          ) : (
            <div className="flex items-center gap-3">
              <Link href="/user/settings/regions" className="text-sm text-gray-600 hover:text-blue-600 hover:underline">
                {t.nav.favoriteRegions}
              </Link>
              <Link href="/login" className="text-blue-600 hover:underline font-medium">
                {t.nav.login}
              </Link>
            </div>
          )}
        </nav>

        {/* 모바일 햄버거 버튼 */}
        <button
          className="md:hidden p-2 rounded text-gray-600 hover:bg-gray-100"
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
        <nav className="md:hidden mt-3 pb-2 border-t pt-3 space-y-1 text-sm text-gray-700">
          {menu.map(({ name, href }) => (
            <Link
              key={href}
              href={href}
              className={`block px-2 py-2 rounded hover:bg-gray-50 hover:text-blue-600 ${pathname.startsWith(href) ? "font-bold text-blue-600" : ""}`}
              onClick={() => setMobileMenuOpen(false)}
            >
              {name}
            </Link>
          ))}
          <div className="flex items-center gap-2 px-2 py-2">
            <span className="text-gray-500">{t.dashboard.langLabel}:</span>
            <select
              value={language}
              onChange={(e) => handleLangChange(e.target.value as LangCode)}
              className="text-sm border border-gray-300 rounded px-2 py-1 text-gray-700 bg-white"
            >
              {LANGUAGES.map(({ code, label }) => (
                <option key={code} value={code}>{label}</option>
              ))}
            </select>
          </div>
          {isLoggedIn ? (
            <>
              <Link href="/notifications" className="block px-2 py-2 rounded hover:bg-gray-50" onClick={() => setMobileMenuOpen(false)}>
                알림 이력
              </Link>
              <Link href="/user/settings/regions" className="block px-2 py-2 rounded hover:bg-gray-50" onClick={() => setMobileMenuOpen(false)}>
                {t.nav.favoriteRegions}
              </Link>
              <Link href="/user/settings" className="block px-2 py-2 rounded hover:bg-gray-50" onClick={() => setMobileMenuOpen(false)}>
                {t.nav.settings}
              </Link>
              <button
                onClick={() => { handleLogout(); setMobileMenuOpen(false); }}
                className="block w-full text-left px-2 py-2 rounded hover:bg-gray-50 text-red-500"
              >
                {t.nav.logout}
              </button>
            </>
          ) : (
            <>
              <Link href="/user/settings/regions" className="block px-2 py-2 rounded hover:bg-gray-50" onClick={() => setMobileMenuOpen(false)}>
                {t.nav.favoriteRegions}
              </Link>
              <Link href="/login" className="block px-2 py-2 rounded hover:bg-gray-50 text-blue-600 font-medium" onClick={() => setMobileMenuOpen(false)}>
                {t.nav.login}
              </Link>
            </>
          )}
        </nav>
      )}
    </header>
  );
}
