/**
 * Tool/page metadata for routing, nav labels, titles, and favicons.
 * Exports: `HOME_PAGE`, `TOOL_PAGES`, `withTrailingSlash`, `getPageMeta`.
 */
import datToPngIcon from "@/assets/DAT-to-PNG.png";
import nbtFormatIcon from "@/assets/NBT-to-PNG.png";
import cacheToPngIcon from "@/assets/CACHE-to-PNG.png";
import mapHasherIcon from "@/assets/MAP-to-HASH.svg";

export type ToolPageId = "pngToDat" | "datToPng" | "nbtToPng" | "cacheToPng" | "mapHasher";

type IconType = "image/png" | "image/svg+xml";

export interface PageHeadMeta {
  tabTitle: string;
  favicon: { href: string; type: IconType };
}

export interface PageMeta extends PageHeadMeta {
  path: string;
}

export interface ToolPageMeta extends PageMeta {
  id: ToolPageId;
  navLabel: string;
  cardDesc: string;
}

const siteIcon = `${import.meta.env.BASE_URL}favicon.png`;

export const HOME_PAGE: PageMeta = {
  path: "/",
  tabTitle: "EvMod Tools",
  favicon: { href: siteIcon, type: "image/png" },
};

export const TOOL_PAGES: ToolPageMeta[] = [
  {
    id: "pngToDat",
    path: "/PNG-to-DAT",
    tabTitle: "PNG → DAT",
    navLabel: "PNG → map_#.dat",
    cardDesc: "Convert images into map_#.dat files. Splits composite images into 128x128 tiles and assigns incrementing map indices.",
    favicon: { href: datToPngIcon, type: "image/png" },
  },
  {
    id: "datToPng",
    path: "/DAT-to-PNG",
    tabTitle: "DAT → PNG",
    navLabel: ".dat → PNG",
    cardDesc: "Convert Minecraft map_#.dat files to PNG images. Supports bulk upload with gallery preview and ZIP download.",
    favicon: { href: datToPngIcon, type: "image/png" },
  },
  {
    id: "nbtToPng",
    path: "/NBT-to-PNG",
    tabTitle: "NBT → PNG",
    navLabel: ".nbt → PNG",
    cardDesc: "Convert 128x128 .nbt, .litematic, or .schem files to PNG images",
    favicon: { href: nbtFormatIcon, type: "image/png" },
  },
  {
    id: "cacheToPng",
    path: "/CACHE-to-PNG",
    tabTitle: "CACHE → PNG",
    navLabel: ".cache → PNG",
    cardDesc: "Convert EvMod's .cache files to PNG images. Parses Java-serialized HashMaps to extract map color data.",
    favicon: { href: cacheToPngIcon, type: "image/png" },
  },
  {
    id: "mapHasher",
    path: "/MapHasher",
    tabTitle: "Map → Hash",
    navLabel: "Map → Hash",
    cardDesc: "Find map hash codes for .png images, .dat file, .cache files (from ./evmod/mapart_caches/), or .group files (from ./evmod/mapart_groups/)",
    favicon: { href: mapHasherIcon, type: "image/svg+xml" },
  },
];

export const withTrailingSlash = (path: string) =>
  path === "/" ? "/" : `${path.replace(/\/+$/, "")}/`;

const PAGE_MAP = new Map<string, PageHeadMeta>([
  [HOME_PAGE.path, HOME_PAGE],
  ...TOOL_PAGES.map(({ path, tabTitle, favicon }) => [path, { tabTitle, favicon }] as [string, PageHeadMeta]),
]);

export const getPageMeta = (pathname: string): PageHeadMeta => PAGE_MAP.get(pathname) ?? HOME_PAGE;
