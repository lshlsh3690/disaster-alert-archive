// UI 체크 agent 전용 스크린샷 스크립트.
// 사용: node scripts/ui-screenshot.mjs <path> [outDir]
//   <path>   예: /alerts, /user/settings/regions (localhost:3000 기준 상대경로)
//   [outDir] 기본값 .ui-check
//
// 회귀 테스트용 베이스라인 비교가 아니라, "지금 이 화면이 특정 크기에서
// 정상으로 보이는가"를 사람(또는 agent) 눈으로 한 번 확인하기 위한
// 일회성 스크린샷이다. dev 서버(npm run dev, localhost:3000)가 먼저
// 떠 있어야 한다.
import { chromium, devices } from "playwright";
import { mkdir } from "node:fs/promises";
import path from "node:path";

// iOS/Android 각각 실기기 프리셋(정확한 UA/픽셀비/터치 에뮬레이션) +
// Tailwind md(768px) 브레이크포인트 경계를 정확히 찍기 위한 커스텀 태블릿
// 폭 + 데스크톱 2종(노트북/데스크톱 모니터 대표 해상도).
const PROFILES = [
  { name: "iphone-se", ...devices["iPhone SE"] },
  { name: "iphone-12", ...devices["iPhone 12"] },
  { name: "galaxy-s24", ...devices["Galaxy S24"] },
  { name: "tablet-768", viewport: { width: 768, height: 1024 }, isMobile: false, hasTouch: false },
  { name: "desktop-1280", viewport: { width: 1280, height: 800 }, isMobile: false, hasTouch: false },
  { name: "desktop-1920", viewport: { width: 1920, height: 1080 }, isMobile: false, hasTouch: false },
];

const targetPath = process.argv[2];
const outDir = process.argv[3] ?? ".ui-check";

if (!targetPath || !targetPath.startsWith("/")) {
  console.error('사용법: node scripts/ui-screenshot.mjs <path> (예: /alerts)');
  process.exit(1);
}

await mkdir(outDir, { recursive: true });

let browser;
try {
  browser = await chromium.launch();
} catch (e) {
  console.error("Chromium 실행 실패 — 이 컴퓨터에 브라우저가 설치되지 않았을 수 있습니다.");
  console.error("다음을 한 번 실행하세요: npx playwright install chromium");
  console.error(String(e?.message ?? e));
  process.exit(1);
}
try {
  for (const profile of PROFILES) {
    const { name, ...contextOptions } = profile;
    const context = await browser.newContext(contextOptions);
    const page = await context.newPage();
    const url = `http://localhost:3000${targetPath}`;
    await page.goto(url, { waitUntil: "networkidle", timeout: 30000 });
    const fileSafeName = targetPath === "/" ? "root" : targetPath.replace(/\//g, "_");
    const outPath = path.join(outDir, `${fileSafeName}__${name}.png`);
    await page.screenshot({ path: outPath, fullPage: true });
    const { width, height } = contextOptions.viewport;
    console.log(`saved: ${outPath} (${width}x${height})`);
    await context.close();
  }
} finally {
  await browser.close();
}
