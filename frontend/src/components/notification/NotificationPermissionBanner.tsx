"use client";

import { useState } from "react";
import { useNotificationPermission } from "@/hooks/useNotificationPermission";
import { useAuthStore } from "@/store/authStore";
import { useTranslation } from "react-i18next";

export default function NotificationPermissionBanner() {
  const { t } = useTranslation();
  const user = useAuthStore((state) => state.user);
  const { permission, isLoading, requestPermission } = useNotificationPermission();
  const [dismissed, setDismissed] = useState(false);

  const isLoggedIn = !!user;
  // 로그인 여부·관심지역 유무와 무관하게 진입 직후 한 번 노출한다. 예전에는 게스트만 관심지역이
  // 있을 때 표시해서 두 경로의 권한 요청 시점이 어긋났다. 권한 허용 시점에 관심지역이 없어도
  // useNotificationPermission 의 동기화 effect 들이 나중에 지역이 채워지면 등록해주므로 문제없다.
  // requestPermission 은 반드시 버튼 클릭(사용자 제스처)으로만 호출한다 — 자동 호출은
  // Firefox/Safari 에서 무시된다.
  const shouldShow = permission === "default" && !dismissed;

  if (!shouldShow) return null;

  return (
    <div className="fixed bottom-4 left-4 right-4 z-50 bg-blue-600 text-white rounded-xl p-4 shadow-lg flex items-center justify-between gap-3">
      <div className="flex items-center gap-2">
        <span className="text-xl">🔔</span>
        <p className="text-sm font-medium">
          {isLoggedIn
            ? t("notificationBanner.askLoggedIn")
            : t("notificationBanner.askGuest")}
        </p>
      </div>
      <div className="flex items-center gap-2 shrink-0">
        <button
          onClick={requestPermission}
          disabled={isLoading}
          className="bg-white text-blue-600 text-sm font-semibold px-3 py-1.5 rounded-lg"
        >
          {isLoading ? t("notificationBanner.processing") : t("notificationBanner.allow")}
        </button>
        {/* 터치 타겟 최소 44px 확보 — 글리프 크기 그대로 두면 실측 히트 영역이 11×20px 남짓이라
            모바일에서 탭이 잘 안 된다. 시각적 크기는 그대로 두고 패딩으로만 넓힌다. */}
        <button
          className="text-white/70 hover:text-white text-sm min-w-11 min-h-11 flex items-center justify-center -mr-2"
          onClick={() => setDismissed(true)}
          aria-label={t("notificationBanner.close")}
        >
          ✕
        </button>
      </div>
    </div>
  );
}
