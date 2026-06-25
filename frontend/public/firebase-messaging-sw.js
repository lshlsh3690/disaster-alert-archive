// frontend/public/firebase-messaging-sw.js

importScripts("https://www.gstatic.com/firebasejs/10.12.0/firebase-app-compat.js");
importScripts("https://www.gstatic.com/firebasejs/10.12.0/firebase-messaging-compat.js");

firebase.initializeApp({
  apiKey: "AIzaSyCsWLhVWPRFZFHmI27xz09qFGp5Lwl8Y9Y",
  authDomain: "disaster-alert-archive.firebaseapp.com",
  projectId: "disaster-alert-archive",
  storageBucket: "disaster-alert-archive.firebasestorage.app",
  messagingSenderId: "920878537636",
  appId: "1:920878537636:web:e5825c24678cc9d215ff31",
});

const messaging = firebase.messaging();

messaging.onBackgroundMessage(async (payload) => {
  const notificationType = payload.data?.notificationType ?? "PUSH";

  if (notificationType === "NONE") return;

  const title = payload.data?.title || payload.notification?.title || "재난문자 알림";
  const body = payload.data?.body || payload.notification?.body || "";
  const alertId = payload.data?.alertId;

  const options = {
    body,
    icon: "/icons/icon-192x192.png",
    badge: "/icons/icon-72x72.png",
    data: { alertId, url: alertId ? `/alerts/${alertId}` : "/" },
    ...(notificationType === "ALARM" && {
      vibrate: [200, 100, 200, 100, 200],
      requireInteraction: true,
      silent: false,
      tag: "disaster-alarm",
    }),
    ...(notificationType === "PUSH" && {
      vibrate: [100],
      silent: false,
      tag: "disaster-push",
    }),
  };

  await self.registration.showNotification(title, options);
});

self.addEventListener("notificationclick", (event) => {
  event.notification.close();

  // data.url은 상대경로(/alerts/{id})이므로 절대 URL로 변환한다.
  // (openWindow와 기존 창 비교 모두 절대 URL이 필요)
  const targetPath = event.notification.data?.url ?? "/";
  const targetUrl = new URL(targetPath, self.location.origin).href;

  event.waitUntil(
    (async () => {
      const clientList = await clients.matchAll({ type: "window", includeUncontrolled: true });

      // 이미 열려 있는 앱/탭이 있으면 해당 상세 페이지로 이동시키고 포커스
      for (const client of clientList) {
        if (!("focus" in client)) continue;
        try {
          if ("navigate" in client && client.url !== targetUrl) {
            const navigated = await client.navigate(targetUrl);
            return (navigated || client).focus();
          }
          return client.focus();
        } catch (e) {
          // navigate 실패 시 아래 openWindow로 폴백
          break;
        }
      }

      // 열린 창이 없으면 새 창(앱)으로 상세 페이지를 연다
      if (clients.openWindow) return clients.openWindow(targetUrl);
    })(),
  );
});
