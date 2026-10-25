import { useEffect, useSyncExternalStore } from "react";

// 다크/라이트 테마 훅 (localStorage hfm_theme 하나를 모든 컴포넌트가 함께 씀, html에 dark 클래스)
const KEY = "hfm_theme";
const listeners = new Set();

const readTheme = () => localStorage.getItem(KEY) || "light";

const subscribe = (listener) => {
  listeners.add(listener);
  return () => listeners.delete(listener);
};

const applyTheme = (theme) =>
  document.documentElement.classList.toggle("dark", theme === "dark");

export function useTheme() {
  const theme = useSyncExternalStore(subscribe, readTheme);

  useEffect(() => {
    applyTheme(theme);
  }, [theme]);

  const toggleTheme = () => {
    localStorage.setItem(KEY, readTheme() === "dark" ? "light" : "dark");
    listeners.forEach((listener) => listener());
  };

  return { theme, toggleTheme };
}
