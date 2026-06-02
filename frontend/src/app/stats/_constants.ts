// ─── 공통 타입 ────────────────────────────────────────────────────────────────

export interface TypeStat   { type: string | null;  count: number }
export interface LevelStat  { level: string | null; count: number }
export interface RegionStat { region: string;        count: number }

export interface LibItem {
  id: string; kind: string; title: string; desc: string; help: string;
  defaultSpan: number; icon: string; sample?: boolean;
  variants?: { key: string; label: string }[];
}

export interface WidgetItem { id: string; libId: string; span: number; variant?: string }

// ─── 색상 팔레트 ──────────────────────────────────────────────────────────────

export const TYPE_COLORS   = ["#3b82f6","#f59e0b","#f97316","#a855f7","#06b6d4","#9ca3af"];
export const BAR_COLORS    = ["#ef4444","#f97316","#f59e0b","#3b82f6","#06b6d4","#a855f7","#6b7280","#9ca3af"];
export const STACKED_COLORS = ["#3b82f6","#f59e0b","#f97316","#a855f7"];
export const CHART_COLORS  = ["#3b82f6","#f97316","#22c55e","#a855f7","#ef4444","#eab308","#06b6d4","#ec4899","#84cc16","#f43f5e"];

export const LEVEL_META: Record<string, { text: string; bg: string; textCls: string; border: string; solid: string }> = {
  LEVEL_1: { text: "안전안내", bg: "bg-blue-50",   textCls: "text-blue-700",   border: "border-blue-200",   solid: "#3b82f6" },
  LEVEL_2: { text: "긴급재난", bg: "bg-orange-50", textCls: "text-orange-700", border: "border-orange-200", solid: "#f97316" },
  LEVEL_3: { text: "위급재난", bg: "bg-red-50",    textCls: "text-red-700",    border: "border-red-200",    solid: "#ef4444" },
};

// ─── 히트맵 상수 ──────────────────────────────────────────────────────────────

export const WEEKDAYS = ["월","화","수","목","금","토","일"];
// PostgreSQL EXTRACT(DOW): 0=Sun,1=Mon,...,6=Sat → 표시 순서 Mon=0..Sun=6
export const DOW_TO_IDX: Record<number, number> = { 1:0, 2:1, 3:2, 4:3, 5:4, 6:5, 0:6 };

// ─── 위젯 라이브러리 정의 ─────────────────────────────────────────────────────

export const WIDGET_LIBRARY: LibItem[] = [
  { id: "type-donut", kind: "donut",   title: "재난 유형 분포",     desc: "유형별 점유율 도넛 차트",    help: "현재 필터 기간의 재난문자를 유형별(폭염·태풍·산불 등)로 분류해 점유율을 보여줍니다.",                                                             defaultSpan: 6,  icon: "🍩", variants: [{ key: "donut", label: "도넛" }, { key: "hbar", label: "가로막대" }, { key: "vbar", label: "세로막대" }] },
  { id: "sido-bar",   kind: "hbar",    title: "시·도 TOP 8",        desc: "지역별 발생 건수 가로 막대", help: "시·도별 재난문자 발생 건수를 내림차순으로 나타냅니다. 재난문자 페이지에서 특정 시·도를 선택하면 해당 시·도의 시·군·구 전체 목록으로 전환됩니다.", defaultSpan: 6,  icon: "📊", variants: [{ key: "hbar", label: "가로막대" }, { key: "vbar", label: "세로막대" }, { key: "donut", label: "도넛" }] },
  { id: "level-card", kind: "levels",  title: "재난 경보 단계 분포", desc: "안전/긴급/위급 비율",        help: "재난 경보 단계(안전안내·긴급재난·위급재난)별 건수와 비율을 보여줍니다. 위급재난 비율이 높을수록 붉은 게이지로 표시됩니다.",                      defaultSpan: 6,  icon: "🚨", variants: [{ key: "card", label: "카드" }, { key: "donut", label: "도넛" }, { key: "hbar", label: "막대" }] },
  { id: "daily-line", kind: "line",    title: "일별 발생 추이",      desc: "날짜별 발생 건수 시계열",    help: "날짜별 재난문자 발생 건수를 그래프로 보여줍니다. 필터 기간이 길수록 추이 파악에 유용합니다.",                                                     defaultSpan: 12, icon: "📈", variants: [{ key: "line", label: "꺾은선" }, { key: "bar", label: "막대" }] },
  { id: "heatmap",    kind: "heatmap", title: "요일·시간대 히트맵",  desc: "발생 빈도 요일×시간대 분석", help: "요일(행)과 시간대(열)의 교차점에 재난문자 발생 건수를 색 농도로 표시합니다. 선택 기간 내 누적 합산 값입니다.",                                 defaultSpan: 12, icon: "🔥", variants: [{ key: "heatmap", label: "히트맵" }, { key: "dow", label: "요일별" }, { key: "hour", label: "시간대별" }] },
  { id: "stacked",    kind: "stacked", title: "월별 유형별 발생",    desc: "유형별 월간 누적 발생 추이", help: "최근 12개월간 월별 재난문자를 유형별로 누적해 보여줍니다. 계절별 재난 패턴 파악에 유용합니다.",                                                 defaultSpan: 6,  icon: "🪜", variants: [{ key: "area", label: "면적" }, { key: "bar", label: "누적막대" }] },
  { id: "compare",    kind: "compare", title: "전년 동기 대비",      desc: "월별 발생 건수 YoY 비교",    help: "올해(파란색)와 작년(회색)의 월별 발생 건수를 비교합니다. 막대에 마우스를 올리면 월별 상세 수치를 확인할 수 있습니다.",                          defaultSpan: 12, icon: "⚖️", variants: [{ key: "bar", label: "막대" }, { key: "line", label: "꺾은선" }] },
  { id: "weather-overlay", kind: "weather",  title: "날씨·재난 상관관계",  desc: "기온·강수·발생건수 복합 차트", help: "날짜별 재난문자 발생 건수와 평균기온을 재난 유형별·지역별로 겹쳐 보여줍니다.", defaultSpan: 12, icon: "🌤️", variants: [{ key: "type", label: "유형별" }, { key: "region", label: "지역별" }] },
  { id: "weather-scatter", kind: "weather2", title: "기온별 발생 산점도",   desc: "기온 vs 발생건수 산점도",      help: "평균기온(X축)과 발생건수(Y축)의 관계를 재난 유형별로 산점도로 나타냅니다. 버블 크기는 최대강수량을 나타냅니다.",           defaultSpan: 6,  icon: "🔵" },
];

export const DEFAULT_LAYOUT: WidgetItem[] = [
  { id: "w1", libId: "type-donut",       span: 6  },
  { id: "w2", libId: "sido-bar",         span: 6  },
  { id: "w3", libId: "level-card",       span: 6  },
  { id: "w4", libId: "daily-line",       span: 12 },
  { id: "w5", libId: "weather-overlay",  span: 12 },
];
