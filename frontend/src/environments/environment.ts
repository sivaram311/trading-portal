export const environment = {
  production: false,
  /** Spring Boot API base (DEV). Bearer JWT required on all but /api/health. */
  apiUrl: 'http://127.0.0.1:3340',
  /** Centralized Security System DEV IdP. Password login: POST /auth/login {username,password,clientId}. */
  cssUrl: 'http://127.0.0.1:9000',
  /** CSS clientId registered in MyAgent workflow/css/CLIENT-REGISTRY.md. */
  clientId: 'trading-portal',
  /**
   * Optional DEV-only demo token. If set, the app can bypass CSS login for demos
   * (e.g. when CSS :9000 is unreachable). Leave empty in normal DEV.
   */
  devToken: '' as string
};
