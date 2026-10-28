import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { Subject, debounceTime } from 'rxjs';
import { I18n, TPipe } from '../core/i18n';
import { DtPipe } from '../core/format';
import { Page, Platoon, ROLES, Role, UserView } from '../core/models';
import { errorMessage } from '../core/errors';
import { ToastService } from '../core/toast.service';
import { toJpeg } from '../core/image';
import { AuthService } from '../core/auth.service';
import { AvatarComponent } from '../shared/avatar.component';
import { IconComponent } from '../shared/icon.component';
import { EmptyStateComponent, PagerComponent, SpinnerComponent } from '../shared/ui';

interface UserForm { id?: number; username: string; fullName: string; militaryRank: string; role: Role; platoonId: number | null; enabled: boolean; temporaryPassword: string; }

@Component({
  standalone: true,
  imports: [FormsModule, TPipe, DtPipe, AvatarComponent, IconComponent, EmptyStateComponent, PagerComponent, SpinnerComponent],
  template: `
  <section class="page">
    <header class="page-head"><h1>{{ 'nav.users' | t }}</h1>
      <button class="btn btn-primary" (click)="edit(null)"><app-icon name="plus" [size]="18"></app-icon>{{ 'usr.new' | t }}</button></header>

    @if (form(); as m) {
      <form class="card form" (ngSubmit)="save()">
        <div class="form-grid">
          <label class="field"><span>{{ 'login.username' | t }} *</span>
            <input name="u" [(ngModel)]="m.username" required pattern="[a-zA-Z0-9._-]{3,50}" dir="ltr" [disabled]="!!m.id"></label>
          <label class="field"><span>{{ 'name' | t }} *</span><input name="n" [(ngModel)]="m.fullName" required maxlength="120"></label>
          <label class="field"><span>{{ 'rank' | t }}</span><input name="r" [(ngModel)]="m.militaryRank" maxlength="50"></label>
          <label class="field"><span>{{ 'usr.role' | t }} *</span>
            <select name="role" [(ngModel)]="m.role">@for (r of roles; track r) { <option [value]="r">{{ r | t:'role' }}</option> }</select></label>
          @if (m.role === 'SENIOR' || m.role === 'PLATOON_LEADER') {
            <label class="field"><span>{{ 'platoon' | t }} *</span>
              <select name="p" [(ngModel)]="m.platoonId" required>@for (p of platoons(); track p.id) { <option [ngValue]="p.id">{{ p.name }}</option> }</select></label>
          }
          @if (!m.id) {
            <label class="field"><span>{{ 'usr.tempPwd' | t }} *</span>
              <input name="pw" type="text" [(ngModel)]="m.temporaryPassword" required minlength="10" dir="ltr" autocomplete="off">
              <small class="hint">{{ 'pwd.rules' | t }}</small></label>
          }
        </div>
        @if (m.id) { <label class="check"><input type="checkbox" name="en" [(ngModel)]="m.enabled">{{ 'usr.enabled' | t }}</label>
          <div class="photo-row compact">
            <app-avatar kind="users" [id]="m.id" [version]="photoVersionOf(m.id)" [name]="m.fullName" [size]="72"></app-avatar>
            <div class="btn-row">
              <label class="btn btn-ghost btn-sm"><app-icon name="upload" [size]="16"></app-icon>{{ 'photo.choose' | t }}
                <input type="file" accept="image/jpeg,image/png,image/webp" hidden (change)="pickPhoto($event, m.id)"></label>
              @if (photoVersionOf(m.id)) { <button type="button" class="btn btn-danger-ghost btn-sm" (click)="removePhoto(m.id)">{{ 'photo.remove' | t }}</button> }
            </div>
          </div> }
        @if (error()) { <div class="alert alert-error">{{ error() }}</div> }
        <div class="form-actions"><button type="button" class="btn btn-ghost" (click)="form.set(null)">{{ 'cancel' | t }}</button>
          <button class="btn btn-primary">{{ 'save' | t }}</button></div>
      </form>
    }

    <div class="filters card">
      <label class="search grow"><app-icon name="search" [size]="18"></app-icon><input [(ngModel)]="q" (ngModelChange)="search$.next()" [placeholder]="'search' | t"></label>
      <label class="field"><span>{{ 'usr.role' | t }}</span>
        <select [(ngModel)]="roleFilter" (ngModelChange)="load(0)"><option [ngValue]="null">{{ 'all' | t }}</option>
          @for (r of roles; track r) { <option [ngValue]="r">{{ r | t:'role' }}</option> }</select></label>
    </div>

    @if (loading()) { <app-spinner [label]="'loading' | t"></app-spinner> }
    @else if (!page()?.items?.length) { <app-empty icon="users" [text]="'noData' | t"></app-empty> }
    @else {
      <div class="card table-wrap">
        <table class="table">
          <thead><tr><th>{{ 'login.username' | t }}</th><th>{{ 'name' | t }}</th><th>{{ 'usr.role' | t }}</th><th>{{ 'platoon' | t }}</th>
            <th>{{ 'usr.lastLogin' | t }}</th><th>{{ 'status' | t }}</th><th>{{ 'actions' | t }}</th></tr></thead>
          <tbody>
            @for (u of page()!.items; track u.id) {
              <tr>
                <td class="ltr-cell">{{ u.username }}</td>
                <td><span class="who-cell"><app-avatar kind="users" [id]="u.id" [version]="u.photoVersion" [name]="u.fullName" [size]="34"></app-avatar><span><strong>{{ u.fullName }}</strong> <small class="muted">{{ u.militaryRank }}</small></span></span></td>
                <td>{{ u.role | t:'role' }}</td><td>{{ u.platoonName || '—' }}</td><td>{{ u.lastLoginAt | dt }}</td>
                <td>@if (u.locked) { <span class="badge s-ABSENT">{{ 'usr.locked' | t }}</span> }
                    @else { <span class="badge" [class.s-PRESENT]="u.enabled" [class.s-NONE]="!u.enabled">{{ (u.enabled ? 'active' : 'inactive') | t }}</span> }</td>
                <td class="actions">
                  <button class="btn btn-ghost btn-sm" (click)="edit(u)">{{ 'edit' | t }}</button>
                  <button class="btn btn-ghost btn-sm" (click)="reset(u)">{{ 'usr.reset' | t }}</button>
                  @if (u.locked) { <button class="btn btn-ghost btn-sm" (click)="unlock(u)">{{ 'usr.unlock' | t }}</button> }
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
      <app-pager [page]="page()!.page" [totalPages]="page()!.totalPages" (go)="load($event)"></app-pager>
    }
  </section>`,
})
export class UsersPage implements OnInit {
  private http = inject(HttpClient);
  private toast = inject(ToastService);
  private i18n = inject(I18n);
  private auth = inject(AuthService);
  roles = ROLES;
  platoons = signal<Platoon[]>([]);
  page = signal<Page<UserView> | null>(null);
  loading = signal(true);
  error = signal<string | null>(null);
  form = signal<UserForm | null>(null);
  q = '';
  roleFilter: Role | null = null;
  search$ = new Subject<void>();

