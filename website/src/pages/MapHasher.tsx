import { useState, useCallback } from "react";
import DropZone from "@/components/DropZone";
import { UUID_RE, parseUUIDs, uuidsToBytes, parseImageUUIDs, nameUUIDFromBytes, setLockedBit, clearLockedBit } from "@/lib/uuid";
import { parseDatFile } from "@/lib/nbt";
import { extractMapsFromCache } from "@/lib/javaDeserialize";

function getFileExtension(name: string): string {
  const dot = name.lastIndexOf(".");
  return dot >= 0 ? name.slice(dot + 1).toLowerCase() : "";
}

async function processFile(file: File): Promise<{ uuids: string[]; error?: string }> {
  const ext = getFileExtension(file.name);

  if (ext === "png") {
    return new Promise((resolve) => {
      const img = new Image();
      img.onload = () => {
        try {
          resolve({ uuids: parseImageUUIDs(img) });
        } catch (err) {
          resolve({ uuids: [], error: err instanceof Error ? err.message : "Image error" });
        }
      };
      img.onerror = () => resolve({ uuids: [], error: "Failed to load image" });
      img.src = URL.createObjectURL(file);
    });
  }

  const buffer = await file.arrayBuffer();

  if (ext === "dat") {
    try {
      const data = parseDatFile(buffer);
      let uuid = nameUUIDFromBytes(data.colors);
      uuid = data.locked ? setLockedBit(uuid) : clearLockedBit(uuid);
      return { uuids: [uuid] };
    } catch (err) {
      return { uuids: [], error: err instanceof Error ? err.message : "DAT parse error" };
    }
  }

  if (ext === "cache") {
    try {
      const maps = extractMapsFromCache(buffer);
      if (!maps.length) return { uuids: [], error: "No map data found in cache file" };
      const uuids = maps.map((m) => {
        let uuid = nameUUIDFromBytes(m.colors);
        uuid = m.locked ? setLockedBit(uuid) : clearLockedBit(uuid);
        return uuid;
      });
      return { uuids };
    } catch (err) {
      return { uuids: [], error: err instanceof Error ? err.message : "Cache parse error" };
    }
  }

  if (ext === "group") {
    if (buffer.byteLength === 0) return { uuids: [], error: "File is empty" };
    if (buffer.byteLength % 16 !== 0) {
      return { uuids: [], error: `File length (${buffer.byteLength} bytes) is not divisible by 16 (UUID length)` };
    }
    return { uuids: parseUUIDs(buffer) };
  }

  return { uuids: [], error: `Unsupported file type: .${ext || "(none)"}` };
}

export default function MapHasher() {
  const [uuids, setUuids] = useState<string[]>([]);
  const [copied, setCopied] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isEditing, setIsEditing] = useState(false);
  const [editText, setEditText] = useState("");

  const handleFiles = useCallback(async (files: File[]) => {
    setError(null);
    setIsEditing(false);
    const allUuids: string[] = [];

    for (const file of files) {
      const result = await processFile(file);
      if (result.error) {
        setError(`${file.name}: ${result.error}`);
        setUuids([]);
        return;
      }
      allUuids.push(...result.uuids);
    }

    if (!allUuids.length) setError("No UUIDs found in the uploaded files.");
    setUuids(allUuids);
  }, []);

  const handleEdit = () => { setEditText(uuids.join("\n")); setIsEditing(true); };

  const handleSave = () => {
    const lines = editText.split("\n").map((l) => l.trim()).filter(Boolean);
    const bytes = uuidsToBytes(lines);
    const a = document.createElement("a");
    a.href = URL.createObjectURL(new Blob([bytes.buffer as ArrayBuffer]));
    a.download = "uuids.bin";
    a.click();
    URL.revokeObjectURL(a.href);
    setUuids(lines);
    setIsEditing(false);
    setError(null);
  };

  const copyAll = async () => {
    await navigator.clipboard.writeText(uuids.join("\n"));
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const editLines = isEditing
    ? editText.split("\n").map((l) => l.trim().toLowerCase()).filter(Boolean)
    : [];
  const invalidCount = editLines.filter((l) => !UUID_RE.test(l)).length;
  const uniqueCount = isEditing
    ? new Set(editLines).size
    : new Set(uuids.map((u) => u.toLowerCase())).size;

  const saveDisabled = (() => {
    if (!isEditing) return true;
    if (invalidCount > 0) return true;
    const editSet = new Set(editLines);
    const origSet = new Set(uuids.map((u) => u.toLowerCase()));
    return editSet.size === origSet.size && [...editSet].every((u) => origSet.has(u));
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
        buttonLabel="Select Files"
      />

      {error && <p className="mt-4 text-red-500 text-sm">{error}</p>}

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
                onChange={(e) => setEditText(e.target.value)}
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
