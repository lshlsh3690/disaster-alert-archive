"use client";

import { EvalLabelsSection } from "../_evalLabels";

// 평가 셋 라벨 관리자 화면. 클러스터링 평가의 정답(사람 라벨)을 셋별로 보고, 사유와 함께 고치고, 이력을 본다.
// 검증 대시보드와 달리 기간과 상관없는 데이터라 화면을 나눴다. 관리자 확인은 ../layout.tsx 가 맡는다.
export default function EvalLabelsPage() {
  return (
    <>
      <div className="space-y-2">
        <h1 className="text-xl font-bold text-[var(--ink)]">평가 셋 라벨</h1>
        <p className="text-sm text-[var(--text-muted)]">
          클러스터링 평가의 정답입니다. 평가 도구(--eval.dataset)가 이 값을 읽으므로, 여기서 고친 라벨이 다음 평가의 정답이 됩니다.
        </p>
      </div>
      <EvalLabelsSection />
    </>
  );
}
