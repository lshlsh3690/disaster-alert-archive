import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  /* config options here */
  eslint: {
    ignoreDuringBuilds: true, //빌드 시 ESLint 에러 무시
  },
};

export default nextConfig;
