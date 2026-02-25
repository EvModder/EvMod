import { md5 } from "@/lib/md5";
import { buildArgbToByteMap } from "@/lib/mapColors";

export const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export const bytesToHex = (bytes: Uint8Array): string =>
  Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");

export const formatUUID = (hex: string) =>
  `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20, 32)}`;

export const nameUUIDFromBytes = (bytes: Uint8Array): string => {
  const hash = md5(bytes);
  hash[6] = (hash[6] & 0x0f) | 0x30;
  hash[8] = (hash[8] & 0x3f) | 0x80;
  return formatUUID(bytesToHex(hash));
};

export const setLockedBit = (uuid: string): string => {
  const [first, ...rest] = uuid.split("-");
  const byte0 = parseInt(first.slice(0, 2), 16) | 0x01;
  return byte0.toString(16).padStart(2, "0") + first.slice(2) + "-" + rest.join("-");
};

export const clearLockedBit = (uuid: string): string => {
  const [first, ...rest] = uuid.split("-");
  const byte0 = parseInt(first.slice(0, 2), 16) & 0xFE;
  return byte0.toString(16).padStart(2, "0") + first.slice(2) + "-" + rest.join("-");
};

export const parseUUIDs = (buffer: ArrayBuffer): string[] => {
  const bytes = new Uint8Array(buffer);
  const result: string[] = [];
  for (let i = 0; i + 16 <= bytes.length; i += 16)
    result.push(formatUUID(bytesToHex(bytes.slice(i, i + 16))));
  return result;
};

export const uuidsToBytes = (uuidList: string[]): Uint8Array => {
  const bytes = new Uint8Array(uuidList.length * 16);
  uuidList.forEach((uuid, i) => {
    const hex = uuid.replace(/-/g, "");
    for (let j = 0; j < 16; j++)
      bytes[i * 16 + j] = parseInt(hex.slice(j * 2, j * 2 + 2), 16);
  });
  return bytes;
};

// Lazily built reverse map for image parsing
let argbMap: Map<number, number> | null = null;
function getArgbMap() {
  if (!argbMap) argbMap = buildArgbToByteMap();
  return argbMap;
}

/** Parse 128×128 blocks from a PNG image, hash each to a UUID with locked bit set */
export const parseImageUUIDs = (img: HTMLImageElement): string[] => {
  const canvas = document.createElement("canvas");
  canvas.width = img.width;
  canvas.height = img.height;
  const ctx = canvas.getContext("2d")!;
  ctx.drawImage(img, 0, 0);

  if (img.width % 128 || img.height % 128)
    throw new Error(`Dimensions must be divisible by 128. Got ${img.width}×${img.height}`);

  const map = getArgbMap();
  const results: string[] = [];
  for (let by = 0; by < img.height / 128; by++) {
    for (let bx = 0; bx < img.width / 128; bx++) {
      const { data } = ctx.getImageData(bx * 128, by * 128, 128, 128);
      const colors = new Uint8Array(128 * 128);
      for (let i = 0; i < 128 * 128; i++) {
        const off = i * 4;
        const argb = ((data[off + 3] << 24) | (data[off] << 16) | (data[off + 1] << 8) | data[off + 2]) | 0;
        const colorByte = map.get(argb);
        if (colorByte === undefined) {
          const x = bx * 128 + (i % 128), y = by * 128 + ((i / 128) | 0);
          throw new Error(`Unsupported color at (${x}, ${y}): ARGB=${argb}`);
        }
        colors[i] = colorByte;
      }
      results.push(setLockedBit(nameUUIDFromBytes(colors)));
    }
  }
  return results;
};
