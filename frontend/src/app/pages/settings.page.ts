import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { I18n, TPipe } from '../core/i18n';
import { Settings } from '../core/models';
import { errorMessage } from '../core/errors';
import { ToastService } from '../core/toast.service';
import { toJpeg } from '../core/image';
import { UserView } from '../core/models';
import { AvatarComponent } from '../shared/avatar.component';
import { IconComponent } from '../shared/icon.component';

@Component({
  standalone: true,
  imports: [FormsModule, RouterLink, TPipe, AvatarComponent, IconComponent],
  template: `
  <section class="page narrow">
    <header class="page-head"><h1>{{ 'nav.settings' | t }}</h1></header>

    <div class="card">
      <h2 class="card-title"><app-icon name="soldier"></app-icon>{{ 'set.account' | t }}</h2>
      @if (auth.user(); as me) {
        <div class="photo-row compact">
          <app-avatar kind="users" [id]="me.id" [version]="me.photoVersion" [name]="me.fullName" [size]="84"></app-avatar>
          <div class="btn-row">
            <label class="btn btn-ghost btn-sm"><app-icon name="upload" [size]="16"></app-icon>{{ 'set.myPhoto' | t }}
              <input type="file" accept="image/jpeg,image/png,image/webp" hidden (change)="pickPhoto($event)"></label>
            @if (me.photoVersion) { <button type="button" class="btn btn-danger-ghost btn-sm" (click)="removePhoto()">{{ 'photo.remove' | t }}</button> }
          </div>
        </div>
      }
      <dl class="kv">
        <dt>{{ 'name' | t }}</dt><dd>{{ auth.user()?.fullName }}</dd>
        <dt>{{ 'login.username' | t }}</dt><dd class="ltr">{{ auth.user()?.username }}</dd>
        <dt>{{ 'usr.role' | t }}</dt><dd>{{ 'role.' + auth.user()?.role | t }}</dd>
        @if (auth.user()?.platoonName) { <dt>{{ 'platoon' | t }}</dt><dd>{{ auth.user()?.platoonName }}</dd> }
      </dl>
      <div class="btn-row">
        <a class="btn btn-ghost" routerLink="/change-password"><app-icon name="lock" [size]="18"></app-icon>{{ 'pwd.title' | t }}</a>
        <button class="btn btn-ghost" (click)="i18n.toggle()"><app-icon name="globe" [size]="18"></app-icon>{{ 'language' | t }}</button>
        <button class="btn btn-ghost only-mobile" (click)="auth.logout()"><app-icon name="logout" [size]="18"></app-icon>{{ 'nav.logout' | t }}</button>
      </div>
    </div>

    @if (s(); as m) {
      <form class="card form" (ngSubmit)="save()">
        <h2 class="card-title"><app-icon name="settings"></app-icon>{{ 'set.system' | t }}</h2>
        @if (!isAgent) { <p class="muted small">{{ 'set.readonly' | t }}</p> }
        <fieldset [disabled]="!isAgent">
          <label class="field"><span>{{ 'set.threshold' | t }}: <b class="ltr">{{ m.recognitionThreshold }}</b></span>
            <input type="range" name="th" min="0.2" max="0.9" step="0.01" [(ngModel)]="m.recognitionThreshold">
            <small class="hint">{{ 'set.thresholdHint' | t }}</small></label>
          <div class="form-grid">
            <label class="field"><span>{{ 'set.minDet' | t }}</span><input type="number" name="md" min="0.3" max="0.99" step="0.01" [(ngModel)]="m.recognitionMinDetScore" dir="ltr"></label>
            <label class="field"><span>{{ 'set.enrollMinDet' | t }}</span><input type="number" name="emd" min="0.3" max="0.99" step="0.01" [(ngModel)]="m.enrollmentMinDetScore" dir="ltr"></label>
            <label class="field"><span>{{ 'set.maxFaces' | t }}</span><input type="number" name="mf" min="1" max="10" [(ngModel)]="m.enrollmentMaxFaces" dir="ltr"></label>
            <label class="field"><span>{{ 'set.dup' | t }}</span><input type="number" name="dup" min="0.3" max="0.95" step="0.01" [(ngModel)]="m.enrollmentDuplicateThreshold" dir="ltr"></label>
            <label class="field"><span>{{ 'set.grace' | t }}</span><input type="number" name="gr" min="0" max="120" [(ngModel)]="m.lateGraceMinutes" dir="ltr"></label>
            <label class="field"><span>{{ 'set.retention' | t }}</span><input type="number" name="ret" min="365" max="3650" [(ngModel)]="m.auditRetentionDays" dir="ltr"></label>
          </div>
        </fieldset>
        @if (error()) { <div class="alert alert-error">{{ error() }}</div> }
        @if (isAgent) { <div class="form-actions"><button class="btn btn-primary">{{ 'save' | t }}</button></div> }
      </form>
    }
  </section>`,
})
export class SettingsPage implements OnInit {
  auth = inject(AuthService);
  i18n = inject(I18n);
  private http = inject(HttpClient);
  private toast = inject(ToastService);
  isAgent = this.auth.hasRole('COMPANY_AGENT');
  s = signal<Settings | null>(null);
  error = signal<string | null>(null);

  ngOnInit() { this.http.get<Settings>('/api/settings').subscribe({ next: s => this.s.set(s), error: e => this.error.set(errorMessage(e)) }); }

  async pickPhoto(ev: Event) {
    const input = ev.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    const me = this.auth.user();
    if (!file || !me) return;
    try {
      const fd = new FormData();
      fd.append('image', await toJpeg(file, 1024), 'photo.jpg');
      this.http.put<UserView>(`/api/users/${me.id}/photo`, fd).subscribe({
        next: u => { this.auth.updateUser(u); this.toast.success(this.i18n.t('photo.saved')); },
        error: e => this.toast.error(errorMessage(e)),
      });
    } catch { this.toast.error(this.i18n.t('photo.unreadable')); }
  }

  removePhoto() {
    const me = this.auth.user();
    if (!me) return;
    this.http.delete<UserView>(`/api/users/${me.id}/photo`).subscribe({
      next: u => { this.auth.updateUser(u); this.toast.success(this.i18n.t('photo.removed')); },
      error: e => this.toast.error(errorMessage(e)),
    });
  }

  save() {
    this.error.set(null);
    this.http.put<Settings>('/api/settings', this.s()).subscribe({
      next: s => { this.s.set(s); this.toast.success(this.i18n.t('set.saved')); },
      error: e => this.error.set(errorMessage(e)),
    });
  }
}
