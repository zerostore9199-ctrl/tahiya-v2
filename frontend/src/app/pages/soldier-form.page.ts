import { Component, Input, OnInit, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { I18n, TPipe } from '../core/i18n';
import { DtPipe } from '../core/format';
import { EnrollResult, FaceTemplate, PhotoResult, Platoon, Soldier } from '../core/models';
import { toJpeg } from '../core/image';
import { errorMessage } from '../core/errors';
import { ToastService } from '../core/toast.service';
import { AvatarComponent } from '../shared/avatar.component';
import { CameraComponent } from '../shared/camera.component';
import { IconComponent } from '../shared/icon.component';
import { ConfirmService, SpinnerComponent } from '../shared/ui';

@Component({
  standalone: true,
  imports: [FormsModule, RouterLink, TPipe, DtPipe, AvatarComponent, CameraComponent, IconComponent, SpinnerComponent],
  template: `
  <section class="page narrow">
    <a class="back-link" routerLink="/soldiers">← {{ 'nav.soldiers' | t }}</a>
    <header class="page-head"><h1>{{ isNew ? ('sol.new' | t) : soldier()?.fullName }}</h1>
      @if (soldier(); as s) { <span class="badge" [class.s-PRESENT]="s.active" [class.s-NONE]="!s.active">{{ (s.active ? 'active' : 'inactive') | t }}</span> }
    </header>

    <form class="card form" (ngSubmit)="save()" #f="ngForm">
      <fieldset [disabled]="!canManage">
        <div class="form-grid">
          <label class="field"><span>{{ 'militaryNumber' | t }} *</span>
            <input name="mn" [(ngModel)]="model.militaryNumber" required pattern="[0-9A-Za-z-]{2,30}" dir="ltr" maxlength="30"></label>
          <label class="field"><span>{{ 'rank' | t }} *</span>
            <input name="rank" [(ngModel)]="model.militaryRank" required maxlength="50" list="ranks">
            <datalist id="ranks">@for (r of ranks; track r) { <option [value]="r"></option> }</datalist></label>
        </div>
        <label class="field"><span>{{ 'name' | t }} *</span><input name="name" [(ngModel)]="model.fullName" required maxlength="120"></label>
        <label class="field"><span>{{ 'platoon' | t }} *</span>
          <select name="platoon" [(ngModel)]="model.platoonId" required [disabled]="!isAgent && !isNew">
            @for (p of platoons(); track p.id) { <option [ngValue]="p.id">{{ p.name }}</option> }</select></label>
        <label class="check"><input type="checkbox" name="consent" [(ngModel)]="model.biometricConsent">{{ 'sol.consent' | t }}</label>
      </fieldset>
      @if (error()) { <div class="alert alert-error">{{ error() }}</div> }
      @if (canManage) {
        <div class="form-actions">
          @if (!isNew && soldier()?.active) { <button type="button" class="btn btn-danger-ghost" (click)="deactivate()">{{ 'sol.deactivate' | t }}</button> }
          @if (!isNew && soldier() && !soldier()!.active) { <button type="button" class="btn btn-ghost" (click)="reactivate()">{{ 'sol.reactivate' | t }}</button> }
          <span class="spacer"></span>
          <button class="btn btn-primary" [disabled]="busy() || f.invalid">{{ 'save' | t }}</button>
        </div>
      }
    </form>

    @if (!isNew && canManage && soldier(); as s) {
      <div class="card">
        <h2 class="card-title"><app-icon name="soldier"></app-icon>{{ 'photo.title' | t }}</h2>
        <div class="photo-row">
          <app-avatar kind="soldiers" [id]="s.id" [version]="s.photoVersion" [name]="s.fullName" [size]="120"></app-avatar>
          <div class="photo-side">
            <p class="muted small">{{ 'photo.hint' | t }}</p>
            @if (s.active) {
              @if (s.biometricConsent && faces().length < maxFaces()) {
                <label class="check"><input type="checkbox" name="enrollToo" [(ngModel)]="enrollWithPhoto">{{ 'photo.enrollToo' | t }}</label>
              } @else if (!s.biometricConsent) {
                <div class="alert alert-info">{{ 'sol.consent' | t }}</div>
              }
              @if (photoBusy()) { <app-spinner [label]="'cam.processing' | t"></app-spinner> }
              <div class="btn-row">
                <label class="btn btn-primary btn-sm" [class.disabled]="photoBusy()"><app-icon name="upload" [size]="16"></app-icon>{{ 'photo.choose' | t }}
                  <input type="file" accept="image/jpeg,image/png,image/webp" hidden (change)="pickPhoto($event)" [disabled]="photoBusy()"></label>
                <button type="button" class="btn btn-ghost btn-sm" (click)="photoCam.set(!photoCam())"><app-icon name="camera" [size]="16"></app-icon>{{ 'cam.start' | t }}</button>
                @if (s.photoVersion) { <button type="button" class="btn btn-danger-ghost btn-sm" (click)="removePhoto()">{{ 'photo.remove' | t }}</button> }
              </div>
              @if (photoCam()) {
                <app-camera facing="user" [maxSide]="1600" (captured)="uploadPhoto($event)" [disabled]="photoBusy()" [hint]="'photo.take' | t"></app-camera>
              }
            } @else if (s.photoVersion) {
              <button type="button" class="btn btn-danger-ghost btn-sm" (click)="removePhoto()">{{ 'photo.remove' | t }}</button>
            }
            @if (photoError()) { <div class="alert alert-error">{{ photoError() }}</div> }
          </div>
        </div>
      </div>
      <div class="card">
        <h2 class="card-title"><app-icon name="face"></app-icon>{{ 'sol.faces' | t }}
          <span class="count">{{ faces().length }} / {{ maxFaces() }}</span></h2>
        <p class="muted small">{{ 'sol.enrollHint' | t }}</p>
        <div class="dots">@for (i of slots(); track i) { <i [class.on]="i < faces().length"></i> }</div>
        @if (s.active && s.biometricConsent && faces().length < maxFaces()) {
          @if (enrolling()) { <app-spinner [label]="'cam.processing' | t"></app-spinner> }
          <button type="button" class="btn btn-ghost btn-sm" (click)="faceCam.set(!faceCam())"><app-icon name="camera" [size]="16"></app-icon>{{ 'sol.enroll' | t }}</button>
          @if (faceCam()) {
            <app-camera facing="user" [maxSide]="1600" (captured)="enroll($event)" [disabled]="enrolling()" [hint]="'sol.enroll' | t"></app-camera>
          }
        } @else if (!s.biometricConsent) {
          <div class="alert alert-info">{{ 'sol.consent' | t }}</div>
        }
        @if (enrollError()) { <div class="alert alert-error">{{ enrollError() }}</div> }
        @if (faces().length) {
          <ul class="mini-list">
            @for (fc of faces(); track fc.id) { <li><app-icon name="check" [size]="14"></app-icon> {{ fc.createdAt | dt }} · <span class="ltr">{{ (fc.detScore * 100).toFixed(0) }}%</span></li> }
          </ul>
          <button class="btn btn-danger-ghost btn-sm" (click)="erase()">{{ 'sol.erase' | t }}</button>
        }
      </div>
    }
  </section>`,
})
export class SoldierFormPage implements OnInit {
  @Input() id?: string;
  private http = inject(HttpClient);
  private router = inject(Router);
  private auth = inject(AuthService);
  private toast = inject(ToastService);
  private confirm = inject(ConfirmService);
  private i18n = inject(I18n);

  ranks = ['جندي', 'جندي أول', 'وكيل عريف', 'عريف', 'رقيب', 'رقيب أول', 'وكيل'];
  isAgent = this.auth.hasRole('COMPANY_AGENT');
  canManage = this.auth.hasRole('PLATOON_LEADER', 'COMPANY_AGENT');
  isNew = true;
  model = { militaryNumber: '', fullName: '', militaryRank: 'جندي', platoonId: null as number | null, biometricConsent: false };
  soldier = signal<Soldier | null>(null);
  platoons = signal<Platoon[]>([]);
  faces = signal<FaceTemplate[]>([]);
  maxFaces = signal(5);
  busy = signal(false);
  error = signal<string | null>(null);
  photoBusy = signal(false);
  photoCam = signal(false);
  faceCam = signal(false);
  photoError = signal<string | null>(null);
  enrollWithPhoto = true;
  enrolling = signal(false);
  enrollError = signal<string | null>(null);

  slots() { return Array.from({ length: this.maxFaces() }, (_, i) => i); }

  ngOnInit() {
    this.isNew = !this.id || this.id === 'new';
    this.http.get<Platoon[]>('/api/platoons').subscribe(ps => {
      this.platoons.set(ps.filter(p => p.active));
      if (this.isNew) this.model.platoonId = this.auth.user()?.platoonId ?? ps[0]?.id ?? null;
    });
    this.http.get<{ enrollmentMaxFaces: number }>('/api/settings').subscribe(s => this.maxFaces.set(s.enrollmentMaxFaces));
    if (!this.isNew) this.loadSoldier();
  }

  loadSoldier() {
    this.http.get<Soldier>(`/api/soldiers/${this.id}`).subscribe({
      next: s => {
        this.soldier.set(s);
        this.model = { militaryNumber: s.militaryNumber, fullName: s.fullName, militaryRank: s.militaryRank,
          platoonId: s.platoonId, biometricConsent: s.biometricConsent };
        if (this.canManage) this.http.get<FaceTemplate[]>(`/api/soldiers/${this.id}/faces`).subscribe(f => this.faces.set(f));
      },
      error: e => this.error.set(errorMessage(e)),
    });
  }

  async save() {
    const s = this.soldier();
    if (s?.biometricConsent && !this.model.biometricConsent && this.faces().length
      && !(await this.confirm.ask(this.i18n.t('sol.eraseConfirm'), true))) return;
    this.busy.set(true);
    this.error.set(null);
    const req = this.isNew ? this.http.post<Soldier>('/api/soldiers', this.model)
      : this.http.put<Soldier>(`/api/soldiers/${this.id}`, this.model);
    req.subscribe({
      next: saved => {
        this.busy.set(false);
        this.toast.success(this.i18n.t('sol.saved'));
        if (this.isNew) this.router.navigate(['/soldiers', saved.id]).then(() => { this.id = String(saved.id); this.isNew = false; this.loadSoldier(); });
        else this.loadSoldier();
      },
      error: e => { this.busy.set(false); this.error.set(errorMessage(e)); },
    });
  }

  pickPhoto(ev: Event) {
    const input = ev.target as HTMLInputElement;
    const f = input.files?.[0];
    input.value = '';
    if (f) this.uploadPhoto(f);
  }

  async uploadPhoto(blob: Blob) {
    const s = this.soldier();
    if (!s) return;
    this.photoBusy.set(true);
    this.photoError.set(null);
    let jpeg: Blob;
    try { jpeg = await toJpeg(blob, 1600); }
    catch { this.photoBusy.set(false); this.photoError.set(this.i18n.t('photo.unreadable')); return; }
    const enroll = this.enrollWithPhoto && s.biometricConsent && this.faces().length < this.maxFaces();
    const fd = new FormData();
    fd.append('image', jpeg, 'photo.jpg');
    this.http.put<PhotoResult>(`/api/soldiers/${s.id}/photo`, fd, { params: { enroll: String(enroll) } }).subscribe({
      next: r => {
        this.photoBusy.set(false);
        this.soldier.set(r.soldier);
        if (r.enrollment) this.faces.update(f => [...f, r.enrollment!.template]);
        this.toast.success(r.enrollment ? `${this.i18n.t('photo.saved')} · ${this.i18n.t('sol.enrolled')} (${r.enrollment.enrolledFaces}/${r.enrollment.maxFaces})` : this.i18n.t('photo.saved'));
      },
      error: e => { this.photoBusy.set(false); this.photoError.set(errorMessage(e)); },
    });
  }

  async removePhoto() {
    if (!(await this.confirm.ask(this.i18n.t('photo.removeConfirm'), true))) return;
    this.http.delete<Soldier>(`/api/soldiers/${this.id}/photo`).subscribe({
      next: s => { this.soldier.set(s); this.toast.success(this.i18n.t('photo.removed')); },
      error: e => this.toast.error(errorMessage(e)),
    });
  }

  enroll(blob: Blob) {
    this.enrolling.set(true);
    this.enrollError.set(null);
    const fd = new FormData();
    fd.append('image', blob, 'face.jpg');
    this.http.post<EnrollResult>(`/api/soldiers/${this.id}/faces`, fd).subscribe({
      next: r => { this.enrolling.set(false); this.faces.update(f => [...f, r.template]); this.toast.success(`${this.i18n.t('sol.enrolled')} (${r.enrolledFaces}/${r.maxFaces})`); },
      error: e => { this.enrolling.set(false); this.enrollError.set(errorMessage(e)); },
    });
  }

  async erase() {
    if (!(await this.confirm.ask(this.i18n.t('sol.eraseConfirm'), true))) return;
    this.http.delete(`/api/soldiers/${this.id}/faces`).subscribe({
      next: () => this.faces.set([]),
      error: e => this.toast.error(errorMessage(e)),
    });
  }

  async deactivate() {
    if (!(await this.confirm.ask(this.i18n.t('sol.deactivateConfirm'), true))) return;
    this.http.post<Soldier>(`/api/soldiers/${this.id}/deactivate`, {}).subscribe({
      next: () => { this.faces.set([]); this.loadSoldier(); },
      error: e => this.toast.error(errorMessage(e)),
    });
  }

  reactivate() {
    this.http.post<Soldier>(`/api/soldiers/${this.id}/reactivate`, {}).subscribe({
      next: () => this.loadSoldier(),
      error: e => this.toast.error(errorMessage(e)),
    });
  }
}
