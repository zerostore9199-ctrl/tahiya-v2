import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { I18n, TPipe } from '../core/i18n';
import { errorMessage } from '../core/errors';
import { IconComponent } from '../shared/icon.component';

@Component({
  standalone: true,
  imports: [FormsModule, TPipe, IconComponent],
  template: `
  <div class="auth-page">
    <section class="auth-hero">
      <div class="auth-logo"><img src="icons/logo.webp" [alt]="'appName' | t" width="356" height="480"></div>
      <h1 class="sr-only">{{ 'appName' | t }} — {{ 'appFull' | t }}</h1>
    </section>
    <form class="card auth-card" (ngSubmit)="submit()" #f="ngForm" novalidate>
      <div class="auth-card-head">
        <h2>{{ 'login.title' | t }}</h2>
        <button type="button" class="btn btn-ghost btn-sm" (click)="i18n.toggle()"><app-icon name="globe" [size]="16"></app-icon>{{ 'language' | t }}</button>
      </div>
      <label class="field">
        <span>{{ 'login.username' | t }}</span>
        <input name="username" [(ngModel)]="username" required autocomplete="username" autocapitalize="none" dir="ltr" maxlength="50">
      </label>
      <label class="field">
        <span>{{ 'login.password' | t }}</span>
        <input name="password" type="password" [(ngModel)]="password" required autocomplete="current-password" dir="ltr" maxlength="128">
      </label>
      @if (error()) { <div class="alert alert-error" role="alert">{{ error() }}</div> }
      <button class="btn btn-primary btn-lg btn-block" [disabled]="busy() || !username || !password">
        <app-icon name="lock" [size]="18"></app-icon>{{ busy() ? ('loading' | t) : ('login.submit' | t) }}
      </button>
    </form>
  </div>`,
})
export class LoginPage {
  private auth = inject(AuthService);
  private router = inject(Router);
  i18n = inject(I18n);
  username = '';
  password = '';
  busy = signal(false);
  error = signal<string | null>(null);

  submit() {
    if (!this.username || !this.password) return;
    this.busy.set(true);
    this.error.set(null);
    this.auth.login(this.username.trim(), this.password).subscribe({
      next: u => {
        this.password = '';
        this.router.navigateByUrl(u.mustChangePassword ? '/change-password' : '/');
      },
      error: e => { this.busy.set(false); this.error.set(errorMessage(e)); },
    });
  }
}
