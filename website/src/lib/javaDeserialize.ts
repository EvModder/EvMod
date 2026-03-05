/**
 * Minimal Java ObjectInputStream deserializer.
 * Handles HashMap, ArrayList, CollSer (immutable lists), primitives, strings, byte arrays.
 * Designed to parse EvMod MapStateCacher .cache files.
 * Exports: `extractMapsFromCache`, `extractMapsFromCacheDetailed`.
 */
import { dashedUuidFromLongs, parseDashedUuid, type DashedUuid } from "@/lib/uuid";
import type { LockedMapData, MapData } from "@/lib/map";

type MapStateData = LockedMapData;

type CacheExtractResult =
  | { kind: "id"; maps: [id: number, map: MapStateData][] }
  | { kind: "name"; maps: [name: string, map: MapStateData][] }
  | { kind: "slot"; containers: [uuid: DashedUuid, maps: [slot: number, map: MapStateData][]][] };

type Entry = [any, any];
type RootKind = "id" | "name" | "slot";
type LooseMapState = MapData & { locked?: boolean };

const listItems = (v: any): any[] | undefined => Array.isArray(v?._elements) ? v._elements : Array.isArray(v) ? v : undefined;

const toBig = (v: any): bigint | undefined =>
  typeof v === "bigint" ? v : typeof v === "number" && Number.isFinite(v) ? BigInt(v) : undefined;

const uuidToString = (key: any): DashedUuid | undefined => {
  if (typeof key === "string") return parseDashedUuid(key) ?? undefined;
  if (!key || typeof key !== "object") return;
  const msb = toBig(key.mostSigBits);
  const lsb = toBig(key.leastSigBits);
  if (msb === undefined || lsb === undefined) return;
  return dashedUuidFromLongs(msb, lsb);
};

const parseRootEntries = (buffer: ArrayBuffer): Entry[] => {
  const parsed = new JavaDeserializer(buffer).parse();
  if (!(parsed && typeof parsed === "object" && Array.isArray((parsed as any)._entries))) throw new Error("Unsupported cache root object");
  return parsed._entries;
};

const getEntryKind = ([k, v]: Entry): RootKind | null => {
  // Container caches store lists (often sparse with null slots); keyed caches store direct map states.
  if (listItems(v)) return "slot";
  if (typeof k === "number") return "id";
  if (typeof k === "string") return "name";
  return null;
};

const resolveRootKind = (entries: Entry[]): RootKind | null => {
  if (!entries.length) return null;
  const firstKind = getEntryKind(entries[0]);
  if (!firstKind) throw new Error("Unsupported cache root entry format");
  for (let i = 1; i < entries.length; ++i) {
    if (getEntryKind(entries[i]) !== firstKind) throw new Error("Mixed cache root entry types");
  }
  return firstKind;
};

const toMapState = (v: LooseMapState): MapStateData => ({ colors: v.colors, locked: !!v.locked });
function assertMapState(v: unknown, where: string): asserts v is LooseMapState {
  if (!(v && typeof v === "object" && (v as any).colors instanceof Uint8Array && (v as any).colors.length === 16384)) throw new Error(`Invalid map state at ${where}`);
}
const getListItemsStrict = (v: unknown, where: string): any[] => {
  const items = listItems(v);
  if (!items) throw new Error(`Invalid container list at ${where}`);
  return items;
};

export function extractMapsFromCache(buffer: ArrayBuffer): MapStateData[] {
  const entries = parseRootEntries(buffer);
  const kind = resolveRootKind(entries);
  if (!kind) return [];
  if (kind !== "slot") return entries.map(([, v], i) => {
    assertMapState(v, `entry ${i}`);
    return toMapState(v);
  });
  const states: MapStateData[] = [];
  for (const [i, [, v]] of entries.entries()) {
    const items = getListItemsStrict(v, `entry ${i}`);
    for (const [slot, item] of items.entries()) {
      if (item == null) continue;
      assertMapState(item, `entry ${i}, slot ${slot}`);
      states.push(toMapState(item));
    }
  }
  return states;
}

