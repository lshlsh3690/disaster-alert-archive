// frontend/src/components/Header.tsx
"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useAuthStore } from "@/store/authStore";
import { useEffect, useRef, useState } from "react";
import { logoutApi } from "@/api/authApi";
import { useRouter } from "next/navigation";
import { useInitAuth } from "@/hooks/useInitAuth";

export default function Header() {
  const router = useRouter();
  const pathname = usePathname();

  // OAuth/쿠키 기반 로그인 후 Zustand 동기화
  useInitAuth();

  const isLoggedIn = useAuthStore((state) => state.user !== null);
  const logout = useAuthStore((state) => state.logout);
  const [open, setOpen] = useState<boolean>(false);
  const dropdownRef = useRef<HTMLDivElement>(null);
  const nickname = useAuthStore((state) => state.user?.nickname);

  const menu = [
    { name: "대시보드", href: "/dashboard" },
    { name: "재난 문자", href: "/alerts" },
    { name: "커뮤니티", href: "/community" },
  ];

  const handleLogout = () => {
    logoutApi()
      .then(() => {
        logout();
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
    <header className="bg-white shadow px-6 py-3 flex items-center justify-between">
      <Link href="/" className="text-xl font-bold text-blue-500">
        재난 문자 아카이브
      </Link>
      <nav className="flex items-center gap-4 text-sm text-gray-700">
        {menu.map(({ name, href }) => (
          <Link
            key={href}
            href={href}
            className={`hover:text-blue-600 ${pathname.startsWith(href) ? "font-bold text-blue-600" : ""}`}
          >
            {name}
          </Link>
        ))}

        {isLoggedIn ? (
          <div className="relative" ref={dropdownRef}>
            <button
              onClick={() => setOpen((prev) => !prev)}
              className="text-sm font-medium text-blue-600 hover:underline"
            >
              {nickname ?? "사용자"} ▾
            </button>
            {open && (
              <div className="absolute right-0 mt-2 w-36 bg-white border rounded shadow text-sm z-50">
                <Link href="/user/settings" className="block px-4 py-2 hover:bg-gray-50" onClick={() => setOpen(false)}>
                  설정
                </Link>
                <button onClick={handleLogout} className="w-full text-left px-4 py-2 hover:bg-gray-50 text-red-500">
                  로그아웃
                </button>
              </div>
            )}
          </div>
        ) : (
          <Link href="/login" className="text-blue-600 hover:underline font-medium">
            로그인
          </Link>
        )}
      </nav>
    </header>
  );
}
