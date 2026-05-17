// frontend/src/hooks/useNotificationPermission.ts
import { useState, useEffect, useCallback } from "react";
import { getFirebaseMessaging, getToken } from "@/lib/firebase";

const VAPID_KEY = process.env.NEXT_PUBLIC_FIREBASE_VAPID_KEY;

export type NotificationPermissionStatus = "default" | "granted" | "denied" | "unsupported";

export const useNotificationPermission = () => {
  const [permission, setPermission] = useState<NotificationPermissionStatus>("default");
  const [fcmToken, setFcmToken] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  // 초기 권한 상태 확인
  useEffect(() => {
    if (typeof window === "undefined" || !("Notification" in window)) {
      setPermission("unsupported");
      return;
    }
    setPermission(Notification.permission as NotificationPermissionStatus);
  }, []);

  // FCM 토큰 발급
  const getFcmToken = useCallback(async (): Promise<string | null> => {
    try {
      const messaging = getFirebaseMessaging();
      if (!messaging) return null;

      // Service Worker 등록 + 활성화 대기
      let swRegistration: ServiceWorkerRegistration | undefined;
      if ("serviceWorker" in navigator) {
        swRegistration = await navigator.serviceWorker.register(
          "/firebase-messaging-sw.js",
          { scope: "/firebase-cloud-messaging-push-scope" },
        );

        // 활성화될 때까지 대기
        await navigator.serviceWorker.ready;

        // installing 상태면 activate 대기
        if (swRegistration.installing) {
          await new Promise<void>((resolve) => {
            swRegistration!.installing!.addEventListener("statechange", (e) => {
              if ((e.target as ServiceWorker).state === "activated") {
                resolve();
              }
            });
          });
        }
      }

      const token = await getToken(messaging, {
        vapidKey: VAPID_KEY,
        serviceWorkerRegistration: swRegistration,
      });

      if (token) {
        console.log("🔥 FCM Token:", token);
        setFcmToken(token);
        await registerFcmToken(token);
        return token;
      }
      return null;
    } catch (error) {
      console.error("FCM 토큰 발급 실패:", error);
      return null;
    }
  }, []);

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
