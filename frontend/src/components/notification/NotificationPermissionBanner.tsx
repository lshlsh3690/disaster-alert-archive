"use client";

import { useNotificationPermission } from "@/hooks/useNotificationPermission";
import { useAuthStore } from "@/store/authStore";

export default function NotificationPermissionBanner() {
  const user = useAuthStore((state) => state.user);
  const { permission, isLoading, requestPermission } = useNotificationPermission();

  // 로그인 안됐거나 이미 권한 있으면 배너 숨김
  if (!user || permission !== "default") return null;

  return (
    <div className="fixed bottom-4 left-4 right-4 z-50 bg-blue-600 text-white rounded-xl p-4 shadow-lg flex items-center justify-between gap-3">
      <div className="flex items-center gap-2">
        <span className="text-xl">🔔</span>
        <p className="text-sm font-medium">재난문자 알림을 받으시겠어요?</p>
      </div>
      <div className="flex gap-2 shrink-0">
        <button
          onClick={requestPermission}
          disabled={isLoading}
          className="bg-white text-blue-600 text-sm font-semibold px-3 py-1.5 rounded-lg"
        >
          {isLoading ? "처리중..." : "허용"}
        </button>
      </div>
    </div>
  );
}