import { useState, useCallback, useEffect } from "react";
import DropZone from "@/components/DropZone";
import FileErrorList from "@/components/FileErrorList";
import MapImageGallery from "@/components/MapImageGallery";
import { buildMapImages, revokeMapImageUrls, type MapImage } from "@/lib/mapPng";
import { parseMapDataFromNbt } from "@/lib/mapNbt";

export default function NbtToPng() {
  const [images, setImages] = useState<MapImage[]>([]);
  const [errors, setErrors] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);

  const handleFiles = useCallback(async (files: File[]) => {
    setErrors([]);
    setLoading(true);
    const result = await buildMapImages({
      files,
      parseMaps: async file => [parseMapDataFromNbt(await file.arrayBuffer())],
      buildName: ({ file }) => file.name.replace(/\.(nbt|litematic|schem)$/i, "") + ".png",
    });
    setImages(result.images);
    setErrors(result.errors);
    setLoading(false);
  }, []);

  useEffect(() => () => revokeMapImageUrls(images), [images]);

  return (
    <div>
      <p className="mb-4 text-sm text-muted-foreground">
        Convert 128x128 .nbt, .litematic, or .schem files to PNG images
      </p>

      <DropZone
        label="Drop .nbt files here"
        subtitle="Supports structure NBTs with X=128 and Z=128 or 129 (for shading/noobline)"
        accept=".nbt,.litematic,.schem"
        multiple
        onFiles={handleFiles}
      />
      <p className="mt-4 text-sm text-muted-foreground">
        Looking for my schematic generator?{" "}
        <a
          href="https://evmodder.net/PNG-to-NBT/"
          target="_blank"
          rel="noopener noreferrer"
          className="underline hover:text-foreground transition-colors"
        >
          PNG → NBT
        </a>
      </p>

      {loading && <p className="mt-4 text-sm text-muted-foreground">Processing…</p>}
      <FileErrorList errors={errors} />

      {images.length > 0 && <MapImageGallery images={images} zipName="nbt_maps.zip" />}
    </div>
  );
}
