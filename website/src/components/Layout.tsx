import { NavLink, Outlet } from "react-router-dom";
import { useTheme } from "@/hooks/useTheme";
import { useFavicon } from "@/hooks/useFavicon";
import evmodIcon from "@/assets/EvMod.png";

const NAV_ITEMS = [
  { to: "/DAT-to-PNG", label: "map_#.dat → PNG" },
  { to: "/CACHE-to-PNG", label: "inv.cache → PNG" },
  { to: "/MapHasher", label: "Map → Hash" },
];

export default function Layout() {
  const { resolved, toggle } = useTheme();
  useFavicon();
  return (
    <div className="min-h-screen">
      <nav className="border-b border-border bg-background/80 backdrop-blur sticky top-0 z-10">
        <div className="mx-auto max-w-6xl flex items-center justify-between px-4 h-12">
          <div className="flex items-center gap-4 overflow-x-auto">
            <NavLink to="/" className="shrink-0 hover:opacity-80 transition-opacity" title="EvMod Tools">
              <img src={evmodIcon} alt="EvMod Tools" className="w-7 h-7" />
            </NavLink>
            <div className="flex gap-1">
              {NAV_ITEMS.map(({ to, label }) => (
                <NavLink
                  key={to}
                  to={to}
                  className={({ isActive }) =>
                    `px-3 py-1.5 rounded text-sm transition-colors whitespace-nowrap ${
                      isActive
                        ? "bg-accent text-accent-foreground"
                        : "text-muted-foreground hover:text-foreground hover:bg-muted"
                    }`
                  }
                >
                  {label}
                </NavLink>
              ))}
            </div>
          </div>
          <button
            onClick={toggle}
            className="rounded p-2 hover:bg-muted transition-colors shrink-0"
            aria-label="Toggle theme"
          >
            {resolved === "dark" ? "☀️" : "🌙"}
          </button>
        </div>
      </nav>
      <main className="mx-auto max-w-6xl p-6">
        <Outlet />
      </main>
    </div>
  );
}
