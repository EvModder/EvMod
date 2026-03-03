import { useEffect } from "react";
import { useLocation } from "react-router-dom";
import { getPageMeta } from "@/lib/toolPages";

export function useFavicon() {
  const { pathname } = useLocation();

  useEffect(() => {
    const normalized = pathname !== "/" && pathname.endsWith("/") ? pathname.slice(0, -1) : pathname;
    const { favicon, tabTitle } = getPageMeta(normalized);
    document.title = tabTitle;
    let link = document.querySelector<HTMLLinkElement>("link[rel='icon']");
    if (!link) {
      link = document.createElement("link");
      link.rel = "icon";
      document.head.appendChild(link);
    }
    link.type = favicon.type;
    link.href = favicon.href;
  }, [pathname]);
}
