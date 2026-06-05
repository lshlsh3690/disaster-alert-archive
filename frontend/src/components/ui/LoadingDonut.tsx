"use client";

/**
 * LoadingDonut
 *
 * progress(0~100) 값에 따라 채워지는 SVG 원형 프로그레스 인디케이터입니다.
 * stroke-dasharray로 원의 일부만 색칠하는 방식으로 구현합니다.
 */
export function LoadingDonut({ progress }: { progress: number }) {
  const r = 7, c = 2 * Math.PI * r;
  const dash = (progress / 100) * c;
  return (
    <svg width={18} height={18} viewBox="0 0 18 18" style={{ display: "block" }}>
      <circle cx={9} cy={9} r={r} fill="none" stroke="#334155" strokeWidth={2.5} />
      <circle cx={9} cy={9} r={r} fill="none" stroke="#60a5fa" strokeWidth={2.5}
        strokeDasharray={`${dash} ${c - dash}`}
        transform="rotate(-90 9 9)" />
    </svg>
  );
}
