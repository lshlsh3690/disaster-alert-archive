/**
 * common.ts
 *
 * charts/ 아래 모든 차트 컴포넌트가 공유하는 툴팁 스타일·타입·로케일 매핑입니다.
 * 예전엔 _DistributionCharts.tsx/_TimeCharts.tsx/_WeatherCharts.tsx 세 파일에
 * 거의 동일한 내용이 각각 복사돼 있었습니다 — 여기 한 곳으로 모았습니다.
 */

export const LANG_LOCALE: Record<string, string> = { ko: "ko-KR", en: "en-US", zh: "zh-CN", ja: "ja-JP" };

// 툴팁 박스 배경·모서리 둥글기 등 컨테이너 스타일
export const TT_BOX: React.CSSProperties = {
  background: "#1e293b",  // 어두운 네이비 배경
  borderRadius: 6,
  padding: "6px 10px",
  border: "none",
};
// 툴팁 제목(날짜, 지역명 등) 스타일
export const TT_LABEL: React.CSSProperties = { color: "#94a3b8", fontSize: 10, margin: "0 0 2px 0" };
// 툴팁 숫자(건수 등) 스타일
export const TT_VALUE: React.CSSProperties = { color: "#fff", fontSize: 12, fontWeight: 700, margin: 0 };

// Recharts가 content 컴포넌트에 자동으로 넘겨주는 props 형태입니다.
// active: 마우스가 차트 위에 있는지 여부 / payload: 현재 호버된 데이터 포인트들의 배열 / label: X축 레이블 값
export type TTProps = {
  active?: boolean;
  payload?: Array<{
    value?: number;
    dataKey?: string;
    payload?: Record<string, number>;
  }>;
  label?: string;
};
