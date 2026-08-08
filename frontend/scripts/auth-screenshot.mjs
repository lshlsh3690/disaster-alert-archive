import { chromium, devices } from "playwright";
import { mkdir } from "node:fs/promises";
import path from "node:path";

const ACCESS = process.env.UI_ACCESS_TOKEN;
const REFRESH = process.env.UI_REFRESH_TOKEN;

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
  console.error("사용법: node auth-screenshot.mjs <path>");
  process.exit(1);
}

await mkdir(outDir, { recursive: true });
const browser = await chromium.launch();
try {
  for (const profile of PROFILES) {
    const { name, ...contextOptions } = profile;
    const context = await browser.newContext(contextOptions);
    await context.addCookies([
      { name: "accessToken", value: ACCESS, domain: "localhost", path: "/", httpOnly: true, sameSite: "Lax" },
      { name: "refreshToken", value: REFRESH, domain: "localhost", path: "/", httpOnly: true, sameSite: "Lax" },
    ]);
    // authStore(zustand persist)를 미리 채워서, /user/settings 등의 가드가
    // useInitAuth의 비동기 재수화(rehydration)보다 먼저 실행돼 /login으로
    // 튕기는 레이스 버그를 우회한다(이 레이스 자체는 별도로 보고함).
    await context.addInitScript(
      ({ memberId, nickname, email }) => {
        window.localStorage.setItem(
          "disaster-alert-auth",
          JSON.stringify({ state: { user: { memberId, nickname, email, role: "USER" } }, version: 0 })
        );
      },
      { memberId: 1, nickname: "uichecktester", email: "ui-check-test@example.com" }
    );
    const page = await context.newPage();
    const url = `http://localhost:3000${targetPath}`;
    await page.goto(url, { waitUntil: "networkidle", timeout: 30000 });
    const fileSafeName = targetPath.replace(/\//g, "_");
    const outPath = path.join(outDir, `${fileSafeName}__${name}.png`);
    await page.screenshot({ path: outPath, fullPage: true });
    const { width, height } = contextOptions.viewport;
    console.log(`saved: ${outPath} (${width}x${height})`);
    await context.close();
  }
} finally {
  await browser.close();
}
