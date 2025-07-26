import type { Config } from 'tailwindcss'

const config: Config = {
  content: [
    "./src/**/*.{ts,tsx,js,jsx}", //src 포함
  ],
  theme: {
    extend: {},
  },
  plugins: [],
}

export default config;
