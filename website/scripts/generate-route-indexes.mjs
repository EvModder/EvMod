import fs from "node:fs/promises";
import path from "node:path";
import process from "node:process";

const cwd = process.cwd();
const distDir = path.resolve(cwd, "dist");
const srcToolPages = path.resolve(cwd, "src/lib/toolPages.ts");
const indexPath = path.join(distDir, "index.html");

const html = await fs.readFile(indexPath, "utf8");
const toolPagesSource = await fs.readFile(srcToolPages, "utf8");
const routeMatches = [...toolPagesSource.matchAll(/path:\s*"([^"]+)"/g)];
const routes = [...new Set(routeMatches.map(([, route]) => route).filter(route => route && route !== "/"))];
if (!routes.length) throw new Error("No tool routes found while generating route index pages.");

for (const route of routes) {
  const rel = route.replace(/^\/+|\/+$/g, "");
  if (!rel) continue;
  const routeDir = path.join(distDir, rel);
  await fs.mkdir(routeDir, { recursive: true });
  await fs.writeFile(path.join(routeDir, "index.html"), html);
}

console.log(`Generated ${routes.length} route index page(s).`);