export function extractMapsFromCacheDetailed(buffer: ArrayBuffer): CacheExtractResult {
  const entries = parseRootEntries(buffer);
  const kind = resolveRootKind(entries);
  if (!kind) return { kind: "id", maps: [] };

  if (kind === "id") {
    const maps: [number, MapStateData][] = entries
      .map(([k, v], i) => {
        if (typeof k !== "number") throw new Error(`Invalid id key at entry ${i}`);
        assertMapState(v, `entry ${i}`);
        return [k, toMapState(v)] as [number, MapStateData];
      })
    return {
      kind,
      maps: maps.sort((a, b) => a[0] - b[0]),
    };
  }
  if (kind === "name") {
    const maps: [string, MapStateData][] = entries
      .map(([k, v], i) => {
        if (typeof k !== "string") throw new Error(`Invalid name key at entry ${i}`);
        assertMapState(v, `entry ${i}`);
        return [k, toMapState(v)] as [string, MapStateData];
      })
    return {
      kind,
      maps: maps.sort((a, b) => a[0].localeCompare(b[0]))
    };
  }

  const containers: [DashedUuid, [number, MapStateData][]][] = [];
  for (const [i, [k, v]] of entries.entries()) {
    const uuid = uuidToString(k);
    if (!uuid) throw new Error(`Invalid container UUID key at entry ${i}`);
    const items = getListItemsStrict(v, `entry ${i}`);
    const maps: [number, MapStateData][] = [];
    // Keep original slot numbers so output names match inventory/container positions.
    for (const [slot, item] of items.entries()) {
      if (item == null) continue;
      assertMapState(item, `entry ${i}, slot ${slot}`);
      maps.push([slot, toMapState(item)]);
    }
    if (maps.length) containers.push([uuid, maps]);
  }
  return {
    kind: "slot",
    containers: containers.sort((a, b) => a[0].localeCompare(b[0])),
  };
}

// --- Internal types ---
interface JavaObj { _class: string; [k: string]: any }
interface ClassDesc { name: string; flags: number; fields: Field[]; superClass: ClassDesc | null }
interface Field { type: string; name: string }

class JavaDeserializer {
  private view: DataView;
  private bytes: Uint8Array;
  private pos = 0;
  private handles: any[] = [];
  private ops = 0;
  private readonly MAX_OPS = 500000;

  constructor(private buf: ArrayBuffer) {
    this.view = new DataView(buf);
    this.bytes = new Uint8Array(buf);
  }

  parse(): any {
    if (this.u16() !== 0xACED) throw new Error("Not a Java serialized stream");
    if (this.u16() !== 5) throw new Error("Unsupported stream version");
    return this.readContent();
  }

  // --- Primitives ---
  private checkBounds(n: number) {
    if (this.pos + n > this.buf.byteLength) throw new Error(`EOF: need ${n} bytes at position ${this.pos}, have ${this.buf.byteLength - this.pos}`);
    if (++this.ops > this.MAX_OPS) throw new Error(`Exceeded max operations (${this.MAX_OPS}), likely infinite loop`);
  }
  private u8() { this.checkBounds(1); return this.view.getUint8(this.pos++); }
  private i8() { this.checkBounds(1); return this.view.getInt8(this.pos++); }
  private u16() { this.checkBounds(2); const v = this.view.getUint16(this.pos); this.pos += 2; return v; }
  private i32() { this.checkBounds(4); const v = this.view.getInt32(this.pos); this.pos += 4; return v; }
  private f32() { this.checkBounds(4); const v = this.view.getFloat32(this.pos); this.pos += 4; return v; }
  private i64() { this.checkBounds(8); const v = this.view.getBigInt64(this.pos); this.pos += 8; return v; }
  private decodeModifiedUtf(len: number) {
    this.checkBounds(len);
    const end = this.pos + len;
    let out = "";
    while (this.pos < end) {
      const b1 = this.bytes[this.pos++];
      if ((b1 & 0x80) === 0) {
        out += String.fromCharCode(b1);
        continue;
      }
      if ((b1 & 0xE0) === 0xC0) {
        if (this.pos >= end) throw new Error("Truncated modified UTF-8 sequence");
        const b2 = this.bytes[this.pos++];
        out += String.fromCharCode(((b1 & 0x1F) << 6) | (b2 & 0x3F));
        continue;
      }
      if ((b1 & 0xF0) === 0xE0) {
        if (this.pos + 1 >= end) throw new Error("Truncated modified UTF-8 sequence");
        const b2 = this.bytes[this.pos++];
        const b3 = this.bytes[this.pos++];
        out += String.fromCharCode(((b1 & 0x0F) << 12) | ((b2 & 0x3F) << 6) | (b3 & 0x3F));
        continue;
      }
      throw new Error("Unsupported modified UTF-8 byte 0x" + b1.toString(16));
    }
    return out;
  }

