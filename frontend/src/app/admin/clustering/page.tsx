"use client";

import { useState } from "react";
import { clampEnd, clampStart, defaultRange } from "./_format";
import { DailyChecksSection, DailyScoresSection, DisagreementsSection, HardPairGradesSection, JudgeVersionsSection } from "./_sections";

// 클러스터링 운영 검증 관리자 화면(specs/008-clustering-admin-dashboard). 쓰기는 불일치 쌍 사람 라벨 저장뿐이다.
// 관리자 확인과 화면 사이 탭은 layout.tsx 가 맡는다 — 이 화면은 ADMIN 일 때만 마운트된다.

const INPUT = "border border-[var(--line)] rounded-[var(--radius-control)] px-2 py-1 bg-[var(--surface)] text-[var(--text-body)]";

export default function ClusteringAdminPage() {
  const [range, setRange] = useState(defaultRange);

  // 바꾼 쪽은 그대로 두고 반대쪽을 맞춘다 — 시작 ≤ 끝, 길이 366일 이하(서버 상한과 같다)
  const changeFrom = (from: string) => from && setRange((r) => ({ from, to: clampEnd(from, r.to) }));
  const changeTo = (to: string) => to && setRange((r) => ({ from: clampStart(r.from, to), to }));

  return (
    <>
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
      <DisagreementsSection from={range.from} to={range.to} />
      <DailyChecksSection from={range.from} to={range.to} />
      <JudgeVersionsSection />
      <HardPairGradesSection />
    </>
  );
}
