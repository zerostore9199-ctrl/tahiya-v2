import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { I18n, TPipe } from '../core/i18n';
import { errorMessage } from '../core/errors';
import { ToastService } from '../core/toast.service';

@Component({
  standalone: true,
  imports: [FormsModule, TPipe],
  template: `
  <div class="auth-page single">
    <form class="card auth-card" (ngSubmit)="submit()" novalidate>
      <h2>{{ 'pwd.title' | t }}</h2>
      @if (auth.user()?.mustChangePassword) { <div class="alert alert-info">{{ 'pwd.required' | t }}</div> }
      <label class="field"><span>{{ 'pwd.current' | t }}</span>
        <input type="password" name="cur" [(ngModel)]="current" required autocomplete="current-password" dir="ltr"></label>
      <label class="field"><span>{{ 'pwd.new' | t }}</span>
        <input type="password" name="nw" [(ngModel)]="next" required minlength="10" autocomplete="new-password" dir="ltr">
        <small class="hint">{{ 'pwd.rules' | t }}</small></label>
      <label class="field"><span>{{ 'pwd.confirm' | t }}</span>
        <input type="password" name="cf" [(ngModel)]="confirm" required autocomplete="new-password" dir="ltr"></label>
      @if (error()) { <div class="alert alert-error" role="alert">{{ error() }}</div> }
      <button class="btn btn-primary btn-lg btn-block" [disabled]="busy()">{{ 'save' | t }}</button>
      @if (!auth.user()?.mustChangePassword) {
        <button type="button" class="btn btn-ghost btn-block" (click)="router.navigateByUrl('/settings')">{{ 'cancel' | t }}</button>
      } @else {
        <button type="button" class="btn btn-ghost btn-block" (click)="auth.logout()">{{ 'nav.logout' | t }}</button>
      }
    </form>
  </div>`,
})
export class ChangePasswordPage {
  auth = inject(AuthService);
  router = inject(Router);
  private i18n = inject(I18n);
  private toast = inject(ToastService);
  current = ''; next = ''; confirm = '';
  busy = signal(false);
  error = signal<string | null>(null);

  constructor() {
    if (!this.auth.isLoggedIn()) this.router.navigateByUrl('/login');
  }

  submit() {
    this.error.set(null);
    if (this.next.length < 10 || !/[0-9]/.test(this.next) || !/\p{L}/u.test(this.next)) {
      this.error.set(this.i18n.t('pwd.rules')); return;
    }
    if (this.next !== this.confirm) { this.error.set(this.i18n.t('pwd.mismatch')); return; }
    this.busy.set(true);
    this.auth.changePassword(this.current, this.next).subscribe({
      next: () => { this.toast.success(this.i18n.t('pwd.done')); this.router.navigateByUrl('/'); },
      error: e => { this.busy.set(false); this.error.set(errorMessage(e)); },
    });
  }
}
