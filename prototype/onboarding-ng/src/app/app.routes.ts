import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: 'onboarding',
    loadComponent: () => import('./app/onboarding-page').then((m) => m.OnboardingPage),
  },
  {
    path: '',
    loadComponent: () => import('./home/app-shell').then((m) => m.AppShell),
    children: [
      { path: 'today', loadComponent: () => import('./app/today-page').then((m) => m.TodayPage) },
      { path: 'syllabus', loadComponent: () => import('./home/syllabus-tab').then((m) => m.SyllabusTab) },
      { path: 'focus', loadComponent: () => import('./focus/focus-screen').then((m) => m.FocusScreen) },
      { path: 'progress', loadComponent: () => import('./home/progress-tab').then((m) => m.ProgressTab) },
      { path: 'settings', loadComponent: () => import('./home/settings-screen').then((m) => m.SettingsScreen) },
      { path: 'organise', loadComponent: () => import('./app/organise-page').then((m) => m.OrganisePage) },
      { path: '', pathMatch: 'full', redirectTo: 'today' },
    ],
  },
  { path: '**', redirectTo: 'today' },
];
