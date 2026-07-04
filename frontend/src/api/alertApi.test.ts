

const mockGet = jest.fn().mockResolvedValue({ data: [] });

jest.mock("./axios", () => ({
  __esModule: true,
  default: { get: (...args: unknown[]) => mockGet(...args) },
}));

import { fetchLatestAlerts, searchAlerts, searchCombinedAlerts } from "./alertApi";

describe("alertApi lang forwarding", () => {
  beforeEach(() => {
    mockGet.mockClear();
  });

  it("fetchLatestAlerts는 선택된 lang을 쿼리 파라미터로 전달한다", async () => {
    await fetchLatestAlerts(5, "en").catch(() => {});

    expect(mockGet).toHaveBeenCalledWith(
      "/api/v1/alerts/latest",
      expect.objectContaining({ params: expect.objectContaining({ lang: "en" }) }),
    );
  });

  it("searchAlerts는 선택된 lang을 쿼리 파라미터로 전달한다", async () => {
    await searchAlerts({}, "ja").catch(() => {});

    expect(mockGet).toHaveBeenCalledWith(
      "/api/v1/alerts/search",
      expect.objectContaining({ params: expect.objectContaining({ lang: "ja" }) }),
    );
  });

  it("searchCombinedAlerts는 선택된 lang을 쿼리 파라미터로 전달한다", async () => {
    await searchCombinedAlerts({}, "zh").catch(() => {});

    expect(mockGet).toHaveBeenCalledWith(
      "/api/v1/alerts/search/combined",
      expect.objectContaining({ params: expect.objectContaining({ lang: "zh" }) }),
    );
  });
});
