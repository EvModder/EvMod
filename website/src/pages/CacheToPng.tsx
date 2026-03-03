import { useState, useCallback, useEffect } from "react";
import DropZone from "@/components/DropZone";
import FileErrorList from "@/components/FileErrorList";
import MapImageGallery from "@/components/MapImageGallery";
import { nextFrame } from "@/lib/browser";
import { extractMapsFromCacheDetailed } from "@/lib/javaDeserialize";
import { containerTypeFromUuid, lookupContainerPlayerNames } from "@/lib/mojang";
import { buildMapImages, revokeMapImageUrls, type MapImage } from "@/lib/mapPng";
import type { DashedUuid } from "@/lib/uuid";

const WINDOWS_RESERVED = /^(con|prn|aux|nul|com[1-9]|lpt[1-9])$/i;
const ILLEGAL_FILENAME_CHARS = '<>:"/\\|?*';

const sanitizeStem = (raw: string) => {
  const normalized = raw.normalize("NFKC").trim();
  const safe = Array.from(normalized, ch => {
    const code = ch.charCodeAt(0);
    return ch === "\uFFFD" || code < 32 || code === 127 || ILLEGAL_FILENAME_CHARS.includes(ch) ? "_" : ch;
  })
    .join("")
    .replace(/\s+/g, " ")
    .replace(/_+/g, "_")
    .replace(/^\.+/g, "")
    .replace(/[. ]+$/g, "")
    .trim();
  const stem = (safe || "map").slice(0, 120);
  return WINDOWS_RESERVED.test(stem) ? `${stem}_` : stem;
};

const shortUuid = (uuid: DashedUuid) => uuid.slice(0, 8) || "unknown";

const buildSlotLabelPrefix = (key: DashedUuid, playerNameByUuid: Map<DashedUuid, string>) => {
  const playerName = playerNameByUuid.get(key);
  return playerName
    ? `${sanitizeStem(playerName)}_${containerTypeFromUuid(key)}_`
    : `${shortUuid(key)}_`;
};

export default function CacheToPng() {
  const [images, setImages] = useState<MapImage[]>([]);
  const [errors, setErrors] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);
  const [statusText, setStatusText] = useState<string | null>(null);
  const [holderCount, setHolderCount] = useState(0);

  const handleFiles = useCallback(async (files: File[]) => {
    setErrors([]);
    setImages([]);
    setStatusText(null);
    setHolderCount(0);
    setLoading(true);
    let holders = 0;
    const result = await buildMapImages({
      files,
      parseMaps: async (file, { fileIndex, fileCount }) => {
        setStatusText(`[${fileIndex + 1}/${fileCount}] ${file.name}: Reading file…`);
        await nextFrame();
        const parsed = extractMapsFromCacheDetailed(await file.arrayBuffer());
        if (parsed.kind === "id") {
          const maps = new Array(parsed.maps.length);
          for (const [i, [id, map]] of parsed.maps.entries()) maps[i] = { colors: map.colors, label: `map_${id}` };
          return {
            maps,
            meta: { containerCount: 0 },
          };
        }
        if (parsed.kind === "name") {
          const maps = new Array(parsed.maps.length);
          for (const [i, [name, map]] of parsed.maps.entries()) maps[i] = { colors: map.colors, label: sanitizeStem(name) };
          return {
            maps,
            meta: { containerCount: 0 },
          };
        }
        const containerKeys = parsed.containers.map(([key]) => key);
        let playerNameByUuid = new Map<DashedUuid, string>();
        if (containerKeys.length) {
          setStatusText(`[${fileIndex + 1}/${fileCount}] ${file.name}: Checking ${containerKeys.length} holder UUID${containerKeys.length !== 1 ? "s" : ""}…`);
          await nextFrame();
          playerNameByUuid = await lookupContainerPlayerNames(containerKeys);
        }
        let totalMaps = 0;
        for (const [, states] of parsed.containers) totalMaps += states.length;
        const maps = new Array(totalMaps);
        let out = 0;
        for (const [key, states] of parsed.containers) {
          const labelPrefix = buildSlotLabelPrefix(key, playerNameByUuid);
          for (const [slot, m] of states) maps[out++] = { colors: m.colors, label: `${labelPrefix}${slot}` };
        }
        return {
          maps,
          meta: { containerCount: parsed.containers.length },
        };
      },
      onFileParsed: async ({ file, maps, meta, fileIndex, fileCount }) => {
        setStatusText(`[${fileIndex + 1}/${fileCount}] ${file.name}: Processing ${maps.length} element${maps.length !== 1 ? "s" : ""}…`);
        await nextFrame();
        holders += meta?.containerCount ?? 0;
      },
      buildName: ({ map }) => `${map.label ?? "map"}.png`,
    });
    setHolderCount(holders);
    setImages(result.images);
    setErrors(result.errors);
    setStatusText(null);
    setLoading(false);
  }, []);

  useEffect(() => () => revokeMapImageUrls(images), [images]);

  return (
    <div>
      <p className="text-sm text-muted-foreground mb-4">
        Convert EvMod .cache files to PNG images
      </p>

      <DropZone
        label="Drop .cache files here"
        subtitle="Java-serialized HashMap files from EvMod's ./map_cache/ folder"
        accept=".cache"
        multiple
        onFiles={handleFiles}
      />

      {loading && (
        <p className="mt-4 text-muted-foreground text-sm">{statusText ?? "Reading file…"}</p>
      )}
      <FileErrorList errors={errors} />

      {images.length > 0 && (
        <MapImageGallery
          images={images}
          zipName="cache_maps.zip"
          summaryLabel={holderCount > 0
            ? `${images.length} image${images.length !== 1 ? "s" : ""} (${holderCount} holder${holderCount !== 1 ? "s" : ""})`
            : undefined}
        />
      )}
    </div>
  );
}
