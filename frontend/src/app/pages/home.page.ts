import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { TPipe } from '../core/i18n';
import { DtPipe, todayIso } from '../core/format';
import { Page, SessionSummary } from '../core/models';
import { errorMessage } from '../core/errors';
import { IconComponent } from '../shared/icon.component';
import { EmptyStateComponent, SessionStatusComponent, SpinnerComponent } from '../shared/ui';

@Component({
  standalone: true,
  imports: [RouterLink, TPipe, DtPipe, IconComponent, EmptyStateComponent, SessionStatusComponent, SpinnerComponent],
  template: `
  <section class="page">
    <header class="page-head">
      <div>
        <h1>{{ 'home.welcome' | t }} {{ auth.user()?.fullName }}</h1>
        <p class="muted">{{ auth.user()?.platoonName }} · {{ today | dt:'date' }}</p>
      </div>
    </header>

    @if (loading()) { <app-spinner [label]="'loading' | t"></app-spinner> }
    @else if (error()) { <div class="alert alert-error">{{ error() }} <button class="btn btn-sm btn-ghost" (click)="load()">{{ 'retry' | t }}</button></div> }
    @else {
      @if (open(); as o) {
        <a class="hero-action" [routerLink]="['/sessions', o.id]">
          <app-icon name="camera" [size]="34"></app-icon>
          <div><strong>{{ 'home.continue' | t }}</strong><span>{{ o.sessionType | t:'type' }} · <span class="ltr">{{ o.counts.present + o.counts.late }}/{{ o.counts.total }}</span></span></div>
        </a>
      } @else {
        <a class="hero-action" routerLink="/sessions/new">
          <app-icon name="camera" [size]="34"></app-icon>
          <div><strong>{{ 'home.start' | t }}</strong><span>{{ 'cam.tip' | t }}</span></div>
        </a>
      }

      @if (last(); as l) {
        <h2 class="section-title">{{ 'home.lastSession' | t }} — {{ l.sessionType | t:'type' }}</h2>
        <div class="stat-grid three">
          <div class="stat ok"><span>{{ 'status.PRESENT' | t }}</span><strong>{{ l.counts.present }}</strong></div>
          <div class="stat warn"><span>{{ 'status.LATE' | t }}</span><strong>{{ l.counts.late }}</strong></div>
          <div class="stat bad"><span>{{ 'status.ABSENT' | t }}</span><strong>{{ l.counts.absent + l.counts.unrecorded }}</strong></div>
        </div>
      }

      <h2 class="section-title">{{ 'home.today' | t }}</h2>
      @if (sessions().length === 0) { <app-empty icon="list" [text]="'home.noSessions' | t"></app-empty> }
      <div class="list">
        @for (s of sessions(); track s.id) {
          <a class="list-row" [routerLink]="['/sessions', s.id]">
            <div><strong>{{ s.sessionType | t:'type' }}</strong> <small class="muted">{{ s.openedAt | dt:'time' }}</small></div>
            <div class="row-end"><span class="pct">{{ s.counts.attendanceRate }}%</span><app-session-status [status]="s.status"></app-session-status></div>
          </a>
        }
      </div>
      <a class="btn btn-ghost btn-block" routerLink="/sessions">{{ 'nav.sessions' | t }}</a>
    }
  </section>`,
})
export class HomePage implements OnInit {
  auth = inject(AuthService);
  private http = inject(HttpClient);
  today = todayIso();
  loading = signal(true);
  error = signal<string | null>(null);
  sessions = signal<SessionSummary[]>([]);
  open = signal<SessionSummary | null>(null);
  last = signal<SessionSummary | null>(null);

  ngOnInit() { this.load(); }

  load() {
    const pid = this.auth.user()?.platoonId;
    this.loading.set(true);
    this.error.set(null);
    this.http.get<Page<SessionSummary>>('/api/sessions', { params: { platoonId: pid ?? '', from: this.today, to: this.today, size: 20 } })
      .subscribe({
        next: p => {
          this.sessions.set(p.items);
          this.open.set(p.items.find(s => s.status === 'OPEN') ?? null);
          this.last.set(p.items.find(s => s.status !== 'OPEN') ?? null);
          this.loading.set(false);
        },
        error: e => { this.error.set(errorMessage(e)); this.loading.set(false); },
      });
  }
}