  private utf() { return this.decodeModifiedUtf(this.u16()); }
  private handle<T>(o: T): T { this.handles.push(o); return o; }

  // --- Content ---
  private readContent(): any {
    const tc = this.u8();
    switch (tc) {
      case 0x73: return this.readObject();
      case 0x74: return this.handle(this.utf());
      case 0x75: return this.readArray();
      case 0x70: return null;
      case 0x71: return this.handles[this.i32() - 0x7E0000];
      default: throw new Error("Unknown typecode 0x" + tc.toString(16) + " at position " + (this.pos - 1));
    }
  }

  // --- Class descriptor ---
  private readClassDesc(): ClassDesc | null {
    const tc = this.u8();
    switch (tc) {
      case 0x72: return this.readNewClassDesc();
      case 0x70: return null;
      case 0x71: return this.handles[this.i32() - 0x7E0000] as ClassDesc;
      default: throw new Error("Expected classDesc, got 0x" + tc.toString(16));
    }
  }

  private readNewClassDesc(): ClassDesc {
    const name = this.utf();
    this.i64(); // serialVersionUID
    // Per Java spec: handle is assigned BEFORE classDescInfo (flags, fields, annotations, superclass)
    const desc: ClassDesc = { name, flags: 0, fields: [], superClass: null };
    this.handle(desc);
    const flags = this.u8();
    const fc = this.u16();
    desc.flags = flags;
    const fields: Field[] = [];
    for (let i = 0; i < fc; ++i) {
      const type = String.fromCharCode(this.u8());
      const fn = this.utf();
      if (type === "L" || type === "[") this.readContent(); // field class name string/object
      fields.push({ type, name: fn });
    }
    desc.fields = fields;
    this.skipAnnotations();
    desc.superClass = this.readClassDesc();
    return desc;
  }

  private skipAnnotations() {
    let iterations = 0;
    while (true) {
      if (++iterations > 10000) throw new Error("Too many annotations, likely parser desync at position " + this.pos);
      const tc = this.u8();
      if (tc === 0x78) return;
      if (tc === 0x77) { const len = this.u8(); this.checkBounds(len); this.pos += len; }
      else if (tc === 0x7A) { const len = this.i32(); this.checkBounds(len); this.pos += len; }
      else { this.pos--; this.readContent(); }
    }
  }

  // --- Object ---
  private readObject(): any {
    const desc = this.readClassDesc()!;
    const obj: JavaObj = { _class: desc.name };
    const hi = this.handles.push(obj) - 1;
    this.readClassData(desc, obj);

    // Boxed Integer keys appear in by-id cache maps.
    if (desc.name === "java.lang.Integer") {
      this.handles[hi] = obj.value; return obj.value;
    }
    if (desc.name.startsWith("java.lang.") && Object.hasOwn(obj, "value")) {
      throw new Error(`Unsupported boxed primitive in cache shape: ${desc.name}`);
    }
    return obj;
  }

