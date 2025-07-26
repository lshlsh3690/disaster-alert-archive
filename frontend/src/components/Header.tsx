// import React from "react";

// export default function Header() {
//   return (
//     <header className="w-full bg-white shadow-sm flex justify-between items-center px-8 py-4 sticky top-0 z-10">
//       <div className="font-bold text-2xl text-blue-700 tracking-tight">재난 안전문자 플랫폼</div>
//       <nav className="space-x-6 text-gray-700 text-base font-medium">
//         <a href="#" className="hover:text-blue-600 transition">통계</a>
//         <span className="text-gray-300">|</span>
//         <a href="#" className="hover:text-blue-600 transition">재난 사고 제보하기</a>
//       </nav>
//       <button className="px-4 py-2 bg-blue-600 text-white rounded-lg font-semibold shadow hover:bg-blue-700 transition">로그인</button>
//     </header>
//   );
// } 

'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'

export default function Header() {
  const pathname = usePathname()

  const menu = [
    { name: '대시보드', href: '/dashboard' },
    { name: '재난 문자', href: '/disasters' },
    { name: '통계', href: '/stats' },
    { name: '커뮤니티', href: '/community' }
  ]

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
              pathname.startsWith(href) ? 'font-bold text-blue-600' : ''
            }`}
          >
            {name}
          </Link>
        ))}
        <Link
          href="/login"
          className="bg-blue-600 text-white px-3 py-1 rounded hover:bg-blue-700 text-sm"
        >
          로그인
        </Link>
      </nav>
    </header>
  )
}
