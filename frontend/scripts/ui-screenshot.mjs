// UI 체크 agent 전용 스크린샷 스크립트.
// 사용: node scripts/ui-screenshot.mjs <path> [outDir]
//   <path>   예: /alerts, /user/settings/regions (localhost:3000 기준 상대경로)
//   [outDir] 기본값 .ui-check
//
// 회귀 테스트용 베이스라인 비교가 아니라, "지금 이 화면이 특정 너비에서
// 정상으로 보이는가"를 사람(또는 agent) 눈으로 한 번 확인하기 위한
// 일회성 스크린샷이다. dev 서버(npm run dev, localhost:3000)가 먼저
// 떠 있어야 한다.
import { chromium } from "playwright";
import { mkdir } from "node:fs/promises";
import path from "node:path";

const VIEWPORTS = [
  { name: "iphone-se", width: 375, height: 667 },
  { name: "iphone-12", width: 390, height: 844 },
  { name: "ipad", width: 768, height: 1024 },
];

const targetPath = process.argv[2];
const outDir = process.argv[3] ?? ".ui-check";

if (!targetPath || !targetPath.startsWith("/")) {
  console.error('사용법: node scripts/ui-screenshot.mjs <path> (예: /alerts)');
  process.exit(1);
}

await mkdir(outDir, { recursive: true });

const browser = await chromium.launch();
try {
  for (const vp of VIEWPORTS) {
    const context = await browser.newContext({
      viewport: { width: vp.width, height: vp.height },
      isMobile: vp.name !== "ipad",
      hasTouch: vp.name !== "ipad",
    });
    const page = await context.newPage();
    const url = `http://localhost:3000${targetPath}`;
    await page.goto(url, { waitUntil: "networkidle", timeout: 30000 });
    const fileSafeName = targetPath.replace(/\//g, "_") || "root";
    const outPath = path.join(outDir, `${fileSafeName}__${vp.name}.png`);
    await page.screenshot({ path: outPath, fullPage: true });
    console.log(`saved: ${outPath} (${vp.width}x${vp.height})`);
    await context.close();
  }
} finally {
  await browser.close();
}
