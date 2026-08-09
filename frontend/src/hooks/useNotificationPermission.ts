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
    // 캐시는 UI 초기값 채우기 용도로만 쓰고, 서비스워커 등록과 토큰 발급·서버 등록은 항상 수행한다.
    // 캐시가 있다고 여기서 그대로 반환하면 (1) 서비스워커가 다시 등록되지 않고
    // (2) retryGetToken 안의 registerFcmToken 이 호출되지 않는다. 그 결과 서버의 fcm_token 행이
    // 사라진 사용자(회원 재가입, 토큰 정리 등)는 브라우저가 캐시만 믿고 재등록을 영영 하지 않아
    // 푸시를 받지 못한다. getToken 은 구독이 그대로면 같은 토큰을 돌려주므로 비용도 크지 않다.
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

  // 로그인 사용자의 회원 토큰 등록 보장. authStore 복원이 토큰 발급보다 늦으면 retryGetToken 안의
  // registerFcmToken 이 isLoggedIn=false 로 실행돼 게스트 경로로 빠지고, 회원 fcm_token 행이
  // 끝내 생성되지 않는다(로그인 사용자에게만 푸시가 안 오는 원인). 위 게스트 동기화와 같은 이유로
  // fcmToken 을 의존성에 포함해 토큰이 늦게 준비돼도 등록이 반드시 한 번 실행되게 한다.
  useEffect(() => {
    if (!isLoggedIn || !fcmToken) return;
    registerFcmToken(fcmToken, true, []);
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
