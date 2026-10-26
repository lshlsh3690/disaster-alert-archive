import { useState, useEffect, useCallback } from "react";
import { getFirebaseMessaging, getToken } from "@/lib/firebase";
import { registerGuestFcmToken, deleteGuestFcmToken } from "@/api/guestFcmApi";
import { deleteFcmToken } from "@/api/fcmTokenApi";
import { isMobileDevice, pushDeviceType } from "@/lib/device";
import { useGuestFavoriteRegionsStore } from "@/store/guestFavoriteRegionsStore";
import { useAuthStore } from "@/store/authStore";

export type NotificationPermissionStatus = "default" | "granted" | "denied" | "unsupported";

const FCM_SW_SCOPE = "/firebase-cloud-messaging-push-scope";

export const useNotificationPermission = () => {
  // 푸시는 모바일 사용자 전용이다(2026-10). 데스크톱이면 권한 요청·토큰 발급·서버 등록을 하지 않고,
  // 예전에 데스크톱에서 켜 둔 구독은 정리한다. permission 과 같은 이유로 lazy initializer 로 바로 판정한다.
  const [isPushTarget] = useState<boolean>(() => isMobileDevice());
  // 초기값을 "default"로 고정하면 실제로는 granted/denied 인 경우에도 첫 렌더에서
  // 배너가 잠깐 노출됐다가 아래 effect 가 진짜 값을 읽어온 뒤에야 사라지는 깜빡임이
  // 생긴다. lazy initializer 로 마운트 시점에 바로 실제 권한을 읽어 그 깜빡임을 없앤다.
  const [permission, setPermission] = useState<NotificationPermissionStatus>(() => {
    if (typeof window === "undefined") return "default";
    if (!("Notification" in window)) return "unsupported";
    return Notification.permission as NotificationPermissionStatus;
  });
  const [fcmToken, setFcmToken] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  const isLoggedIn = useAuthStore((s) => s.user !== null);
  const guestRegions = useGuestFavoriteRegionsStore((s) => s.regions);

  const getFcmToken = useCallback(async (): Promise<string | null> => {
    // 캐시는 UI 초기값 채우기 용도로만 쓰고, 서비스워커 등록과 토큰 발급은 항상 수행한다.
    // 캐시가 있다고 여기서 그대로 반환하면 서비스워커가 다시 등록되지 않는다. 또 setFcmToken 이
    // 호출되지 않으면 등록 effect 들도 깨어나지 않아, 서버의 fcm_token 행이 사라진 사용자
    // (회원 재가입, 토큰 정리 등)는 브라우저가 캐시만 믿고 재등록을 영영 하지 않아 푸시를
    // 받지 못한다. getToken 은 구독이 그대로면 같은 토큰을 돌려주므로 비용도 크지 않다.
    const cached = localStorage.getItem("fcm-token");
    if (cached) setFcmToken(cached);

    if ("serviceWorker" in navigator) {
      await navigator.serviceWorker.register("/firebase-messaging-sw.js", {
        scope: FCM_SW_SCOPE,
      });
    }

    return (await retryGetToken(3)) ?? cached;
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (typeof window === "undefined" || !("Notification" in window)) {
      setPermission("unsupported");
      return;
    }
    const current = Notification.permission as NotificationPermissionStatus;
    setPermission(current);
    if (!isPushTarget) {
      cleanUpDesktopPush();
      return;
    }
    if (current === "granted") {
      getFcmToken();
    }
  }, [getFcmToken, isPushTarget]);

  // 비로그인 상태에서 토큰 발급 완료 또는 관심지역 변경 시 서버 게스트 토큰 동기화.
  // 토큰은 비동기로 발급되며(발급 완료가 이 effect의 첫 실행보다 늦을 수 있음), fcmToken 상태를
  // 의존성에 포함해 토큰이 준비된 뒤에도 반드시 등록이 한 번 실행되게 한다. localStorage 를
  // 직접 읽으면 값이 늦게 채워져도 재실행되지 않아 등록이 누락된다(첫 방문 게스트 등록 누락 버그).
  useEffect(() => {
    if (!isPushTarget || isLoggedIn) return;
    if (!fcmToken || guestRegions.length === 0) return;
    const codes = guestRegions.map((r) => r.legalDistrictCode);
    registerGuestFcmToken(fcmToken, codes);
  }, [isPushTarget, isLoggedIn, guestRegions, fcmToken]);

  // 로그인 사용자의 회원 토큰 등록. 위 게스트 동기화와 대칭이며, 서버 등록은 이 두 effect 가
  // 전담한다(토큰 발급 함수 안에서는 등록하지 않는다). getFcmToken 이 useCallback([]) 으로
  // 첫 렌더에 고정되는 탓에 그 안에서 등록하면 authStore 하이드레이션 전의 isLoggedIn=false 가
  // 클로저에 박혀 계속 게스트 경로로 새기 때문이다. effect 는 매 렌더의 최신 값을 보므로
  // 하이드레이션이 늦어도 올바른 경로로 정확히 한 번 등록된다.
  useEffect(() => {
    if (!isPushTarget || !isLoggedIn || !fcmToken) return;
    registerMemberFcmToken(fcmToken);
  }, [isPushTarget, isLoggedIn, fcmToken]);

  async function retryGetToken(retries: number): Promise<string | null> {
    try {
      const messaging = getFirebaseMessaging();
      if (!messaging) return null;

      const swRegistration = await navigator.serviceWorker.getRegistration(FCM_SW_SCOPE);

      const token = await getToken(messaging, {
        vapidKey: process.env.NEXT_PUBLIC_FIREBASE_VAPID_KEY,
        serviceWorkerRegistration: swRegistration,
      });

      if (token) {
        // 서버 등록은 하지 않는다 — setFcmToken 이 위 두 effect 를 깨워 등록을 수행한다.
        // 여기서도 등록하면 같은 토큰에 대해 등록 API 가 두 번 호출된다.
        localStorage.setItem("fcm-token", token);
        setFcmToken(token);
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
    if (!isPushTarget || typeof window === "undefined" || !("Notification" in window)) return false;

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
  }, [getFcmToken, isPushTarget]);

  return { permission, fcmToken, isLoading, requestPermission, isPushTarget };
};

/**
 * 모바일 전용 전환 전에 데스크톱에서 알림을 켜 둔 브라우저 정리 — 서버 토큰을 지우고 브라우저 구독을 해제한다.
 * 게스트 삭제는 회원 소유 토큰을 건드리지 않고, 회원 삭제는 로그인 쿠키가 없으면 401 로 끝나므로 둘 다 부른다.
 * 전환 전에 WEB 으로 등록된 토큰은 V134 마이그레이션이 서버에서 일괄 삭제했으므로, 여기서는 그 뒤에도 남은
 * 브라우저 쪽 구독(서비스워커·localStorage)과 혹시 남은 서버 행을 정리한다.
 */
async function cleanUpDesktopPush() {
  const token = localStorage.getItem("fcm-token");
  if (token) {
    await deleteGuestFcmToken(token);
    await deleteFcmToken(token);
    localStorage.removeItem("fcm-token");
  }
  if (!("serviceWorker" in navigator)) return;
  try {
    // getRegistration 의 인자는 스코프가 아니라 "이 URL 을 담당하는 등록"을 찾는 주소라, FCM 워커가 없으면
    // 루트 스코프(/)의 next-pwa sw.js 가 대신 반환된다. 스코프가 정확히 FCM 스코프일 때만 해제한다.
    const registration = await navigator.serviceWorker.getRegistration(FCM_SW_SCOPE);
    if (!registration || new URL(registration.scope).pathname !== FCM_SW_SCOPE) return;
    const subscription = await registration.pushManager.getSubscription();
    await subscription?.unsubscribe();
    await registration.unregister();
  } catch (error) {
    console.warn("데스크톱 푸시 구독 해제 실패:", error);
  }
}

async function registerMemberFcmToken(token: string) {
  const deviceType = pushDeviceType();

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
}
