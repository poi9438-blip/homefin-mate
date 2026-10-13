import { useState, useEffect } from "react";

// 다크/라이트 테마 훅 (localStorage hfm_theme, html에 dark 클래스)
export function useTheme() {
  const [theme, setTheme] = useState(() => {
    return localStorage.getItem("hfm_theme") || "light";
  });

  useEffect(() => {
    localStorage.setItem("hfm_theme", theme);
    if (theme === "dark") {
      document.documentElement.classList.add("dark");
    } else {
      document.documentElement.classList.remove("dark");
    }
  }, [theme]);

  const toggleTheme = () =>
    setTheme((prev) => (prev === "dark" ? "light" : "dark"));

  return { theme, toggleTheme };
}
