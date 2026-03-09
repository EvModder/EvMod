import { useState, useCallback } from "react";
import DropZone from "@/components/DropZone";
import FileErrorList from "@/components/FileErrorList";
import { UUID_RE, parseUUIDs, uuidsToBytes, parseImageUUIDs, nameUUIDFromBytes, setLockedBit, clearLockedBit } from "@/lib/uuid";
import { parseMapDataFromDat } from "@/lib/mapDat";
import { extractMapsFromCache } from "@/lib/javaDeserialize";
import { downloadBlob, loadImageFromFile, nextFrame } from "@/lib/browser";
import type { MapData } from "@/lib/map";

interface ProcessResult {
  uuids: string[];
  error?: string;
}

type FileHandler = (file: File) => Promise<ProcessResult>;

function getFileExtension(name: string): string {
  const dot = name.lastIndexOf(".");
  return dot >= 0 ? name.slice(dot + 1).toLowerCase() : "";
}

const dedupeUuids = (list: string[]) => {
  const seen = new Set<string>();
  return list.filter(uuid => !seen.has(uuid) && !!seen.add(uuid));
};

const mapDataToUuid = ({ colors }: MapData, locked: boolean) => {
  const uuid = nameUUIDFromBytes(colors);
  return locked ? setLockedBit(uuid) : clearLockedBit(uuid);
};

const fileHandlers: Record<string, FileHandler> = {
  png: async file => {
    try {
      return { uuids: parseImageUUIDs(await loadImageFromFile(file)) };
    } catch (err) {
      return { uuids: [], error: err instanceof Error ? err.message : "Image error" };
    }
  },
  dat: async file => {
    try {
      const mapData = parseMapDataFromDat(await file.arrayBuffer());
      return { uuids: [mapDataToUuid(mapData, mapData.locked)] };
    } catch (err) {
      return { uuids: [], error: err instanceof Error ? err.message : "DAT parse error" };
    }
  },
  cache: async file => {
    try {
      const maps = extractMapsFromCache(await file.arrayBuffer());
      if (!maps.length) return { uuids: [], error: "No map data found in cache file" };
      return { uuids: maps.map(m => mapDataToUuid(m, m.locked)) };
    } catch (err) {
      return { uuids: [], error: err instanceof Error ? err.message : "Cache parse error" };
    }
  },
  group: async file => {
    const buffer = await file.arrayBuffer();
    if (buffer.byteLength === 0) return { uuids: [], error: "File is empty" };
    if (buffer.byteLength % 16 !== 0) {
      return { uuids: [], error: `File length (${buffer.byteLength} bytes) is not divisible by 16 (UUID length)` };
    }
    return { uuids: parseUUIDs(buffer) };
  },
};

async function processFile(file: File): Promise<ProcessResult> {
  const ext = getFileExtension(file.name);
  const handler = fileHandlers[ext];
  return handler ? handler(file) : { uuids: [], error: `Unsupported file type: .${ext || "(none)"}` };
}

export default function MapHasher() {
  const [uuids, setUuids] = useState<string[]>([]);
  const [copied, setCopied] = useState(false);
  const [errors, setErrors] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);
  const [statusText, setStatusText] = useState<string | null>(null);
  const [isEditing, setIsEditing] = useState(false);
  const [editText, setEditText] = useState("");

  const handleFiles = useCallback(async (files: File[]) => {
    setErrors([]);
    setIsEditing(false);
    setLoading(true);
    setStatusText(null);
    const allUuids: string[] = [];
    const allErrors: string[] = [];

    for (const [i, file] of files.entries()) {
      setStatusText(`[${i + 1}/${files.length}] ${file.name}`);
      await nextFrame();
      const result = await processFile(file);
      if (result.error) allErrors.push(`${file.name}: ${result.error}`);
      allUuids.push(...result.uuids);
    }
    setLoading(false);
    setStatusText(null);

    if (allErrors.length) setErrors(allErrors);
    else if (!allUuids.length) setErrors(["No UUIDs found in the uploaded files."]);
    setUuids(dedupeUuids(allUuids));
  }, []);

  const handleEdit = () => { setEditText(uuids.join("\n")); setIsEditing(true); };

  const handleSave = () => {
    const lines = editText.split("\n").map(l => l.trim()).filter(Boolean);
    const bytes = uuidsToBytes(lines);
    downloadBlob(new Blob([bytes]), "uuids.bin");
    setUuids(lines);
    setIsEditing(false);
    setErrors([]);
  };

  const copyAll = async () => {
    await navigator.clipboard.writeText(dedupeUuids(uuids).join("\n"));
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const editLines = isEditing
    ? editText.split("\n").map(l => l.trim().toLowerCase()).filter(Boolean)
    : [];
  const invalidCount = editLines.filter(l => !UUID_RE.test(l)).length;
  const uniqueCount = isEditing
    ? new Set(editLines).size
    : new Set(uuids.map(u => u.toLowerCase())).size;

  const saveDisabled = (() => {
    if (!isEditing) return true;
    if (invalidCount > 0) return true;
    const editSet = new Set(editLines);
    const origSet = new Set(uuids.map(u => u.toLowerCase()));
    return editSet.size === origSet.size && [...editSet].every(u => origSet.has(u));
  })();

  return (
    <div>
      <p className="text-sm text-muted-foreground mb-4">
        Find map hash codes for .png images, .dat file, .cache files (from ./evmod/mapart_caches/), or .group files (from ./evmod/mapart_groups/)
      </p>

      <DropZone
        label="Drop files here"
        subtitle="Supports .png, .dat, .cache, and .group"
        accept=".png,.dat,.cache,.group"
        multiple
        onFiles={handleFiles}
      />
      {loading && <p className="mt-4 text-sm text-muted-foreground">{statusText ?? "Reading file…"}</p>}

      <FileErrorList errors={errors} />

      {uuids.length > 0 && (
        <div className="mt-6 border border-border rounded-lg p-4">
          <div className="mb-3 flex items-center justify-between">
            <span className="text-sm text-muted-foreground">
              {isEditing && invalidCount > 0
                ? <span className="text-red-500">{invalidCount} invalid UUID{invalidCount !== 1 && "s"}</span>
                : `${uniqueCount} UUID${uniqueCount !== 1 ? "s" : ""}`}
            </span>
            <div className="flex gap-2">
              <button onClick={copyAll} className="px-3 py-1 text-sm rounded hover:bg-muted transition-colors">
                {copied ? "✓ Copied" : "Copy All"}
              </button>
              {isEditing ? (
                <button
                  onClick={handleSave}
                  disabled={saveDisabled}
                  className={`px-3 py-1 text-sm rounded transition-colors ${
                    saveDisabled
                      ? "bg-muted text-muted-foreground cursor-not-allowed"
                      : "bg-accent text-accent-foreground hover:bg-accent/80"
                  }`}
                >
                  Save
                </button>
              ) : (
                <button onClick={handleEdit} className="px-3 py-1 text-sm rounded hover:bg-muted transition-colors">
                  Edit
                </button>
              )}
            </div>
          </div>
          <div className="max-h-96 overflow-auto rounded bg-muted p-4">
            {isEditing ? (
              <textarea
                value={editText}
                onChange={e => setEditText(e.target.value)}
                className="w-full h-64 font-mono text-sm bg-transparent border-none outline-none resize-none"
              />
            ) : (
              <pre className="font-mono text-sm whitespace-pre-wrap break-all">{uuids.join("\n")}</pre>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
