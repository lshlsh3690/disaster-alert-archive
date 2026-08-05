// frontend/next.config.ts
import type { NextConfig } from "next";
import withPWA from "@ducanh2912/next-pwa";

const API_BASE = process.env.BASE_API_URL ?? "https://api.disaster-alert-archive.co.kr";

const nextConfig: NextConfig = {
  async rewrites() {
    return [
      {
        source: "/api/:path*",
        destination: `${API_BASE}/api/:path*`,
      },
    ];
  },
  async headers() {
    return [
      {
        source: "/firebase-messaging-sw.js",
        headers: [
          {
            key: "Service-Worker-Allowed",
            value: "/",
          },
        ],
      },
      // 아래 두 개는 원래 netlify.toml에 있던 규칙 — Vercel 등 다른 호스팅으로
      // 옮겨도 그대로 적용되도록 호스트 무관한 Next.js 레벨로 옮김.
      // 서비스워커/manifest를 캐싱하면 배포 후에도 브라우저가 구버전을 계속 써서
      // PWA 업데이트가 반영 안 되는 문제가 생길 수 있어 항상 재검증하도록 강제.
      {
        source: "/sw.js",
        headers: [{ key: "Cache-Control", value: "public, max-age=0, must-revalidate" }],
      },
      {
        source: "/manifest.json",
        headers: [{ key: "Cache-Control", value: "public, max-age=0, must-revalidate" }],
      },
    ];
  },
  compiler: {
    removeConsole: process.env.NODE_ENV === "production" ? { exclude: ["error", "warn"] } : false,
    reactRemoveProperties: process.env.NODE_ENV === "production" ? { properties: ["^data-testid$"] } : false,
  },
  env: {
    // Service Worker에서 접근 가능하도록 빌드 시 삽입
    NEXT_PUBLIC_FIREBASE_API_KEY: process.env.NEXT_PUBLIC_FIREBASE_API_KEY!,
    NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN: process.env.NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN!,
    NEXT_PUBLIC_FIREBASE_PROJECT_ID: process.env.NEXT_PUBLIC_FIREBASE_PROJECT_ID!,
    NEXT_PUBLIC_FIREBASE_STORAGE_BUCKET: process.env.NEXT_PUBLIC_FIREBASE_STORAGE_BUCKET!,
    NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID: process.env.NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID!,
    NEXT_PUBLIC_FIREBASE_APP_ID: process.env.NEXT_PUBLIC_FIREBASE_APP_ID!,
  },
};

export default withPWA({
  dest: "public",
  cacheOnFrontEndNav: true,
  aggressiveFrontEndNavCaching: true,
  reloadOnOnline: true,
  disable: process.env.NODE_ENV === "development",
  workboxOptions: {
    disableDevLogs: true,
    // 기본값(skipWaiting/clientsClaim: true)이면 새 SW가 배포될 때마다 열려있는 탭을
    // 즉시 강제로 재장악한다. 이 과정에서 firebase-messaging-sw.js가 들고 있는 FCM
    // push 구독이 세션 도중에 끊겨, 방금 발급한 토큰도 서버에서 곧바로
    // "NotRegistered/Device unregistered"로 실패하는 문제가 있었다. false로 두면
    // 새 SW는 설치만 되고, 기존 탭을 다 닫고 다시 열 때(=구독이 끊길 일 없는 시점)
    // 활성화되어 세션 중 push 구독 안정성이 올라간다.
    skipWaiting: false,
    clientsClaim: false,
  },
})(nextConfig);
