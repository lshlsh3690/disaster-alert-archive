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
 * - 마운트 시 딱 한 번 /api/v1/members/me 를 호출해 서버 세션을 검증한다.
 *   authStore에 영속화(persist)된 user가 있어도 검증을 건너뛰지 않는다 —
 *   쿠키 만료나 서버 세션 종료 후에도 localStorage에는 이전 user가 그대로
 *   남아있을 수 있어, 그 상태를 곧바로 신뢰하면 /user/settings 등이 만료된
 *   사용자 정보를 보여줄 수 있다.
 * - 로그인 직후(useLogin이 setUser를 직접 호출한 경우)에는 이 훅이 이미
 *   같은 세션에서 한 번 실행된 뒤이므로(initCalledRef가 마운트당 1회로
 *   막아줌) 중복 호출되지 않는다.
 * - 로그인되지 않은 상태(401)라면, 영속화된 stale user가 남아있을 수 있으므로
 *   logout()으로 정리한다.
 * - 검증 요청이 진행되는 동안 logout()이 호출되면(레이스), 응답이 늦게
 *   도착해도 로그아웃 상태를 되돌리지 않는다.
 */
export function useInitAuth() {
  const setUser = useAuthStore((state) => state.setUser);
  const logout = useAuthStore((state) => state.logout);
  const setInitializing = useAuthStore((state) => state.setInitializing);
  // 중복 호출 방지용 ref (Strict Mode 이중 실행 및 재로그인 방지)
  const initCalledRef = useRef(false);

  useEffect(() => {
    if (initCalledRef.current) return;
    initCalledRef.current = true;

    // 이 요청이 진행되는 동안 로그아웃되면 늦게 도착한 응답을 무시하기 위한 플래그
    let loggedOutMeanwhile = false;
    const unsubscribe = useAuthStore.subscribe((state, prevState) => {
      if (prevState.user !== null && state.user === null) {
        loggedOutMeanwhile = true;
      }
    });

    (async () => {
      try {
        // getMyInfo()는 SuccessResponse<MemberInfo> 형태를 반환:
        // { success, code, message, data: { memberId, nickname, email, role } }
        const response = await getMyInfo();

        // ApiResponse 래퍼 안의 실제 데이터를 꺼낸다
        const member = response?.data ?? response;

        if (loggedOutMeanwhile || !member || !member.email) {
          // 유효한 사용자 정보가 없거나, 응답이 오는 사이 로그아웃됐으면 반영하지 않음
          return;
        }

        setUser({
          memberId: member.memberId ?? member.id ?? 0,
          nickname: member.nickname ?? "",
          email: member.email ?? "",
          role: member.role ?? null,
        });
      } catch (error: unknown) {
        // 401 = 로그인 안 된 상태(또는 세션 만료) → 영속화된 stale user가 남아있을 수
        // 있으므로 제거한다. 로그아웃 레이스 중이면 logout()이 이미 호출됐으므로 스킵.
        if (error instanceof AxiosError && error.response?.status === 401) {
          if (!loggedOutMeanwhile) logout();
          return;
        }
        // 그 외 에러는 개발 환경에서만 출력
        if (process.env.NODE_ENV !== "production") {
          console.warn("[useInitAuth] 사용자 정보 초기화 실패:", error);
        }
        // initCalledRef를 false로 되돌려 재시도 가능하게 하지 않는다
        // (실패 시 반복 요청을 막기 위해 그냥 둔다)
      } finally {
        unsubscribe();
        setInitializing(false);
      }
    })();

    return () => unsubscribe();
  }, [setUser, logout, setInitializing]);
}
