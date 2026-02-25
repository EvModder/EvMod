import { useState, useEffect } from "react";

type Theme = "light" | "dark" | "system";

const getSystemTheme = () =>
  matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";

export function useTheme() {
  const [theme, setTheme] = useState<Theme>(
    () => (localStorage.getItem("theme") as Theme) ?? "system"
  );

  const resolved = theme === "system" ? getSystemTheme() : theme;

  useEffect(() => {
    const root = document.documentElement;
    root.classList.toggle("dark", resolved === "dark");

    if (theme === "system") localStorage.removeItem("theme");
    else localStorage.setItem("theme", theme);

    if (theme !== "system") return;
    const mq = matchMedia("(prefers-color-scheme: dark)");
    const onChange = () => setTheme((t) => (t === "system" ? "system" : t));
    mq.addEventListener("change", onChange);
    return () => mq.removeEventListener("change", onChange);
  }, [theme, resolved]);

  const toggle = () =>
    setTheme(resolved === "dark" ? "light" : "dark");

  return { theme, resolved, toggle } as const;
}