  constructor() { this.search$.pipe(debounceTime(300)).subscribe(() => this.load(0)); }

  ngOnInit() {
    this.http.get<Platoon[]>('/api/platoons').subscribe(p => this.platoons.set(p));
    this.load(0);
  }

  load(p: number) {
    const params: Record<string, string | number> = { page: p, size: 20 };
    if (this.q.trim()) params['q'] = this.q.trim();
    if (this.roleFilter) params['role'] = this.roleFilter;
    this.http.get<Page<UserView>>('/api/users', { params }).subscribe({
      next: r => { this.page.set(r); this.loading.set(false); },
      error: e => { this.toast.error(errorMessage(e)); this.loading.set(false); },
    });
  }

  edit(u: UserView | null) {
    this.error.set(null);
    this.form.set(u ? { id: u.id, username: u.username, fullName: u.fullName, militaryRank: u.militaryRank ?? '', role: u.role,
      platoonId: u.platoonId ?? null, enabled: u.enabled, temporaryPassword: '' }
      : { username: '', fullName: '', militaryRank: '', role: 'SENIOR', platoonId: this.platoons()[0]?.id ?? null, enabled: true,
        temporaryPassword: this.generate() });
  }

  save() {
    const m = this.form()!;
    const platoonId = m.role === 'SENIOR' || m.role === 'PLATOON_LEADER' ? m.platoonId : null;
    const req = m.id
      ? this.http.put(`/api/users/${m.id}`, { fullName: m.fullName, militaryRank: m.militaryRank, role: m.role, platoonId, enabled: m.enabled })
      : this.http.post('/api/users', { username: m.username, fullName: m.fullName, militaryRank: m.militaryRank, role: m.role, platoonId,
        temporaryPassword: m.temporaryPassword });
    req.subscribe({
      next: () => {
        if (!m.id) this.toast.info(`${this.i18n.t('usr.tempPwd')}: ${m.temporaryPassword}`, 20000);
        else this.toast.success(this.i18n.t('usr.saved'));
        this.form.set(null);
        this.load(0);
      },
      error: e => this.error.set(errorMessage(e)),
    });
  }

  photoVersionOf(id: number): number | undefined { return this.page()?.items.find(u => u.id === id)?.photoVersion; }

  private applyUser(u: UserView) {
    this.page.update(p => p && { ...p, items: p.items.map(x => x.id === u.id ? u : x) });
    if (u.id === this.auth.user()?.id) this.auth.updateUser(u);
  }

  async pickPhoto(ev: Event, id: number) {
    const input = ev.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    try {
      const fd = new FormData();
      fd.append('image', await toJpeg(file, 1024), 'photo.jpg');
      this.http.put<UserView>(`/api/users/${id}/photo`, fd).subscribe({
        next: u => { this.applyUser(u); this.toast.success(this.i18n.t('photo.saved')); },
        error: e => this.toast.error(errorMessage(e)),
      });
    } catch { this.toast.error(this.i18n.t('photo.unreadable')); }
  }

  removePhoto(id: number) {
    this.http.delete<UserView>(`/api/users/${id}/photo`).subscribe({
      next: u => { this.applyUser(u); this.toast.success(this.i18n.t('photo.removed')); },
      error: e => this.toast.error(errorMessage(e)),
    });
  }

  reset(u: UserView) {
    const pw = this.generate();
    this.http.post(`/api/users/${u.id}/reset-password`, { temporaryPassword: pw }).subscribe({
      next: () => { this.toast.info(`${u.username} — ${this.i18n.t('usr.tempPwd')}: ${pw}`, 20000); this.load(this.page()?.page ?? 0); },
      error: e => this.toast.error(errorMessage(e)),
    });
  }

  unlock(u: UserView) {
    this.http.post(`/api/users/${u.id}/unlock`, {}).subscribe({
      next: () => this.load(this.page()?.page ?? 0),
      error: e => this.toast.error(errorMessage(e)),
    });
  }

  /** Random temporary password (shown once to the administrator, must be changed at first login). */
  private generate(): string {
    const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789';
    const a = new Uint32Array(12);
    crypto.getRandomValues(a);
    let s = Array.from(a, v => chars[v % chars.length]).join('');
    if (!/[0-9]/.test(s)) s = s.slice(0, 11) + '7';
    if (!/[a-zA-Z]/.test(s)) s = 'K' + s.slice(1);
    return s;
  }
}
