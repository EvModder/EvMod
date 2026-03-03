/**
 * Player-profile lookup + container UUID helpers for CACHE-to-PNG naming.
 * Exports: `containerTypeFromUuid`, `lookupContainerPlayerNames`.
 */
import { parseDashedUuid, type DashedUuid } from "@/lib/uuid";

const PROFILE_CACHE_KEY = "evmod_mojang_profile_cache";
const LOOKUP_TIMEOUT_MS = 3000;
const POSITIVE_TTL_MS = 24 * 60 * 60 * 1000;
const CONTAINER_FLAG_NIBBLE_INDEX = 17; // Matches Java: (uuid.getMostSignificantBits() & ~1L) | bit

interface CachedProfile {
  name: string | null;
  ts: number;
}

let persistedCache: Record<string, CachedProfile> | null = null;
let persistTimer: ReturnType<typeof setTimeout> | null = null;
const inflightByUuid = new Map<DashedUuid, Promise<string | null>>();

const readProfileName = (json: { username?: unknown; name?: unknown }) =>
  typeof json.username === "string" && json.username
    ? json.username
    : typeof json.name === "string" && json.name
      ? json.name
      : null;

const asCachedProfile = (entry: unknown): CachedProfile | null => {
  if (!entry || typeof entry !== "object") return null;
  const { name, ts } = entry as { name?: unknown; ts?: unknown };
  if (typeof ts !== "number" || !Number.isFinite(ts)) return null;
  if (typeof name === "string") return { name, ts };
  if (name === null) return { name: null, ts };
  return null;
};

const loadCache = () => {
  if (persistedCache) return persistedCache;
  if (typeof window === "undefined") return (persistedCache = {});
  try {
    const raw = window.localStorage.getItem(PROFILE_CACHE_KEY);
    const parsed = raw ? JSON.parse(raw) as Record<string, unknown> : {};
    const safe: Record<string, CachedProfile> = {};
    for (const [key, entry] of Object.entries(parsed)) {
      const uuid = parseDashedUuid(key);
      const profile = asCachedProfile(entry);
      if (!uuid || !profile) continue;
      safe[uuid] = profile;
    }
    persistedCache = safe;
  } catch {
    persistedCache = {};
  }
  return persistedCache;
};

const queuePersist = () => {
  if (persistTimer || typeof window === "undefined") return;
  persistTimer = setTimeout(() => {
    persistTimer = null;
    try { window.localStorage.setItem(PROFILE_CACHE_KEY, JSON.stringify(loadCache())); }
    catch { /* ignore storage failures */ }
  }, 120);
};

const getCached = (uuid: DashedUuid) => {
  const cache = loadCache();
  const entry = cache[uuid];
  if (!entry) return undefined;
  if (entry.name === null) return null;
  if (Date.now() - entry.ts <= POSITIVE_TTL_MS) return entry.name;
  delete cache[uuid];
  queuePersist();
  return undefined;
};

const setCached = (uuid: DashedUuid, name: string | null) => {
  loadCache()[uuid] = { name, ts: Date.now() };
  queuePersist();
};

const getContainerFlagNibble = (uuid: DashedUuid) =>
  Number.parseInt(uuid[CONTAINER_FLAG_NIBBLE_INDEX] ?? "0", 16);

const toggleFirstBit = (uuid: DashedUuid): DashedUuid => {
  const nibble = getContainerFlagNibble(uuid);
  return `${uuid.slice(0, CONTAINER_FLAG_NIBBLE_INDEX)}${(nibble ^ 1).toString(16)}${uuid.slice(CONTAINER_FLAG_NIBBLE_INDEX + 1)}` as DashedUuid;
};

export const containerTypeFromUuid = (uuid: DashedUuid): "inv" | "ec" => {
  const nibble = getContainerFlagNibble(uuid);
  return (nibble & 1) === 0 ? "inv" : "ec";
};

const fetchProfileNameByUuid = async (uuid: DashedUuid): Promise<string | null> => {
  const cached = getCached(uuid);
  if (cached !== undefined) return cached;

  const inflight = inflightByUuid.get(uuid);
  if (inflight) return inflight;

  const promise = (async () => {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), LOOKUP_TIMEOUT_MS);
    try {
      // GH Pages is static-only; browser CORS for Mojang endpoints is unreliable.
      // Keep a single Ashcon lookup endpoint that is CORS-friendly for static sites.
      const resp = await fetch(`https://api.ashcon.app/mojang/v2/user/${uuid}`, {
        method: "GET",
        signal: controller.signal,
      });
      if (resp.status !== 404 && resp.status !== 204 && resp.ok) {
        const json = await resp.json() as { username?: unknown; name?: unknown };
        const name = readProfileName(json);
        if (name) {
          setCached(uuid, name);
          return name;
        }
      }
      setCached(uuid, null);
      return null;
    } catch {
      return null;
    } finally {
      clearTimeout(timeout);
      inflightByUuid.delete(uuid);
    }
  })();

  inflightByUuid.set(uuid, promise);
  return promise;
};

const lookupContainerPlayerName = async (base: DashedUuid) => {
  const toggled = toggleFirstBit(base);
  const [direct, toggledName] = await Promise.all([
    fetchProfileNameByUuid(base),
    toggled === base ? Promise.resolve<string | null>(null) : fetchProfileNameByUuid(toggled),
  ]);
  return direct ?? toggledName ?? null;
};

export const lookupContainerPlayerNames = async (uuids: readonly string[], concurrency = 8) => {
  const unique = [...new Set(uuids.map(parseDashedUuid).filter((uuid): uuid is DashedUuid => !!uuid))];
  const out = new Map<DashedUuid, string>();
  let next = 0;
  const worker = async () => {
    for (;;) {
      const i = next++;
      if (i >= unique.length) return;
      const uuid = unique[i];
      const name = await lookupContainerPlayerName(uuid);
      if (name) out.set(uuid, name);
    }
  };
  await Promise.all(Array.from({ length: Math.max(1, Math.min(concurrency, unique.length)) }, () => worker()));
  return out;
};
