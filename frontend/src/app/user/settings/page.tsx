"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuthStore } from "@/store/authStore";

export default function AccountSettingsPage() {
  const router = useRouter();
  const user = useAuthStore((s) => s.user);

  useEffect(() => {
    if (!user) router.push("/login");
  }, [user, router]);

  if (!user) return null;

  return (
    <main className="p-6 space-y-6 max-w-3xl mx-auto">
      <header>
        <h1 className="text-2xl font-bold text-gray-900">⚙️ 계정 설정</h1>
        <p className="text-sm text-gray-500 mt-1">계정 정보를 확인하고 관리합니다.</p>
      </header>

      {/* 프로필 정보 */}
      <section className="bg-white rounded-xl shadow p-6 space-y-4">
        <h2 className="text-lg font-semibold text-gray-800">프로필 정보</h2>

        <InfoRow label="이메일" value={user.email ?? "-"} />
        <InfoRow label="닉네임" value={user.nickname ?? "-"} />
      </section>

      {/* 계정 관리 */}
      <section className="bg-white rounded-xl shadow p-6 space-y-3">
        <h2 className="text-lg font-semibold text-gray-800">계정 관리</h2>

        <button
          className="w-full text-left px-4 py-3 border border-gray-200 rounded-lg hover:bg-gray-50 transition"
          onClick={() => alert("비밀번호 변경 기능은 준비 중입니다.")}
        >
          <div className="font-medium text-gray-800">비밀번호 변경</div>
          <div className="text-xs text-gray-500 mt-1">계정 비밀번호를 변경합니다.</div>
        </button>

        <button
          className="w-full text-left px-4 py-3 border border-red-200 rounded-lg hover:bg-red-50 transition"
          onClick={() => alert("회원 탈퇴 기능은 준비 중입니다.")}
        >
          <div className="font-medium text-red-500">회원 탈퇴</div>
          <div className="text-xs text-gray-500 mt-1">계정과 모든 데이터를 삭제합니다.</div>
        </button>
      </section>
    </main>
  );
}

function InfoRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between py-2 border-b border-gray-100 last:border-0">
      <span className="text-sm text-gray-500">{label}</span>
      <span className="text-sm text-gray-800 font-medium">{value}</span>
    </div>
  );
}