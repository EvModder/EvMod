import { useState, useCallback } from "react";
import { md5 } from "@/lib/md5";

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

export default function Index() {
  const [uuids, setUuids] = useState<string[]>([]);
  const [copied, setCopied] = useState(false);
  const [isDragging, setIsDragging] = useState(false);
  const [activeTab, setActiveTab] = useState<"binary" | "image">("binary");
  const [error, setError] = useState<string | null>(null);

  const bytesToHex = (bytes: Uint8Array): string =>
    Array.from(bytes).map((b) => b.toString(16).padStart(2, "0")).join("");

  const parseUUIDs = (buffer: ArrayBuffer): string[] => {
    const bytes = new Uint8Array(buffer);
    const result: string[] = [];
    for (let i = 0; i + 16 <= bytes.length; i += 16) {
      const msb = bytes.slice(i, i + 8);
      const lsb = bytes.slice(i + 8, i + 16);
      const hex = bytesToHex(msb) + bytesToHex(lsb);
      result.push(`${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20, 32)}`);
    }
    return result;
  };

  const nameUUIDFromBytes = (bytes: Uint8Array): string => {
    const hash = md5(bytes);
    hash[6] = (hash[6] & 0x0f) | 0x30;
    hash[8] = (hash[8] & 0x3f) | 0x80;
    const hex = bytesToHex(hash);
    return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20, 32)}`;
  };

  const setLockedBit = (uuid: string): string => {
    const parts = uuid.split("-");
    const firstByte = parseInt(parts[0].slice(0, 2), 16) | 0x01;
    return firstByte.toString(16).padStart(2, "0") + parts[0].slice(2) + "-" + parts.slice(1).join("-");
  };

  const parseImageUUIDs = (img: HTMLImageElement): string[] => {
    const canvas = document.createElement("canvas");
    canvas.width = img.width;
    canvas.height = img.height;
    const ctx = canvas.getContext("2d")!;
    ctx.drawImage(img, 0, 0);

    if (img.width % 128 !== 0 || img.height % 128 !== 0) {
      throw new Error(`Dimensions must be divisible by 128. Got ${img.width}x${img.height}`);
    }

    const results: string[] = [];
    const blocksX = img.width / 128;
    const blocksY = img.height / 128;

    for (let by = 0; by < blocksY; by++) {
      for (let bx = 0; bx < blocksX; bx++) {
        const imageData = ctx.getImageData(bx * 128, by * 128, 128, 128);
        const colors = new Uint8Array(128 * 128);

        for (let y = 0; y < 128; y++) {
          for (let x = 0; x < 128; x++) {
            const idx = (y * 128 + x) * 4;
            const a = imageData.data[idx + 3];
            const r = imageData.data[idx];
            const g = imageData.data[idx + 1];
            const b = imageData.data[idx + 2];
            const argb = ((a << 24) | (r << 16) | (g << 8) | b) | 0;
            const colorByte = MAP_COLORS_REVERSE.get(argb);
            if (colorByte === undefined) {
              throw new Error(`Unsupported color at (${bx * 128 + x}, ${by * 128 + y}): ARGB=${argb}`);
            }
            colors[x + y * 128] = colorByte;
          }
        }
        results.push(setLockedBit(nameUUIDFromBytes(colors)));
      }
    }
    return results;
  };

  const handleBinaryFile = useCallback((file: File) => {
    setError(null);
    const reader = new FileReader();
    reader.onload = (e) => {
      const buffer = e.target?.result as ArrayBuffer;
      const parsed = parseUUIDs(buffer);
      setUuids(parsed);
      if (parsed.length === 0) setError("No UUIDs found. File may be empty or invalid.");
    };
    reader.readAsArrayBuffer(file);
  }, []);

  const handleImageFile = useCallback((file: File) => {
    setError(null);
    const img = new window.Image();
    img.onload = () => {
      try {
        const parsed = parseImageUUIDs(img);
        setUuids(parsed);
        if (parsed.length === 0) setError("No UUIDs found.");
      } catch (err) {
        setError(err instanceof Error ? err.message : "Unknown error");
      }
    };
    img.src = URL.createObjectURL(file);
  }, []);

  const handleDrop = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(false);
    const file = e.dataTransfer.files[0];
    if (file) activeTab === "binary" ? handleBinaryFile(file) : handleImageFile(file);
  }, [activeTab, handleBinaryFile, handleImageFile]);

  const handleInput = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) activeTab === "binary" ? handleBinaryFile(file) : handleImageFile(file);
  };

  const copyToClipboard = async () => {
    await navigator.clipboard.writeText(uuids.join("\n"));
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="min-h-screen p-6 md:p-12">
      <div className="mx-auto max-w-2xl">
        <h1 className="mb-6 text-2xl font-semibold">Map ID Extractor</h1>

        <div className="mb-4 flex gap-2">
          <button
            onClick={() => setActiveTab("binary")}
            className={`px-4 py-2 rounded border transition-colors ${
              activeTab === "binary" ? "bg-accent text-white border-accent" : "border-border hover:bg-muted"
            }`}
          >
            Binary File
          </button>
          <button
            onClick={() => setActiveTab("image")}
            className={`px-4 py-2 rounded border transition-colors ${
              activeTab === "image" ? "bg-accent text-white border-accent" : "border-border hover:bg-muted"
            }`}
          >
            PNG Image
          </button>
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
            <input
              type="file"
              className="hidden"
              onChange={handleInput}
              accept={activeTab === "image" ? "image/png" : "*"}
              key={activeTab}
            />
            Select {activeTab === "binary" ? "File" : "PNG"}
          </label>
        </div>

        {error && <p className="mt-4 text-red-500 text-sm">{error}</p>}

        {uuids.length > 0 && (
          <div className="mt-6 border border-border rounded-lg p-4">
            <div className="mb-3 flex items-center justify-between">
              <span className="text-sm text-muted-foreground">{uuids.length} UUID{uuids.length !== 1 && "s"}</span>
              <button onClick={copyToClipboard} className="px-3 py-1 text-sm rounded hover:bg-muted transition-colors">
                {copied ? "✓ Copied" : "Copy All"}
              </button>
            </div>
            <div className="max-h-96 overflow-auto rounded bg-muted p-4">
              <pre className="font-mono text-sm whitespace-pre-wrap break-all">{uuids.join("\n")}</pre>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}