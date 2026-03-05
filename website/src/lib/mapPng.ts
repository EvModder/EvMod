/**
 * Map image encoding + batch helpers.
 * Exports: `revokeMapImageUrls`, `buildMapImages`, `MapImage`.
 */
import pako from "pako";
import { MAP_RGBA_PALETTE } from "@/lib/mapColors";
import type { MapData } from "@/lib/map";

export const revokeMapImageUrls = (images: MapImage[]) => {
  for (const { url } of images) URL.revokeObjectURL(url);
};

const PNG_SIGNATURE = new Uint8Array([137, 80, 78, 71, 13, 10, 26, 10]);
const CHUNK_IHDR = new Uint8Array([73, 72, 68, 82]);
const CHUNK_PLTE = new Uint8Array([80, 76, 84, 69]);
const CHUNK_TRNS = new Uint8Array([116, 82, 78, 83]);
const CHUNK_IDAT = new Uint8Array([73, 68, 65, 84]);
const CHUNK_IEND = new Uint8Array([73, 69, 78, 68]);
const CRC_TABLE = (() => {
  const table = new Uint32Array(256);
  for (let i = 0; i < 256; ++i) {
    let c = i;
    for (let k = 0; k < 8; ++k) c = (c & 1) ? (0xEDB88320 ^ (c >>> 1)) : (c >>> 1);
    table[i] = c >>> 0;
  }
  return table;
})();

const INDEXED_RGB = new Uint8Array(256 * 3);
const INDEXED_ALPHA = new Uint8Array(256);
for (let i = 0; i < 256; ++i) {
  const src = i * 4;
  const dst = i * 3;
  INDEXED_RGB[dst] = src < MAP_RGBA_PALETTE.length ? MAP_RGBA_PALETTE[src] : 0;
  INDEXED_RGB[dst + 1] = src + 1 < MAP_RGBA_PALETTE.length ? MAP_RGBA_PALETTE[src + 1] : 0;
  INDEXED_RGB[dst + 2] = src + 2 < MAP_RGBA_PALETTE.length ? MAP_RGBA_PALETTE[src + 2] : 0;
  INDEXED_ALPHA[i] = src + 3 < MAP_RGBA_PALETTE.length ? MAP_RGBA_PALETTE[src + 3] : 0;
}

const u32be = (n: number) => new Uint8Array([(n >>> 24) & 0xFF, (n >>> 16) & 0xFF, (n >>> 8) & 0xFF, n & 0xFF]);
const crc32 = (a: Uint8Array, b: Uint8Array) => {
  let c = 0xFFFFFFFF;
  for (const v of a) c = CRC_TABLE[(c ^ v) & 0xFF] ^ (c >>> 8);
  for (const v of b) c = CRC_TABLE[(c ^ v) & 0xFF] ^ (c >>> 8);
  return (c ^ 0xFFFFFFFF) >>> 0;
};
const chunk = (type: Uint8Array, data: Uint8Array) => {
  const out = new Uint8Array(12 + data.length);
  out.set(u32be(data.length), 0);
  out.set(type, 4);
  out.set(data, 8);
  out.set(u32be(crc32(type, data)), 8 + data.length);
  return out;
};
const concat = (...parts: Uint8Array[]) => {
  let len = 0;
  for (const p of parts) len += p.length;
  const out = new Uint8Array(len);
  let off = 0;
  for (const p of parts) { out.set(p, off); off += p.length; }
  return out;
};
const makeIHDR = () => {
  const d = new Uint8Array(13);
  d.set(u32be(128), 0);
  d.set(u32be(128), 4);
  d[8] = 8; // bit depth
  d[9] = 3; // indexed color
  d[10] = 0; // compression
  d[11] = 0; // filter
  d[12] = 0; // no interlace
  return chunk(CHUNK_IHDR, d);
};
const IHDR_CHUNK = makeIHDR();
const PLTE_CHUNK = chunk(CHUNK_PLTE, INDEXED_RGB);
const TRNS_CHUNK = chunk(CHUNK_TRNS, INDEXED_ALPHA);
const IEND_CHUNK = chunk(CHUNK_IEND, new Uint8Array(0));

function encodeMapDataToPng({ colors }: MapData): Blob {
  const indexed = new Uint8Array((128 + 1) * 128);
  for (let y = 0; y < 128; ++y) {
    const row = y * 129;
    indexed[row] = 0; // no PNG row filter
    indexed.set(colors.subarray(y * 128, y * 128 + 128), row + 1);
  }
  const idat = chunk(CHUNK_IDAT, pako.deflate(indexed, { level: 1 }));
  const png = concat(PNG_SIGNATURE, IHDR_CHUNK, PLTE_CHUNK, TRNS_CHUNK, idat, IEND_CHUNK);
  return new Blob([png], { type: "image/png" });
}

interface ParsedMap extends MapData {
  label?: string;
}

interface ParsedMapsWithMeta<TMeta> {
  maps: ParsedMap[];
  meta?: TMeta;
}

export interface MapImage {
  name: string;
  url: string;
  blob: Blob;
}

interface BuildMapImagesParams<TMeta = unknown> {
  files: File[];
  parseMaps: (file: File, ctx: { fileIndex: number; fileCount: number }) => Promise<ParsedMap[] | ParsedMapsWithMeta<TMeta>>;
  buildName: (ctx: { file: File; map: ParsedMap; index: number; total: number }) => string;
  onFileParsed?: (ctx: { file: File; maps: ParsedMap[]; meta: TMeta | undefined; fileIndex: number; fileCount: number }) => void | Promise<void>;
}

export async function buildMapImages<TMeta = unknown>({ files, parseMaps, buildName, onFileParsed }: BuildMapImagesParams<TMeta>): Promise<{ images: MapImage[]; errors: string[] }> {
  const errors: string[] = [];
  const images: MapImage[] = [];
  let lastYieldAt = typeof performance !== "undefined" ? performance.now() : 0;

  for (const [fileIndex, file] of files.entries()) {
    try {
      const parsed = await parseMaps(file, { fileIndex, fileCount: files.length });
      const maps = Array.isArray(parsed) ? parsed : parsed.maps;
      const meta = Array.isArray(parsed) ? undefined : parsed.meta;
      await onFileParsed?.({ file, maps, meta, fileIndex, fileCount: files.length });
      if (!maps.length) {
        errors.push(`${file.name}: No map data found`);
        continue;
      }
      for (const [index, map] of maps.entries()) {
        const blob = encodeMapDataToPng(map);
        images.push({
          name: buildName({ file, map, index, total: maps.length }),
          blob,
          url: URL.createObjectURL(blob),
        });
        // Yield only when we've spent enough main-thread time to keep UI responsive on large batches.
        if ((index & 31) === 31 && typeof performance !== "undefined" && performance.now() - lastYieldAt > 24) {
          await new Promise<void>(resolve => setTimeout(resolve, 0));
          lastYieldAt = performance.now();
        }
      }
    } catch (err) {
      errors.push(`${file.name}: ${err instanceof Error ? err.message : "Unknown error"}`);
    }
  }

  return { images, errors };
}
