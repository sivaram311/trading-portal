/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ['./src/**/*.{html,ts}'],
  theme: {
    extend: {
      fontFamily: {
        // Expressive, deliberately NOT Inter/Roboto/Arial/system stacks.
        display: ['"Fraunces"', 'Georgia', 'serif'],
        body: ['"Archivo"', 'system-ui', 'sans-serif'],
        mono: ['"JetBrains Mono"', 'ui-monospace', 'monospace']
      },
      colors: {
        // Atmospheric obsidian + gold (XAUUSD), not purple-on-white / cream+terracotta.
        obsidian: {
          950: '#07080a',
          900: '#0a0c10',
          800: '#111419',
          700: '#1a1f27',
          600: '#252c37'
        },
        gold: {
          300: '#f6d98a',
          400: '#eec25a',
          500: '#d9a441',
          600: '#b8862f'
        },
        bull: '#37c99e',
        bear: '#f2617a',
        flatline: '#8b93a7'
      },
      boxShadow: {
        glow: '0 0 40px -8px rgba(217,164,65,0.35)',
        'glow-bull': '0 0 34px -8px rgba(55,201,158,0.45)',
        'glow-bear': '0 0 34px -8px rgba(242,97,122,0.45)'
      },
      keyframes: {
        pulseline: {
          '0%,100%': { opacity: '0.35' },
          '50%': { opacity: '1' }
        }
      },
      animation: {
        pulseline: 'pulseline 2.6s ease-in-out infinite'
      }
    }
  },
  plugins: []
};
