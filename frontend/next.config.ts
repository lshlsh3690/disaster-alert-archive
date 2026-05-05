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
  compiler: {
    removeConsole:
      process.env.NODE_ENV === "production" ? { exclude: ["error", "warn"] } : false,
    reactRemoveProperties:
      process.env.NODE_ENV === "production"
        ? { properties: ["^data-testid$"] }
        : false,
  },
};

export default withPWA({
  dest: "public",
  cacheOnFrontEndNav: true,
  aggressiveFrontEndNavCaching: true,
  reloadOnOnline: true,
  disable: process.env.NODE_ENV === "development", // 개발 중엔 SW 비활성
  workboxOptions: {
    disableDevLogs: true,
  },
})(nextConfig);