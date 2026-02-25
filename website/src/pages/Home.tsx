import { Link } from "react-router-dom";

const TOOLS = [
  {
    to: "/DAT-to-PNG",
    title: "Map.dat → PNG",
    desc: "Convert Minecraft map_#.dat files to PNG images. Supports bulk upload with gallery preview and ZIP download.",
  },
  {
    to: "/CACHE-to-PNG",
    title: "inv.cache → PNG",
    desc: "Convert EvMod's .cache files to PNG images. Parses Java-serialized HashMaps to extract map color data.",
  },
  {
    to: "/MapHasher",
    title: "Map → Hash",
    desc: "Find map hash codes for .png images, .dat file, .cache files (from ./evmod/mapart_caches/), or .group files (from ./evmod/mapart_groups/)",
  },
];

const LINKS = [
  {
    href: "https://modrinth.com/mod/evmod",
    title: "EvMod on Modrinth",
    desc: "Downloads and usage guide for the EvMod plugin. Get the latest release and read installation instructions.",
  },
  {
    href: "https://github.com/EvModder/EvMod",
    title: "Source on GitHub",
    desc: "View the EvMod source code, track the latest features, or compile the plugin yourself.",
  },
  {
    href: "https://discord.gg/r7Tuerq",
    title: "2b2t MapArt Discord",
    desc: "Get support, discuss map art, and access a vast pool of community knowledge.",
  },
];

export default function Home() {
  return (
    <div>
      <div className="mb-8">
        <h1 className="text-3xl font-bold mb-2">EvMod Tools</h1>
        <p className="text-muted-foreground">
          Utilities for working with Minecraft map data — converting, previewing, and hashing.
        </p>
      </div>
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {TOOLS.map(({ to, title, desc }) => (
          <Link
            key={to}
            to={to}
            className="group block rounded-lg border border-border p-5 hover:border-accent hover:bg-accent/5 transition-colors"
          >
            <h2 className="text-lg font-semibold mb-2 group-hover:text-accent transition-colors">
              {title}
            </h2>
            <p className="text-sm text-muted-foreground leading-relaxed">{desc}</p>
          </Link>
        ))}
        {LINKS.map(({ href, title, desc }) => (
          <a
            key={href}
            href={href}
            target="_blank"
            rel="noopener noreferrer"
            className="group block rounded-lg border border-border p-5 hover:border-accent hover:bg-accent/5 transition-colors"
          >
            <h2 className="text-lg font-semibold mb-2 group-hover:text-accent transition-colors">
              {title} ↗
            </h2>
            <p className="text-sm text-muted-foreground leading-relaxed">{desc}</p>
          </a>
        ))}
      </div>
    </div>
  );
}
