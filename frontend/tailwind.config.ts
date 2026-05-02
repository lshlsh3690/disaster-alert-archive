import type { Config } from 'tailwindcss'

const config: Config = {
  content: [
    "./src/**/*.{ts,tsx,js,jsx}", //src 포함
  ],
  darkMode: "class", // OS가 아닌 클래스 기반 다크모드
  theme: {
    extend: {},
  },
  plugins: [],
}

export default config;
