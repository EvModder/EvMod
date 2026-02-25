import { useEffect } from "react";
import { useLocation } from "react-router-dom";
import evmodIcon from "@/assets/EvMod.png";
import datToPngIcon from "@/assets/DAT_to_PNG.png";
import cacheToPngIcon from "@/assets/CACHE_to_PNG.png";
import mapHasherIcon from "@/assets/MAP-HASH.svg";

const ROUTE_FAVICONS: Record<string, string> = {
  "/DAT-to-PNG": datToPngIcon,
  "/CACHE-to-PNG": cacheToPngIcon,
  "/MapHasher": mapHasherIcon,
};

export function useFavicon() {
  const { pathname } = useLocation();

  useEffect(() => {
    const icon = ROUTE_FAVICONS[pathname] ?? evmodIcon;
    let link = document.querySelector<HTMLLinkElement>("link[rel='icon']");
    if (!link) {
      link = document.createElement("link");
      link.rel = "icon";
      document.head.appendChild(link);
    }
    link.type = "image/png";
    link.href = icon;
  }, [pathname]);
}
