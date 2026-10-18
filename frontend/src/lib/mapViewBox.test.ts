import { BASE_VIEWBOX, fitViewBox, unionBBox, type BBox } from "./mapViewBox";

// 기본 viewBox: 682 x 714
const BASE = BASE_VIEWBOX;

describe("fitViewBox", () => {
  it("작은 구역은 최소 배율(0.02)까지만 확대하고 bbox 중심에 맞춘다", () => {
    // 서울 종로구 크기(7.4 x 8.3). 여백을 더해도 배율이 0.015 라 최소 배율 0.02 로 제한된다.
    const bbox: BBox = [253.2, 145.7, 260.6, 154];

    const vb = fitViewBox(bbox);

    expect(vb.w).toBeCloseTo(682 * 0.02, 2); // 13.64
    expect(vb.h).toBeCloseTo(714 * 0.02, 2); // 14.28
    // 중심 (256.9, 149.85) 이 viewBox 중앙에 온다
    expect(vb.x + vb.w / 2).toBeCloseTo(256.9, 2);
    expect(vb.y + vb.h / 2).toBeCloseTo(149.85, 2);
  });

  it("중간 크기 구역은 여백 25% 를 더한 크기로 확대한다", () => {
    const bbox: BBox = [100, 200, 300, 400]; // 200 x 200

    const vb = fitViewBox(bbox);

    // 폭 기준: 200 * 1.25 = 250 → 배율 250/682
    expect(vb.w).toBeCloseTo(250, 2);
    expect(vb.h).toBeCloseTo((714 * 250) / 682, 2);
    expect(vb.x).toBeCloseTo(75, 2);
  });

  it("세로로 긴 구역은 높이 기준으로 배율을 정한다", () => {
    const bbox: BBox = [300, 100, 320, 400]; // 20 x 300

    const vb = fitViewBox(bbox);

    // 높이 기준: 300 * 1.25 = 375 → 배율 375/714
    expect(vb.h).toBeCloseTo(375, 2);
    expect(vb.w).toBeCloseTo((682 * 375) / 714, 2);
  });

  it("결과는 항상 기본 viewBox 와 같은 종횡비다", () => {
    const boxes: BBox[] = [
      [253.2, 145.7, 260.6, 154],
      [100, 200, 300, 400],
      [300, 100, 320, 400],
      [2, 300, 6, 304],
    ];

    for (const bbox of boxes) {
      const vb = fitViewBox(bbox);
      expect(vb.w / vb.h).toBeCloseTo(BASE.w / BASE.h, 6);
    }
  });

  it("전국처럼 큰 영역은 기본 viewBox 보다 커지지 않는다", () => {
    const vb = fitViewBox([24, 23, 744, 690]);

    expect(vb).toEqual(BASE);
  });

  it("왼쪽·위 가장자리 구역은 기본 viewBox 범위 안으로 밀어 넣는다", () => {
    // 중심이 x=4 라 그대로면 x 가 음수가 된다
    const vb = fitViewBox([2, 300, 6, 304]);

    expect(vb.x).toBe(0);
    expect(vb.y).toBeGreaterThanOrEqual(0);
  });

  it("오른쪽·아래 가장자리 구역은 기본 viewBox 범위 안으로 밀어 넣는다", () => {
    const vb = fitViewBox([676, 708, 680, 712]);

    expect(vb.x + vb.w).toBeCloseTo(BASE.x + BASE.w, 6);
    expect(vb.y + vb.h).toBeCloseTo(BASE.y + BASE.h, 6);
  });

  it("범위 안의 bbox 는 결과 viewBox 에 모두 담긴다", () => {
    const bbox: BBox = [253.2, 145.7, 260.6, 154];

    const vb = fitViewBox(bbox);

    expect(vb.x).toBeLessThanOrEqual(bbox[0]);
    expect(vb.y).toBeLessThanOrEqual(bbox[1]);
    expect(vb.x + vb.w).toBeGreaterThanOrEqual(bbox[2]);
    expect(vb.y + vb.h).toBeGreaterThanOrEqual(bbox[3]);
  });

  it("크기가 0 인 bbox(점)도 최소 배율로 그 위치를 중심에 둔다", () => {
    const vb = fitViewBox([300, 300, 300, 300]);

    expect(vb.w).toBeCloseTo(682 * 0.02, 2);
    expect(vb.x + vb.w / 2).toBeCloseTo(300, 2);
    expect(vb.y + vb.h / 2).toBeCloseTo(300, 2);
  });

  it("기본 viewBox 밖으로 걸친 bbox(독도를 포함한 울릉군 등)도 결과는 기본 viewBox 안에 머문다", () => {
    // 데이터에서는 독도를 뺀 영역으로 계산하지만, 범위 밖 값이 들어와도 viewBox 가 지도 밖으로 나가지는 않아야 한다
    const vb = fitViewBox([630, 155, 744, 170]);

    expect(vb.x).toBeGreaterThanOrEqual(BASE.x);
    expect(vb.y).toBeGreaterThanOrEqual(BASE.y);
    expect(vb.x + vb.w).toBeLessThanOrEqual(BASE.x + BASE.w + 1e-9);
    expect(vb.y + vb.h).toBeLessThanOrEqual(BASE.y + BASE.h + 1e-9);
  });

  it("여백과 최소 배율을 바꿀 수 있다", () => {
    // 여백 0, 최소 배율 0.001 → bbox 가 viewBox 에 딱 맞는다 (폭 기준 200/682 > 높이 기준 200/714)
    const vb = fitViewBox([100, 200, 300, 400], BASE, 0, 0.001);

    expect(vb.w).toBeCloseTo(200, 2);
    expect(vb.h).toBeCloseTo((714 * 200) / 682, 2);
  });
});

describe("unionBBox", () => {
  it("여러 경계 상자를 모두 담는 하나의 상자로 합친다", () => {
    const boxes: BBox[] = [
      [0, 5, 10, 20],
      [5, 0, 15, 10],
      [3, 2, 4, 25],
    ];

    expect(unionBBox(boxes)).toEqual([0, 0, 15, 25]);
  });

  it("상자가 하나면 그대로 돌려준다", () => {
    expect(unionBBox([[1, 2, 3, 4]])).toEqual([1, 2, 3, 4]);
  });

  it("비어 있으면 null 이다(합칠 상자가 없는 시도)", () => {
    expect(unionBBox([])).toBeNull();
  });
});
