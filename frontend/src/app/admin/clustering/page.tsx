"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuthStore } from "@/store/authStore";
import Button from "@/components/ui/Button";
import { clampEnd, clampStart, defaultRange } from "./_format";
import { DailyChecksSection, DailyScoresSection, DisagreementsSection, HardPairGradesSection, JudgeVersionsSection } from "./_sections";

// 클러스터링 운영 검증 관리자 화면(specs/008-clustering-admin-dashboard). 읽기 전용이다.
// ADMIN 이 아니면 안내 모달 뒤 다른 화면으로 보내고 요청을 보내지 않는다 — 영역 컴포넌트는 ADMIN 일 때만 마운트된다.

/** 접근할 수 없을 때 띄우는 안내 모달. 확인을 누르면 target 으로 이동한다(뒤로 가기로 다시 오지 않게 replace). */
function RedirectNotice({ message, target }: { message: string; target: string }) {
  const router = useRouter();
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4" role="dialog" aria-modal="true">
      <div className="w-full max-w-sm space-y-4 rounded-[var(--radius-panel-card)] bg-[var(--surface)] p-6 text-center shadow-[0_10px_30px_rgba(28,39,60,0.12)]">
        <p className="text-[var(--ink)]">{message}</p>
        <Button fullWidth onClick={() => router.replace(target)} autoFocus>
          확인
        </Button>
      </div>
    </div>
  );
}

const INPUT ="border border-[var(--line)] rounded-[var(--radius-control)] px-2 py-1 bg-[var(--surface)] text-[var(--text-body)]";

export default function ClusteringAdminPage() {
  const user = useAuthStore((s) => s.user);
  const isInitializing = useAuthStore((s) => s.isInitializing);
  const [range, setRange] = useState(defaultRange);
  const router = useRouter();

  // 이 화면을 보던 중 로그인 상태였다가 user 가 사라지면(로그아웃) 모달 없이 바로 홈으로 보낸다.
  // 처음부터 로그인 안 한 방문(딥링크)만 아래 안내 모달을 띄운다.
  // 초기화(서버 확인)가 끝난 뒤의 user 만 센다 — localStorage 에 남은 옛 user 로 세면, 세션 만료로 들어온
  // 딥링크도 "여기서 로그아웃"으로 오인해 로그인 안내 없이 홈으로 보낸다
  const [seenUser, setSeenUser] = useState(false);
  if (!isInitializing && user && !seenUser) setSeenUser(true);
  const loggedOutHere = seenUser && !user;
  useEffect(() => {
    if (loggedOutHere) router.replace("/");
  }, [loggedOutHere, router]);

  if (isInitializing) return null;

  // 이 화면에서 로그아웃한 경우 — 헤더가 홈으로 보내므로 안내 모달 없이 아무것도 그리지 않는다(위 effect 가 보조)
  if (!user && loggedOutHere) return null;

  // 로그인 안 함 → 로그인 화면으로, 관리자 아님 → 홈으로. 안내 모달에서 확인을 누르면 이동한다
  if (!user) {
    return <RedirectNotice message="로그인이 필요한 화면입니다." target="/login" />;
  }
  if (user.role !== "ADMIN") {
    return <RedirectNotice message="관리자만 볼 수 있는 화면입니다." target="/" />;
  }

  // 바꾼 쪽은 그대로 두고 반대쪽을 맞춘다 — 시작 ≤ 끝, 길이 366일 이하(서버 상한과 같다)
  const changeFrom = (from: string) => from && setRange((r) => ({ from, to: clampEnd(from, r.to) }));
  const changeTo = (to: string) => to && setRange((r) => ({ from: clampStart(r.from, to), to }));

  return (
    <main className="bg-[var(--canvas)] min-h-[calc(100vh-48px)]">
      <div className="mx-auto max-w-7xl px-4 py-6 space-y-4">
        <div className="space-y-2">
          <h1 className="text-xl font-bold text-[var(--ink)]">클러스터링 검증</h1>
          <p className="text-sm text-[var(--text-muted)]">
            정답은 사람 라벨뿐입니다. 아래 점수는 LLM 판사의 판정을 정답 삼아 센 값이라, 같은 줄의 판사 일치율만큼만 믿을 수 있습니다.
          </p>
          <div className="flex flex-wrap items-center gap-2 text-sm">
            <label className="text-[var(--text-muted)]" htmlFor="range-from">기간</label>
            <input id="range-from" type="date" value={range.from} onChange={(e) => changeFrom(e.target.value)} className={INPUT} />
            <span className="text-[var(--text-muted)]">~</span>
            <input aria-label="기간 끝" type="date" value={range.to} onChange={(e) => changeTo(e.target.value)} className={INPUT} />
          </div>
        </div>
        <DailyScoresSection from={range.from} to={range.to} />
        <DisagreementsSection key={`${range.from}-${range.to}`} from={range.from} to={range.to} />
        <DailyChecksSection from={range.from} to={range.to} />
        <JudgeVersionsSection />
        <HardPairGradesSection />
      </div>
    </main>
  );
}
