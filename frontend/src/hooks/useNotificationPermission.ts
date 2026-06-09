import { useState, useEffect, useCallback } from "react";
import { getFirebaseMessaging, getToken } from "@/lib/firebase";
import { registerGuestFcmToken } from "@/api/guestFcmApi";
import { useGuestFavoriteRegionsStore } from "@/store/guestFavoriteRegionsStore";
import { useAuthStore } from "@/store/authStore";

export type NotificationPermissionStatus = "default" | "granted" | "denied" | "unsupported";

export const useNotificationPermission = () => {
  const [permission, setPermission] = useState<NotificationPermissionStatus>("default");
  const [fcmToken, setFcmToken] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  const isLoggedIn = useAuthStore((s) => s.user !== null);
  const guestRegions = useGuestFavoriteRegionsStore((s) => s.regions);

  const getFcmToken = useCallback(async (): Promise<string | null> => {
    const cached = localStorage.getItem("fcm-token");
    if (cached) {
      setFcmToken(cached);
      return cached;
    }

    if ("serviceWorker" in navigator) {
      await navigator.serviceWorker.register("/firebase-messaging-sw.js", {
        scope: "/firebase-cloud-messaging-push-scope",
      });
    }

    return await retryGetToken(3);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (typeof window === "undefined" || !("Notification" in window)) {
      setPermission("unsupported");
      return;
    }
    const current = Notification.permission as NotificationPermissionStatus;
    setPermission(current);
    if (current === "granted") {
      getFcmToken();
    }
  }, [getFcmToken]);

  // 비로그인 상태에서 관심지역 변경 시 서버 게스트 토큰 동기화
  useEffect(() => {
    if (isLoggedIn) return;
    const token = localStorage.getItem("fcm-token");
    if (!token || guestRegions.length === 0) return;
    const codes = guestRegions.map((r) => r.legalDistrictCode);
    registerGuestFcmToken(token, codes);
  }, [isLoggedIn, guestRegions]);

  async function retryGetToken(retries: number): Promise<string | null> {
    try {
      const messaging = getFirebaseMessaging();
      if (!messaging) return null;

      const swRegistration = await navigator.serviceWorker.getRegistration(
        "/firebase-cloud-messaging-push-scope"
      );

      const token = await getToken(messaging, {
        vapidKey: process.env.NEXT_PUBLIC_FIREBASE_VAPID_KEY,
        serviceWorkerRegistration: swRegistration,
      });

      if (token) {
        localStorage.setItem("fcm-token", token);
        setFcmToken(token);
        await registerFcmToken(token, isLoggedIn, guestRegions.map((r) => r.legalDistrictCode));
        return token;
      }
      return null;
    } catch (error) {
      console.warn(`FCM 재시도 남은 횟수: ${retries}`, error);
      if (retries === 0) {
        console.error("FCM 토큰 발급 최종 실패");
        return null;
      }
      await new Promise(resolve => setTimeout(resolve, 1500));
      return retryGetToken(retries - 1);
    }
  }

  const requestPermission = useCallback(async (): Promise<boolean> => {
    if (typeof window === "undefined" || !("Notification" in window)) return false;

    setIsLoading(true);
    try {
      const result = await Notification.requestPermission();
      setPermission(result as NotificationPermissionStatus);

      if (result === "granted") {
        await getFcmToken();
        return true;
      }
      return false;
    } catch (error) {
      console.error("알림 권한 요청 실패:", error);
      return false;
    } finally {
      setIsLoading(false);
    }
  }, [getFcmToken]);

  return { permission, fcmToken, isLoading, requestPermission };
};

async function registerFcmToken(token: string, isLoggedIn: boolean, guestCodes: string[]) {
  const isTWA = document.referrer.includes("android-app://");
  const deviceType = isTWA ? "ANDROID" : "WEB";

  if (isLoggedIn) {
    try {
      await fetch("/api/v1/fcm-token", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({ token, deviceType }),
      });
    } catch (error) {
      console.error("FCM 토큰 등록 실패:", error);
    }
  } else {
    await registerGuestFcmToken(token, guestCodes);
  }
}
