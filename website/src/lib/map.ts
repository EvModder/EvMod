/**
 * Shared map payload types used by converter libs.
 */
export interface MapData {
  colors: Uint8Array;
}

export interface LockedMapData extends MapData {
  locked: boolean;
}
