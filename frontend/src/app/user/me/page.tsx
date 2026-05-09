"use client";

import { getMyInfo } from "@/api/memberApi";
import { useQuery } from "@tanstack/react-query";
import { useState } from "react";

export default function MePage() {
  const [myInfo, setMyInfo] = useState({
    email: "",
    nickname: "",
    role: "",
  } as {
    email: string;
    nickname: string;
    role: string;
  });

  const { data, isSuccess } = useQuery({
    queryKey: ["myInfo"],
    queryFn: getMyInfo,
    retry: false,
    refetchOnWindowFocus: false,
  });

  if (
    isSuccess &&
    data &&
    (myInfo.email !== data.data.email ||    // ← data → data.data
     myInfo.nickname !== data.data.nickname ||
     myInfo.role !== data.data.role)
  ) {
    setMyInfo({
      email: data.data.email,       // ← data → data.data
      nickname: data.data.nickname,
      role: data.data.role,
    });
  }

  return (
    <div>
      {myInfo && (
        <div className="mb-4">
          <h2 className="text-lg font-semibold">내 정보</h2>
          <p>이메일: {myInfo.email}</p>
          <p>닉네임: {myInfo.nickname}</p>
          <p>역할: {myInfo.role}</p>
        </div>
      )}
      <h1 className="text-xl font-bold mb-4">👤 내 정보</h1>
      <p>이곳에서 내 정보를 확인하고 수정할 수 있습니다.</p>
    </div>
  );
}