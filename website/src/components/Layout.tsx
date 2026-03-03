import { NavLink, Outlet } from "react-router-dom";
import { useTheme } from "@/hooks/useTheme";
import { useFavicon } from "@/hooks/useFavicon";
import { TOOL_PAGES, withTrailingSlash } from "@/lib/toolPages";
import { MoonIcon, SunIcon } from "@/components/ThemeIcons";

export default function Layout() {
  const { resolved, toggle } = useTheme();
  const siteIcon = `${import.meta.env.BASE_URL}favicon.png`;
  useFavicon();
  return (
    <div className="min-h-screen">
      <nav className="border-b border-border bg-background/80 backdrop-blur sticky top-0 z-10">
        <div className="mx-auto max-w-6xl flex items-center justify-between px-4 h-12">
          <div className="flex items-center gap-4 overflow-x-auto">
            <a href={import.meta.env.BASE_URL} className="shrink-0 hover:opacity-80 transition-opacity" title="EvMod Tools">
              <img src={siteIcon} alt="EvMod Tools" className="w-7 h-7" />
            </a>
            <div className="flex items-center">
              {TOOL_PAGES.map(({ id, path, navLabel }, idx) => (
                <div key={path} className="flex items-center">
                  {idx > 0 && (
                    <span
                      aria-hidden="true"
                      className="mx-[1.5px] inline-block h-4 w-px rounded-full bg-muted-foreground/35"
                    />
                  )}
                  <NavLink
                    to={withTrailingSlash(path)}
                    className={({ isActive }) =>
                      `px-3 py-1.5 rounded text-sm transition-colors whitespace-nowrap ${id === "mapHasher" ? "font-semibold" : ""} ${
                        isActive
                          ? "bg-accent text-accent-foreground"
                          : "text-muted-foreground hover:text-foreground hover:bg-muted"
                      }`
                    }
                  >
                    {navLabel}
                  </NavLink>
                </div>
              ))}
            </div>
          </div>
          <button
            onClick={toggle}
            className="rounded p-2 hover:bg-muted transition-colors shrink-0"
            aria-label="Toggle theme"
          >
            {resolved === "dark" ? <SunIcon className="h-4 w-4" /> : <MoonIcon className="h-4 w-4" />}
          </button>
        </div>
      </nav>
      <main className="mx-auto max-w-6xl p-6">
        <Outlet />
      </main>
    </div>
  );
}
