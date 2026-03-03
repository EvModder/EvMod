/**
 * Minecraft map-color palette and nearest-color matching helpers.
 * Exports: `MAP_RGBA_PALETTE`, `parseMapDataFromImageData`.
 */
import { BASE_COLORS, SHADE_MULTIPLIERS } from "@/lib/mapColorDataSynced";
import type { MapData } from "@/lib/map";

// Pre-built RGBA palette (248 colors × 4 channels)
const PALETTE = new Uint8Array(BASE_COLORS.length * 16);
for (let base = 0; base < BASE_COLORS.length; ++base) {
  const { r, g, b } = BASE_COLORS[base];
  for (let shade = 0; shade < 4; ++shade) {
    const idx = (base * 4 + shade) * 4;
    if (base === 0) {
      PALETTE[idx] = PALETTE[idx + 1] = PALETTE[idx + 2] = PALETTE[idx + 3] = 0;
    } else {
      const m = SHADE_MULTIPLIERS[shade];
      PALETTE[idx] = Math.floor(r * m / 255);
      PALETTE[idx + 1] = Math.floor(g * m / 255);
      PALETTE[idx + 2] = Math.floor(b * m / 255);
      PALETTE[idx + 3] = 255;
    }
  }
}

interface PaletteEntry {
  byte: number;
  r: number;
  g: number;
  b: number;
}

const OPAQUE_PALETTE: PaletteEntry[] = [];
for (let base = 1; base < BASE_COLORS.length; ++base) {
  const { r, g, b } = BASE_COLORS[base];
  for (let shade = 0; shade < 4; ++shade) {
    const m = SHADE_MULTIPLIERS[shade];
    OPAQUE_PALETTE.push({
      byte: base * 4 + shade,
      r: Math.floor(r * m / 255),
      g: Math.floor(g * m / 255),
      b: Math.floor(b * m / 255),
    });
  }
}

let argbMap: Map<number, number> | null = null;
export const MAP_RGBA_PALETTE = PALETTE;

/** Build ARGB→byte reverse map for image parsing (opaque black for base 0) */
function buildArgbToByteMap(): Map<number, number> {
  const map = new Map<number, number>();
  for (let base = 0; base < BASE_COLORS.length; ++base) {
    const { r: br, g: bg, b: bb } = BASE_COLORS[base];
    for (let shade = 0; shade < 4; ++shade) {
      const idx = base * 4 + shade;
      let r: number, g: number, b: number;
      if (base === 0) {
        r = g = b = 0;
      } else {
        const m = SHADE_MULTIPLIERS[shade];
        r = Math.floor(br * m / 255);
        g = Math.floor(bg * m / 255);
        b = Math.floor(bb * m / 255);
      }
      const argb = ((0xFF << 24) | (r << 16) | (g << 8) | b) | 0;
      map.set(argb, idx);
    }
  }
  map.set(0, 0);
  return map;
}

interface ParseMapDataFromImageDataOptions {
  sourceWidth?: number;
  startX?: number;
  startY?: number;
}

export function parseMapDataFromImageData(
  pixels: Uint8ClampedArray,
  { sourceWidth = 128, startX = 0, startY = 0 }: ParseMapDataFromImageDataOptions = {}
): { mapData: MapData; approximatedPixels: number; approximatedColorKeys: Set<number> } {
  const mapData: MapData = { colors: new Uint8Array(16384) };
  const { colors } = mapData;
  const map = argbMap ?? (argbMap = buildArgbToByteMap());
  let approximatedPixels = 0;
  const approximatedColorKeys = new Set<number>();
  for (let y = 0; y < 128; ++y) {
    for (let x = 0; x < 128; ++x) {
      const i = y * 128 + x;
      const off = ((startY + y) * sourceWidth + (startX + x)) * 4;
      const a = pixels[off + 3];
      if (a < 128) {
        colors[i] = 0;
        continue;
      }
      const r = pixels[off], g = pixels[off + 1], b = pixels[off + 2];
      const argb = ((0xFF << 24) | (r << 16) | (g << 8) | b) | 0;
      const exact = map.get(argb);
      if (exact !== undefined && exact >= 4) {
        colors[i] = exact;
        continue;
      }

      let best = OPAQUE_PALETTE[0];
      let bestDist = Infinity;
      for (const candidate of OPAQUE_PALETTE) {
        const dr = r - candidate.r, dg = g - candidate.g, db = b - candidate.b;
        const dist = dr * dr + dg * dg + db * db;
        if (dist < bestDist) {
          bestDist = dist;
          best = candidate;
          if (dist === 0) break;
        }
      }
      colors[i] = best.byte;
      ++approximatedPixels;
      approximatedColorKeys.add((r << 16) | (g << 8) | b);
    }
  }
  return { mapData, approximatedPixels, approximatedColorKeys };
}
