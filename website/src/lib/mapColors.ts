// Minecraft map base colors (62 entries, RGB)
const BASE_COLORS: [number, number, number][] = [
  [0, 0, 0], [127, 178, 56], [247, 233, 163], [199, 199, 199],
  [255, 0, 0], [160, 160, 255], [167, 167, 167], [0, 124, 0],
  [255, 255, 255], [164, 168, 184], [151, 109, 77], [112, 112, 112],
  [64, 64, 255], [143, 119, 72], [255, 252, 245], [216, 127, 51],
  [178, 76, 216], [102, 153, 216], [229, 229, 51], [127, 204, 25],
  [242, 127, 165], [76, 76, 76], [153, 153, 153], [76, 127, 153],
  [127, 63, 178], [51, 76, 178], [102, 76, 51], [102, 127, 51],
  [153, 51, 51], [25, 25, 25], [250, 238, 77], [92, 219, 213],
  [74, 128, 255], [0, 217, 58], [129, 86, 49], [112, 2, 0],
  [209, 177, 161], [159, 82, 36], [149, 87, 108], [112, 108, 138],
  [186, 133, 36], [103, 117, 53], [160, 77, 78], [57, 41, 35],
  [135, 107, 98], [87, 92, 92], [122, 73, 88], [76, 62, 92],
  [76, 50, 35], [76, 82, 42], [142, 60, 46], [37, 22, 16],
  [189, 48, 49], [148, 63, 97], [92, 25, 29], [22, 126, 134],
  [58, 142, 140], [86, 44, 62], [20, 180, 133], [100, 100, 100],
  [216, 175, 147], [127, 167, 150],
];

// Shade multipliers: LOW=180, NORMAL=220, HIGH=255, LOWEST=135
const SHADE_MULTIPLIERS = [180, 220, 255, 135];

// Pre-built RGBA palette (248 colors × 4 channels)
const PALETTE = new Uint8Array(248 * 4);
for (let base = 0; base < 62; base++) {
  for (let shade = 0; shade < 4; shade++) {
    const idx = (base * 4 + shade) * 4;
    if (base === 0) {
      PALETTE[idx] = PALETTE[idx + 1] = PALETTE[idx + 2] = PALETTE[idx + 3] = 0;
    } else {
      const m = SHADE_MULTIPLIERS[shade];
      PALETTE[idx] = Math.floor(BASE_COLORS[base][0] * m / 255);
      PALETTE[idx + 1] = Math.floor(BASE_COLORS[base][1] * m / 255);
      PALETTE[idx + 2] = Math.floor(BASE_COLORS[base][2] * m / 255);
      PALETTE[idx + 3] = 255;
    }
  }
}

/** Convert 128×128 color byte array to a PNG Blob */
export function colorsToPngBlob(colors: Uint8Array): Promise<Blob> {
  const canvas = document.createElement("canvas");
  canvas.width = 128;
  canvas.height = 128;
  const ctx = canvas.getContext("2d")!;
  const imageData = ctx.createImageData(128, 128);
  for (let i = 0; i < 16384; i++) {
    const palIdx = (colors[i] & 0xFF) * 4;
    const pixIdx = i * 4;
    imageData.data[pixIdx] = PALETTE[palIdx];
    imageData.data[pixIdx + 1] = PALETTE[palIdx + 1];
    imageData.data[pixIdx + 2] = PALETTE[palIdx + 2];
    imageData.data[pixIdx + 3] = PALETTE[palIdx + 3];
  }
  ctx.putImageData(imageData, 0, 0);
  return new Promise((resolve) => canvas.toBlob((b) => resolve(b!), "image/png"));
}

/** Build ARGB→byte reverse map for image parsing (opaque black for base 0) */
export function buildArgbToByteMap(): Map<number, number> {
  const map = new Map<number, number>();
  for (let base = 0; base < 62; base++) {
    for (let shade = 0; shade < 4; shade++) {
      const idx = base * 4 + shade;
      let r: number, g: number, b: number;
      if (base === 0) {
        r = g = b = 0;
      } else {
        const m = SHADE_MULTIPLIERS[shade];
        r = Math.floor(BASE_COLORS[base][0] * m / 255);
        g = Math.floor(BASE_COLORS[base][1] * m / 255);
        b = Math.floor(BASE_COLORS[base][2] * m / 255);
      }
      const argb = ((0xFF << 24) | (r << 16) | (g << 8) | b) | 0;
      map.set(argb, idx);
    }
  }
  map.set(0, 0);
  return map;
}
