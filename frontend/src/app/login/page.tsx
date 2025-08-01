"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { login } from "@api/api"; // ✅ API 함수 가져오기
import { useAuthStore } from "store/auth";

export default function LoginPage() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();

    login(email, password)
      .then((data) => {
        useAuthStore.getState().setAuth({
          accessToken: data.accessToken,
          memberId: data.memberId,
          nickname: data.nickname,
          email: data.email,
        });
        router.push("/dashboard"); // 로그인 성공 후 대시보드로 이동
      })
      .catch((err) => {
        console.error("로그인 실패:", err);
        setError("로그인에 실패했습니다. 이메일과 비밀번호를 확인해주세요.");
      });
  };

  return (
    <main className="min-h-screen flex flex-col items-center justify-center p-4 bg-gray-50">
      <div className="w-full max-w-md bg-white rounded-xl shadow p-6 space-y-4">
        <h1 className="text-2xl font-bold text-center">🔐 로그인</h1>
        <form onSubmit={handleLogin} className="space-y-4">
          <input
            type="email"
            placeholder="이메일"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            className="w-full border rounded px-3 py-2"
          />
          <input
            type="password"
            placeholder="비밀번호"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className="w-full border rounded px-3 py-2"
          />
          {error && <p className="text-red-500 text-sm">{error}</p>}
          <button
            type="submit"
            className="w-full bg-blue-600 text-white py-2 rounded hover:bg-blue-700 transition"
          >
            로그인
          </button>
        </form>

        {/* 🔽 여기 추가! */}
        <p className="text-sm text-center text-gray-600">
          계정이 없으신가요?{" "}
          <Link
            href="/signup"
            className="text-blue-600 hover:underline font-medium"
          >
            회원가입
          </Link>
        </p>
      </div>
    </main>
  );
}
