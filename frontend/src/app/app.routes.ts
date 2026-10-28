import { Routes, Router } from '@angular/router';
import { inject } from '@angular/core';
import { authGuard, guestGuard, roleGuard } from './core/guards';
import { AuthService } from './core/auth.service';
import { ShellComponent } from './layout/shell.component';

/** Seniors and platoon leaders land on their platoon home; command roles on the live dashboard. */
const landing = () => {
  const auth = inject(AuthService);
  return inject(Router).parseUrl(auth.hasRole('SENIOR', 'PLATOON_LEADER') ? '/home' : '/dashboard');
};

export const routes: Routes = [
  { path: 'login', canActivate: [guestGuard], loadComponent: () => import('./pages/login.page').then(m => m.LoginPage) },
  { path: 'change-password', loadComponent: () => import('./pages/change-password.page').then(m => m.ChangePasswordPage) },
  {
    path: '', component: ShellComponent, canActivate: [authGuard], children: [
      { path: '', pathMatch: 'full', canActivate: [landing], children: [] },
      { path: 'home', canActivate: [roleGuard('SENIOR', 'PLATOON_LEADER')], loadComponent: () => import('./pages/home.page').then(m => m.HomePage) },
      { path: 'dashboard', canActivate: [roleGuard('GENERAL_SENIOR', 'COMPANY_AGENT', 'PLATOON_LEADER')], loadComponent: () => import('./pages/dashboard.page').then(m => m.DashboardPage) },
      { path: 'sessions', loadComponent: () => import('./pages/sessions.page').then(m => m.SessionsPage) },
      { path: 'sessions/new', canActivate: [roleGuard('SENIOR', 'PLATOON_LEADER', 'COMPANY_AGENT')], loadComponent: () => import('./pages/new-session.page').then(m => m.NewSessionPage) },
      { path: 'sessions/:id', loadComponent: () => import('./pages/session-detail.page').then(m => m.SessionDetailPage) },
      { path: 'approvals', canActivate: [roleGuard('PLATOON_LEADER', 'COMPANY_AGENT')], loadComponent: () => import('./pages/approvals.page').then(m => m.ApprovalsPage) },
      { path: 'soldiers', loadComponent: () => import('./pages/soldiers.page').then(m => m.SoldiersPage) },
      { path: 'soldiers/new', canActivate: [roleGuard('PLATOON_LEADER', 'COMPANY_AGENT')], loadComponent: () => import('./pages/soldier-form.page').then(m => m.SoldierFormPage) },
      { path: 'soldiers/:id', loadComponent: () => import('./pages/soldier-form.page').then(m => m.SoldierFormPage) },
      { path: 'platoons', canActivate: [roleGuard('COMPANY_AGENT', 'GENERAL_SENIOR')], loadComponent: () => import('./pages/platoons.page').then(m => m.PlatoonsPage) },
      { path: 'reports', canActivate: [roleGuard('GENERAL_SENIOR', 'PLATOON_LEADER', 'COMPANY_AGENT')], loadComponent: () => import('./pages/reports.page').then(m => m.ReportsPage) },
      { path: 'users', canActivate: [roleGuard('COMPANY_AGENT')], loadComponent: () => import('./pages/users.page').then(m => m.UsersPage) },
      { path: 'audit', canActivate: [roleGuard('COMPANY_AGENT')], loadComponent: () => import('./pages/audit.page').then(m => m.AuditPage) },
      { path: 'settings', loadComponent: () => import('./pages/settings.page').then(m => m.SettingsPage) },
      { path: '**', loadComponent: () => import('./pages/not-found.page').then(m => m.NotFoundPage) },
    ],
  },
];
