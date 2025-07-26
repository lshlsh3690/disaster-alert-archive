"use client";

import Image from "next/image";

export default function OAuthButton({
  provider,
}: {
  provider: "google" | "kakao" | "naver";
}) {
  const label = {
    google: "Google 계정으로 로그인",
    kakao: "카카오 계정으로 로그인",
    naver: "네이버 계정으로 로그인",
  }[provider];

  const loginUrl = {
    google: "/api/oauth/google",
    kakao: "/api/oauth/kakao",
    naver: "/api/oauth/naver",
  }[provider];

  const logo = {
    google: "/oauth/google.svg", // public 폴더에 이미지 넣어두기
    kakao: "/oauth/kakao.png",
    naver: "/oauth/naver.png",
  }[provider];

  return (
    <button
      onClick={() => (window.location.href = loginUrl)}
      className="w-full border px-4 py-2 rounded flex items-center gap-3 hover:bg-gray-50 transition"
    >
      <Image src={logo} alt={provider} width={50} height={50} />
      <span className="text-sm">{label}</span>
    </button>
  );
}
