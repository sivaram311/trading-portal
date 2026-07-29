import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./pages/login/login.component').then((m) => m.LoginComponent),
    title: 'Sign in · Trading Portal'
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./pages/confluence/confluence.component').then((m) => m.ConfluenceComponent),
    title: 'Live Confluence · Trading Portal'
  },
  {
    path: 'journal',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/journal/journal.component').then((m) => m.JournalComponent),
    title: 'Journal · Trading Portal'
  },
  {
    path: 'analytics',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/analytics/analytics.component').then((m) => m.AnalyticsComponent),
    title: 'Analytics · Trading Portal'
  },
  { path: '**', redirectTo: '' }
];
