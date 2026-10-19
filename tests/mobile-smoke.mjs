import { chromium } from "playwright";
import { mkdir } from "node:fs/promises";

const url = "http://127.0.0.1:18080/";
const browser = await chromium.launch({ headless: true });
await mkdir("tests/screenshots", { recursive: true });

async function checkViewport(width, height, role) {
  const context = await browser.newContext({
    viewport: { width, height }, isMobile: width <= 500,
    deviceScaleFactor: 1, hasTouch: width <= 500
  });
  const page = await context.newPage();
  page.setDefaultTimeout(20000);
  page.on("pageerror", error => { throw error; });
  try {
    await page.route("https://pokeapi.co/**", route => route.abort());
    await page.goto(url, { waitUntil: "domcontentloaded" });
    await page.locator("#login-username").fill(role === "manager" ? "manager" : "coach1");
    await page.locator("#login-password").fill(role === "manager" ? "manager" : "password");
    await page.locator("#login-button").click();
    await page.locator("#app:not(.hidden)").waitFor();
    const pages = role === "manager"
      ? ["home","draft","rosters","standings","schedule","playoffs","lineup","freeagency","manager","account"]
      : ["home","draft","rosters","standings","schedule","playoffs","lineup","freeagency","account"];
    for (const name of pages) {
      const selector = name === "account" ? '.sidebar > button[data-page="account"]'
        : `.sidebar nav button[data-page="${name}"]`;
      await page.locator(selector).click();
      await page.waitForTimeout(450);
      await page.locator(`#page-${name}:not(.hidden)`).waitFor();
      // Error rendering still keeps the page visible, so detect the error card.
      const pageError = await page.locator(`#page-${name} h2`).allTextContents();
      if (pageError.includes("Page error"))
        throw new Error(`Page ${name} rendered an API exception`);
      if (width <= 800) {
        const { total, screen } = await page.evaluate(() => ({
          total: document.documentElement.scrollWidth, screen: window.innerWidth
        }));
        if (total > screen + 2)
          throw new Error(`Page ${name} overflows mobile viewport: ${total}px > ${screen}px`);
      }
    }
    if (width <= 800) {
      const style = await page.locator(".sidebar nav").evaluate(el => getComputedStyle(el));
      if (style.overflowX !== "auto")
        throw new Error("Mobile navigation must scroll horizontally");
    }
    await page.screenshot({ path: `tests/screenshots/${role}-${width}.png`, fullPage: true });
    process.stdout.write(`PASS ${role}, ${width}x${height}, ${pages.length} pages\n`);
  } catch (error) {
    await page.screenshot({path: `tests/screenshots/failed-${role}-${width}.png`,fullPage:true}).catch(()=>{});
    throw error;
  } finally {
    await context.close();
  }
}

try {
  await checkViewport(390, 844, "coach");
  await checkViewport(320, 700, "manager");
  await checkViewport(768, 900, "manager");
  await checkViewport(1440, 900, "coach");
} finally {
  await browser.close();
}
