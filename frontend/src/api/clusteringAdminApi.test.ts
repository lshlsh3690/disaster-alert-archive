// jest 객체는 npm test 실행 시 자동 주입된다. alertApi.test.ts 와 같은 방식으로 axios 를 가짜로 바꿔
// 실제 요청 대신 "어떤 경로·파라미터로 불렀는지"와 "응답에서 무엇을 꺼냈는지"만 본다.
// 실제 응답은 { data: ApiResponse } 모양이라 가짜 응답도 같은 모양으로 만든다.
const mockGet = jest.fn();

jest.mock("./axios", () => ({
  __esModule: true,
  default: { get: (...args: unknown[]) => mockGet(...args) },
}));

import { fetchClusteringDailyScores, fetchClusteringDisagreements } from "./clusteringAdminApi";

describe("clusteringAdminApi", () => {
  beforeEach(() => {
    mockGet.mockReset();
  });

  // 결정: 불일치 쌍 화면의 날짜 범위·종류 필터·페이지가 서버로 그대로 가야 사용자가 고른 조건의 쌍이 보인다.
  // 값이 안 붙으면 서버는 기본값(두 종류 모두, 0쪽, 20건)으로 응답해 필터가 조용히 무시된다.
  // Red: 뼈대는 요청을 보내지 않으므로 mockGet 호출이 0번이라 toHaveBeenCalledWith 단언에서 실패한다.
  it("fetchClusteringDisagreements 는 날짜 범위·종류·페이지를 쿼리 파라미터로 붙여 관리자 경로로 요청한다", async () => {
    // 준비: 응답은 ApiResponse 껍데기만 갖춘 빈 페이지(이 테스트의 관심은 요청 쪽이다)
    mockGet.mockResolvedValue({
      data: { success: true, data: { page: 2, size: 50, totalElements: 0, totalPages: 0, pairs: [] } },
    });

    // 실행: 하루 범위(from=to), 과병합만, 3번째 쪽(page=2, 0부터 시작), 50건씩
    await fetchClusteringDisagreements({
      from: "2026-11-17",
      to: "2026-11-17",
      kind: "OVER_MERGED",
      page: 2,
      size: 50,
    });

    // 검증: 경로는 계약의 /api/v1/admin/clustering/disagreements, 파라미터 5개가 모두 params 로 간다
    expect(mockGet).toHaveBeenCalledWith(
      "/api/v1/admin/clustering/disagreements",
      expect.objectContaining({
        params: expect.objectContaining({
          from: "2026-11-17",
          to: "2026-11-17",
          kind: "OVER_MERGED",
          page: 2,
          size: 50,
        }),
      }),
    );
  });

  // 결정: 서버는 ApiResponse({success, message, code, data}) 로 감싸 보내므로 호출자는 안쪽 data 만 받아야 한다.
  // 껍데기째 돌려주면 화면이 response.days 를 읽다 undefined 가 된다.
  // Red: 뼈대는 요청 없이 빈 days 를 돌려주므로 응답의 2건과 달라 toEqual 단언에서 실패한다.
  it("fetchClusteringDailyScores 는 ApiResponse 의 data 를 꺼내 돌려준다", async () => {
    // 준비: 계약 1절 모양의 응답. 11-17 은 채점됨, 11-16 은 기록 없음(날짜·상태만 오는 날)
    const body = {
      from: "2026-11-16",
      to: "2026-11-17",
      days: [
        {
          date: "2026-11-17",
          status: "SCORED",
          incidentCount: 8,
          pairCount: 6,
          purity: { numerator: 1, denominator: 1, percent: 100.0 },
          score: 100.0,
        },
        { date: "2026-11-16", status: "NO_RECORD" },
      ],
    };
    mockGet.mockResolvedValue({ data: { success: true, message: "요청이 성공했습니다.", code: 200, data: body } });

    // 실행
    const result = await fetchClusteringDailyScores("2026-11-16", "2026-11-17");

    // 검증: 껍데기(success·message·code)는 벗겨지고 data 와 똑같은 값이 나온다
    expect(result).toEqual(body);
  });
});