  private readClassData(desc: ClassDesc | null, obj: JavaObj) {
    if (!desc) return;
    this.readClassData(desc.superClass, obj);

    if (desc.flags & 0x02) { // SC_SERIALIZABLE
      for (const f of desc.fields) obj[f.name] = this.readFieldValue(f);
      if (desc.flags & 0x01) this.readWriteObjectData(desc, obj); // SC_WRITE_METHOD
    } else if (desc.flags & 0x04) { // SC_EXTERNALIZABLE
      this.readExternalData(desc, obj);
    }
  }

  private readFieldValue(f: Field): any {
    switch (f.type) {
      case "B": return this.i8();
      case "F": return this.f32();
      case "I": return this.i32();
      case "J": return this.i64();
      case "Z": return this.u8() !== 0;
      case "L": case "[": return this.readContent();
      default: throw new Error("Unsupported field type in cache shape: " + f.type);
    }
  }

  private readWriteObjectData(desc: ClassDesc, obj: JavaObj) {
    const n = desc.name;
    if (n === "java.util.HashMap" || n === "java.util.LinkedHashMap") {
      // HashMap.writeObject: block data = capacity (int) + size (int) = 8 bytes
      const bd = this.readBlockData();
      const bv = new DataView(bd.buffer, bd.byteOffset, bd.byteLength);
      /* capacity */ bv.getInt32(0);
      const size = bv.getInt32(4);
      const entries: [any, any][] = [];
      for (let i = 0; i < size; ++i) entries.push([this.readContent(), this.readContent()]);
      obj._entries = entries;
      if (this.u8() !== 0x78) throw new Error("Expected endBlockData after HashMap entries");
    } else if (n.includes("CollSer") || n.includes("ImmutableCollections")) {
      const bd = this.readBlockData();
      const bv = new DataView(bd.buffer, bd.byteOffset, bd.byteLength);
      const arrayLen = bv.getInt32(0);
      const elements: any[] = [];
      for (let i = 0; i < arrayLen; ++i) elements.push(this.readContent());
      obj._elements = elements;
      if (this.u8() !== 0x78) throw new Error("Expected endBlockData after CollSer entries");
    } else {
      throw new Error(`Unsupported writeObject class: ${n}`);
    }
  }

  /** Read TC_BLOCKDATA (0x77) or TC_BLOCKDATALONG (0x7A) */
  private readBlockData(): Uint8Array {
    const tc = this.u8();
    if (tc !== 0x77) throw new Error("Unsupported blockdata type 0x" + tc.toString(16) + " at position " + (this.pos - 1));
    const len = this.u8();
    const data = this.bytes.slice(this.pos, this.pos + len);
    this.pos += len;
    return data;
  }

  private readExternalData(desc: ClassDesc, obj: JavaObj) {
    if (desc.flags & 0x08) { // SC_BLOCK_DATA
      if (desc.name.includes("CollSer") || desc.name.includes("ImmutableCollections")) {
        const bd = this.readBlockData();
        const bv = new DataView(bd.buffer, bd.byteOffset, bd.byteLength);
        const tag = bv.getUint8(0);
        const arrLen = bv.getInt32(1);
        obj._tag = tag;
        const elements: any[] = [];
        for (let i = 0; i < arrLen; ++i) elements.push(this.readContent());
        obj._elements = elements;
        if (this.u8() !== 0x78) throw new Error("Expected endBlockData after CollSer external data");
      } else {
        throw new Error(`Unsupported externalizable class: ${desc.name}`);
      }
    }
  }

  // --- Array ---
  private readArray(): any {
    const desc = this.readClassDesc()!;
    const length = this.i32();
    const et = desc.name.charAt(1);

    if (et !== "B") throw new Error("Unsupported array type in cache shape: " + desc.name);
    const arr = this.bytes.slice(this.pos, this.pos + length);
    this.pos += length;
    return this.handle(arr);
  }
}
