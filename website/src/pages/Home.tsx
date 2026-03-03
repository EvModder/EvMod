import { Link } from "react-router-dom";
import { TOOL_PAGES, withTrailingSlash } from "@/lib/toolPages";

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
        <h1 className="text-3xl font-bold mb-2 text-[#4C8131]">EvMod Tools</h1>
        <p className="text-muted-foreground">
          Minecraft map data utilities (convert, preview, hash).
        </p>
      </div>
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {TOOL_PAGES.map(({ path, navLabel, cardDesc }) => (
          <Link
            key={path}
            to={withTrailingSlash(path)}
            className="group block rounded-lg border border-border p-5 hover:border-accent hover:bg-accent/5 transition-colors"
          >
            <h2 className="text-lg font-semibold mb-2 group-hover:text-accent transition-colors">
              {navLabel}
            </h2>
            <p className="text-sm text-muted-foreground leading-relaxed">{cardDesc}</p>
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
