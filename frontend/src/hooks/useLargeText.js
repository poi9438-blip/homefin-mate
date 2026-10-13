import { useState, useEffect } from "react";

// 큰 글씨 모드 훅 (localStorage hfm_large_text, html에 large-text 클래스)
export function useLargeText() {
  const [largeText, setLargeText] = useState(() => {
    return localStorage.getItem("hfm_large_text") === "true";
  });

  useEffect(() => {
    localStorage.setItem("hfm_large_text", String(largeText));
    document.documentElement.classList.toggle("large-text", largeText);
  }, [largeText]);

  useEffect(() => {
    document.documentElement.classList.toggle("large-text", largeText);
  }, []);

  const toggleLargeText = () => setLargeText((prev) => !prev);

  return { largeText, toggleLargeText };
}
