"use client";

import { useI18n } from "@/hooks/useI18n";

const PROVIDER_STYLE = {
  google: "bg-white text-[#1f1f1f] border border-[#747775] hover:bg-[#f7f8f8]",
  kakao: "bg-[#FEE500] text-[rgba(0,0,0,0.85)] hover:brightness-[0.97]",
  naver: "bg-[#03C75A] text-white hover:brightness-[0.95]",
} as const;

export default function OAuthButton({
  provider,
}: {
  provider: "google" | "kakao" | "naver";
}) {
  const t = useI18n();
  const cfg = { label: t.oauth[provider], className: PROVIDER_STYLE[provider] };

  const handleOAuth = () => {
    const apiBase = process.env.NEXT_PUBLIC_API_BASE_URL || "";
    const redirect = `${window.location.origin}/`;
    window.location.href = `${apiBase}/api/v1/auth/oauth/${provider}/authorize?redirect=${encodeURIComponent(redirect)}`;
  };

  return (
    <button
      type="button"
      onClick={handleOAuth}
      className={`relative flex h-11 w-full items-center justify-center rounded-[var(--radius-control)] px-12 text-sm font-medium transition focus:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-[var(--blue)] ${cfg.className}`}
    >
      <span className="absolute left-3.5 flex h-[18px] w-[18px] items-center justify-center" aria-hidden="true">
        {provider === "google" && (
          <svg width="18" height="18" viewBox="0 0 18 18">
            <path fill="#4285F4" d="M17.64 9.2c0-.64-.06-1.25-.16-1.84H9v3.48h4.84a4.14 4.14 0 0 1-1.8 2.72v2.26h2.92c1.71-1.57 2.68-3.88 2.68-6.62z" />
            <path fill="#34A853" d="M9 18c2.43 0 4.47-.8 5.96-2.18l-2.92-2.26c-.8.54-1.84.86-3.04.86-2.34 0-4.32-1.58-5.03-3.7H.96v2.33A9 9 0 0 0 9 18z" />
            <path fill="#FBBC05" d="M3.97 10.72a5.41 5.41 0 0 1 0-3.44V4.95H.96a9 9 0 0 0 0 8.1l3.01-2.33z" />
            <path fill="#EA4335" d="M9 3.58c1.32 0 2.5.45 3.44 1.35l2.58-2.58C13.47.9 11.43 0 9 0A9 9 0 0 0 .96 4.95l3.01 2.33C4.68 5.16 6.66 3.58 9 3.58z" />
          </svg>
        )}
        {provider === "kakao" && (
          <svg width="18" height="18" viewBox="0 0 256 256">
            <path fill="#000000" d="M128 36C70.56 36 24 72.89 24 118.4c0 29.43 19.47 55.25 48.74 69.78-1.61 5.6-10.34 35.7-10.69 38.07 0 0-.21 1.79.95 2.47s2.71.17 2.71.17c3.32-.46 38.45-25.13 44.53-29.41 6.07.86 12.32 1.32 18.76 1.32 57.44 0 104-36.89 104-82.4S185.44 36 128 36z" />
          </svg>
        )}
        {provider === "naver" && (
          <svg width="15" height="15" viewBox="0 0 20 20">
            <path fill="#ffffff" d="M13.56 10.7 6.16 0H0v20h6.44V9.3l7.4 10.7H20V0h-6.44v10.7z" />
          </svg>
        )}
      </span>
      {cfg.label}
    </button>
  );
}
