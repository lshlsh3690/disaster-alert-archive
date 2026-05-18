// frontend/src/hooks/useNotificationPermission.ts
import { useState, useEffect, useCallback } from "react";
import { getFirebaseMessaging, getToken } from "@/lib/firebase";

const VAPID_KEY = process.env.NEXT_PUBLIC_FIREBASE_VAPID_KEY;

export type NotificationPermissionStatus = "default" | "granted" | "denied" | "unsupported";

export const useNotificationPermission = () => {
  const [permission, setPermission] = useState<NotificationPermissionStatus>("default");
  const [fcmToken, setFcmToken] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  // FCM 토큰 발급
  const getFcmToken = useCallback(async (): Promise<string | null> => {

    // SW 등록 후 활성화까지 대기
    if ("serviceWorker" in navigator) {
      await navigator.serviceWorker.register("/firebase-messaging-sw.js", {
        scope: "/firebase-cloud-messaging-push-scope",
      });
    }

    // 재시도 로직 (SW 활성화 + Firebase 간헐적 401 모두 대응)
    return await retryGetToken(3);
  }, []);

  // 초기 권한 상태 확인 + 이미 granted면 토큰 재등록
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
        console.log("🔥 FCM Token:", token);
        await registerFcmToken(token);
        return token;
      }
      return null;
    } catch (error) {
      console.warn(`FCM 재시도 남은 횟수: ${retries}`, error);
      if (retries === 0) {
        console.error("FCM 토큰 발급 최종 실패");
        return null;
      }
      // 재시도 전 대기 (점진적 증가)
      await new Promise(resolve => setTimeout(resolve, 1500));
      return retryGetToken(retries - 1);
    }
  }

  // 알림 권한 요청
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

// BE fcm_token API 호출
async function registerFcmToken(token: string) {
  try {
    await fetch("/api/v1/fcm-token", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      credentials: "include",
      body: JSON.stringify({
        token,
        deviceType: "WEB",
      }),
    });
  } catch (error) {
    console.error("FCM 토큰 등록 실패:", error);
  }
}
