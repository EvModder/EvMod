import type { MapImage } from "@/lib/mapPng";
import { downloadUrl, zipAndDownload } from "@/lib/browser";

interface MapImageGalleryProps {
  images: MapImage[];
  zipName: string;
  summaryLabel?: string;
}

export default function MapImageGallery({ images, zipName, summaryLabel }: MapImageGalleryProps) {
  return (
    <div className="mt-6">
      <div className="mb-3 flex items-center justify-between">
        <span className="text-sm text-muted-foreground">
          {summaryLabel ?? `${images.length} image${images.length !== 1 ? "s" : ""}`}
        </span>
        {images.length > 1 && (
          <button onClick={() => zipAndDownload(images, zipName)} className="rounded bg-accent px-3 py-1 text-sm text-accent-foreground transition-colors hover:bg-accent/80">
            Download All (ZIP)
          </button>
        )}
      </div>
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4 md:grid-cols-6 lg:grid-cols-8 xl:grid-cols-9">
        {images.map(img => (
          <button key={img.url} onClick={() => downloadUrl(img.url, img.name)} className="group overflow-hidden rounded-lg border border-border transition-colors hover:border-accent" title={`Download ${img.name}`}>
            <img src={img.url} alt={img.name} className="aspect-square w-full object-contain bg-muted" style={{ imageRendering: "pixelated" }} />
            <div className="break-all px-2 py-1.5 text-[11px] leading-tight text-muted-foreground transition-colors group-hover:text-accent">{img.name}</div>
          </button>
        ))}
      </div>
    </div>
  );
}
