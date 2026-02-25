import { useState, useCallback } from "react";
import { md5 } from "@/lib/md5";
import { useTheme } from "@/hooks/useTheme";

const MAP_COLORS: number[] = [
  0xff000000, 0xff000000, 0xff000000, 0xff000000,
  -10912473, -9594576, -8408520, -12362211, -5331853, -2766452, -530013, -8225962, -7566196, -5526613,
  -3684409, -9868951, -4980736, -2359296, -65536, -7929856, -9408332, -7697700, -6250241, -11250553, -9079435, -7303024, -5789785, -10987432,
  -16754944, -16750080, -16745472, -16760576, -4934476, -2302756, -1, -7895161, -9210239, -7499618, -5986120, -11118495, -9810890, -8233406, -6853299,
  -11585240, -11579569, -10461088, -9408400, -12895429, -13816396, -13158436, -12566273, -14605945, -10202062, -8690114, -7375032, -11845850,
  -4935252, -2303533, -779, -7895679, -6792924, -4559572, -2588877, -9288933, -8571496, -6733382, -5092136, -10606478, -12030824, -10976070,
  -10053160, -13217422, -6184668, -3816148, -1710797, -8816357, -10907631, -9588715, -8401895, -12358643, -5613196, -3117682, -884827, -8371369,
  -13290187, -12500671, -11776948, -14145496, -9671572, -8092540, -6710887, -11447983, -13280916, -12489340, -11763815, -14138543, -10933123,
  -9619815, -8437838, -12377762, -14404227, -13876839, -13415246, -14997410, -12045020, -10993364, -10073037, -13228005, -12035804, -10982100,
  -10059981, -13221093, -9690076, -8115156, -6737101, -11461861, -15658735, -15395563, -15132391, -15921907, -5199818, -2634430, -332211, -8094168,
  -12543338, -11551561, -10691627, -13601936, -13346124, -12620068, -11894529, -14204025, -16738008, -16729294, -16721606, -16748002, -10798046,
  -9483734, -8301007, -12309223, -11599616, -10485504, -9436672, -12910336, -7111567, -4941686, -3034719, -9544363, -9422567, -7780833, -6335964,
  -11261165, -9880244, -8369315, -6989972, -11653575, -11580319, -10461833, -9409398, -12895927, -8168167, -6262241, -4553436, -10336749, -12037595,
  -10984403, -9997003, -13222628, -9423305, -7716285, -6271666, -11261911, -14148584, -13556962, -13031133, -14805742, -10532027, -9151404, -7902366,
  -12109773, -12763072, -11841713, -11051940, -13750224, -11128002, -9879989, -8763048, -12573138, -13292736, -12503729, -11780516, -14147536,
  -13294824, -12506338, -11783645, -14149102, -13289187, -12499420, -11775446, -14144746, -10212832, -8768729, -7455698, -11854056, -15069429,
  -14740979, -14346736, -15529208, -8052446, -6084310, -4378575, -10217191, -9950140, -8440237, -7061663, -11656909, -12578540, -11594471, -10741475,
  -13628145, -15771554, -15569805, -15303034, -16039354, -14130078, -13469064, -12939636, -14791862, -12837077, -11918027, -11129794, -13822176,
  -15827107, -15623310, -15420283, -16097466, -12171706, -11119018, -10197916, -13355980, -6784153, -4548994, -2576493, -9282483, -10914455, -9596799,
  -8411242, -12363697
];

const MAP_COLORS_REVERSE = new Map<number, number>();
MAP_COLORS.forEach((color, idx) => MAP_COLORS_REVERSE.set(color, idx));
MAP_COLORS_REVERSE.set(0, 0);
MAP_COLORS_REVERSE.set(0xff000000 | 0, 0);

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

const bytesToHex = (bytes: Uint8Array): string =>
  Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");

const formatUUID = (hex: string) =>
  `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20, 32)}`;

const uuidsToBytes = (uuidList: string[]): Uint8Array => {
  const bytes = new Uint8Array(uuidList.length * 16);
  uuidList.forEach((uuid, i) => {
    const hex = uuid.replace(/-/g, "");
    for (let j = 0; j < 16; j++)
      bytes[i * 16 + j] = parseInt(hex.slice(j * 2, j * 2 + 2), 16);
  });
  return bytes;
};

const parseUUIDs = (buffer: ArrayBuffer): string[] => {
  const bytes = new Uint8Array(buffer);
  const result: string[] = [];
  for (let i = 0; i + 16 <= bytes.length; i += 16)
    result.push(formatUUID(bytesToHex(bytes.slice(i, i + 16))));
  return result;
};

const nameUUIDFromBytes = (bytes: Uint8Array): string => {
  const hash = md5(bytes);
  hash[6] = (hash[6] & 0x0f) | 0x30;
  hash[8] = (hash[8] & 0x3f) | 0x80;
  return formatUUID(bytesToHex(hash));
};

const setLockedBit = (uuid: string): string => {
  const [first, ...rest] = uuid.split("-");
  const byte0 = parseInt(first.slice(0, 2), 16) | 0x01;
  return byte0.toString(16).padStart(2, "0") + first.slice(2) + "-" + rest.join("-");
};

