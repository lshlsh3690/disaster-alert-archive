// frontend/src/hooks/useForegroundMessage.ts
"use client";

import { useEffect } from "react";
import { getFirebaseMessaging, onMessage } from "@/lib/firebase";

export const useForegroundMessage = () => {
  useEffect(() => {
    const messaging = getFirebaseMessaging();
    if (!messaging) return;

    const unsubscribe = onMessage(messaging, async (payload) => {
      const notificationType = payload.data?.notificationType ?? "PUSH";
      if (notificationType === "NONE") return;

      const title = payload.notification?.title ?? "재난문자 알림";
      const body = payload.notification?.body ?? "";
      const alertId = payload.data?.alertId;

      if (Notification.permission !== "granted") return;

      const options: NotificationOptions = {
        body,
        icon: "/icons/icon-192x192.png",
        badge: "/icons/icon-72x72.png",
        data: { url: alertId ? `/alerts/${alertId}` : "/" },
        ...(notificationType === "ALARM" && {
          vibrate: [200, 100, 200, 100, 200],
          requireInteraction: true,
          silent: false,
        }),
      };

      //SW 있을 때는 registration.showNotification() 사용
      if ("serviceWorker" in navigator) {
        const registration = await navigator.serviceWorker.ready;
        await registration.showNotification(title, options);
      } else {
        new Notification(title, options);
      }
    });

    return () => unsubscribe();
  }, []);
};