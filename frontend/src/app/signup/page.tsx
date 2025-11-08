"use client";

import OAuthButton from "@components/OAuthButton";
import Link from "next/link";
import SignupForm from "./SignupForm";

export default function SignupPage() {
  return (
    <main className="min-h-screen flex flex-col items-center justify-center p-4 bg-gray-50">
      <div className="w-full max-w-md bg-white rounded-xl shadow p-6 space-y-4">
        <h1 className="text-2xl font-bold text-center">👤 회원가입</h1>
        <SignupForm/>

        <p className="text-sm text-center text-gray-600">
          이미 계정이 있으신가요?{" "}
          <Link
            href="/login"
            className="text-blue-600 hover:underline font-medium"
          >
            로그인
          </Link>
        </p>

        <div className="border-t pt-4 space-y-3">
          <p className="text-sm text-center text-gray-500">간편 로그인</p>
          <OAuthButton provider="google" />
          <OAuthButton provider="kakao" />
          <OAuthButton provider="naver" />
        </div>
      </div>
    </main>
  );
}
