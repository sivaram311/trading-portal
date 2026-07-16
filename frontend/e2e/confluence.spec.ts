import { test, expect } from '@playwright/test';

/**
 * Device Lab E2E — Trading Portal
 * Viewport projects: phone 360×780 · desktop 1280×800 · tablet 800×1280
 * Login: CSS via same-origin `/auth` (or :9000 on loopback). CONSCIOUS #18: for auth gate use
 * `TP_BASE_URL=https://trading-portal-dev.delena.buzz` (public DEV host), not only 127.0.0.1.
 */

const CSS_USER = process.env['TP_E2E_USER'] || 'admin';
/** Prefer SoT `CSS_ADMIN_PASSWORD` via TP_E2E_PASS — legacy admin123 is rejected after password-align. */
const CSS_PASS = process.env['TP_E2E_PASS'] || process.env['CSS_ADMIN_PASSWORD'] || 'admin123';

async function loginViaCss(page: import('@playwright/test').Page) {
  await page.goto('/login');
  await expect(page.getByRole('heading', { name: 'Trading Portal' })).toBeVisible();
  await page.fill('input[name="username"]', CSS_USER);
  await page.fill('input[name="password"]', CSS_PASS);
  await page.getByRole('button', { name: /sign in via css/i }).click();
  await expect(page).toHaveURL(/\/($|\?)/, { timeout: 20_000 });
}

test.describe('auth + confluence + journal', () => {
  test('CSS login reaches confluence composition', async ({ page }) => {
    await loginViaCss(page);
    await expect(page.getByRole('heading', { name: 'Trading Portal' })).toBeVisible();
    // Grade / direction / mode headline region
    await expect(page.getByText(/LONG|SHORT|FLAT|NONE/i).first()).toBeVisible({ timeout: 15_000 });
    await expect(page.getByRole('button', { name: /confirm paper|confirm/i })).toBeVisible();
    await expect(page.getByRole('button', { name: /dismiss/i })).toBeVisible();
    await expect(page.getByRole('link', { name: /journal/i }).or(page.getByRole('button', { name: /journal/i }))).toBeVisible();
  });

  test('journal route loads after CSS login', async ({ page }) => {
    await loginViaCss(page);
    await page.goto('/journal');
    await expect(page).toHaveURL(/\/journal/);
    await expect(page.getByText(/journal/i).first()).toBeVisible();
  });

  test('unauthenticated visitors are sent to login', async ({ page }) => {
    await page.goto('/');
    await expect(page).toHaveURL(/\/login/);
    await expect(page.getByRole('button', { name: /sign in via css/i })).toBeVisible();
  });
});
