"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useAuthStore } from "@/store/authStore";
import { useEffect, useRef, useState } from "react";
import { logoutApi } from "@/api/authApi";

export default function Header() {
  const pathname = usePathname();
  const isLoggedIn = useAuthStore((state) => state.accessToken !== null);
  const logout = useAuthStore((state) => state.logout);
  const [open, setOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);
  const nickname = useAuthStore((state) => state.nickname);

  const menu = [
    { name: "대시보드", href: "/dashboard" },
    { name: "재난 문자", href: "/disasters" },
    { name: "통계", href: "/stats" },
    { name: "커뮤니티", href: "/community" },
  ];

  const handleLogout = () => {
    logoutApi()
      .then(() => {
        console.log("로그아웃 성공");
        logout();
        setOpen(false);
      })
      .catch((error) => {
        console.error("로그아웃 실패:", error);
      });
  };

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (
        dropdownRef.current &&
        !dropdownRef.current.contains(event.target as Node)
      ) {
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
            className={`hover:text-blue-600 ${
              pathname.startsWith(href) ? "font-bold text-blue-600" : ""
            }`}
          >
            {name}
          </Link>
        ))}

        {isLoggedIn ? (
          // <div className="relative" ref={dropdownRef}>
          //   <button
          //     onClick={() => setOpen(!open)}
          //     className="bg-gray-200 px-3 py-1 rounded hover:bg-gray-300"
          //   >
          //     내 계정 ⌄
          //   </button>
          //   {open && (
          //     <div className="absolute right-0 mt-2 w-40 bg-white border rounded shadow z-10">
          //       <Link
          //         href="/user/me"
          //         className="block px-4 py-2 hover:bg-gray-100 text-sm"
          //         onClick={() => setOpen(false)}
          //       >
          //         내 정보
          //       </Link>
          //       <Link
          //         href="/user/settings"
          //         className="block px-4 py-2 hover:bg-gray-100 text-sm"
          //         onClick={() => setOpen(false)}
          //       >
          //         설정
          //       </Link>
          //       <button
          //         onClick={handleLogout}
          //         className="w-full text-left px-4 py-2 hover:bg-red-100 text-sm text-red-600"
          //       >
          //         로그아웃
          //       </button>
          //     </div>
          //   )}
          // </div>
          <div className="relative" ref={dropdownRef}>
            <button
              onClick={() => setOpen(!open)}
              className="flex items-center gap-2 bg-gray-100 px-3 py-1 rounded hover:bg-gray-200"
            >
              <span className="text-sm font-medium text-gray-800">
                {nickname}
              </span>
              {/* 아이콘 대신 이미지도 가능 */}
              <svg
                className="w-4 h-4 text-gray-600"
                fill="none"
                stroke="currentColor"
                strokeWidth={2}
                viewBox="0 0 24 24"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  d="M19 9l-7 7-7-7"
                />
              </svg>
            </button>

            {open && (
              <div className="absolute right-0 mt-2 w-40 bg-white border rounded shadow z-10">
                <Link
                  href="/user/me"
                  className="block px-4 py-2 hover:bg-gray-100 text-sm"
                >
                  내 정보
                </Link>
                <Link
                  href="/user/settings"
                  className="block px-4 py-2 hover:bg-gray-100 text-sm"
                >
                  설정
                </Link>
                <button
                  onClick={handleLogout}
                  className="w-full text-left px-4 py-2 hover:bg-red-100 text-sm text-red-600"
                >
                  로그아웃
                </button>
              </div>
            )}
          </div>
        ) : (
          <Link
            href="/login"
            className="bg-blue-600 text-white px-3 py-1 rounded hover:bg-blue-700 text-sm"
          >
            로그인
          </Link>
        )}
      </nav>
    </header>
  );
}
