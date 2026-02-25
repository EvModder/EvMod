import { useState, useCallback } from "react";
import JSZip from "jszip";
import DropZone from "@/components/DropZone";
import { extractMapsFromCache } from "@/lib/javaDeserialize";
import { colorsToPngBlob } from "@/lib/mapColors";

interface MapImage {
  name: string;
  url: string;
  blob: Blob;
}

export default function CacheToPng() {
  const [images, setImages] = useState<MapImage[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const handleFiles = useCallback(async (files: File[]) => {
    setError(null);
    setLoading(true);
    const results: MapImage[] = [];

    for (const file of files) {
      try {
        const buffer = await file.arrayBuffer();
        const maps = extractMapsFromCache(buffer);
        if (!maps.length) {
          setError(`No map data found in ${file.name}`);
          continue;
        }
        for (const map of maps) {
          const blob = await colorsToPngBlob(map.colors);
          const url = URL.createObjectURL(blob);
          const label = map.label ?? "map";
          const base = file.name.replace(/\.cache$/i, "");
          const name = maps.length === 1 ? `${base}.png` : `${base}_${label}.png`;
          results.push({ name, url, blob });
        }
      } catch (err) {
        setError(`Error processing ${file.name}: ${err instanceof Error ? err.message : "Unknown error"}`);
      }
    }

    setImages((prev) => {
      prev.forEach((img) => URL.revokeObjectURL(img.url));
      return results;
    });
    setLoading(false);
  }, []);

  const downloadOne = (img: MapImage) => {
    const a = document.createElement("a");
    a.href = img.url;
    a.download = img.name;
    a.click();
  };

  const downloadAll = async () => {
    const zip = new JSZip();
    for (const img of images) zip.file(img.name, img.blob);
    const content = await zip.generateAsync({ type: "blob" });
    const a = document.createElement("a");
    a.href = URL.createObjectURL(content);
    a.download = "cache_maps.zip";
    a.click();
    URL.revokeObjectURL(a.href);
  };

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
        buttonLabel="Select .cache Files"
      />

      {loading && <p className="mt-4 text-muted-foreground text-sm">Processing…</p>}
      {error && <p className="mt-4 text-red-500 text-sm">{error}</p>}

      {images.length > 0 && (
        <div className="mt-6">
          <div className="flex items-center justify-between mb-3">
            <span className="text-sm text-muted-foreground">
              {images.length} image{images.length !== 1 && "s"}
            </span>
            {images.length > 1 && (
              <button
                onClick={downloadAll}
                className="px-3 py-1 text-sm rounded bg-accent text-accent-foreground hover:bg-accent/80 transition-colors"
              >
                Download All (ZIP)
              </button>
            )}
          </div>
          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-3">
            {images.map((img) => (
              <button
                key={img.name}
                onClick={() => downloadOne(img)}
                className="group border border-border rounded-lg overflow-hidden hover:border-accent transition-colors"
                title={`Download ${img.name}`}
              >
                <img
                  src={img.url}
                  alt={img.name}
                  className="w-full aspect-square object-contain bg-muted"
                  style={{ imageRendering: "pixelated" }}
                />
                <div className="px-2 py-1.5 text-xs text-muted-foreground truncate group-hover:text-accent transition-colors">
                  {img.name}
                </div>
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
