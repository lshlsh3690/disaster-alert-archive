// frontend/public/firebase-messaging-sw.js
//
// Firebase JS SDK의 messaging().onBackgroundMessage()는 push 이벤트를 못 받거나
// 씹는 경우가 있어(SDK 내부 라우팅 이슈), SDK를 아예 로드하지 않고 표준 Push API의
// 'push' 이벤트를 직접 파싱해서 처리한다. 백엔드는 data-only 메시지만 보낸다
// (FcmSendService 참고 - notification 페이로드를 넣으면 중복 알림 발생).

self.addEventListener("push", (event) => {
  event.waitUntil(handlePush(event));
});

async function handlePush(event) {
  let raw = {};
  try {
    raw = event.data ? event.data.json() : {};
  } catch (e) {
    raw = {};
  }

  // FCM data-only 웹푸시는 event.data.json()이 data 필드 내용을 그대로 최상위로 주기도 하고
  // { data: {...} } 형태로 감싸서 주기도 해서 둘 다 대응한다.
  const data = raw.data || raw;

  try {
    const notificationType = data.notificationType ?? "PUSH";

    if (notificationType === "NONE") return;

    const title = data.title || raw.notification?.title || "재난문자 알림";
    const body = data.body || raw.notification?.body || "";
    const alertId = data.alertId;

    const options = {
      body,
      icon: "/icons/icon-192x192.png",
      badge: "/icons/icon-72x72.png",
      data: { alertId, url: alertId ? `/alerts/${alertId}` : "/" },
      ...(notificationType === "ALARM" && {
        vibrate: [200, 100, 200, 100, 200],
        requireInteraction: true,
        silent: false,
      }),
      ...(notificationType === "PUSH" && {
        vibrate: [100],
        silent: false,
      }),
    };

    await self.registration.showNotification(title, options);
  } catch (e) {
    // TEMP DEBUG: 원인 확인 후 제거할 것
    await self.registration.showNotification("SW DEBUG ERROR", {
      body: `${e?.message || e} | raw=${JSON.stringify(raw)}`,
    });
  }
}

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
