import { defineConfig, devices } from '@playwright/test';

/**
 * Device Lab targets (machine SoT):
 *  - Realme P2 Pro primary phone: 360x780
 *  - Desktop: 1280x800
 *  - Tablet: 800x1280
 *
 * Specs may soft-skip auth when CSS (:9000) is unreachable — see e2e specs.
 */
const PORT = process.env['TP_UI_PORT'] || '3341';
const BASE_URL = process.env['TP_BASE_URL'] || `http://127.0.0.1:${PORT}`;
const isPublicHost = /^https:\/\//i.test(BASE_URL);

export default defineConfig({
  testDir: './e2e',
  timeout: isPublicHost ? 45_000 : 30_000,
  expect: { timeout: 7_000 },
  fullyParallel: false,
  forbidOnly: !!process.env['CI'],
  retries: 0,
  workers: 1,
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }]],
  use: {
    baseURL: BASE_URL,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure'
  },
  projects: [
    {
      name: 'phone-realme-360x780',
      use: { ...devices['Pixel 5'], viewport: { width: 360, height: 780 } }
    },
    {
      name: 'desktop-1280x800',
      use: { ...devices['Desktop Chrome'], viewport: { width: 1280, height: 800 } }
    },
    {
      name: 'tablet-800x1280',
      use: { ...devices['Desktop Chrome'], viewport: { width: 800, height: 1280 } }
    }
  ],
  // Local loopback only — public DEV host (CONSCIOUS #18) already serves nginx static.
  ...(isPublicHost
    ? {}
    : {
        webServer: {
          command: 'npm run start',
          url: BASE_URL,
          reuseExistingServer: true,
          timeout: 120_000
        }
      })
});
