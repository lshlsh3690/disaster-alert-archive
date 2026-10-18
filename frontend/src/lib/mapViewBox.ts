// 대시보드 지도에서 시군구를 눌렀을 때 그 영역으로 확대할 SVG viewBox 를 계산한다. 순수 함수라 jest 로 검증한다.

/** [minX, minY, maxX, maxY] — koreaSido.data.ts 와 같은 화면 좌표(px) */
export type BBox = [number, number, number, number];

export interface ViewBox {
  x: number;
  y: number;
  w: number;
  h: number;
}

/**
 * 지도 전체를 보여주는 기본 viewBox. koreaSido.data.ts 의 원본 viewBox(0 0 760 714)는 독도 쪽 오른쪽 여백이 넓어 육지가 왼쪽으로
 * 쏠려 보이므로 육지 기준(울릉도 x≈641 까지)으로 잘라 중앙 정렬한 값이다. 독도(x≈737)는 이 밖이라 보이지 않는다.
 */
export const BASE_VIEWBOX: ViewBox = { x: 0, y: 0, w: 682, h: 714 };

/** 경계 상자 둘레에 두는 여백 비율 */
export const DEFAULT_PADDING = 0.25;

/**
 * 확대 한도(기본 viewBox 대비 폭 비율). 아주 작은 구역을 너무 크게 확대하지 않는다.
 * 0.02 는 viewBox 한 변이 약 14 단위(화면에서 1 단위 ≈ 60px)라는 뜻으로, 가장 작은 시군구(서울 종로구 급, 배율 0.015)도
 * 화면의 절반 이상을 채운다. 읍면동 경계를 단순화한 허용오차(0.06 단위 ≈ 3.7px, scripts/map-regions)가 눈에 띄지 않는
 * 한계 배율이라 이보다 더 확대하려면 허용오차를 같이 낮춰야 한다.
 */
export const MIN_SCALE = 0.02;

/**
 * bbox 를 여백과 함께 담는 확대 viewBox.
 * - 기본 viewBox 와 같은 종횡비를 유지한다(레터박스가 생기지 않게).
 * - 확대 배율은 [minScale, 1] 로 제한한다. 1 이면 기본 viewBox 와 같다.
 * - 중심은 bbox 중심이고, 결과가 기본 viewBox 범위 밖으로 나가지 않게 밀어 넣는다.
 */
export function fitViewBox(
  bbox: BBox,
  base: ViewBox = BASE_VIEWBOX,
  padding: number = DEFAULT_PADDING,
  minScale: number = MIN_SCALE
): ViewBox {
  const [minX, minY, maxX, maxY] = bbox;

  // 여백을 더한 크기가 들어가는 배율 중 큰 쪽(폭/높이)을 쓴다. 종횡비는 기본 viewBox 와 같게 유지한다.
  const fit = Math.max(((maxX - minX) * (1 + padding)) / base.w, ((maxY - minY) * (1 + padding)) / base.h);
  const scale = Math.min(1, Math.max(minScale, fit));
  const w = base.w * scale;
  const h = base.h * scale;

  // bbox 중심에 맞추되 기본 viewBox 밖으로 나가지 않게 밀어 넣는다(scale=1 이면 항상 기본 viewBox 와 같다).
  const clamp = (v: number, lo: number, hi: number) => Math.min(hi, Math.max(lo, v));
  return {
    x: clamp((minX + maxX) / 2 - w / 2, base.x, base.x + base.w - w),
    y: clamp((minY + maxY) / 2 - h / 2, base.y, base.y + base.h - h),
    w,
    h,
  };
}

/**
 * 여러 경계 상자를 모두 담는 하나의 상자. 시도를 눌렀을 때 그 시도의 시군구 경계 상자들을 합쳐 확대할 영역을 구한다.
 * 합칠 상자가 없으면 null.
 */
export function unionBBox(boxes: BBox[]): BBox | null {
  if (boxes.length === 0) return null;
  return boxes.reduce<BBox>(
    (acc, [minX, minY, maxX, maxY]) => [
      Math.min(acc[0], minX),
      Math.min(acc[1], minY),
      Math.max(acc[2], maxX),
      Math.max(acc[3], maxY),
    ],
    [Infinity, Infinity, -Infinity, -Infinity]
  );
}
