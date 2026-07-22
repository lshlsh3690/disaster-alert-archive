import type { MetadataRoute } from "next";

const SITE_URL = process.env.NEXT_PUBLIC_SITE_URL ?? "https://www.disaster-alert-archive.co.kr";

export default function robots(): MetadataRoute.Robots {
  return {
    rules: [
      { userAgent: "Googlebot", allow: "/" },
      { userAgent: "Bingbot", allow: "/" },
      { userAgent: "Yeti", allow: "/" },
      { userAgent: "*", disallow: ["/mypage", "/admin", "/api"] },
      { userAgent: "GPTBot", disallow: "/" },
      { userAgent: "Claude-Web", disallow: "/" },
      { userAgent: "Baiduspider", disallow: "/" },
      { userAgent: "AhrefsBot", disallow: "/" },
      { userAgent: "SemrushBot", disallow: "/" },
      { userAgent: "MJ12bot", disallow: "/" },
      { userAgent: "DotBot", disallow: "/" },
      { userAgent: "PetalBot", disallow: "/" },
    ],
    sitemap: `${SITE_URL}/sitemap.xml`,
  };
}
