import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { I18n, TPipe } from '../core/i18n';
import { NetworkService } from '../core/network.service';
import { Role } from '../core/models';
import { AvatarComponent } from '../shared/avatar.component';
import { IconComponent } from '../shared/icon.component';

interface NavItem { path: string; label: string; icon: string; roles: Role[]; mobile?: boolean; }

const ALL: Role[] = ['SENIOR', 'GENERAL_SENIOR', 'PLATOON_LEADER', 'COMPANY_AGENT'];
const NAV: NavItem[] = [
  { path: '/home', label: 'nav.home', icon: 'home', roles: ['SENIOR', 'PLATOON_LEADER'], mobile: true },
  { path: '/dashboard', label: 'nav.dashboard', icon: 'dashboard', roles: ['GENERAL_SENIOR', 'COMPANY_AGENT', 'PLATOON_LEADER'], mobile: true },
  { path: '/sessions', label: 'nav.sessions', icon: 'list', roles: ALL, mobile: true },
  { path: '/approvals', label: 'nav.approvals', icon: 'approve', roles: ['PLATOON_LEADER', 'COMPANY_AGENT'], mobile: true },
  { path: '/soldiers', label: 'nav.soldiers', icon: 'soldier', roles: ALL },
  { path: '/platoons', label: 'nav.platoons', icon: 'flag', roles: ['COMPANY_AGENT', 'GENERAL_SENIOR'] },
  { path: '/reports', label: 'nav.reports', icon: 'report', roles: ['GENERAL_SENIOR', 'PLATOON_LEADER', 'COMPANY_AGENT'], mobile: true },
  { path: '/users', label: 'nav.users', icon: 'users', roles: ['COMPANY_AGENT'] },
  { path: '/audit', label: 'nav.audit', icon: 'audit', roles: ['COMPANY_AGENT'] },
  { path: '/settings', label: 'nav.settings', icon: 'settings', roles: ALL, mobile: true },
];

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, TPipe, AvatarComponent, IconComponent],
  template: `
  <div class="shell" [class.nav-open]="menuOpen()">
    <aside class="sidebar" aria-label="القائمة">
      <div class="brand">
        <span class="brand-mark"><img src="icons/emblem.webp" alt="" width="40" height="40"></span>
        <div><strong>{{ 'appName' | t }}</strong><small>{{ user()?.platoonName || ('role.' + user()?.role | t) }}</small></div>
      </div>
      <nav>
        @for (n of items(); track n.path) {
          <a [routerLink]="n.path" routerLinkActive="active" (click)="menuOpen.set(false)">
            <app-icon [name]="n.icon"></app-icon><span>{{ n.label | t }}</span>
          </a>
        }
      </nav>
      <div class="sidebar-foot">
        <div class="who-row">
          <app-avatar kind="users" [id]="user()?.id" [version]="user()?.photoVersion" [name]="user()?.fullName ?? ''" [size]="40"></app-avatar>
          <div class="who"><strong>{{ user()?.fullName }}</strong><small>{{ 'role.' + user()?.role | t }}</small></div>
        </div>
        <button class="btn btn-ghost btn-sm" (click)="i18n.toggle()"><app-icon name="globe" [size]="16"></app-icon>{{ 'language' | t }}</button>
        <button class="btn btn-ghost btn-sm" (click)="auth.logout()"><app-icon name="logout" [size]="16"></app-icon>{{ 'nav.logout' | t }}</button>
      </div>
    </aside>
    <div class="scrim" (click)="menuOpen.set(false)"></div>

    <div class="main">
      <header class="topbar">
        <button class="icon-btn only-mobile" (click)="menuOpen.set(!menuOpen())" aria-label="القائمة"><app-icon name="menu"></app-icon></button>
        <span class="topbar-title">{{ 'appFull' | t }}</span>
        <span class="spacer"></span>
        @if (net.queued() > 0) { <span class="badge s-LATE">{{ net.queued() }} {{ 'queued' | t }}</span> }
        <span class="who-chip">{{ user()?.fullName }}</span>
      </header>
      @if (!net.online()) {
        <div class="offline-banner" role="status"><app-icon name="wifiOff" [size]="18"></app-icon>{{ 'offline' | t }}</div>
      }
      <main class="content"><router-outlet></router-outlet></main>
    </div>

    <nav class="bottom-nav" aria-label="التنقل">
      @for (n of mobileItems(); track n.path) {
        <a [routerLink]="n.path" routerLinkActive="active"><app-icon [name]="n.icon" [size]="22"></app-icon><span>{{ n.label | t }}</span></a>
      }
    </nav>
  </div>`,
})
export class ShellComponent {
  auth = inject(AuthService);
  i18n = inject(I18n);
  net = inject(NetworkService);
  user = this.auth.user;
  menuOpen = signal(false);
  items = computed(() => NAV.filter(n => this.auth.hasRole(...n.roles)));
  mobileItems = computed(() => this.items().filter(n => n.mobile).slice(0, 5));
}
