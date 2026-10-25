import { useEffect, useSyncExternalStore } from "react";

// 큰 글씨 모드 훅 (localStorage hfm_large_text 하나를 모든 컴포넌트가 함께 씀, html에 large-text 클래스)
const KEY = "hfm_large_text";
const listeners = new Set();

const readLargeText = () => localStorage.getItem(KEY) === "true";

const subscribe = (listener) => {
  listeners.add(listener);
  return () => listeners.delete(listener);
};

export function useLargeText() {
  const largeText = useSyncExternalStore(subscribe, readLargeText);

  useEffect(() => {
    document.documentElement.classList.toggle("large-text", largeText);
  }, [largeText]);

  const toggleLargeText = () => {
    localStorage.setItem(KEY, String(!readLargeText()));
    listeners.forEach((listener) => listener());
  };

  return { largeText, toggleLargeText };
}
