/**
 * Shared color types required by the synced map-color dataset.
 * Exports: `Shade`, `ColorRgb`, `ColorRgbBase`.
 */
export enum Shade {
  Dark = 0,
  Flat = 1,
  Light = 2,
  Darkest = 3,
}

export interface ColorRgb {
  r: number;
  g: number;
  b: number;
  blocks: string[];
}

export interface ColorRgbBase extends ColorRgb {
  name: string;
}
