// javaDeserialize.ts
var listItems = (v) => Array.isArray(v?._elements) ? v._elements : Array.isArray(v) ? v : void 0;
var toBig = (v) => typeof v === "bigint" ? v : typeof v === "number" && Number.isFinite(v) ? BigInt(v) : void 0;
var uuidToString = (key) => {
  if (typeof key === "string") return key.toLowerCase();
  if (!key || typeof key !== "object") return;
  const msb = toBig(key.mostSigBits);
  const lsb = toBig(key.leastSigBits);
  if (msb === void 0 || lsb === void 0) return;
  const a = BigInt.asUintN(64, msb).toString(16).padStart(16, "0");
  const b = BigInt.asUintN(64, lsb).toString(16).padStart(16, "0");
  return `${a.slice(0, 8)}-${a.slice(8, 12)}-${a.slice(12, 16)}-${b.slice(0, 4)}-${b.slice(4)}`;
};
var parseRootEntries = (buffer) => {
  const parsed = new JavaDeserializer(buffer).parse();
  if (!(parsed && typeof parsed === "object" && Array.isArray(parsed._entries))) throw new Error("Unsupported cache root object");
  return parsed._entries;
};
var getEntryKind = ([k, v]) => {
  if (listItems(v)) return "slot";
  if (typeof k === "number") return "id";
  if (typeof k === "string") return "name";
  return null;
};
var resolveRootKind = (entries) => {
  if (!entries.length) return null;
  const firstKind = getEntryKind(entries[0]);
  if (!firstKind) throw new Error("Unsupported cache root entry format");
  for (let i = 1; i < entries.length; ++i) {
    if (getEntryKind(entries[i]) !== firstKind) throw new Error("Mixed cache root entry types");
  }
  return firstKind;
};
var toMapState = (v) => ({ colors: v.colors, locked: !!v.locked });
var assertMapState = (v, where) => {
  if (!(v && typeof v === "object" && v.colors instanceof Uint8Array && v.colors.length === 16384)) throw new Error(`Invalid map state at ${where}`);
};
var getListItemsStrict = (v, where) => {
  const items = listItems(v);
  if (!items) throw new Error(`Invalid container list at ${where}`);
  return items;
};
function extractMapsFromCache(buffer) {
  const entries = parseRootEntries(buffer);
  const kind = resolveRootKind(entries);
  if (!kind) return [];
  if (kind !== "slot") return entries.map(([, v], i) => {
    assertMapState(v, `entry ${i}`);
    return toMapState(v);
  });
  const states = [];
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
function extractMapsFromCacheDetailed(buffer) {
  const entries = parseRootEntries(buffer);
  const kind = resolveRootKind(entries);
  if (!kind) return { kind: "id", maps: [] };
  if (kind === "id") {
    const maps = entries.map(([k, v], i) => {
      if (typeof k !== "number") throw new Error(`Invalid id key at entry ${i}`);
      assertMapState(v, `entry ${i}`);
      return [k, toMapState(v)];
    });
    return {
      kind,
      maps: maps.sort((a, b) => a[0] - b[0])
    };
  }
  if (kind === "name") {
    const maps = entries.map(([k, v], i) => {
      if (typeof k !== "string") throw new Error(`Invalid name key at entry ${i}`);
      assertMapState(v, `entry ${i}`);
      return [k, toMapState(v)];
    });
    return {
      kind,
      maps: maps.sort((a, b) => a[0].localeCompare(b[0]))
    };
  }
  const containers = [];
  for (const [i, [k, v]] of entries.entries()) {
    const uuid = uuidToString(k);
    if (!uuid) throw new Error(`Invalid container UUID key at entry ${i}`);
    const items = getListItemsStrict(v, `entry ${i}`);
    const maps = [];
    for (const [slot, item] of items.entries()) {
      if (item == null) continue;
      assertMapState(item, `entry ${i}, slot ${slot}`);
      maps.push([slot, toMapState(item)]);
    }
    if (maps.length) containers.push([uuid, maps]);
  }
  return {
    kind: "slot",
    containers: containers.sort((a, b) => a[0].localeCompare(b[0]))
  };
}
var JavaDeserializer = class {
  constructor(buf) {
    this.buf = buf;
    this.view = new DataView(buf);
    this.bytes = new Uint8Array(buf);
  }
  view;
  bytes;
  pos = 0;
  handles = [];
  ops = 0;
  MAX_OPS = 5e5;
  parse() {
    if (this.u16() !== 44269) throw new Error("Not a Java serialized stream");
    if (this.u16() !== 5) throw new Error("Unsupported stream version");
    return this.readContent();
  }
  // --- Primitives ---
  checkBounds(n) {
    if (this.pos + n > this.buf.byteLength) throw new Error(`EOF: need ${n} bytes at position ${this.pos}, have ${this.buf.byteLength - this.pos}`);
    if (++this.ops > this.MAX_OPS) throw new Error(`Exceeded max operations (${this.MAX_OPS}), likely infinite loop`);
  }
  u8() {
    this.checkBounds(1);
    return this.view.getUint8(this.pos++);
  }
  i8() {
    this.checkBounds(1);
    return this.view.getInt8(this.pos++);
  }
  u16() {
    this.checkBounds(2);
    const v = this.view.getUint16(this.pos);
    this.pos += 2;
    return v;
  }
  i32() {
    this.checkBounds(4);
    const v = this.view.getInt32(this.pos);
    this.pos += 4;
    return v;
  }
  f32() {
    this.checkBounds(4);
    const v = this.view.getFloat32(this.pos);
    this.pos += 4;
    return v;
  }
  i64() {
    this.checkBounds(8);
    const v = this.view.getBigInt64(this.pos);
    this.pos += 8;
    return v;
  }
  decodeModifiedUtf(len) {
    this.checkBounds(len);
    const end = this.pos + len;
    let out = "";
    while (this.pos < end) {
      const b1 = this.bytes[this.pos++];
      if ((b1 & 128) === 0) {
        out += String.fromCharCode(b1);
        continue;
      }
      if ((b1 & 224) === 192) {
        if (this.pos >= end) throw new Error("Truncated modified UTF-8 sequence");
        const b2 = this.bytes[this.pos++];
        out += String.fromCharCode((b1 & 31) << 6 | b2 & 63);
        continue;
      }
      if ((b1 & 240) === 224) {
        if (this.pos + 1 >= end) throw new Error("Truncated modified UTF-8 sequence");
        const b2 = this.bytes[this.pos++];
        const b3 = this.bytes[this.pos++];
        out += String.fromCharCode((b1 & 15) << 12 | (b2 & 63) << 6 | b3 & 63);
        continue;
      }
      throw new Error("Unsupported modified UTF-8 byte 0x" + b1.toString(16));
    }
    return out;
  }
  utf() {
    return this.decodeModifiedUtf(this.u16());
  }
  handle(o) {
    this.handles.push(o);
    return o;
  }
  // --- Content ---
  readContent() {
    const tc = this.u8();
    switch (tc) {
      case 115:
        return this.readObject();
      case 116:
        return this.handle(this.utf());
      case 117:
        return this.readArray();
      case 112:
        return null;
      case 113:
        return this.handles[this.i32() - 8257536];
      default:
        throw new Error("Unknown typecode 0x" + tc.toString(16) + " at position " + (this.pos - 1));
    }
  }
  // --- Class descriptor ---
  readClassDesc() {
    const tc = this.u8();
    switch (tc) {
      case 114:
        return this.readNewClassDesc();
      case 112:
        return null;
      case 113:
        return this.handles[this.i32() - 8257536];
      default:
        throw new Error("Expected classDesc, got 0x" + tc.toString(16));
    }
  }
  readNewClassDesc() {
    const name = this.utf();
    const uid = this.i64();
    const desc = { name, uid, flags: 0, fields: [], superClass: null };
    this.handle(desc);
    const flags = this.u8();
    const fc = this.u16();
    desc.flags = flags;
    const fields = [];
    for (let i = 0; i < fc; ++i) {
      const type = String.fromCharCode(this.u8());
      const fn = this.utf();
      const className = type === "L" || type === "[" ? this.readContent() : void 0;
      fields.push({ type, name: fn, className });
    }
    desc.fields = fields;
    this.skipAnnotations();
    desc.superClass = this.readClassDesc();
    return desc;
  }
  skipAnnotations() {
    let iterations = 0;
    while (true) {
      if (++iterations > 1e4) throw new Error("Too many annotations, likely parser desync at position " + this.pos);
      const tc = this.u8();
      if (tc === 120) return;
      if (tc === 119) {
        const len = this.u8();
        this.checkBounds(len);
        this.pos += len;
      } else if (tc === 122) {
        const len = this.i32();
        this.checkBounds(len);
        this.pos += len;
      } else {
        this.pos--;
        this.readContent();
      }
    }
  }
  // --- Object ---
  readObject() {
    const desc = this.readClassDesc();
    const obj = { _class: desc.name };
    const hi = this.handles.push(obj) - 1;
    this.readClassData(desc, obj);
    if (desc.name === "java.lang.Integer") {
      this.handles[hi] = obj.value;
      return obj.value;
    }
    if (desc.name.startsWith("java.lang.") && Object.hasOwn(obj, "value")) {
      throw new Error(`Unsupported boxed primitive in cache shape: ${desc.name}`);
    }
    return obj;
  }
  readClassData(desc, obj) {
    if (!desc) return;
    this.readClassData(desc.superClass, obj);
    if (desc.flags & 2) {
      for (const f of desc.fields) obj[f.name] = this.readFieldValue(f);
      if (desc.flags & 1) this.readWriteObjectData(desc, obj);
    } else if (desc.flags & 4) {
      this.readExternalData(desc, obj);
    }
  }
  readFieldValue(f) {
    switch (f.type) {
      case "B":
        return this.i8();
      case "F":
        return this.f32();
      case "I":
        return this.i32();
      case "J":
        return this.i64();
      case "Z":
        return this.u8() !== 0;
      case "L":
      case "[":
        return this.readContent();
      default:
        throw new Error("Unsupported field type in cache shape: " + f.type);
    }
  }
  readWriteObjectData(desc, obj) {
    const n = desc.name;
    if (n === "java.util.HashMap" || n === "java.util.LinkedHashMap") {
      const bd = this.readBlockData();
      const bv = new DataView(bd.buffer, bd.byteOffset, bd.byteLength);
      bv.getInt32(0);
      const size = bv.getInt32(4);
      const entries = [];
      for (let i = 0; i < size; ++i) entries.push([this.readContent(), this.readContent()]);
      obj._entries = entries;
      if (this.u8() !== 120) throw new Error("Expected endBlockData after HashMap entries");
    } else if (n.includes("CollSer") || n.includes("ImmutableCollections")) {
      const bd = this.readBlockData();
      const bv = new DataView(bd.buffer, bd.byteOffset, bd.byteLength);
      const arrayLen = bv.getInt32(0);
      const elements = [];
      for (let i = 0; i < arrayLen; ++i) elements.push(this.readContent());
      obj._elements = elements;
      if (this.u8() !== 120) throw new Error("Expected endBlockData after CollSer entries");
    } else {
      throw new Error(`Unsupported writeObject class: ${n}`);
    }
  }
  /** Read TC_BLOCKDATA (0x77) or TC_BLOCKDATALONG (0x7A) */
  readBlockData() {
    const tc = this.u8();
    if (tc !== 119) throw new Error("Unsupported blockdata type 0x" + tc.toString(16) + " at position " + (this.pos - 1));
    const len = this.u8();
    const data = this.bytes.slice(this.pos, this.pos + len);
    this.pos += len;
    return data;
  }
  readExternalData(desc, obj) {
    if (desc.flags & 8) {
      if (desc.name.includes("CollSer") || desc.name.includes("ImmutableCollections")) {
        const bd = this.readBlockData();
        const bv = new DataView(bd.buffer, bd.byteOffset, bd.byteLength);
        const tag = bv.getUint8(0);
        const arrLen = bv.getInt32(1);
        obj._tag = tag;
        const elements = [];
        for (let i = 0; i < arrLen; ++i) elements.push(this.readContent());
        obj._elements = elements;
        if (this.u8() !== 120) throw new Error("Expected endBlockData after CollSer external data");
      } else {
        throw new Error(`Unsupported externalizable class: ${desc.name}`);
      }
    }
  }
  // --- Array ---
  readArray() {
    const desc = this.readClassDesc();
    const length = this.i32();
    const et = desc.name.charAt(1);
    if (et !== "B") throw new Error("Unsupported array type in cache shape: " + desc.name);
    const arr = this.bytes.slice(this.pos, this.pos + length);
    this.pos += length;
    return this.handle(arr);
  }
};
export {
  extractMapsFromCache,
  extractMapsFromCacheDetailed
};
