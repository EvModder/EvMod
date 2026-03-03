/**
 * Decodes structure `.nbt`, `.litematic`, and `.schem` into map color data.
 * Exports: `parseMapDataFromNbt`.
 */
import { parseNbtRoot } from "@/lib/mapDat";
import { WATER_BASE_INDEX, BASE_COLORS } from "@/lib/mapColorDataSynced";
import type { MapData } from "@/lib/map";

interface StructurePaletteEntry {
  Name?: string;
  Properties?: Record<string, string>;
}

interface StructureBlockEntry {
  pos?: number[];
  state?: number;
}

interface StructureRoot {
  size?: number[];
  palette?: StructurePaletteEntry[];
  blocks?: StructureBlockEntry[];
}

interface DecodedBlockSource {
  sizeX: number;
  sizeY: number;
  sizeZ: number;
  palette: StructurePaletteEntry[];
  forEachBlock: (visit: (x: number, y: number, z: number, state: number) => void) => void;
}

const BLOCK_TO_BASE = new Map<string, number>();
const NAME_TO_BASE = new Map<string, number>();
const AIR_NAMES = new Set(["air", "cave_air", "void_air"]);
for (let i = 0; i < BASE_COLORS.length; ++i) {
  for (const block of BASE_COLORS[i].blocks) {
    if (!BLOCK_TO_BASE.has(block)) BLOCK_TO_BASE.set(block, i);
    const nameOnly = block.replace(/\[.*$/, "");
    if (!NAME_TO_BASE.has(nameOnly)) NAME_TO_BASE.set(nameOnly, i);
  }
}

const getSize = (root: StructureRoot): number[] | undefined => {
  if (Array.isArray(root.size)) return root.size;
  const d = (root as Record<string, unknown>).data as StructureRoot | undefined;
  return d && Array.isArray(d.size) ? d.size : undefined;
};

const getPalette = (root: StructureRoot): StructurePaletteEntry[] => {
  if (Array.isArray(root.palette)) return root.palette;
  const d = (root as Record<string, unknown>).data as StructureRoot | undefined;
  return d && Array.isArray(d.palette) ? d.palette : [];
};

const getBlocks = (root: StructureRoot): StructureBlockEntry[] => {
  if (Array.isArray(root.blocks)) return root.blocks;
  const d = (root as Record<string, unknown>).data as StructureRoot | undefined;
  return d && Array.isArray(d.blocks) ? d.blocks : [];
};

const asObject = (value: unknown): Record<string, unknown> | undefined =>
  value && typeof value === "object" && !Array.isArray(value) ? value as Record<string, unknown> : undefined;

const toInt = (value: unknown): number => typeof value === "number" ? value : Number(value);

const readVec3Object = (value: unknown): { x: number; y: number; z: number } | undefined => {
  const obj = asObject(value);
  if (!obj) return undefined;
  const x = toInt(obj.x), y = toInt(obj.y), z = toInt(obj.z);
  if (!Number.isFinite(x) || !Number.isFinite(y) || !Number.isFinite(z)) return undefined;
  return { x, y, z };
};

const stripNamespace = (name: string) => name.startsWith("minecraft:") ? name.slice("minecraft:".length) : name;

const normalizeProperties = (props: unknown): Record<string, string> => {
  if (!props || typeof props !== "object") return {};
  const out: Record<string, string> = {};
  for (const [k, v] of Object.entries(props as Record<string, unknown>)) out[k] = String(v);
  return out;
};

const stringifyBlock = (entry: StructurePaletteEntry): { name: string; key: string; props: Record<string, string> } => {
  const name = stripNamespace(String(entry.Name ?? ""));
  const props = normalizeProperties(entry.Properties);
  const keys = Object.keys(props).sort();
  if (!keys.length) return { name, key: name, props };
  return { name, key: `${name}[${keys.map(k => `${k}=${props[k]}`).join(",")}]`, props };
};

const entryKey = (entry: StructurePaletteEntry): string => {
  const name = String(entry.Name ?? "");
  const props = normalizeProperties(entry.Properties);
  const keys = Object.keys(props).sort();
  return keys.length ? `${name}[${keys.map(k => `${k}=${props[k]}`).join(",")}]` : name;
};

const parseStateIdentifier = (id: string): StructurePaletteEntry => {
  const trimmed = id.trim();
  const bracket = trimmed.indexOf("[");
  if (bracket < 0 || !trimmed.endsWith("]")) return { Name: trimmed };
  const name = trimmed.slice(0, bracket).trim();
  const body = trimmed.slice(bracket + 1, -1);
  const props: Record<string, string> = {};
  for (const part of body.split(",")) {
    const eq = part.indexOf("=");
    if (eq <= 0) continue;
    props[part.slice(0, eq).trim()] = part.slice(eq + 1).trim();
  }
  return Object.keys(props).length ? { Name: name, Properties: props } : { Name: name };
};

const resolveBaseIndex = (entry: StructurePaletteEntry): number | undefined => {
  const { name, key, props } = stringifyBlock(entry);
  if (!name) return 0;
  if (AIR_NAMES.has(name)) return 0;

  if (props.waterlogged === "true") {
    const waterlogged = `${name}[waterlogged=true]`;
    if (BLOCK_TO_BASE.has(waterlogged)) return BLOCK_TO_BASE.get(waterlogged);
    if (name.endsWith("_leaves") || name === "water") return WATER_BASE_INDEX;
  }

  if (BLOCK_TO_BASE.has(key)) return BLOCK_TO_BASE.get(key);
  if (BLOCK_TO_BASE.has(name)) return BLOCK_TO_BASE.get(name);
  return NAME_TO_BASE.get(name);
};

const getWaterShade = (depth: number, x: number, z: number): number => {
  if (depth <= 1) return 2;
  const even = (x + z) % 2 === 0;
  if (even) {
    if (depth >= 10) return 0;
    if (depth >= 5) return 1;
    return 2;
  }
  if (depth >= 7) return 0;
  if (depth >= 3) return 1;
  return 2;
};

const getDepth = (ys: Set<number> | undefined, top: number): number => {
  if (!ys || !ys.has(top)) return 1;
  let depth = 1;
  for (let y = top - 1; ys.has(y); --y) ++depth;
  return depth;
};

const decodeVarIntArray = (data: Uint8Array, expected: number): Uint32Array => {
  const out = new Uint32Array(expected);
  let pos = 0;
  for (let i = 0; i < expected; ++i) {
    let value = 0;
    let shift = 0;
    let done = false;
    for (let read = 0; read < 5; ++read) {
      if (pos >= data.length) throw new Error("Invalid .schem BlockData: truncated varint stream");
      const b = data[pos++];
      value |= (b & 0x7F) << shift;
      if ((b & 0x80) === 0) {
        done = true;
        break;
      }
      shift += 7;
    }
    if (!done) throw new Error("Invalid .schem BlockData: varint longer than 5 bytes");
    out[i] = value >>> 0;
  }
  return out;
};

const bitsForPalette = (size: number): number => Math.max(2, Math.ceil(Math.log2(Math.max(1, size))));

const readPacked = (words: bigint[], bits: number, index: number): number => {
  const start = index * bits;
  const wordIndex = start >> 6;
  const bitOffset = start & 63;
  const mask = (1n << BigInt(bits)) - 1n;
  const lo = BigInt.asUintN(64, words[wordIndex] ?? 0n);
  if (bitOffset + bits <= 64) return Number((lo >> BigInt(bitOffset)) & mask);
  const hi = BigInt.asUintN(64, words[wordIndex + 1] ?? 0n);
  const joined = (lo >> BigInt(bitOffset)) | (hi << BigInt(64 - bitOffset));
  return Number(joined & mask);
};

const decodeStructureSource = (root: unknown): DecodedBlockSource | undefined => {
  const sr = root as StructureRoot;
  const size = getSize(sr);
  const palette = getPalette(sr);
  const blocks = getBlocks(sr);
  if (!size || size.length < 3 || !palette.length || !blocks.length) return undefined;
  const sizeX = toInt(size[0]), sizeY = toInt(size[1]), sizeZ = toInt(size[2]);
  return {
    sizeX,
    sizeY,
    sizeZ,
    palette,
    forEachBlock: visit => {
      for (const b of blocks) {
        if (!Array.isArray(b.pos) || b.pos.length < 3 || typeof b.state !== "number") continue;
        visit(toInt(b.pos[0]), toInt(b.pos[1]), toInt(b.pos[2]), b.state);
      }
    },
  };
};

const decodeSpongeSchemSource = (root: unknown): DecodedBlockSource | undefined => {
  const top = asObject(root);
  if (!top) return undefined;
  const nested = asObject(top.Schematic);
  const schem = nested ?? top;

  const width = toInt(schem.Width);
  const height = toInt(schem.Height);
  const length = toInt(schem.Length);
  if (!Number.isFinite(width) || !Number.isFinite(height) || !Number.isFinite(length) || width <= 0 || height <= 0 || length <= 0)
    return undefined;

  const blocksObj = asObject(schem.Blocks);
  const paletteObj = asObject(blocksObj?.Palette ?? schem.Palette);
  const data = (blocksObj?.Data ?? schem.BlockData) as Uint8Array | undefined;
  if (!paletteObj || !(data instanceof Uint8Array)) return undefined;

  let maxIndex = -1;
  for (const idx of Object.values(paletteObj)) {
    const n = toInt(idx);
    if (Number.isFinite(n) && n > maxIndex) maxIndex = n;
  }
  if (maxIndex < 0) throw new Error("Invalid .schem: palette is empty");

  const palette: StructurePaletteEntry[] = new Array(maxIndex + 1);
  for (const [stateId, idxRaw] of Object.entries(paletteObj)) {
    const idx = toInt(idxRaw);
    if (!Number.isInteger(idx) || idx < 0) continue;
    palette[idx] = parseStateIdentifier(stateId);
  }

  const volume = width * height * length;
  const indices = decodeVarIntArray(data, volume);

  return {
    sizeX: width,
    sizeY: height,
    sizeZ: length,
    palette,
    forEachBlock: visit => {
      const layer = width * length;
      for (let i = 0; i < volume; ++i) {
        const y = Math.floor(i / layer);
        const inLayer = i - y * layer;
        const z = Math.floor(inLayer / width);
        const x = inLayer - z * width;
        visit(x, y, z, indices[i]);
      }
    },
  };
};

interface LitematicRegion {
  posX: number;
  posY: number;
  posZ: number;
  minLX: number;
  minLY: number;
  minLZ: number;
  absX: number;
  absY: number;
  absZ: number;
  bits: number;
  words: bigint[];
  stateMap: Int32Array;
}

const decodeLitematicSource = (root: unknown): DecodedBlockSource | undefined => {
  const top = asObject(root);
  const regionsObj = asObject(top?.Regions);
  if (!regionsObj) return undefined;

  const globalPalette: StructurePaletteEntry[] = [];
  const globalStateByKey = new Map<string, number>();
  const regions: LitematicRegion[] = [];
  let minX = Infinity, minY = Infinity, minZ = Infinity;
  let maxX = -Infinity, maxY = -Infinity, maxZ = -Infinity;

  for (const regionVal of Object.values(regionsObj)) {
    const region = asObject(regionVal);
    if (!region) continue;

    const pos = readVec3Object(region.Position);
    const size = readVec3Object(region.Size);
    const paletteList = Array.isArray(region.BlockStatePalette) ? region.BlockStatePalette : undefined;
    const words = Array.isArray(region.BlockStates) ? region.BlockStates as bigint[] : undefined;
    if (!pos || !size || !paletteList || !words) continue;

    const sx = toInt(size.x), sy = toInt(size.y), sz = toInt(size.z);
    const absX = Math.abs(sx), absY = Math.abs(sy), absZ = Math.abs(sz);
    if (!absX || !absY || !absZ) continue;

    const minLX = sx < 0 ? sx + 1 : 0;
    const minLY = sy < 0 ? sy + 1 : 0;
    const minLZ = sz < 0 ? sz + 1 : 0;

    const regionMinX = pos.x + minLX;
    const regionMinY = pos.y + minLY;
    const regionMinZ = pos.z + minLZ;
    const regionMaxX = regionMinX + absX - 1;
    const regionMaxY = regionMinY + absY - 1;
    const regionMaxZ = regionMinZ + absZ - 1;
    minX = Math.min(minX, regionMinX);
    minY = Math.min(minY, regionMinY);
    minZ = Math.min(minZ, regionMinZ);
    maxX = Math.max(maxX, regionMaxX);
    maxY = Math.max(maxY, regionMaxY);
    maxZ = Math.max(maxZ, regionMaxZ);

    const stateMap = new Int32Array(paletteList.length);
    stateMap.fill(-1);
    for (let i = 0; i < paletteList.length; ++i) {
      const entryObj = asObject(paletteList[i]);
      if (!entryObj) continue;
      const entry: StructurePaletteEntry = {
        Name: typeof entryObj.Name === "string" ? entryObj.Name : String(entryObj.Name ?? ""),
        Properties: normalizeProperties(entryObj.Properties),
      };
      const key = entryKey(entry);
      let globalState = globalStateByKey.get(key);
      if (globalState === undefined) {
        globalState = globalPalette.length;
        globalPalette.push(entry);
        globalStateByKey.set(key, globalState);
      }
      stateMap[i] = globalState;
    }

    const volume = absX * absY * absZ;
    const bits = bitsForPalette(paletteList.length);
    const expectedWords = Math.ceil((volume * bits) / 64);
    if (words.length < expectedWords)
      throw new Error(`Invalid .litematic region: BlockStates is too short (need ${expectedWords} longs, got ${words.length})`);

    regions.push({ posX: pos.x, posY: pos.y, posZ: pos.z, minLX, minLY, minLZ, absX, absY, absZ, bits, words, stateMap });
  }

  if (!regions.length) throw new Error("Invalid .litematic: no readable regions found");

  return {
    sizeX: maxX - minX + 1,
    sizeY: maxY - minY + 1,
    sizeZ: maxZ - minZ + 1,
    palette: globalPalette,
    forEachBlock: visit => {
      for (const region of regions) {
        const layer = region.absX * region.absZ;
        const volume = region.absX * region.absY * region.absZ;
        for (let i = 0; i < volume; ++i) {
          const y = Math.floor(i / layer);
          const inLayer = i - y * layer;
          const z = Math.floor(inLayer / region.absX);
          const x = inLayer - z * region.absX;

          const localState = readPacked(region.words, region.bits, i);
          const globalState = localState < region.stateMap.length ? region.stateMap[localState] : -1;
          const gx = region.posX + region.minLX + x - minX;
          const gy = region.posY + region.minLY + y - minY;
          const gz = region.posZ + region.minLZ + z - minZ;
          visit(gx, gy, gz, globalState);
        }
      }
    },
  };
};

const decodeAnyBlockSource = (root: unknown): DecodedBlockSource => {
  const structure = decodeStructureSource(root);
  if (structure) return structure;
  const litematic = decodeLitematicSource(root);
  if (litematic) return litematic;
  const schem = decodeSpongeSchemSource(root);
  if (schem) return schem;
  throw new Error("Unsupported NBT schematic format (expected structure .nbt, .litematic, or Sponge .schem)");
};

export function parseMapDataFromNbt(buffer: ArrayBuffer): MapData {
  const source = decodeAnyBlockSource(parseNbtRoot(buffer));
  const { sizeX, sizeY, sizeZ, palette } = source;

  if (sizeX !== 128 || (sizeZ !== 128 && sizeZ !== 129))
    throw new Error(`NBT dimensions must be 128 x Y x 128 or 128 x Y x 129 (got ${sizeX} x ${sizeY} x ${sizeZ})`);

  const area = 128 * sizeZ;
  const topY = new Int32Array(area).fill(-2147483648);
  const topBase = new Int16Array(area);
  const waterYs = new Map<number, Set<number>>();
  const stateToBase = new Map<number, number | undefined>();
  const unknownStates = new Set<string>();

  source.forEachBlock((x, y, z, state) => {
    if (x < 0 || x >= 128 || z < 0 || z >= sizeZ) return;

    let base = stateToBase.get(state);
    if (base === undefined && !stateToBase.has(state)) {
      const entry = palette[state];
      base = entry ? resolveBaseIndex(entry) : undefined;
      stateToBase.set(state, base);
      if (base === undefined) {
        const rawName = entry?.Name ? String(entry.Name) : `state#${state}`;
        unknownStates.add(stripNamespace(rawName));
      }
    }
    if (base === undefined) return;

    const idx = z * 128 + x;
    if (y > topY[idx]) {
      topY[idx] = y;
      topBase[idx] = base;
    }

    if (base === WATER_BASE_INDEX) {
      let set = waterYs.get(idx);
      if (!set) {
        set = new Set<number>();
        waterYs.set(idx, set);
      }
      set.add(y);
    }
  });

  if (unknownStates.size) {
    const uniq = [...unknownStates];
    throw new Error(`Unsupported block color mapping for ${uniq.length} block state${uniq.length !== 1 ? "s" : ""}: ${uniq.slice(0, 8).join(", ")}${uniq.length > 8 ? "…" : ""}`);
  }

  const zOffset = sizeZ === 129 ? 1 : 0;
  const colors = new Uint8Array(128 * 128);
  let belowTopUniformShade = -1;
  let belowTopHasShade = false;
  let belowTopMixed = false;

  for (let z = 0; z < 128; ++z) {
    const zInNbt = z + zOffset;
    for (let x = 0; x < 128; ++x) {
      const idx = zInNbt * 128 + x;
      const y = topY[idx];
      if (y === -2147483648) {
        colors[z * 128 + x] = 0;
        continue;
      }

      const base = topBase[idx] || 0;
      if (base === 0) {
        colors[z * 128 + x] = 0;
        continue;
      }

      let shade: number;
      if (base === WATER_BASE_INDEX) {
        shade = getWaterShade(getDepth(waterYs.get(idx), y), x, z);
      } else {
        const northY = zInNbt > 0 ? topY[(zInNbt - 1) * 128 + x] : y;
        const nY = northY === -2147483648 ? y : northY;
        shade = y > nY ? 2 : y === nY ? 1 : 0;
      }
      if (sizeZ === 128 && z > 0 && !belowTopMixed) {
        if (!belowTopHasShade) {
          belowTopUniformShade = shade;
          belowTopHasShade = true;
        } else if (shade !== belowTopUniformShade) belowTopMixed = true;
      }

      colors[z * 128 + x] = base * 4 + shade;
    }
  }

  if (sizeZ === 128 && belowTopHasShade && !belowTopMixed) {
    for (let x = 0; x < 128; ++x) {
      const idx = x;
      const y = topY[idx];
      if (y === -2147483648) continue;
      const base = topBase[idx] || 0;
      if (!base) continue;
      colors[x] = base * 4 + belowTopUniformShade;
    }
  }

  return { colors };
}
