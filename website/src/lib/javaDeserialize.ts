/**
 * Minimal Java ObjectInputStream deserializer.
 * Handles HashMap, ArrayList, CollSer (immutable lists), primitives, strings, byte arrays.
 * Designed to parse EvMod MapStateCacher .cache files.
 */

export interface MapData {
  colors: Uint8Array;
  locked: boolean;
  scale?: number;
  label?: string;
}

export function extractMapsFromCache(buffer: ArrayBuffer): MapData[] {
  const objs = new JavaDeserializer(buffer).parse();
  const maps: MapData[] = [];
  const visited = new Set<any>();

  function deepFind(obj: any, label?: string) {
    if (!obj || typeof obj !== "object" || visited.has(obj)) return;
    if (obj instanceof Uint8Array || obj instanceof ArrayBuffer) return;
    visited.add(obj);

    // Check if this object has a 16384-byte colors array
    if (obj.colors instanceof Uint8Array && obj.colors.length === 16384) {
      maps.push({
        colors: obj.colors,
        locked: !!obj.locked,
        scale: typeof obj.scale === "number" ? obj.scale : undefined,
        label,
      });
      return;
    }

    // Recurse into HashMap entries
    if (obj._entries) {
      for (const [k, v] of obj._entries) {
        deepFind(v, String(k));
      }
    }

    // Recurse into list elements (ArrayList, CollSer)
    if (obj._elements) {
      obj._elements.forEach((el: any, i: number) => {
        deepFind(el, label ? `${label}_${i}` : `${i}`);
      });
    }

    // Recurse into plain arrays
    if (Array.isArray(obj)) {
      obj.forEach((el: any, i: number) => {
        deepFind(el, label ? `${label}_${i}` : `${i}`);
      });
    }

    // Recurse into any object properties (catch-all)
    for (const key of Object.keys(obj)) {
      if (key === "_class" || key === "colors") continue;
      const val = obj[key];
      if (val && typeof val === "object" && !(val instanceof Uint8Array)) {
        deepFind(val, key);
      }
    }
  }

  for (const obj of objs) {
    deepFind(obj);
  }
  return maps;
}

