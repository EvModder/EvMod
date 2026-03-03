/**
 * Browser IO helpers.
 * Exports: `nextFrame`, `downloadUrl`, `downloadBlob`, `zipAndDownload`, `loadImageFromFile`.
 */
import JSZip from "jszip";

interface NamedBlob {
  name: string;
  blob: Blob;
}

export const nextFrame = () => new Promise<void>(resolve => requestAnimationFrame(() => resolve()));

export const downloadUrl = (url: string, name: string) => {
  const a = document.createElement("a");
  a.href = url;
  a.download = name;
  a.click();
};

export const downloadBlob = (blob: Blob, name: string) => {
  const url = URL.createObjectURL(blob);
  downloadUrl(url, name);
  URL.revokeObjectURL(url);
};

export const zipAndDownload = async (items: NamedBlob[], zipName: string) => {
  const zip = new JSZip();
  for (const { name, blob } of items) zip.file(name, blob);
  downloadBlob(await zip.generateAsync({ type: "blob" }), zipName);
};

export const loadImageFromFile = (file: File): Promise<HTMLImageElement> =>
  new Promise((resolve, reject) => {
    const img = new Image();
    const url = URL.createObjectURL(file);
    img.onload = () => {
      URL.revokeObjectURL(url);
      resolve(img);
    };
    img.onerror = () => {
      URL.revokeObjectURL(url);
      reject(new Error("Failed to load image"));
    };
    img.src = url;
  });
