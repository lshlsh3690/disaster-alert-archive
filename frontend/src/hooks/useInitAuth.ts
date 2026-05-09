// frontend/src/hooks/useInitAuth.ts
"use client";

import { useEffect, useRef } from "react";
import { useAuthStore } from "@/store/authStore";
import { getMyInfo } from "@/api/memberApi";
import { AxiosError } from "axios";

/**
 * OAuth/쿠키 기반 로그인 후 Zustand authStore를 서버 응답으로 동기화하는 훅.
 *
 * 호출 위치: 루트 레이아웃(layout.tsx) 또는 최상위 클라이언트 컴포넌트.
 *
 * 동작 원리:
 * - Zustand에 user가 없을 때(= OAuth 리다이렉트 직후, 새로고침 등)
 *   /api/v1/members/me 를 호출해 사용자 정보를 채운다.
 * - 이미 user가 있으면 재호출하지 않는다.
 * - 로그인되지 않은 상태(401)라면 조용히 무시한다.
 */
export function useInitAuth() {
  const user = useAuthStore((state) => state.user);
  const setUser = useAuthStore((state) => state.setUser);
  // 중복 호출 방지용 ref (Strict Mode 이중 실행 방어)
  const initCalledRef = useRef(false);

  useEffect(() => {
    // 이미 user가 있거나 초기화를 시도했으면 스킵
    if (user !== null || initCalledRef.current) return;

    initCalledRef.current = true;

    (async () => {
      try {
        // getMyInfo()는 SuccessResponse<MemberInfo> 형태를 반환:
        // { success, code, message, data: { memberId, nickname, email, role } }
        const response = await getMyInfo();

        // ApiResponse 래퍼 안의 실제 데이터를 꺼낸다
        const member = response?.data ?? response;

        if (!member || !member.email) {
          // 유효한 사용자 정보가 없으면 설정하지 않음
          return;
        }

        setUser({
          memberId: member.memberId ?? member.id ?? 0,
          nickname: member.nickname ?? "",
          email: member.email ?? "",
          role: member.role ?? null,
        });
      } catch (error: unknown) {
        // 401 = 로그인 안 된 상태 → 정상, 무시
        if (error instanceof AxiosError && error.response?.status === 401) {
          return;
        }
        // 그 외 에러는 개발 환경에서만 출력
        if (process.env.NODE_ENV !== "production") {
          console.warn("[useInitAuth] 사용자 정보 초기화 실패:", error);
        }
        // initCalledRef를 false로 되돌려 재시도 가능하게 하지 않는다
        // (실패 시 반복 요청을 막기 위해 그냥 둔다)
      }
    })();
  }, [user, setUser]);
}
