// 재난 유형별 칩 색상 — 의미가 비슷한 유형끼리 팔레트를 공유한다.
// 키는 한국어 원본 유형명(백엔드 canonical). 매핑이 없으면 중립(gray) 사용.

const PALETTE = {
  red: { bg: "#fdecea", text: "#c0392b" }, // 화재·폭발·테러 등 즉각 위험
  orange: { bg: "#fff1e3", text: "#c1670c" }, // 화재 위험·폭염·건조
  blue: { bg: "#e7eefb", text: "#2c5fd6" }, // 강수·풍수해
  cyan: { bg: "#e3f4f7", text: "#0e7490" }, // 한파·대기·시야
  amber: { bg: "#f6efe2", text: "#92600a" }, // 지질 재해
  green: { bg: "#e8f5ec", text: "#1d7a44" }, // 보건·환경
  slate: { bg: "#eef2f7", text: "#475569" }, // 교통
  purple: { bg: "#f1ecfb", text: "#6d3bd1" }, // 인프라·기타 사회재난
  gray: { bg: "#eef1f5", text: "#687386" }, // 기타/미분류
} as const;

type PaletteKey = keyof typeof PALETTE;

const TYPE_TO_PALETTE: Record<string, PaletteKey> = {
  화재: "red", 폭발: "red", 테러: "red", 민방공: "red",
  산불: "orange", 폭염: "orange", 건조: "orange",
  호우: "blue", 홍수: "blue", 태풍: "blue", 강풍: "blue", 풍랑: "blue",
  한파: "cyan", 대설: "cyan", 안개: "cyan", 가뭄: "cyan", 황사: "cyan", 미세먼지: "cyan",
  지진: "amber", 지진해일: "amber", 산사태: "amber", 붕괴: "amber",
  전염병: "green", 가축질병: "green", 환경오염사고: "green",
  교통사고: "slate", 교통통제: "slate", 교통: "slate",
  정전: "purple", 수도: "purple", 통신: "purple", 에너지: "purple", 금융: "purple", AI: "purple",
};

export function disasterTypePalette(type?: string | null) {
  return PALETTE[TYPE_TO_PALETTE[type ?? ""]] ?? PALETTE.gray;
}

// 인라인 style 바인딩용
export function disasterTypeChipStyle(type?: string | null) {
  const p = disasterTypePalette(type);
  return { backgroundColor: p.bg, color: p.text };
}
