export const environment = {
  production: true,
  /** Same-origin via nginx (`/api` → Spring). */
  apiUrl: '',
  /** Same-origin via nginx (`/auth` → CSS). */
  cssUrl: '',
  clientId: 'trading-portal',
  devToken: '' as string
};
