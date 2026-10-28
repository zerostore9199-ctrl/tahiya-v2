import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { AuthService } from '../core/auth.service';
import { I18n, TPipe } from '../core/i18n';
import { DtPipe } from '../core/format';
import { LiveDashboard, TrendPoint } from '../core/models';
import { errorMessage } from '../core/errors';
import { IconComponent } from '../shared/icon.component';
import { SessionStatusComponent, SpinnerComponent } from '../shared/ui';

@Component({
  standalone: true,
  imports: [RouterLink, TPipe, DtPipe, IconComponent, SessionStatusComponent, SpinnerComponent],
  template: `
  <section class="page wide">
    <header class="page-head">
      <div><h1>{{ 'nav.dashboard' | t }}</h1><p class="muted">{{ data()?.date | dt:'date' }} · {{ updated() | dt:'time' }}</p></div>
      <button class="btn btn-ghost" (click)="load()" [disabled]="loading()"><app-icon name="refresh" [size]="18"></app-icon>{{ 'dash.refresh' | t }}</button>
    </header>
    @if (error()) { <div class="alert alert-error">{{ error() }}</div> }
    @if (!data() && loading()) { <app-spinner [label]="'loading' | t"></app-spinner> }
    @if (data(); as d) {
      <div class="stat-grid four">
        <div class="stat big"><span>{{ 'dash.totalSoldiers' | t }}</span><strong>{{ d.kpis.totalSoldiers }}</strong><small>{{ 'rate' | t }} <b class="ltr">{{ d.kpis.attendanceRate }}%</b></small></div>
        <div class="stat big ok"><span>{{ 'status.PRESENT' | t }}</span><strong>{{ d.kpis.present }}</strong></div>
        <div class="stat big warn"><span>{{ 'status.LATE' | t }}</span><strong>{{ d.kpis.late }}</strong></div>
        <div class="stat big bad"><span>{{ 'status.ABSENT' | t }}</span><strong>{{ d.kpis.absent }}</strong><small>{{ 'status.EXCUSED' | t }}: {{ d.kpis.excused }}</small></div>
      </div>

      <div class="dash-grid">
        <div class="dash-main">
          <div class="card table-wrap">
            <table class="table">
              <thead><tr><th>{{ 'platoon' | t }}</th><th>{{ 'dash.senior' | t }}</th><th>{{ 'dash.session' | t }}</th>
                <th class="num">{{ 'status.PRESENT' | t }}</th><th>{{ 'rate' | t }}</th></tr></thead>
              <tbody>
                @for (p of d.platoons; track p.platoonId) {
                  <tr [class.clickable]="!!p.sessionId" (click)="p.sessionId && open(p.sessionId)">
                    <td><strong>{{ p.platoonName }}</strong></td>
                    <td>{{ p.seniorName || '—' }}</td>
                    <td>@if (p.sessionStatus) { <app-session-status [status]="p.sessionStatus"></app-session-status> <small class="muted">{{ p.sessionType! | t:'type' }}</small> }
                        @else { <span class="muted">{{ 'dash.noSession' | t }}</span> }</td>
                    <td class="num"><span class="ltr">{{ p.counts ? (p.counts.present + p.counts.late) : 0 }} / {{ p.counts?.total ?? p.activeSoldiers }}</span></td>
                    <td class="bar-cell"><div class="progress small"><i [style.width.%]="p.counts?.attendanceRate ?? 0"></i></div><span class="ltr">{{ p.counts?.attendanceRate ?? 0 }}%</span></td>
                  </tr>
                }
              </tbody>
            </table>
          </div>

          <div class="card">
            <h2 class="card-title">{{ 'dash.trend' | t }}</h2>
            @if (trend().length) {
              <svg class="chart" viewBox="0 0 700 220" role="img" [attr.aria-label]="'dash.trend' | t">
                @for (g of [0, 25, 50, 75, 100]; track g) {
                  <line x1="40" x2="690" [attr.y1]="y(g)" [attr.y2]="y(g)" class="grid"></line>
                  <text x="34" [attr.y]="y(g) + 4" class="axis" text-anchor="end">{{ g }}</text>
                }
                @for (p of trend(); track p.date; let i = $index) {
                  <rect [attr.x]="x(i) - barW() / 2" [attr.y]="y(p.attendanceRate)" [attr.width]="barW()"
                        [attr.height]="190 - y(p.attendanceRate)" rx="4" class="bar" [class.empty]="!p.records">
                    <title>{{ p.date }}: {{ p.attendanceRate }}%</title></rect>
                  <text [attr.x]="x(i)" y="210" class="axis" text-anchor="middle">{{ day(p.date) }}</text>
                  @if (p.records) { <text [attr.x]="x(i)" [attr.y]="y(p.attendanceRate) - 6" class="val" text-anchor="middle">{{ p.attendanceRate }}</text> }
                }
              </svg>
            }
          </div>
        </div>

        <aside class="dash-side">
          <div class="card">
            <h2 class="card-title">{{ 'dash.pending' | t }} <span class="count">{{ d.pendingApprovals.length }}</span></h2>
            @if (!d.pendingApprovals.length) { <p class="muted small">{{ 'appr.none' | t }}</p> }
            @for (a of d.pendingApprovals; track a.sessionId) {
              <a class="side-item" [routerLink]="['/sessions', a.sessionId]">
                <strong>{{ a.platoonName }}</strong><small>{{ a.sessionType | t:'type' }} · {{ a.sessionDate | dt:'date' }}</small>
                <small><span class="badge s-ABSENT">{{ a.absent }}</span> <span class="badge s-LATE">{{ a.late }}</span></small>
              </a>
            }
          </div>
          @if (isAgent) {
            <div class="card">
              <h2 class="card-title">{{ 'dash.activity' | t }}</h2>
              @for (e of d.recentActivity; track $index) {
                <div class="side-item"><strong class="ltr">{{ e.action }}</strong><small>{{ e.username || '—' }} · {{ e.occurredAt | dt:'time' }}</small></div>
              }
              <a class="btn btn-ghost btn-sm btn-block" routerLink="/audit">{{ 'nav.audit' | t }}</a>
            </div>
          }
        </aside>
      </div>
    }
  </section>`,
})
export class DashboardPage implements OnInit, OnDestroy {
  private http = inject(HttpClient);
  private auth = inject(AuthService);
  private i18n = inject(I18n);
  private router = inject(Router);
  isAgent = this.auth.hasRole('COMPANY_AGENT');
  data = signal<LiveDashboard | null>(null);
  trend = signal<TrendPoint[]>([]);
  loading = signal(false);
  error = signal<string | null>(null);
  updated = signal<string | null>(null);
  barW = computed(() => Math.min(56, 520 / Math.max(1, this.trend().length)));
  private timer?: ReturnType<typeof setInterval>;

  ngOnInit() {
    this.load();
    this.timer = setInterval(() => document.visibilityState === 'visible' && this.load(), 30000);
  }

  ngOnDestroy() { clearInterval(this.timer); }

  load() {
    this.loading.set(true);
    forkJoin({
      live: this.http.get<LiveDashboard>('/api/dashboard/live'),
      trend: this.http.get<TrendPoint[]>('/api/dashboard/trend', { params: { days: 7 } }),
    }).subscribe({
      next: r => { this.data.set(r.live); this.trend.set(r.trend); this.loading.set(false); this.error.set(null); this.updated.set(new Date().toISOString()); },
      error: e => { this.loading.set(false); this.error.set(errorMessage(e)); },
    });
  }

  open(id: number) { this.router.navigate(['/sessions', id]); }

  x(i: number): number { const n = this.trend().length; return 40 + (650 / n) * (i + 0.5); }
  y(v: number): number { return 190 - 1.7 * v; }
  day(iso: string): string {
    return new Intl.DateTimeFormat(this.i18n.locale(), { weekday: 'short', timeZone: 'UTC' }).format(new Date(iso + 'T00:00:00Z'));
  }
}