const parseImageUUIDs = (img: HTMLImageElement): string[] => {
  const canvas = document.createElement("canvas");
  canvas.width = img.width;
  canvas.height = img.height;
  const ctx = canvas.getContext("2d")!;
  ctx.drawImage(img, 0, 0);

  if (img.width % 128 || img.height % 128)
    throw new Error(`Dimensions must be divisible by 128. Got ${img.width}×${img.height}`);

  const results: string[] = [];
  for (let by = 0; by < img.height / 128; by++) {
    for (let bx = 0; bx < img.width / 128; bx++) {
      const { data } = ctx.getImageData(bx * 128, by * 128, 128, 128);
      const colors = new Uint8Array(128 * 128);
      for (let i = 0; i < 128 * 128; i++) {
        const off = i * 4;
        const argb = ((data[off + 3] << 24) | (data[off] << 16) | (data[off + 1] << 8) | data[off + 2]) | 0;
        const colorByte = MAP_COLORS_REVERSE.get(argb);
        if (colorByte === undefined) {
          const x = bx * 128 + (i % 128), y = by * 128 + ((i / 128) | 0);
          throw new Error(`Unsupported color at (${x}, ${y}): ARGB=${argb}`);
        }
        colors[i] = colorByte;
      }
      results.push(setLockedBit(nameUUIDFromBytes(colors)));
    }
  }
  return results;
};

export default function Index() {
  const { resolved, toggle } = useTheme();
  const [uuids, setUuids] = useState<string[]>([]);
  const [copied, setCopied] = useState(false);
  const [isDragging, setIsDragging] = useState(false);
  const [activeTab, setActiveTab] = useState<"binary" | "image">("binary");
  const [error, setError] = useState<string | null>(null);
  const [isEditing, setIsEditing] = useState(false);
  const [editText, setEditText] = useState("");

  const handleFile = useCallback((file: File, mode: "binary" | "image") => {
    setError(null);
    if (mode === "binary") {
      const reader = new FileReader();
      reader.onload = (e) => {
        const parsed = parseUUIDs(e.target!.result as ArrayBuffer);
        setUuids(parsed);
        if (!parsed.length) setError("No UUIDs found. File may be empty or invalid.");
      };
      reader.readAsArrayBuffer(file);
    } else {
      const img = new Image();
      img.onload = () => {
        try {
          const parsed = parseImageUUIDs(img);
          setUuids(parsed);
          if (!parsed.length) setError("No UUIDs found.");
        } catch (err) {
          setError(err instanceof Error ? err.message : "Unknown error");
        }
      };
      img.src = URL.createObjectURL(file);
    }
  }, []);

  const handleDrop = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(false);
    const file = e.dataTransfer.files[0];
    if (file) handleFile(file, activeTab);
  }, [activeTab, handleFile]);

  const handleInput = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) handleFile(file, activeTab);
  };

  const handleEdit = () => { setEditText(uuids.join("\n")); setIsEditing(true); };

  const handleSave = () => {
    const lines = editText.split("\n").map(l => l.trim()).filter(Boolean);
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
    <div className="min-h-screen p-6 md:p-12">
      <div className="mx-auto max-w-2xl">
        <div className="mb-6 flex items-center justify-between">
          <h1 className="text-2xl font-semibold">Map Hash Extractor</h1>
          <button
            onClick={toggle}
            className="rounded p-2 hover:bg-muted transition-colors"
            aria-label="Toggle theme"
          >
            {resolved === "dark" ? "☀️" : "🌙"}
          </button>
        </div>

        <div className="mb-4 flex gap-2">
          {(["binary", "image"] as const).map(tab => (
            <button
              key={tab}
              onClick={() => setActiveTab(tab)}
              className={`px-4 py-2 rounded border transition-colors ${
                activeTab === tab ? "bg-accent text-white border-accent" : "border-border hover:bg-muted"
              }`}
            >
              {tab === "binary" ? "Binary File" : "PNG Image"}
            </button>
          ))}
        </div>

        <div
          className={`border-2 border-dashed rounded-lg p-8 text-center transition-colors ${
            isDragging ? "border-accent bg-accent/10" : "border-border hover:border-muted-foreground"
          }`}
          onDragOver={(e) => { e.preventDefault(); setIsDragging(true); }}
          onDragLeave={() => setIsDragging(false)}
          onDrop={handleDrop}
        >
          <p className="mb-2 text-muted-foreground">
            {activeTab === "binary" ? "Drop binary file here" : "Drop PNG image here"}
          </p>
          {activeTab === "image" && (
            <p className="mb-4 text-xs text-muted-foreground">Dimensions must be 128×128 multiples. Lossy formats unsupported.</p>
          )}
          <label className="inline-block cursor-pointer px-4 py-2 rounded bg-muted hover:bg-border transition-colors">
            <input type="file" className="hidden" onChange={handleInput} accept={activeTab === "image" ? "image/png" : "*"} key={activeTab} />
            Select {activeTab === "binary" ? "File" : "PNG"}
          </label>
        </div>

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
                      saveDisabled ? "bg-muted text-muted-foreground cursor-not-allowed" : "bg-accent text-white hover:bg-accent/80"
                    }`}
                  >
                    Save
                  </button>
                ) : (
                  <button onClick={handleEdit} className="px-3 py-1 text-sm rounded hover:bg-muted transition-colors">Edit</button>
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
    </div>
  );
}
