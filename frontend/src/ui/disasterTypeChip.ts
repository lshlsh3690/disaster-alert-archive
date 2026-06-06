const TYPE_CHIP_MAP: Record<string, string> = {
  폭염: "bg-red-100 text-red-700",
  호우: "bg-blue-100 text-blue-700",
  홍수: "bg-blue-100 text-blue-700",
  태풍: "bg-blue-200 text-blue-800",
  대설: "bg-sky-100 text-sky-700",
  한파: "bg-sky-200 text-sky-800",
  산불: "bg-orange-100 text-orange-700",
  화재: "bg-orange-100 text-orange-700",
  산사태: "bg-amber-100 text-amber-700",
  강풍: "bg-cyan-100 text-cyan-700",
  미세먼지: "bg-yellow-100 text-yellow-700",
  황사: "bg-yellow-100 text-yellow-700",
  건조: "bg-yellow-50 text-yellow-600",
  지진: "bg-stone-100 text-stone-700",
  지진해일: "bg-stone-100 text-stone-700",
  풍랑: "bg-indigo-100 text-indigo-700",
  전염병: "bg-purple-100 text-purple-700",
  가축질병: "bg-purple-100 text-purple-700",
};

export function disasterTypeChipClass(type?: string | null): string {
  if (!type) return "bg-gray-100 text-gray-600";
  return TYPE_CHIP_MAP[type] ?? "bg-gray-100 text-gray-600";
}
