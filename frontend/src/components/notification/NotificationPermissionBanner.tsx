"use client";

import { useState } from "react";
import { useNotificationPermission } from "@/hooks/useNotificationPermission";
import { useAuthStore } from "@/store/authStore";
import { useGuestFavoriteRegionsStore } from "@/store/guestFavoriteRegionsStore";
import { useI18n } from "@/hooks/useI18n";

export default function NotificationPermissionBanner() {
  const t = useI18n();
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
            ? t.notificationBanner.askLoggedIn
            : t.notificationBanner.askGuest}
        </p>
      </div>
      <div className="flex items-center gap-2 shrink-0">
        <button
          onClick={requestPermission}
          disabled={isLoading}
          className="bg-white text-blue-600 text-sm font-semibold px-3 py-1.5 rounded-lg"
        >
          {isLoading ? t.notificationBanner.processing : t.notificationBanner.allow}
        </button>
        <button className="text-white/70 hover:text-white text-sm" onClick={() => setDismissed(true)} aria-label={t.notificationBanner.close}>
          ✕
        </button>
      </div>
    </div>
  );
}
