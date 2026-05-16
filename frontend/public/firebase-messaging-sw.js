// frontend/public/firebase-messaging-sw.js
importScripts("https://www.gstatic.com/firebasejs/10.12.0/firebase-app-compat.js");
importScripts("https://www.gstatic.com/firebasejs/10.12.0/firebase-messaging-compat.js");

firebase.initializeApp({
  apiKey: "AIzaSyApBI5Nt1HoxcodorezhYPCR1_wprPDwWY",
  authDomain: "disaster-alert-archive-c02ea.firebaseapp.com",
  projectId: "disaster-alert-archive-c02ea",
  storageBucket: "disaster-alert-archive-c02ea.firebasestorage.app",
  messagingSenderId: "212414658608",
  appId: "1:212414658608:web:c6a06ffecf9ffaea1955f5",
});

const messaging = firebase.messaging();

messaging.onBackgroundMessage((payload) => {
  const notificationType = payload.data?.notificationType ?? "PUSH";

  if (notificationType === "NONE") return;

  const title = payload.notification?.title ?? "재난문자 알림";
  const body = payload.notification?.body ?? "";
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

  self.registration.showNotification(title, options);
});

self.addEventListener("notificationclick", (event) => {
  event.notification.close();
  const url = event.notification.data?.url ?? "/";

  event.waitUntil(
    clients.matchAll({ type: "window", includeUncontrolled: true }).then((clientList) => {
      for (const client of clientList) {
        if (client.url === url && "focus" in client) return client.focus();
      }
      if (clients.openWindow) return clients.openWindow(url);
    }),
  );
});
