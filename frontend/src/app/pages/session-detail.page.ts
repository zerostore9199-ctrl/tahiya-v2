import { Component, Input, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { I18n, TPipe } from '../core/i18n';
import { DtPipe } from '../core/format';
import { AttendanceStatus, RecognitionResult, RecordView, RosterEntry, STATUSES, SessionDetail, SessionSummary } from '../core/models';
import { errorCode, errorMessage } from '../core/errors';
import { NetworkService } from '../core/network.service';
import { ToastService } from '../core/toast.service';
import { AvatarComponent } from '../shared/avatar.component';
import { CameraComponent } from '../shared/camera.component';
import { IconComponent } from '../shared/icon.component';
import { ConfirmService, EmptyStateComponent, SessionStatusComponent, SpinnerComponent, StatusBadgeComponent } from '../shared/ui';

type Tab = 'camera' | 'roster';

@Component({
  standalone: true,
  imports: [FormsModule, RouterLink, TPipe, DtPipe, AvatarComponent, CameraComponent, IconComponent, EmptyStateComponent,
    SessionStatusComponent, SpinnerComponent, StatusBadgeComponent],
  template: `
  <section class="page">
    @if (!detail() && loading()) { <app-spinner [label]="'loading' | t"></app-spinner> }
    @if (loadError()) { <div class="alert alert-error">{{ loadError() }} <a routerLink="/sessions">{{ 'back' | t }}</a></div> }

    @if (detail(); as d) {
      <header class="page-head">
        <div>
          <a class="back-link" routerLink="/sessions">← {{ 'nav.sessions' | t }}</a>
          <h1>{{ d.session.sessionType | t:'type' }} <small class="muted">{{ d.session.title }}</small></h1>
          <p class="muted">{{ d.session.platoonName }} · {{ d.session.openedAt | dt }} · {{ 'sess.openedBy' | t }}: {{ d.session.openedBy }}</p>
        </div>
        <app-session-status [status]="d.session.status"></app-session-status>
      </header>

      <div class="stat-grid five">
        <div class="stat"><span>{{ 'total' | t }}</span><strong>{{ d.session.counts.total }}</strong></div>
        <div class="stat ok"><span>{{ 'status.PRESENT' | t }}</span><strong>{{ d.session.counts.present }}</strong></div>
        <div class="stat warn"><span>{{ 'status.LATE' | t }}</span><strong>{{ d.session.counts.late }}</strong></div>
        <div class="stat info"><span>{{ 'status.EXCUSED' | t }}</span><strong>{{ d.session.counts.excused }}</strong></div>
        <div class="stat bad"><span>{{ isOpen() ? ('sess.remaining' | t) : ('status.ABSENT' | t) }}</span>
          <strong>{{ isOpen() ? d.session.counts.unrecorded : d.session.counts.absent }}</strong></div>
      </div>
      <div class="progress" [attr.aria-label]="'rate' | t"><i [style.width.%]="progress()"></i></div>

      @if (canTake()) {
        <div class="tabs" role="tablist">
          <button role="tab" [class.on]="tab() === 'camera'" (click)="tab.set('camera')"><app-icon name="camera" [size]="18"></app-icon>{{ 'sess.camera' | t }}</button>
          <button role="tab" [class.on]="tab() === 'roster'" (click)="tab.set('roster')"><app-icon name="list" [size]="18"></app-icon>{{ 'sess.roster' | t }}
            <span class="count">{{ d.session.counts.unrecorded }}</span></button>
        </div>
      } @else if (!canApprove()) {
        <p class="muted small">{{ 'sess.readOnly' | t }}</p>
      }

      <!-- ===================== CAMERA ===================== -->
      @if (canTake() && tab() === 'camera') {
        <div class="card">
          @if (!preview()) {
            <app-camera (captured)="recognize($event)" [disabled]="busy()"></app-camera>
          } @else {
            <div class="result-image" [style.aspect-ratio]="aspect()">
              <img [src]="preview()" alt="">
              @if (result(); as r) {
                @for (f of r.faces; track $index) {
                  <div class="facebox" [class]="'facebox fb-' + f.outcome"
                       [style.left.%]="100 * f.bbox[0] / r.imageWidth" [style.top.%]="100 * f.bbox[1] / r.imageHeight"
                       [style.width.%]="100 * (f.bbox[2] - f.bbox[0]) / r.imageWidth" [style.height.%]="100 * (f.bbox[3] - f.bbox[1]) / r.imageHeight">
                    <span>{{ f.fullName || (f.outcome | t:'face') }}@if (f.similarity) { <b class="ltr">{{ (f.similarity * 100).toFixed(0) }}%</b> }</span>
                  </div>
                }
              }
              @if (busy()) { <div class="result-busy"><app-spinner [label]="'cam.processing' | t"></app-spinner></div> }
            </div>
            @if (result(); as r) {
              <div class="stat-grid four compact">
                <div class="stat ok"><span>{{ 'cam.recognized' | t }}</span><strong>{{ r.recognized }}</strong></div>
                <div class="stat"><span>{{ 'cam.already' | t }}</span><strong>{{ r.alreadyRecorded }}</strong></div>
                <div class="stat bad"><span>{{ 'cam.unknown' | t }}</span><strong>{{ r.unknown + r.ignoredLowQuality }}</strong></div>
                <div class="stat"><span>{{ 'cam.detected' | t }}</span><strong>{{ r.detected }}</strong></div>
              </div>
              @if (r.detected === 0) { <div class="alert alert-info">{{ 'cam.noFace' | t }}</div> }
              @if (matched().length) {
                <ul class="match-list">
                  @for (f of matched(); track f.soldierId) {
                    <li><app-icon name="check" [size]="16"></app-icon><strong>{{ f.fullName }}</strong>
                      <span class="ltr muted">{{ f.militaryNumber }}</span>
                      <span class="ltr pct">{{ ((f.similarity || 0) * 100).toFixed(0) }}%</span></li>
                  }
                </ul>
              }
            }
            <div class="form-actions">
              <button class="btn btn-ghost" (click)="resetCapture()" [disabled]="busy()"><app-icon name="refresh" [size]="18"></app-icon>{{ 'cam.retake' | t }}</button>
              <button class="btn btn-primary" (click)="tab.set('roster')" [disabled]="busy()">{{ 'sess.roster' | t }} ({{ d.session.counts.unrecorded }})</button>
            </div>
          }
          @if (camError()) {
            <div class="alert alert-error">{{ camError() }}
              @if (fallback()) { <button class="btn btn-sm btn-primary" (click)="tab.set('roster')">{{ 'sess.roster' | t }}</button> }
            </div>
          }
        </div>
      }

      <!-- ===================== ROSTER / MANUAL / APPROVAL ===================== -->
      @if (!canTake() || tab() === 'roster') {
        <div class="card">
          <div class="roster-tools">
            <label class="search"><app-icon name="search" [size]="18"></app-icon>
              <input [(ngModel)]="query" [placeholder]="'sol.searchPh' | t" [attr.aria-label]="'search' | t"></label>
            @if (isOpen()) {
              <label class="check"><input type="checkbox" [(ngModel)]="onlyRemaining">{{ 'sess.filterRemaining' | t }}</label>
            }
          </div>
          @if (filtered().length === 0) { <app-empty icon="soldier" [text]="'sol.empty' | t"></app-empty> }
          <ul class="roster">
            @for (r of filtered(); track r.soldierId) {
              <li class="roster-row" [class.pending]="pendingIds().has(r.soldierId)">
                <div class="roster-id">
                <app-avatar kind="soldiers" [id]="r.soldierId" [version]="r.photoVersion" [name]="r.fullName" [size]="40"></app-avatar>
                <div class="roster-who">
                  <strong>{{ r.fullName }}</strong>
                  <small><span class="ltr">{{ r.militaryNumber }}</span> · {{ r.militaryRank }}
                    @if (!r.hasFaces) { · <span class="warn-text">{{ 'sess.noFaces' | t }}</span> }</small>
                  @if (r.record?.note) { <small class="note">“{{ r.record?.note }}”</small> }
                </div>
                </div>
                <div class="roster-state">
                  <app-status [status]="statusOf(r)"></app-status>
                  @if (r.record; as rec) {
                    <small class="muted">{{ rec.method | t:'method' }}@if (rec.similarity) { · <span class="ltr">{{ (rec.similarity * 100).toFixed(0) }}%</span> }</small>
                  }
                </div>
                @if (canTake()) {
                  <div class="status-buttons" role="group">
                    @for (st of statuses; track st) {
                      <button class="sbtn" [class]="'sbtn s-' + st" [class.on]="r.record?.status === st" (click)="mark(r, st)"
                              [disabled]="savingId() === r.soldierId">{{ st | t:'status' }}</button>
                    }
                    <button class="sbtn note-btn" (click)="toggleNote(r)" [title]="'note' | t"><app-icon name="edit" [size]="16"></app-icon></button>
                  </div>
                  @if (noteFor() === r.soldierId) {
                    <div class="note-edit">
                      <input [(ngModel)]="noteText" maxlength="500" [placeholder]="'note' | t">
                      <button class="btn btn-sm btn-primary" (click)="saveNote(r)">{{ 'save' | t }}</button>
                    </div>
                  }
                }
                @if (canApprove() && r.record) {
                  <div class="review">
                    <select [ngModel]="review()[r.soldierId]?.status ?? r.record.status" (ngModelChange)="setReview(r, $event)">
                      @for (st of statuses; track st) { <option [value]="st">{{ st | t:'status' }}</option> }
                    </select>
                    <input [ngModel]="review()[r.soldierId]?.note ?? (r.record.note || '')" (ngModelChange)="setReviewNote(r, $event)"
                           maxlength="500" [placeholder]="'note' | t">
                  </div>
                }
              </li>
            }
          </ul>
        </div>
      }

      <div class="sticky-actions">
        @if (canTake()) {
          <button class="btn btn-danger btn-lg" (click)="close()" [disabled]="busy()"><app-icon name="lock" [size]="18"></app-icon>{{ 'sess.close' | t }}</button>
        }
        @if (canApprove()) {
          <span class="muted">{{ changeCount() }} {{ 'sess.changes' | t }}</span>
          <button class="btn btn-primary btn-lg" (click)="approve()" [disabled]="busy()"><app-icon name="approve" [size]="18"></app-icon>{{ 'sess.approve' | t }}</button>
        }
      </div>
    }
  </section>`,
})
export class SessionDetailPage implements OnInit, OnDestroy {
  @Input() id!: string;
  private http = inject(HttpClient);
  private auth = inject(AuthService);
  private i18n = inject(I18n);
  private toast = inject(ToastService);
  private confirm = inject(ConfirmService);
  private net = inject(NetworkService);

  statuses = STATUSES;
  detail = signal<SessionDetail | null>(null);
  loading = signal(true);
  loadError = signal<string | null>(null);
  tab = signal<Tab>('camera');
  busy = signal(false);
  preview = signal<string | null>(null);
  aspect = signal('4 / 3');
  result = signal<RecognitionResult | null>(null);
  camError = signal<string | null>(null);
  fallback = signal(false);
  savingId = signal<number | null>(null);
  noteFor = signal<number | null>(null);
  noteText = '';
  query = '';
  onlyRemaining = false;
  review = signal<Partial<Record<number, { status: AttendanceStatus; note: string }>>>({});
  pendingIds = signal<Set<number>>(new Set());

  isOpen = computed(() => this.detail()?.session.status === 'OPEN');
  canTake = computed(() => {
    const d = this.detail();
    const u = this.auth.user();
    if (!d || !u || d.session.status !== 'OPEN') return false;
    if (u.role === 'COMPANY_AGENT') return true;
    return (u.role === 'SENIOR' || u.role === 'PLATOON_LEADER') && u.platoonId === d.session.platoonId;
  });
  canApprove = computed(() => {
    const d = this.detail();
    const u = this.auth.user();
    if (!d || !u || d.session.status !== 'CLOSED') return false;
    return u.role === 'COMPANY_AGENT' || (u.role === 'PLATOON_LEADER' && u.platoonId === d.session.platoonId);
  });
  progress = computed(() => {
    const c = this.detail()?.session.counts;
    return c && c.total ? Math.round(100 * (c.total - c.unrecorded) / c.total) : 0;
  });
  matched = computed(() => (this.result()?.faces ?? []).filter(f => f.outcome === 'MATCHED' || f.outcome === 'MATCHED_LATE'));
  changeCount = computed(() => Object.keys(this.review()).length);

  ngOnInit() { this.load(); }

  ngOnDestroy() { this.revokePreview(); }

  load() {
    this.http.get<SessionDetail>(`/api/sessions/${this.id}`).subscribe({
      next: d => {
        this.detail.set(d);
        this.loading.set(false);
        this.pendingIds.set(new Set(this.net.pendingFor(d.session.id).map(m => m.soldierId)));
        if (d.session.status !== 'OPEN') this.tab.set('roster');
      },
      error: e => { this.loading.set(false); this.loadError.set(errorMessage(e)); },
    });
  }

  filtered(): RosterEntry[] {
    const d = this.detail();
    if (!d) return [];
    const q = this.query.trim().toLowerCase();
    return d.roster.filter(r =>
      (!this.onlyRemaining || !r.record) &&
      (!q || r.fullName.toLowerCase().includes(q) || r.militaryNumber.includes(q)));
  }

  statusOf(r: RosterEntry): string | null {
    if (this.pendingIds().has(r.soldierId)) return this.net.pendingFor(this.detail()!.session.id).find(m => m.soldierId === r.soldierId)?.status ?? null;
    return r.record?.status ?? null;
  }

  // ---------------------------------------------------------------- camera

  recognize(blob: Blob) {
    this.revokePreview();
    const url = URL.createObjectURL(blob);
    this.preview.set(url);
    const img = new Image();
    img.onload = () => this.aspect.set(`${img.naturalWidth} / ${img.naturalHeight}`);
    img.src = url;
    this.result.set(null);
    this.camError.set(null);
    this.fallback.set(false);
    this.busy.set(true);
    const fd = new FormData();
    fd.append('image', blob, 'parade.jpg');
    this.http.post<RecognitionResult>(`/api/sessions/${this.id}/recognize`, fd).subscribe({
      next: r => {
        this.result.set(r);
        this.busy.set(false);
        this.patchCounts(r.counts);
        this.refreshRoster();
      },
      error: e => {
        this.busy.set(false);
        const code = errorCode(e);
        this.fallback.set(code === 'FACE_SERVICE_UNAVAILABLE' || e?.status === 0);
        this.camError.set(this.fallback() ? this.i18n.t('cam.fallback') : errorMessage(e));
      },
    });
  }

  resetCapture() {
    this.revokePreview();
    this.result.set(null);
    this.camError.set(null);
  }

  private revokePreview() {
    const p = this.preview();
    if (p) URL.revokeObjectURL(p);
    this.preview.set(null);
  }

  // ---------------------------------------------------------------- manual

  mark(r: RosterEntry, status: AttendanceStatus, note?: string) {
    const sessionId = this.detail()!.session.id;
    const body = { soldierId: r.soldierId, status, note: note ?? r.record?.note ?? undefined };
    if (!this.net.online()) {
      this.net.enqueue({ sessionId, ...body });
      this.pendingIds.update(s => new Set(s).add(r.soldierId));
      return;
    }
    this.savingId.set(r.soldierId);
    this.http.post<RecordView>(`/api/sessions/${sessionId}/manual`, body).subscribe({
      next: rec => {
        this.savingId.set(null);
        this.updateRecord(r.soldierId, rec);
        this.refreshCountsOnly();
      },
      error: e => {
        this.savingId.set(null);
        if (e?.status === 0) {
          this.net.enqueue({ sessionId, ...body });
          this.pendingIds.update(s => new Set(s).add(r.soldierId));
        } else {
          this.toast.error(errorMessage(e));
        }
      },
    });
  }

  toggleNote(r: RosterEntry) {
    this.noteFor.set(this.noteFor() === r.soldierId ? null : r.soldierId);
    this.noteText = r.record?.note ?? '';
  }

  saveNote(r: RosterEntry) {
    this.mark(r, r.record?.status ?? 'PRESENT', this.noteText);
    this.noteFor.set(null);
  }

  // ---------------------------------------------------------------- close / approve

  async close() {
    const d = this.detail()!;
    if (this.net.pendingFor(d.session.id).length) await this.net.flush();
    if (!(await this.confirm.ask(this.i18n.t('sess.closeConfirm'), true))) return;
    this.busy.set(true);
    this.http.post<SessionSummary>(`/api/sessions/${d.session.id}/close`, {}).subscribe({
      next: () => { this.busy.set(false); this.toast.success(this.i18n.t('sess.closed')); this.load(); },
      error: e => { this.busy.set(false); this.toast.error(errorMessage(e)); },
    });
  }

  setReview(r: RosterEntry, status: AttendanceStatus) {
    this.review.update(m => ({ ...m, [r.soldierId]: { status, note: m[r.soldierId]?.note ?? r.record?.note ?? '' } }));
  }

  setReviewNote(r: RosterEntry, note: string) {
    this.review.update(m => ({ ...m, [r.soldierId]: { status: m[r.soldierId]?.status ?? r.record!.status, note } }));
  }

  async approve() {
    if (!(await this.confirm.ask(this.i18n.t('sess.approve') + '؟'))) return;
    const changes = Object.entries(this.review()).map(([soldierId, v]) => ({ soldierId: +soldierId, status: v!.status, note: v!.note }));
    this.busy.set(true);
    this.http.post<SessionSummary>(`/api/sessions/${this.id}/approve`, { changes }).subscribe({
      next: () => { this.busy.set(false); this.review.set({}); this.toast.success(this.i18n.t('sess.approved')); this.load(); },
      error: e => { this.busy.set(false); this.toast.error(errorMessage(e)); },
    });
  }

  // ---------------------------------------------------------------- local state helpers

  private updateRecord(soldierId: number, rec: RecordView) {
    const d = this.detail();
    if (!d) return;
    this.detail.set({ ...d, roster: d.roster.map(x => x.soldierId === soldierId ? { ...x, record: rec } : x) });
  }

  private patchCounts(counts: SessionSummary['counts']) {
    const d = this.detail();
    if (d) this.detail.set({ ...d, session: { ...d.session, counts } });
  }

  private refreshRoster() {
    this.http.get<SessionDetail>(`/api/sessions/${this.id}`).subscribe(d => this.detail.set(d));
  }

  private refreshCountsOnly() { this.refreshRoster(); }
}
