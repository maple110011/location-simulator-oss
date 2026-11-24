// 下载 Robolectric 离线测试所需的 android-all jar（带 sha1 校验）。
// 用法：node tools/fetch-test-deps.mjs
// 产物目录：默认 <工程根>/.robolectric-deps（已 gitignore），
// 可用环境变量 ROBOLECTRIC_DEPS_DIR 或 Gradle 属性 -ProbolectricDepsDir 覆盖，
// 必须与 app/build.gradle 中 robolectric.dependency.dir 指向同一目录。
import fs from "node:fs";
import path from "node:path";
import crypto from "node:crypto";

import { fileURLToPath } from "node:url";
const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const dir = process.env.ROBOLECTRIC_DEPS_DIR || path.join(projectRoot, ".robolectric-deps");
fs.mkdirSync(dir, { recursive: true });

const artifacts = [
  // Robolectric 4.14.1 的 API 34 映射
  ["14-robolectric-10818077-i7", "android-all-instrumented-14-robolectric-10818077-i7.jar"],
  // Robolectric 4.14.1 的默认（最新）SDK 35 映射，测试运行时会用到
  ["15-robolectric-12650502-i7", "android-all-instrumented-15-robolectric-12650502-i7.jar"],
];
const bases = [
  "https://maven.aliyun.com/repository/central/org/robolectric/android-all-instrumented/",
  "https://repo1.maven.org/maven2/org/robolectric/android-all-instrumented/",
];

async function fetchText(urls) {
  for (const u of urls) {
    try { const r = await fetch(u); if (r.ok) return (await r.text()).trim(); } catch (e) { }
  }
  throw new Error("fetch failed: " + urls[0]);
}

for (const [ver, file] of artifacts) {
  const dest = path.join(dir, file);
  const expected = (await fetchText(bases.map((b) => b + ver + "/" + file + ".sha1"))).split(/\s+/)[0];
  if (fs.existsSync(dest)) {
    const hash = crypto.createHash("sha1").update(fs.readFileSync(dest)).digest("hex");
    if (hash === expected) { console.log("ok (cached):", file); continue; }
    fs.unlinkSync(dest);
  }
  let done = false;
  for (const base of bases) {
    try {
      console.log("downloading:", file, "from", base.split("/")[2]);
      const res = await fetch(base + ver + "/" + file);
      if (!res.ok) { console.log("  HTTP", res.status); continue; }
      const ws = fs.createWriteStream(dest);
      const reader = res.body.getReader();
      let got = 0;
      for (;;) {
        const { done: eof, value } = await reader.read();
        if (eof) break;
        ws.write(Buffer.from(value));
        got += value.length;
      }
      await new Promise((r) => ws.end(r));
      const hash = crypto.createHash("sha1").update(fs.readFileSync(dest)).digest("hex");
      if (hash === expected) { console.log("  ok:", got, "bytes"); done = true; break; }
      console.log("  sha1 mismatch, retry");
      fs.unlinkSync(dest);
    } catch (e) { console.log("  error:", e.message); }
  }
  if (!done) { console.error("FAILED:", file); process.exit(1); }
}
console.log("all test dependencies ready in", dir);
