import { useState, useCallback, useEffect } from "react";
import DropZone from "@/components/DropZone";
import FileErrorList from "@/components/FileErrorList";
import MapImageGallery from "@/components/MapImageGallery";
import { parseMapDataFromDat } from "@/lib/mapDat";
import { buildMapImages, revokeMapImageUrls, type MapImage } from "@/lib/mapPng";

export default function DatToPng() {
  const [images, setImages] = useState<MapImage[]>([]);
  const [errors, setErrors] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);

  const handleFiles = useCallback(async (files: File[]) => {
    setErrors([]);
    setLoading(true);
    const result = await buildMapImages({
      files,
      parseMaps: async file => [parseMapDataFromDat(await file.arrayBuffer())],
      buildName: ({ file }) => file.name.replace(/\.dat$/i, "") + ".png",
    });
    setImages(result.images);
    setErrors(result.errors);
    setLoading(false);
  }, []);

  useEffect(() => () => revokeMapImageUrls(images), [images]);

  return (
    <div>
      <p className="text-sm text-muted-foreground mb-4">
        Convert Minecraft map_#.dat files to PNG images
      </p>

      <DropZone
        label="Drop .dat files here"
        subtitle="GZip-compressed NBT map files from your world's ./data/ folder"
        accept=".dat"
        multiple
        onFiles={handleFiles}
      />

      {loading && <p className="mt-4 text-muted-foreground text-sm">Processing…</p>}
      <FileErrorList errors={errors} />

      {images.length > 0 && <MapImageGallery images={images} zipName="maps.zip" />}
    </div>
  );
}
