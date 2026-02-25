import { useState, useCallback } from "react";

interface DropZoneProps {
  label: string;
  subtitle?: string;
  accept?: string;
  multiple?: boolean;
  onFiles: (files: File[]) => void;
  buttonLabel?: string;
}

export default function DropZone({ label, subtitle, accept, multiple, onFiles, buttonLabel = "Select Files" }: DropZoneProps) {
  const [isDragging, setIsDragging] = useState(false);

  const handleDrop = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(false);
    const files = Array.from(e.dataTransfer.files);
    if (files.length) onFiles(files);
  }, [onFiles]);

  const handleInput = (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(e.target.files ?? []);
    if (files.length) onFiles(files);
    e.target.value = "";
  };

  return (
    <div
      className={`border-2 border-dashed rounded-lg p-8 text-center transition-colors ${
        isDragging ? "border-accent bg-accent/10" : "border-border hover:border-muted-foreground"
      }`}
      onDragOver={(e) => { e.preventDefault(); setIsDragging(true); }}
      onDragLeave={() => setIsDragging(false)}
      onDrop={handleDrop}
    >
      <p className="mb-2 text-muted-foreground">{label}</p>
      {subtitle && <p className="mb-4 text-xs text-muted-foreground">{subtitle}</p>}
      <label className="inline-block cursor-pointer px-4 py-2 rounded bg-muted hover:bg-border transition-colors">
        <input type="file" className="hidden" onChange={handleInput} accept={accept} multiple={multiple} />
        {buttonLabel}
      </label>
    </div>
  );
}
