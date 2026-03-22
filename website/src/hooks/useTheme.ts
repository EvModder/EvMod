import { useEffect, useState } from "react";

type StoredTheme = "light" | "dark";
type ResolvedTheme = StoredTheme;

const getSystemTheme = () =>
  matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";

const readStoredTheme = (): StoredTheme | null => {
  const theme = localStorage.getItem("theme");
  return theme === "light" || theme === "dark" ? theme : null;
};

export function useTheme() {
  const initialSystemTheme = getSystemTheme();
  const [systemTheme, setSystemTheme] = useState<ResolvedTheme>(initialSystemTheme);
  const [override, setOverride] = useState<StoredTheme | null>(() => {
    const stored = readStoredTheme();
    return stored === initialSystemTheme ? null : stored;
  });
  const resolved = override ?? systemTheme;

  useEffect(() => {
    const mq = matchMedia("(prefers-color-scheme: dark)");
    const onChange = (e: MediaQueryListEvent) => {
      const nextSystemTheme = e.matches ? "dark" : "light";
      setSystemTheme(nextSystemTheme);
      setOverride(current => current === nextSystemTheme ? null : current);
    };
    mq.addEventListener("change", onChange);
    return () => mq.removeEventListener("change", onChange);
  }, []);

  useEffect(() => {
    document.documentElement.classList.toggle("dark", resolved === "dark");
    if (override) localStorage.setItem("theme", override);
    else localStorage.removeItem("theme");
  }, [override, resolved]);

  const toggle = () => {
    const next = resolved === "dark" ? "light" : "dark";
    setOverride(next === systemTheme ? null : next);
  };

  return { resolved, toggle } as const;
}
