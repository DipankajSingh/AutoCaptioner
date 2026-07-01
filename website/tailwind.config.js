/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        deepSpace: '#0b0f19',
        surface: '#131a2a',
        surfaceVariant: '#1e293b',
        glassSurface: 'rgba(255, 255, 255, 0.03)',
        glassBorder: 'rgba(255, 255, 255, 0.08)',
        accentBlue: '#38bdf8',
        accentViolet: '#818cf8',
        accentCyan: '#22d3ee',
        textPrimary: '#F8FAFC',
        textSecondary: '#94A3B8',
        warningYellow: '#FFE600',
        warningYellowBg: 'rgba(255, 230, 0, 0.1)',
      },
      fontFamily: {
        sans: ['Inter', 'sans-serif'],
        heading: ['Outfit', 'Inter', 'sans-serif'],
        mono: ['Courier New', 'Courier', 'monospace'],
      },
      animation: {
        blink: 'blink 1s infinite alternate',
        float: 'float 6s ease-in-out infinite',
        sweep: 'sweep 8s infinite',
      },
      keyframes: {
        blink: {
          'from': { opacity: '1', textShadow: '0 0 10px #FFE600' },
          'to': { opacity: '0.4', textShadow: 'none' },
        },
        float: {
          '0%, 100%': { transform: 'translateY(0)' },
          '50%': { transform: 'translateY(-15px)' },
        },
        sweep: {
          '0%': { left: '-100%' },
          '20%': { left: '200%' },
          '100%': { left: '200%' },
        }
      }
    },
  },
  plugins: [],
}
