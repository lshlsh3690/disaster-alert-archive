import type { Metadata } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import "./globals.css";
import Header from "@components/Header";
import ReactQueryProvider from "@/lib/reactQueryProvider";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "My project",
  description: "Next.js + TypeScript 기반 개인 프로젝트",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ko">
      <body
        className={`${geistSans.variable} ${geistMono.variable} antialiased`}
      >
        <ReactQueryProvider>
          <Header />
          {children}
          <footer className="bg-gray-100 p-4 text-center text-sm">
            © 2024 재난 안전문자 플랫폼 | 문의: help@disaster-sms.com
          </footer>
        </ReactQueryProvider>
      </body>
    </html>
  );
}
