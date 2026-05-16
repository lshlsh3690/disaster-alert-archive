// frontend/src/hooks/useForegroundMessage.ts
"use client";

import { useEffect } from "react";
import { getFirebaseMessaging, onMessage } from "@/lib/firebase";

export const useForegroundMessage = () => {
  useEffect(() => {
    const messaging = getFirebaseMessaging();
    if (!messaging) return;

    const unsubscribe = onMessage(messaging, (payload) => {
      const notificationType = payload.data?.notificationType ?? "PUSH";

      if (notificationType === "NONE") return;

      const title = payload.notification?.title ?? "재난문자 알림";
      const body = payload.notification?.body ?? "";
      const alertId = payload.data?.alertId;

      // 브라우저 Notification API로 직접 표시
      if (Notification.permission === "granted") {
        const options: NotificationOptions = {
          body,
          icon: "/icons/icon-192x192.png",
          data: { url: alertId ? `/alerts/${alertId}` : "/" },
          ...(notificationType === "ALARM" && {
            vibrate: [200, 100, 200, 100, 200],
            requireInteraction: true,
            silent: false,
          }),
        };

        const notification = new Notification(title, options);
        notification.onclick = () => {
          window.focus();
          if (alertId) window.location.href = `/alerts/${alertId}`;
          notification.close();
        };
      }
    });

    return () => unsubscribe();
  }, []);
};