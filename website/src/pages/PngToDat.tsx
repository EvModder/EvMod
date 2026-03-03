import { useCallback, useEffect, useState } from "react";
import DropZone from "@/components/DropZone";
import { parseMapDataFromImageData } from "@/lib/mapColors";
import { encodeMapDataToDat } from "@/lib/mapDat";
import { downloadUrl, loadImageFromFile, zipAndDownload } from "@/lib/browser";

interface GeneratedDat {
  name: string;
  blob: Blob;
  url: string;
}

interface FileMessage {
  file?: string;
  message: string;
}

const revokeUrls = (items: GeneratedDat[]) => {
  for (const { url } of items) URL.revokeObjectURL(url);
};

export default function PngToDat() {
  const [filesOut, setFilesOut] = useState<GeneratedDat[]>([]);
  const [errors, setErrors] = useState<FileMessage[]>([]);
  const [warnings, setWarnings] = useState<FileMessage[]>([]);
  const [loading, setLoading] = useState(false);
  const [startIndex, setStartIndex] = useState("0");

  const handleFiles = useCallback(async (files: File[]) => {
    setErrors([]);
    setWarnings([]);
    setLoading(true);

    const parsedStart = Number(startIndex);
    if (!Number.isInteger(parsedStart) || parsedStart < 0) {
      setLoading(false);
      setFilesOut([]);
      setErrors([{ message: "Starting index must be a non-negative integer." }]);
      return;
    }

    const next: GeneratedDat[] = [];
    const nextErrors: FileMessage[] = [];
    const nextWarnings: FileMessage[] = [];
    let mapIndex = parsedStart;

    for (const file of files) {
      try {
        const img = await loadImageFromFile(file);
        if (img.width % 128 || img.height % 128) {
          nextErrors.push({ file: file.name, message: `Image dimensions must be multiples of 128 (got ${img.width}×${img.height})` });
          continue;
        }

        const canvas = document.createElement("canvas");
        canvas.width = img.width;
        canvas.height = img.height;
        const ctx = canvas.getContext("2d");
        if (!ctx) throw new Error("2D canvas context unavailable");
        ctx.drawImage(img, 0, 0);

        let approxPixelsForFile = 0;
        const approxColorKeysForFile = new Set<number>();
        let approxTilesForFile = 0;
        let tilesForFile = 0;
        for (let y = 0; y < img.height; y += 128) {
          for (let x = 0; x < img.width; x += 128) {
            ++tilesForFile;
            const tile = ctx.getImageData(x, y, 128, 128);
            const { mapData, approximatedPixels, approximatedColorKeys } = parseMapDataFromImageData(tile.data);
            if (approximatedPixels > 0) {
              approxPixelsForFile += approximatedPixels;
              ++approxTilesForFile;
              for (const key of approximatedColorKeys) approxColorKeysForFile.add(key);
            }
            const name = `map_${mapIndex}.dat`;
            ++mapIndex;
            const blob = encodeMapDataToDat(mapData);
            next.push({ name, blob, url: URL.createObjectURL(blob) });
          }
        }

        if (approxPixelsForFile > 0) {
          const isEntireImage = approxPixelsForFile === tilesForFile * 16384;
          const pxText = isEntireImage
            ? "entire image"
            : `${approxPixelsForFile} pixel${approxPixelsForFile !== 1 ? "s" : ""}`;
          const tileText = !isEntireImage && tilesForFile > 1
            ? ` across ${approxTilesForFile}/${tilesForFile} tile${tilesForFile !== 1 ? "s" : ""}`
            : "";
          nextWarnings.push({
            file: file.name,
            message: `${approxColorKeysForFile.size} color${approxColorKeysForFile.size !== 1 ? "s" : ""} (${pxText}) approximated${tileText}.`,
          });
        }
      } catch (err) {
        nextErrors.push({ file: file.name, message: err instanceof Error ? err.message : "Unknown error" });
      }
    }

    setFilesOut(next);
    setWarnings(nextWarnings);
    setErrors(nextErrors);
    setLoading(false);
  }, [startIndex]);

  useEffect(() => () => revokeUrls(filesOut), [filesOut]);

  return (
    <div>
      <p className="mb-4 text-sm text-muted-foreground">
        Convert image files to Minecraft map_#.dat files
      </p>

      <DropZone
        label="Drop image files here"
        subtitle="Each image must be a multiple of 128x128. Larger images are split into 128x128 map tiles."
        accept="image/*"
        multiple
        onFiles={handleFiles}
      />

      <div className="mt-4 flex items-center gap-2 text-sm text-muted-foreground">
        <label htmlFor="start-index">Starting Index:</label>
        <input
          id="start-index"
          className="w-28 rounded border border-border bg-background px-2 py-1 text-sm [appearance:textfield] [&::-webkit-inner-spin-button]:appearance-none [&::-webkit-outer-spin-button]:appearance-none"
          type="number"
          min={0}
          step={1}
          value={startIndex}
          onChange={e => setStartIndex(e.target.value)}
        />
      </div>

      {loading && <p className="mt-4 text-sm text-muted-foreground">Processing…</p>}
      {(errors.length > 0 || warnings.length > 0) && (
        <ul className="mt-4 list-disc space-y-1 pl-5 text-sm">
          {errors.map((item, i) => (
            <li className="text-red-500" key={`e-${i}-${item.file ?? "general"}-${item.message}`}>
              {item.file && <span className="font-semibold text-foreground">{item.file}: </span>}
              {item.message}
            </li>
          ))}
          {warnings.map((item, i) => (
            <li className="text-amber-500" key={`w-${i}-${item.file ?? "general"}-${item.message}`}>
              {item.file && <span className="font-semibold text-foreground">{item.file}: </span>}
              {item.message}
            </li>
          ))}
        </ul>
      )}

      {filesOut.length > 0 && (
        <div className="mt-6">
          <div className="mb-3 flex items-center justify-between">
            <span className="text-sm text-muted-foreground">
              {filesOut.length} .dat file{filesOut.length !== 1 && "s"}
            </span>
            {filesOut.length > 1 && (
              <button onClick={() => zipAndDownload(filesOut, "maps_dat.zip")} className="rounded bg-accent px-3 py-1 text-sm text-accent-foreground transition-colors hover:bg-accent/80">
                Download All (ZIP)
              </button>
            )}
          </div>
          <div className="grid grid-cols-3 gap-2 sm:grid-cols-6 lg:grid-cols-9">
            {filesOut.map(f => (
              <button
                key={f.name}
                onClick={() => downloadUrl(f.url, f.name)}
                className="truncate rounded border border-border px-3 py-2 text-left text-sm text-muted-foreground transition-colors hover:border-accent hover:text-accent"
                title={`Download ${f.name}`}
              >
                {f.name}
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
