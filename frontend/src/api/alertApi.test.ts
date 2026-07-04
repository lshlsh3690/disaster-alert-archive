//jest 객체는 npm test 실행 시 자동으로 글로벌 스코프에 주입되므로 import할 필요가 없다
//https://jestjs.io/docs/mock-function-api#mockfnmockresolvedvaluevalue
//실제 응답에는 data 속성이 존재하므로 mockResolvedValue()로 반환되는 객체에도 data 속성을 포함시킨다.
const mockGet = jest.fn().mockResolvedValue({ data: [] });

//axiods 모듈을 가짜 객체로 대체한다. axios.get() 호출 시 mockGet()이 호출되도록 한다.
jest.mock("./axios", () => ({
  __esModule: true,
  default: { get: (...args: unknown[]) => mockGet(...args) },
}));

import { fetchLatestAlerts, searchAlerts, searchCombinedAlerts } from "./alertApi";

//alertApi의 각 함수가 선택된 언어를 쿼리 파라미터로 전달하는지 테스트한다.
describe("alertApi의 lang 파라미터 전달", () => {
  //각 테스트 케이스 실행 전 mockGet 호출 기록을 초기화한다.
  beforeEach(() => {
    mockGet.mockClear();
  });

  //it() 함수는 테스트 케이스를 정의한다. 첫 번째 인자는 테스트 설명, 두 번째 인자는 테스트 함수이다.
  //각 테스트 케이스 순서대로 영어, 일본어, 중국어를 선택된 언어로 전달하는지 확인한다.
  it("fetchLatestAlerts는 선택된 lang을 쿼리 파라미터로 전달한다", async () => {
    await fetchLatestAlerts(5, "en").catch(() => {});

    //expect() 함수는 기대값을 검증한다. toHaveBeenCalledWith()는 mockGet이 특정 인자와 함께 호출되었는지 확인한다.
    expect(mockGet).toHaveBeenCalledWith(
      "/api/v1/alerts/latest",
      //expect.objectContaining()는 객체의 일부 속성만 검증할 때 사용한다. 여기서는 params.lang 속성만 확인한다.
      expect.objectContaining({ params: expect.objectContaining({ lang: "en" }) }),
    );
    // api/v1/alerts/latest의 parameter가 limit=5, lang=en으로 전달되었는지 확인한다.
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
