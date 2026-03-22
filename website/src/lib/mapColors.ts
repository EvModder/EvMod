/**
 * Minecraft map-color palette and nearest-color matching helpers.
 * Exports: `MAP_RGBA_PALETTE`, `parseMapDataFromImageData`.
 */
import { BASE_COLORS, SHADE_MULTIPLIERS } from "@/lib/mapColorDataSynced";
import type { MapData } from "@/lib/map";

const packRgb = (r: number, g: number, b: number) => (r << 16) | (g << 8) | b;
const getShadedRgb = ({ baseIndex, shade }: { baseIndex: number; shade: number }): [number, number, number] => {
  const { r, g, b } = BASE_COLORS[baseIndex];
  const multiplier = SHADE_MULTIPLIERS[shade];
  return [
    Math.floor((r * multiplier) / 255),
    Math.floor((g * multiplier) / 255),
    Math.floor((b * multiplier) / 255),
  ];
};

// Pre-built RGBA palette (248 colors × 4 channels)
const PALETTE = new Uint8Array(BASE_COLORS.length * 16);
const SHADE_COUNT = SHADE_MULTIPLIERS.length;
for (let base = 0; base < BASE_COLORS.length; ++base) {
  for (let shade = 0; shade < SHADE_COUNT; ++shade) {
    const idx = (base * 4 + shade) * 4;
    if (base === 0) {
      PALETTE[idx] = PALETTE[idx + 1] = PALETTE[idx + 2] = PALETTE[idx + 3] = 0;
    } else {
      const [r, g, b] = getShadedRgb({ baseIndex: base, shade });
      PALETTE[idx] = r;
      PALETTE[idx + 1] = g;
      PALETTE[idx + 2] = b;
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
  for (let shade = 0; shade < SHADE_COUNT; ++shade) {
    const [r, g, b] = getShadedRgb({ baseIndex: base, shade });
    OPAQUE_PALETTE.push({
      byte: base * 4 + shade,
      r,
      g,
      b,
    });
  }
}

let argbMap: Map<number, number> | null = null;
export const MAP_RGBA_PALETTE = PALETTE;

/** Build ARGB→byte reverse map for image parsing (opaque black for base 0) */
function buildArgbToByteMap(): Map<number, number> {
  const map = new Map<number, number>();
  for (let base = 0; base < BASE_COLORS.length; ++base) {
    for (let shade = 0; shade < SHADE_COUNT; ++shade) {
      const idx = base * 4 + shade;
      const rgb = base === 0 ? 0 : packRgb(...getShadedRgb({ baseIndex: base, shade }));
      const argb = (0xFF000000 | rgb) | 0;
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
      const argb = (0xFF000000 | packRgb(r, g, b)) | 0;
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
      approximatedColorKeys.add(packRgb(r, g, b));
    }
  }
  return { mapData, approximatedPixels, approximatedColorKeys };
}
