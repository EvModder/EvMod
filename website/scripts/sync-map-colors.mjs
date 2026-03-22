import fs from "node:fs/promises";
import path from "node:path";
import process from "node:process";

const args = process.argv.slice(2);
const explicitSource = args.find((arg) => !arg.startsWith("--"));
const cwd = process.cwd();
const targetPath = path.resolve(cwd, "src/lib/mapColorDataSynced.ts");
const banner =
  "// AUTO-SYNCED from PNG-to-NBT/website/src/data/mapColors.ts\n" +
  "// Run `bun run sync:map-colors` to refresh.\n" +
  "// Exports: `Shade`, `SHADE_MULTIPLIERS`, `TRANSPARENCY_BASE_INDEX`, `WATER_BASE_INDEX`, `BASE_COLORS`.\n\n";

const normalize = (text) => text.replace(/\r\n/g, "\n").trimEnd() + "\n";

const sourceCandidates = [
  explicitSource,
  process.env.MAP_COLORS_SOURCE,
  "../../PNG-to-NBT/website/src/data/mapColors.ts",
  "../../PNG-to-NBT/website/src/lib/mapColors.ts",
  "../PNG-to-NBT/website/src/data/mapColors.ts",
  "../PNG-to-NBT/website/src/lib/mapColors.ts",
  "../png-to-nbt/website/src/data/mapColors.ts",
  "../png-to-nbt/website/src/lib/mapColors.ts",
].filter(Boolean);

const findSourcePath = async () => {
  for (const candidate of sourceCandidates) {
    const resolved = path.resolve(cwd, candidate);
    try {
      await fs.access(resolved);
      return resolved;
    } catch {}
  }
  throw new Error(
    `Unable to find mapColors.ts source. Checked:\n${sourceCandidates
      .map((c) => `- ${path.resolve(cwd, c)}`)
      .join("\n")}`,
  );
};

const sourcePath = await findSourcePath();
const sourceText = await fs.readFile(sourcePath, "utf8");
const desired = banner + normalize(sourceText);
const current = await fs.readFile(targetPath, "utf8").catch(() => "");

if (normalize(current) === normalize(desired)) {
  console.log("mapColorDataSynced.ts is already synced.");
  process.exit(0);
}

await fs.writeFile(targetPath, desired);
console.log(`Synced mapColorDataSynced.ts from ${sourcePath}`);
