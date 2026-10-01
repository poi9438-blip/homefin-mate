// Tailwind 설정: 스캔할 파일, 다크 모드 방식, 커스텀 색상
/** @type {import('tailwindcss').Config} */
export default {
  // 클래스를 찾을 파일
  content: ["./index.html", "./src/**/*.{js,jsx,ts,tsx}"],

  // 다크 모드 사용할 때 dark 클래스로 전환
  darkMode: "class",

  theme: {
    extend: {
      colors: {
        // 주 색상 (blue 계열)
        primary: {
          50: "#eff6ff",
          500: "#3b82f6",
          600: "#2563eb",
          700: "#1d4ed8",
        },
      },
    },
  },
  plugins: [],
};
