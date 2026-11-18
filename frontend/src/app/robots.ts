import type { MetadataRoute } from "next";

const SITE_URL = process.env.NEXT_PUBLIC_SITE_URL ?? "https://www.disaster-alert-archive.co.kr";

// 검색 결과에 나올 이유가 없는 개인 화면·관리 화면·API.
// robots.txt 는 크롤러 이름별 그룹을 "*" 그룹과 합치지 않는다 — 이름을 지정한 크롤러는
// 자기 그룹 규칙만 따르므로, 허용하는 검색엔진 그룹마다 이 목록을 직접 넣어야 차단이 걸린다.
const PRIVATE_PATHS = ["/user/me", "/user/settings", "/user/delete", "/notifications", "/admin", "/api"];

// 검색엔진 + AI 검색(GEO). AI 검색 답변에 출처 링크로 인용돼 유입이 생기는 크롤러만 연다.
// AI 회사는 학습·검색 색인·사용자 요청 열람 크롤러를 이름으로 나눠 두었으므로 학습용만 따로 막을 수 있다.
// 이름은 각 사 공식 문서 기준(2026-11 확인) — 바뀌면 여기를 갱신한다.
const ALLOWED_CRAWLERS = [
  "Googlebot",
  "Bingbot",
  "Yeti",
  "OAI-SearchBot", // ChatGPT 검색 색인
  "ChatGPT-User", // ChatGPT 사용자 요청 열람
  "Claude-SearchBot", // Claude 검색 색인
  "Claude-User", // Claude 사용자 요청 열람
  "PerplexityBot", // Perplexity 검색 색인(학습에는 쓰지 않는다고 밝힘)
  "Perplexity-User", // Perplexity 사용자 요청 열람
];

const BLOCKED_BOTS = [
  "GPTBot", // OpenAI 학습용
  "ClaudeBot", // Anthropic 학습용 (예전 이름 Claude-Web 은 폐기됨)
  "Baiduspider",
  "AhrefsBot",
  "SemrushBot",
  "MJ12bot",
  "DotBot",
  "PetalBot",
];

export default function robots(): MetadataRoute.Robots {
  return {
    rules: [
      ...ALLOWED_CRAWLERS.map((userAgent) => ({ userAgent, allow: "/", disallow: PRIVATE_PATHS })),
      { userAgent: "*", disallow: PRIVATE_PATHS },
      ...BLOCKED_BOTS.map((userAgent) => ({ userAgent, disallow: "/" })),
    ],
    sitemap: `${SITE_URL}/sitemap.xml`,
  };
}
