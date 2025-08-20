export const LEVEL_OPTIONS = [
  { code: "LEVEL_1", text: "안전안내" },
  { code: "LEVEL_2", text: "긴급재난" },
  { code: "LEVEL_3", text: "위급재난" },
] as const;

export type LevelCode = (typeof LEVEL_OPTIONS)[number]["code"];

export function levelTextToCode(input?: string | null): LevelCode | undefined {
  if (!input) return undefined;
  const norm = input.replace(/\s+/g, "").trim();
  const found = LEVEL_OPTIONS.find(
    (o) => o.text.replace(/\s+/g, "") === norm
  );
  if (found) return found.code;
  // 사용자가 코드(LEVEL_2)로 직접 입력해도 허용
  const upper = input.toUpperCase().trim();
  const byCode = LEVEL_OPTIONS.find((o) => o.code === upper);
  return byCode?.code;
}