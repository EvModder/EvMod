import pako from "pako";

export interface DatFileData {
  colors: Uint8Array;
  locked: boolean;
  scale?: number;
  dimension?: string;
}

export function parseDatFile(buffer: ArrayBuffer): DatFileData {
  let data: Uint8Array;
  try {
    data = pako.inflate(new Uint8Array(buffer));
  } catch {
    data = new Uint8Array(buffer);
  }

  const reader = new NbtReader(data);
  const root = reader.readNamedTag();
  const d = root.value?.data ?? root.value;
  if (!d) throw new Error('No "data" tag found in NBT');

  const colors = d.colors;
  if (!colors || colors.length !== 16384)
    throw new Error("Invalid or missing colors array (expected 16384 bytes)");

  return {
    colors: new Uint8Array(colors),
    locked: d.locked === 1,
    scale: d.scale,
    dimension: d.dimension,
  };
}

class NbtReader {
  private pos = 0;
  private view: DataView;

  constructor(private data: Uint8Array) {
    this.view = new DataView(data.buffer, data.byteOffset, data.byteLength);
  }

  readNamedTag(): { name: string; value: any } {
    const type = this.u8();
    if (type === 0) return { name: "", value: null };
    const name = this.str();
    return { name, value: this.payload(type) };
  }

  private u8() { return this.data[this.pos++]; }
  private i8() { return this.view.getInt8(this.pos++); }
  private i16() { const v = this.view.getInt16(this.pos); this.pos += 2; return v; }
  private i32() { const v = this.view.getInt32(this.pos); this.pos += 4; return v; }
  private i64() { const v = this.view.getBigInt64(this.pos); this.pos += 8; return v; }
  private f32() { const v = this.view.getFloat32(this.pos); this.pos += 4; return v; }
  private f64() { const v = this.view.getFloat64(this.pos); this.pos += 8; return v; }

  private str(): string {
    const len = this.view.getUint16(this.pos);
    this.pos += 2;
    const s = new TextDecoder().decode(this.data.slice(this.pos, this.pos + len));
    this.pos += len;
    return s;
  }

  private payload(type: number): any {
    switch (type) {
      case 1: return this.i8();
      case 2: return this.i16();
      case 3: return this.i32();
      case 4: return Number(this.i64());
      case 5: return this.f32();
      case 6: return this.f64();
      case 7: { const n = this.i32(); const a = this.data.slice(this.pos, this.pos + n); this.pos += n; return a; }
      case 8: return this.str();
      case 9: { const et = this.u8(); const n = this.i32(); const a: any[] = []; for (let i = 0; i < n; i++) a.push(this.payload(et)); return a; }
      case 10: {
        const c: Record<string, any> = {};
        while (true) { const t = this.u8(); if (t === 0) break; const nm = this.str(); c[nm] = this.payload(t); }
        return c;
      }
      case 11: { const n = this.i32(); const a: number[] = []; for (let i = 0; i < n; i++) a.push(this.i32()); return a; }
      case 12: { const n = this.i32(); const a: bigint[] = []; for (let i = 0; i < n; i++) a.push(this.i64()); return a; }
      default: throw new Error("Unknown NBT tag: " + type);
    }
  }
}
