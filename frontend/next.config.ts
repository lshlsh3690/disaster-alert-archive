// next.config.ts
import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // SWC 미니파이어 사용(기본이지만 명시 추천)
  swcMinify: true,

  // SWC 전용 변환 옵션
  compiler: {
    // Styled-Components 사용 시: Babel 플러그인 없이 이걸로만 처리
    styledComponents: {
      ssr: true,
      displayName: process.env.NODE_ENV !== "production",
      fileName: false,
      meaninglessFileNames: [],
      minify: true,
      transpileTemplateLiterals: true,
      pure: true,
    },
    // Emotion을 쓴다면 위 대신
    // emotion: true,

    // 프로덕션에서 콘솔 제거(에러/워닝만 제외)
    removeConsole:
      process.env.NODE_ENV === "production" ? { exclude: ["error", "warn"] } : false,

    // 불필요한 테스트용 props 제거
    reactRemoveProperties:
      process.env.NODE_ENV === "production"
        ? { properties: ["^data-testid$"] }
        : false,
  },

  // 빌드 중 ESLint 무시(이미 사용 중)
  eslint: {
    ignoreDuringBuilds: true,
  },

  // 필요 시: Turbopack(dev용) 강제
  // experimental: { turbo: { rules: {} } },
};

export default nextConfig;
