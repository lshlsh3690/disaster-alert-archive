import { useState, useEffect, useCallback } from "react";
import { getFirebaseMessaging, getToken } from "@/lib/firebase";
import { registerGuestFcmToken } from "@/api/guestFcmApi";
import { useGuestFavoriteRegionsStore } from "@/store/guestFavoriteRegionsStore";
import { useAuthStore } from "@/store/authStore";

export type NotificationPermissionStatus = "default" | "granted" | "denied" | "unsupported";

export const useNotificationPermission = () => {
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
        scope: "/firebase-cloud-messaging-push-scope",
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
    if (current === "granted") {
      getFcmToken();
    }
  }, [getFcmToken]);

  // 비로그인 상태에서 토큰 발급 완료 또는 관심지역 변경 시 서버 게스트 토큰 동기화.
  // 토큰은 비동기로 발급되며(발급 완료가 이 effect의 첫 실행보다 늦을 수 있음), fcmToken 상태를
  // 의존성에 포함해 토큰이 준비된 뒤에도 반드시 등록이 한 번 실행되게 한다. localStorage 를
  // 직접 읽으면 값이 늦게 채워져도 재실행되지 않아 등록이 누락된다(첫 방문 게스트 등록 누락 버그).
  useEffect(() => {
    if (isLoggedIn) return;
    if (!fcmToken || guestRegions.length === 0) return;
    const codes = guestRegions.map((r) => r.legalDistrictCode);
    registerGuestFcmToken(fcmToken, codes);
  }, [isLoggedIn, guestRegions, fcmToken]);

  // 로그인 사용자의 회원 토큰 등록. 위 게스트 동기화와 대칭이며, 서버 등록은 이 두 effect 가
  // 전담한다(토큰 발급 함수 안에서는 등록하지 않는다). getFcmToken 이 useCallback([]) 으로
  // 첫 렌더에 고정되는 탓에 그 안에서 등록하면 authStore 하이드레이션 전의 isLoggedIn=false 가
  // 클로저에 박혀 계속 게스트 경로로 새기 때문이다. effect 는 매 렌더의 최신 값을 보므로
  // 하이드레이션이 늦어도 올바른 경로로 정확히 한 번 등록된다.
  useEffect(() => {
    if (!isLoggedIn || !fcmToken) return;
    registerMemberFcmToken(fcmToken);
  }, [isLoggedIn, fcmToken]);

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

async function registerMemberFcmToken(token: string) {
  const isTWA = document.referrer.includes("android-app://");
  const deviceType = isTWA ? "ANDROID" : "WEB";

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
