import type { RegionImpact } from "@/types/risk";

/**
 * 위험도 점수 공용 로직 — 지도(AlertRiskMap)와 통계 카드(AlertRiskSection)가 함께 사용.
 * 등급 기준이 두 곳에서 어긋나지 않도록 한 곳에서만 정의한다.
 */

/** 영향 등급 (정규화 점수 절대 기준 4단계). */
export type ImpactGrade = 0 | 1 | 2 | 3;

/** 백엔드 RiskConstants.NORMALIZE_DIVISOR 와 동일 — raw 최댓값(≈2.7)을 0~1로 압축. */
export const NORMALIZE_DIVISOR = 2.0;

/** raw impactScore → 0~1 정규화 (백엔드 RiskScore.normalize 와 동일 규칙). */
export function normalizeScore(score: number): number {
  return Math.min(1, score / NORMALIZE_DIVISOR);
}

/**
 * 절대 기준 등급. 정규화 점수(0~1) 기준 0.25/0.5/0.75 컷.
 * 예) 호우·안전안내 ≈ 0.27 → 주의, 지진·위급재난 ≈ 0.6 → 경계.
 */
export function scoreToGrade(score: number): ImpactGrade {
  const n = normalizeScore(score);
  if (n < 0.25) return 0;
  if (n < 0.5)  return 1;
  if (n < 0.75) return 2;
  return 3;
}

/**
 * 법정동 단위 영향 점수 → 시군구(코드 앞 5자리) 단위로 집계.
 * 백엔드 RegionRiskIndex 와 동일하게 MAX 결합.
 */
export function aggregateBySigungu(impacts: RegionImpact[]): Map<string, number> {
  const m = new Map<string, number>();
  impacts.forEach(({ regionCode, impactScore }) => {
    const sig = regionCode.slice(0, 5);
    m.set(sig, Math.max(m.get(sig) ?? 0, impactScore));
  });
  return m;
}