// --- Internal types ---
interface JavaObj { _class: string; [k: string]: any }
interface ClassDesc { name: string; uid: bigint; flags: number; fields: Field[]; superClass: ClassDesc | null }
interface Field { type: string; name: string; className?: string }

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

  parse(): any[] {
    if (this.u16() !== 0xACED) throw new Error("Not a Java serialized stream");
    if (this.u16() !== 5) throw new Error("Unsupported stream version");

    const first = this.readContent();
    const res: any[] = [first];

    while (this.pos < this.buf.byteLength) {
      const prevPos = this.pos;
      try { res.push(this.readContent()); } catch { break; }
      if (this.pos === prevPos) break; // safety: no progress
    }
    return res;
  }

  // --- Primitives ---
  private checkBounds(n: number) {
    if (this.pos + n > this.buf.byteLength) throw new Error(`EOF: need ${n} bytes at position ${this.pos}, have ${this.buf.byteLength - this.pos}`);
    if (++this.ops > this.MAX_OPS) throw new Error(`Exceeded max operations (${this.MAX_OPS}), likely infinite loop`);
  }
  private u8() { this.checkBounds(1); return this.view.getUint8(this.pos++); }
  private i8() { this.checkBounds(1); return this.view.getInt8(this.pos++); }
  private u16() { this.checkBounds(2); const v = this.view.getUint16(this.pos); this.pos += 2; return v; }
  private i16() { this.checkBounds(2); const v = this.view.getInt16(this.pos); this.pos += 2; return v; }
  private i32() { this.checkBounds(4); const v = this.view.getInt32(this.pos); this.pos += 4; return v; }
  private f32() { this.checkBounds(4); const v = this.view.getFloat32(this.pos); this.pos += 4; return v; }
  private f64() { this.checkBounds(8); const v = this.view.getFloat64(this.pos); this.pos += 8; return v; }
  private i64() { this.checkBounds(8); const v = this.view.getBigInt64(this.pos); this.pos += 8; return v; }
  private utf() { const n = this.u16(); this.checkBounds(n); const s = new TextDecoder().decode(this.bytes.slice(this.pos, this.pos + n)); this.pos += n; return s; }
  private handle<T>(o: T): T { this.handles.push(o); return o; }

  // --- Content ---
  private readContent(): any {
    const tc = this.u8();
    switch (tc) {
      case 0x73: return this.readObject();
      case 0x74: return this.handle(this.utf());
      case 0x7C: { const n = Number(this.i64()); const s = new TextDecoder().decode(this.bytes.slice(this.pos, this.pos + n)); this.pos += n; return this.handle(s); }
      case 0x75: return this.readArray();
      case 0x70: return null;
      case 0x71: return this.handles[this.i32() - 0x7E0000];
      case 0x76: { const d = this.readClassDesc(); return this.handle({ _class: "Class", name: d?.name }); }
      case 0x7E: return this.readEnum();
      default: throw new Error("Unknown typecode 0x" + tc.toString(16) + " at position " + (this.pos - 1));
    }
  }

  // --- Class descriptor ---
  private readClassDesc(): ClassDesc | null {
    const tc = this.u8();
    switch (tc) {
      case 0x72: return this.readNewClassDesc();
      case 0x7D: return this.readProxyClassDesc();
      case 0x70: return null;
      case 0x71: return this.handles[this.i32() - 0x7E0000] as ClassDesc;
      default: throw new Error("Expected classDesc, got 0x" + tc.toString(16));
    }
  }

  private readNewClassDesc(): ClassDesc {
    const name = this.utf();
    const uid = this.i64();
    // Per Java spec: handle is assigned BEFORE classDescInfo (flags, fields, annotations, superclass)
    const desc: ClassDesc = { name, uid, flags: 0, fields: [], superClass: null };
    this.handle(desc);
    const flags = this.u8();
    const fc = this.u16();
    desc.flags = flags;
    const fields: Field[] = [];
    for (let i = 0; i < fc; i++) {
      const type = String.fromCharCode(this.u8());
      const fn = this.utf();
      const className = (type === "L" || type === "[") ? this.readContent() as string : undefined;
      fields.push({ type, name: fn, className });
    }
    desc.fields = fields;
    this.skipAnnotations();
    desc.superClass = this.readClassDesc();
    return desc;
  }

  private readProxyClassDesc(): ClassDesc {
    const desc: ClassDesc = { name: "Proxy", uid: 0n, flags: 0, fields: [], superClass: null };
    this.handle(desc);
    const n = this.i32();
    for (let i = 0; i < n; i++) this.utf();
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

    // Unwrap primitives
    if (desc.name === "java.lang.Integer" || desc.name === "java.lang.Short" || desc.name === "java.lang.Byte") {
      this.handles[hi] = obj.value; return obj.value;
    }
    if (desc.name === "java.lang.Long") { this.handles[hi] = obj.value; return obj.value; }
    if (desc.name === "java.lang.Boolean") { this.handles[hi] = obj.value; return obj.value; }
    if (desc.name === "java.lang.Float" || desc.name === "java.lang.Double") { this.handles[hi] = obj.value; return obj.value; }
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
      case "C": return String.fromCharCode(this.u16());
      case "D": return this.f64();
      case "F": return this.f32();
      case "I": return this.i32();
      case "J": return this.i64();
      case "S": return this.i16();
      case "Z": return this.u8() !== 0;
      case "L": case "[": return this.readContent();
      default: throw new Error("Unknown field type: " + f.type);
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
      for (let i = 0; i < size; i++) entries.push([this.readContent(), this.readContent()]);
      obj._entries = entries;
      if (this.u8() !== 0x78) throw new Error("Expected endBlockData after HashMap entries");
    } else if (n === "java.util.Hashtable") {
      // Hashtable.writeObject: block data = capacity (int) + loadFactor (float) + count (int) = 12 bytes
      const bd = this.readBlockData();
      const bv = new DataView(bd.buffer, bd.byteOffset, bd.byteLength);
      /* capacity */ bv.getInt32(0);
      /* loadFactor */ bv.getFloat32(4);
      const size = bv.getInt32(8);
      const entries: [any, any][] = [];
      for (let i = 0; i < size; i++) entries.push([this.readContent(), this.readContent()]);
      obj._entries = entries;
      if (this.u8() !== 0x78) throw new Error("Expected endBlockData after Hashtable entries");
    } else if (n === "java.util.HashSet" || n === "java.util.LinkedHashSet") {
      // HashSet.writeObject: block data = capacity (int) + loadFactor (float) + size (int) = 12 bytes
      const bd = this.readBlockData();
      const bv = new DataView(bd.buffer, bd.byteOffset, bd.byteLength);
      /* capacity */ bv.getInt32(0);
      /* loadFactor */ bv.getFloat32(4);
      const size = bv.getInt32(8);
      const elements: any[] = [];
      for (let i = 0; i < size; i++) elements.push(this.readContent());
      obj._elements = elements;
      if (this.u8() !== 0x78) throw new Error("Expected endBlockData after HashSet entries");
    } else if (n === "java.util.ArrayList") {
      const bd = this.readBlockData();
      // block data contains capacity (int)
      const size = obj.size as number;
      const elements: any[] = [];
      for (let i = 0; i < size; i++) elements.push(this.readContent());
      obj._elements = elements;
      if (this.u8() !== 0x78) throw new Error("Expected endBlockData after ArrayList entries");
    } else if (n.includes("CollSer") || n.includes("ImmutableCollections")) {
      const bd = this.readBlockData();
      const bv = new DataView(bd.buffer, bd.byteOffset, bd.byteLength);
      const arrayLen = bv.getInt32(0);
      const elements: any[] = [];
      for (let i = 0; i < arrayLen; i++) elements.push(this.readContent());
      obj._elements = elements;
      if (this.u8() !== 0x78) throw new Error("Expected endBlockData after CollSer entries");
    } else {
      // Unknown class with writeObject — skip custom data
      this.skipAnnotations();
    }
  }

  /** Read TC_BLOCKDATA (0x77) or TC_BLOCKDATALONG (0x7A) */
  private readBlockData(): Uint8Array {
    const tc = this.u8();
    let len: number;
    if (tc === 0x77) {
      len = this.u8();
    } else if (tc === 0x7A) {
      len = this.i32();
    } else {
      throw new Error("Expected blockdata (0x77 or 0x7A), got 0x" + tc.toString(16) + " at position " + (this.pos - 1));
    }
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
        for (let i = 0; i < arrLen; i++) elements.push(this.readContent());
        obj._elements = elements;
        if (this.u8() !== 0x78) throw new Error("Expected endBlockData after CollSer external data");
      } else {
        this.skipAnnotations();
      }
    }
  }

  // --- Array ---
  private readArray(): any {
    const desc = this.readClassDesc()!;
    const length = this.i32();
    const et = desc.name.charAt(1);

    if (et === "B") {
      const arr = this.bytes.slice(this.pos, this.pos + length);
      this.pos += length;
      return this.handle(arr);
    }

    const arr: any[] = [];
    this.handle(arr);
    for (let i = 0; i < length; i++) {
      switch (et) {
        case "I": arr.push(this.i32()); break;
        case "J": arr.push(this.i64()); break;
        case "S": arr.push(this.i16()); break;
        case "F": arr.push(this.f32()); break;
        case "D": arr.push(this.f64()); break;
        case "Z": arr.push(this.u8() !== 0); break;
        case "C": arr.push(String.fromCharCode(this.u16())); break;
        default: arr.push(this.readContent()); break;
      }
    }
    return arr;
  }

  // --- Enum ---
  private readEnum(): any {
    const desc = this.readClassDesc()!;
    const name = this.readContent() as string;
    return this.handle({ _class: desc.name, name });
  }
}
