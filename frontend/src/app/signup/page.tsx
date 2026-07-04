"use client";

import OAuthButton from "@/components/ui/OAuthButton";
import Link from "next/link";
import SignupForm from "./SignupForm";

export default function SignupPage() {
  return (
    <main className="flex min-h-[calc(100vh-48px)] flex-col items-center justify-center bg-[var(--canvas)] p-4">
      <div className="w-full max-w-md space-y-4 rounded-[var(--radius-panel-card)] border border-[var(--line)] bg-[var(--surface)] p-6 shadow-[0_10px_30px_rgba(28,39,60,0.04)]">
        <h1 className="text-center text-2xl font-bold tracking-tight text-[var(--ink)]">회원가입</h1>
        <SignupForm/>

        <p className="text-center text-[13px] text-[var(--text-muted)]">
          이미 계정이 있으신가요?{" "}
          <Link
            href="/login"
            className="font-medium text-[var(--blue)] hover:underline"
          >
            로그인
          </Link>
        </p>

        <div className="border-t border-[var(--line)] pt-4">
          <div className="mb-3 flex items-center gap-3">
            <span className="h-px flex-1 bg-[var(--line)]"></span>
            <span className="text-[13px] text-[var(--text-subtle)]">간편 로그인</span>
            <span className="h-px flex-1 bg-[var(--line)]"></span>
          </div>
          <div className="space-y-2.5">
            <OAuthButton provider="google" />
            <OAuthButton provider="kakao" />
            <OAuthButton provider="naver" />
          </div>
        </div>
      </div>
    </main>
  );
}
