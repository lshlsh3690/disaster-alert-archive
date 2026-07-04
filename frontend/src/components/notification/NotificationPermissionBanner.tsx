"use client";

import { useState } from "react";
import { useNotificationPermission } from "@/hooks/useNotificationPermission";
import { useAuthStore } from "@/store/authStore";
import { useGuestFavoriteRegionsStore } from "@/store/guestFavoriteRegionsStore";

export default function NotificationPermissionBanner() {
  const user = useAuthStore((state) => state.user);
  const guestRegions = useGuestFavoriteRegionsStore((s) => s.regions);
  const { permission, isLoading, requestPermission } = useNotificationPermission();
  const [dismissed, setDismissed] = useState(false);

  const isLoggedIn = !!user;
  // 비로그인은 관심지역이 있을 때만 배너 표시
  const shouldShow = permission === "default" && (isLoggedIn || guestRegions.length > 0) && !dismissed;

  if (!shouldShow) return null;

  return (
    <div className="fixed bottom-4 left-4 right-4 z-50 bg-blue-600 text-white rounded-xl p-4 shadow-lg flex items-center justify-between gap-3">
      <div className="flex items-center gap-2">
        <span className="text-xl">🔔</span>
        <p className="text-sm font-medium">
          {isLoggedIn
            ? "재난문자 알림을 받으시겠어요?"
            : "관심지역 재난문자를 바로 받으시겠어요?"}
        </p>
      </div>
      <div className="flex items-center gap-2 shrink-0">
        <button
          onClick={requestPermission}
          disabled={isLoading}
          className="bg-white text-blue-600 text-sm font-semibold px-3 py-1.5 rounded-lg"
        >
          {isLoading ? "처리중..." : "허용"}
        </button>
        <button className="text-white/70 hover:text-white text-sm" onClick={() => setDismissed(true)} aria-label="닫기">
          ✕
        </button>
      </div>
    </div>
  );
}
