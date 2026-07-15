import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from './auth.service';
import { environment } from '../../environments/environment';

/** Attach Bearer JWT to API requests only (never to CSS login itself). */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const token = auth.token();
  const isApi = environment.apiUrl
    ? req.url.startsWith(environment.apiUrl)
    : req.url.startsWith('/api/') || req.url === '/api' || req.url.includes('/api/');
  if (token && isApi) {
    req = req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
  }
  return next(req);
};
