// frontend/src/components/Header.tsx
"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useAuthStore } from "@/store/authStore";
import { useLanguageStore } from "@/store/languageStore";
import { useEffect, useRef, useState } from "react";
import { logoutApi } from "@/api/authApi";
import { deleteFcmToken } from "@/api/fcmTokenApi";
import { useRouter } from "next/navigation";
import { useInitAuth } from "@/hooks/useInitAuth";
import { LANGUAGES, LangCode } from "@/constants/language";
import { useTranslation } from "react-i18next";
import { useForegroundMessage } from "@/hooks/useForegroundMessage";

export default function Header() {
  useForegroundMessage();
  const router = useRouter();
  const pathname = usePathname();

  useInitAuth();

  const isLoggedIn = useAuthStore((state) => state.user !== null);
  const logout = useAuthStore((state) => state.logout);
  const nickname = useAuthStore((state) => state.user?.nickname);
  // 관리자 전용 링크(클러스터링 검증 화면). 관리자만 보는 화면이라 문구는 한국어만 둔다
  const isAdmin = useAuthStore((state) => state.user?.role === "ADMIN");

  const language = useLanguageStore((state) => state.language);
  const setLanguage = useLanguageStore((state) => state.setLanguage);

  const [open, setOpen] = useState<boolean>(false);
  const [mobileMenuOpen, setMobileMenuOpen] = useState<boolean>(false);
  const dropdownRef = useRef<HTMLDivElement>(null);

  const { t } = useTranslation();

  const menu = [
    { name: t("nav.dashboard"), href: "/" },
    { name: t("nav.alerts"), href: "/alerts" },
    { name: t("nav.events"), href: "/events" },
    { name: t("nav.stats"), href: "/stats" },
    { name: t("nav.community"), href: "/community" },
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
    // 아직 인증이 살아있는 동안(로그아웃 API 호출 전) 회원 FCM 토큰을 먼저 삭제해야
    // DELETE /api/v1/fcm-token의 isAuthenticated() 검사를 통과한다.
    const token = localStorage.getItem("fcm-token");
    const cleanup = token ? deleteFcmToken(token) : Promise.resolve();
    cleanup
      .then(() => logoutApi())
      .then(() => {
        logout();
        localStorage.removeItem("fcm-token");
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

  const subLink =
    "px-[10px] py-2 rounded-[var(--radius-control)] whitespace-nowrap text-[15px] text-[var(--krds-gray-70)] transition-colors hover:text-[var(--krds-primary-50)]";

  return (
    <header className="relative z-40 bg-[var(--surface)] border-b border-[var(--line)] px-4 sm:px-8">
      <div className="flex items-center justify-between h-16 gap-4">
        {/* 왼쪽: 로고 + 서비스 이름. 누르면 대시보드로 */}
        <Link href="/" className="flex items-center gap-[10px] shrink-0">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src="/icons/apple-icon-180.png" alt="logo" className="w-8 h-8 rounded-full" />
          {/* nav 가 들어오는 lg~xl 구간에서는 이름을 숨겨 한 줄을 지킨다 */}
          <span className="lg:hidden xl:inline text-[19px] max-[359px]:text-base font-bold tracking-[-0.02em] text-[var(--ink)] whitespace-nowrap">
            {t("appName")}
          </span>
        </Link>

        {/* 데스크톱 네비게이션 */}
        {/* prefetch=false: Header는 모든 페이지에 항상 떠 있어 링크가 계속 뷰포트에 잡힘.
            "/", "/alerts"는 force-dynamic + 서버 prefetchQuery(axios)라 기본 prefetch가
            어느 페이지를 보든 백그라운드에서 그 데이터 페칭을 통째로 실행해버려 백엔드에
            불필요한 요청이 계속 발생했음(캐디 로그에서 axios UA 중복 호출로 확인). */}
        <nav className="hidden lg:flex items-stretch self-stretch gap-1 text-[17px] shrink-0">
          {menu.map(({ name, href }) => (
            <Link
              key={href}
              href={href}
              prefetch={false}
              aria-current={isActive(href) ? "page" : undefined}
              className={`flex items-center px-[10px] whitespace-nowrap transition-colors hover:text-[var(--krds-primary-50)] ${
                isActive(href)
                  ? "font-bold text-[var(--krds-primary-60)] shadow-[inset_0_-3px_0_var(--krds-primary-50)]"
                  : "text-[var(--krds-gray-70)]"
              }`}
            >
              {name}
            </Link>
          ))}
        </nav>

        {/* 오른쪽: 관심지역 · 언어 · 로그인(또는 사용자 메뉴) */}
        <div className="hidden lg:flex items-center gap-2 shrink-0">
          <Link href="/user/settings/regions" className={subLink}>
            {t("nav.favoriteRegions")}
          </Link>
          <select
            value={language}
            onChange={(e) => handleLangChange(e.target.value as LangCode)}
            aria-label={t("dashboard.langLabel")}
            className="px-[10px] py-2 bg-transparent text-[15px] text-[var(--krds-gray-70)] cursor-pointer rounded-[var(--radius-control)] hover:text-[var(--krds-primary-50)]"
          >
            {LANGUAGES.map(({ code, label }) => (
              <option key={code} value={code}>{label}</option>
            ))}
          </select>

          {isLoggedIn ? (
            <div className="relative" ref={dropdownRef}>
              <button
                onClick={() => setOpen((prev) => !prev)}
                aria-expanded={open}
                className="krds-btn krds-btn--secondary krds-btn--sm text-[15px]"
              >
                {nickname ?? t("nav.user")} ▾
              </button>
              {open && (
                <div className="absolute right-0 mt-2 w-48 bg-[var(--surface)] border border-[var(--line)] rounded-[var(--radius-compact)] text-[16px] z-50 overflow-hidden">
                  <Link href="/notifications" className="block px-4 py-2.5 text-[var(--text-body)] hover:bg-[var(--blue-soft)] hover:text-[var(--blue)]" onClick={() => setOpen(false)}>
                    {t("nav.notificationHistory")}
                  </Link>
                  <Link href="/user/settings" className="block px-4 py-2.5 text-[var(--text-body)] hover:bg-[var(--blue-soft)] hover:text-[var(--blue)]" onClick={() => setOpen(false)}>
                    {t("nav.settings")}
                  </Link>
                  {isAdmin && (
                    <Link href="/admin/clustering" className="block px-4 py-2.5 text-[var(--text-body)] hover:bg-[var(--blue-soft)] hover:text-[var(--blue)]" onClick={() => setOpen(false)}>
                      클러스터링 검증
                    </Link>
                  )}
                  <button onClick={handleLogout} className="w-full text-left px-4 py-2.5 text-[var(--coral)] hover:bg-[var(--coral-soft)]">
                    {t("nav.logout")}
                  </button>
                </div>
              )}
            </div>
          ) : (
            <Link href="/login" className="krds-btn krds-btn--primary krds-btn--sm text-[15px]">
              {t("nav.login")}
            </Link>
          )}
        </div>

        {/* 모바일 햄버거 버튼 */}
        <button
          className="lg:hidden p-2 rounded-[var(--radius-control)] text-[var(--text-muted)] hover:bg-[var(--blue-soft)] hover:text-[var(--blue)]"
          onClick={() => setMobileMenuOpen((prev) => !prev)}
          aria-label={t("nav.openMenu")}
          aria-expanded={mobileMenuOpen}
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
        <nav className="lg:hidden pb-3 border-t border-[var(--line)] pt-3 space-y-1 text-[16px]">
          {menu.map(({ name, href }) => (
            <Link
              key={href}
              href={href}
              prefetch={false}
              className={`block px-3 py-2.5 rounded-[var(--radius-control)] hover:bg-[var(--blue-soft)] hover:text-[var(--blue)] ${
                isActive(href) ? "font-bold text-[var(--krds-primary-60)] bg-[var(--blue-soft)]" : "text-[var(--text-body)]"
              }`}
              onClick={() => setMobileMenuOpen(false)}
            >
              {name}
            </Link>
          ))}
          <div className="flex items-center gap-2 px-3 py-2.5">
            <span className="text-[var(--text-muted)]">{t("dashboard.langLabel")}:</span>
            <select
              value={language}
              onChange={(e) => handleLangChange(e.target.value as LangCode)}
              className="text-[16px] border border-[var(--krds-gray-40)] rounded-[var(--radius-control)] px-2 py-1 text-[var(--text-body)] bg-[var(--surface)]"
            >
              {LANGUAGES.map(({ code, label }) => (
                <option key={code} value={code}>{label}</option>
              ))}
            </select>
          </div>
          {isLoggedIn ? (
            <>
              <Link href="/notifications" className="block px-3 py-2.5 rounded-[var(--radius-control)] text-[var(--text-body)] hover:bg-[var(--blue-soft)] hover:text-[var(--blue)]" onClick={() => setMobileMenuOpen(false)}>
                {t("nav.notificationHistory")}
              </Link>
              <Link href="/user/settings/regions" className="block px-3 py-2.5 rounded-[var(--radius-control)] text-[var(--text-body)] hover:bg-[var(--blue-soft)] hover:text-[var(--blue)]" onClick={() => setMobileMenuOpen(false)}>
                {t("nav.favoriteRegions")}
              </Link>
              <Link href="/user/settings" className="block px-3 py-2.5 rounded-[var(--radius-control)] text-[var(--text-body)] hover:bg-[var(--blue-soft)] hover:text-[var(--blue)]" onClick={() => setMobileMenuOpen(false)}>
                {t("nav.settings")}
              </Link>
              {isAdmin && (
                <Link href="/admin/clustering" className="block px-3 py-2.5 rounded-[var(--radius-control)] text-[var(--text-body)] hover:bg-[var(--blue-soft)] hover:text-[var(--blue)]" onClick={() => setMobileMenuOpen(false)}>
                  클러스터링 검증
                </Link>
              )}
              <button
                onClick={() => { handleLogout(); setMobileMenuOpen(false); }}
                className="block w-full text-left px-3 py-2.5 rounded-[var(--radius-control)] text-[var(--coral)] hover:bg-[var(--coral-soft)]"
              >
                {t("nav.logout")}
              </button>
            </>
          ) : (
            <>
              <Link href="/user/settings/regions" className="block px-3 py-2.5 rounded-[var(--radius-control)] text-[var(--text-body)] hover:bg-[var(--blue-soft)] hover:text-[var(--blue)]" onClick={() => setMobileMenuOpen(false)}>
                {t("nav.favoriteRegions")}
              </Link>
              <Link href="/login" className="block px-3 py-2.5 rounded-[var(--radius-control)] text-[var(--blue)] font-medium hover:bg-[var(--blue-soft)]" onClick={() => setMobileMenuOpen(false)}>
                {t("nav.login")}
              </Link>
            </>
          )}
        </nav>
      )}
    </header>
  );
}
