import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../core/auth.service';
import { TPipe } from '../core/i18n';
import { thisMonth, todayIso } from '../core/format';
import { DailyReport, MonthlyReport, Platoon, Totals } from '../core/models';
import { downloadFile } from '../core/download';
import { errorMessage } from '../core/errors';
import { ToastService } from '../core/toast.service';
import { IconComponent } from '../shared/icon.component';
import { EmptyStateComponent, SessionStatusComponent, SpinnerComponent, StatusBadgeComponent } from '../shared/ui';

@Component({
  standalone: true,
  imports: [FormsModule, TPipe, IconComponent, EmptyStateComponent, SessionStatusComponent, SpinnerComponent, StatusBadgeComponent],
  template: `
  <section class="page wide">
    <header class="page-head"><h1>{{ 'nav.reports' | t }}</h1>
      <div class="btn-row">
        <button class="btn btn-ghost" (click)="export('pdf')" [disabled]="exporting()"><app-icon name="download" [size]="18"></app-icon>{{ 'rep.pdf' | t }}</button>
        <button class="btn btn-ghost" (click)="export('xlsx')" [disabled]="exporting()"><app-icon name="download" [size]="18"></app-icon>{{ 'rep.xlsx' | t }}</button>
      </div>
    </header>
    <div class="tabs">
      <button [class.on]="mode() === 'monthly'" (click)="mode.set('monthly'); load()">{{ 'rep.monthly' | t }}</button>
      <button [class.on]="mode() === 'daily'" (click)="mode.set('daily'); load()">{{ 'rep.daily' | t }}</button>
    </div>
    <div class="filters card">
      @if (mode() === 'monthly') {
        <label class="field"><span>{{ 'rep.month' | t }}</span><input type="month" [(ngModel)]="month" (change)="load()" dir="ltr"></label>
      } @else {
        <label class="field"><span>{{ 'date' | t }}</span><input type="date" [(ngModel)]="date" (change)="load()" dir="ltr"></label>
      }
      @if (platoons().length > 1) {
        <label class="field"><span>{{ 'platoon' | t }}</span>
          <select [(ngModel)]="platoonId" (ngModelChange)="load()"><option [ngValue]="null">{{ 'all' | t }}</option>
            @for (p of platoons(); track p.id) { <option [ngValue]="p.id">{{ p.name }}</option> }</select></label>
      }
    </div>

    @if (loading()) { <app-spinner [label]="'loading' | t"></app-spinner> }
    @if (error()) { <div class="alert alert-error">{{ error() }}</div> }

    @if (totals(); as t) {
      <div class="stat-grid four">
        <div class="stat big"><span>{{ 'rep.discipline' | t }}</span><strong class="ltr">{{ t.disciplineRate }}%</strong>
          @if (prev(); as p) { <small [class.up]="t.disciplineRate >= p.disciplineRate" [class.down]="t.disciplineRate < p.disciplineRate">
            {{ delta(t.disciplineRate, p.disciplineRate) }} {{ 'rep.prevMonth' | t }}</small> }</div>
        <div class="stat big ok"><span>{{ 'rate' | t }}</span><strong class="ltr">{{ t.attendanceRate }}%</strong>
          @if (prev(); as p) { <small>{{ delta(t.attendanceRate, p.attendanceRate) }}</small> }</div>
        <div class="stat big warn"><span>{{ 'status.LATE' | t }}</span><strong>{{ t.late }}</strong>
          @if (prev(); as p) { <small>{{ 'prev' | t }}: {{ p.late }}</small> }</div>
        <div class="stat big bad"><span>{{ 'status.ABSENT' | t }}</span><strong>{{ t.absent }}</strong>
          <small>{{ 'rep.sessions' | t }}: {{ t.sessions }}</small></div>
      </div>
    }

    @if (mode() === 'monthly' && monthly(); as m) {
      @if (!m.soldiers.length) { <app-empty icon="report" [text]="'noData' | t"></app-empty> }
      @else {
        <div class="card table-wrap">
          <table class="table">
            <thead><tr><th>{{ 'militaryNumber' | t }}</th><th>{{ 'name' | t }}</th><th>{{ 'platoon' | t }}</th><th class="num">{{ 'rep.sessions' | t }}</th>
              <th class="num">{{ 'status.PRESENT' | t }}</th><th class="num">{{ 'status.LATE' | t }}</th><th class="num">{{ 'status.ABSENT' | t }}</th>
              <th class="num">{{ 'status.EXCUSED' | t }}</th><th>{{ 'rate' | t }}</th><th>{{ 'rep.mainMethod' | t }}</th></tr></thead>
            <tbody>
              @for (s of m.soldiers; track s.soldierId) {
                <tr><td class="ltr-cell">{{ s.militaryNumber }}</td><td><strong>{{ s.fullName }}</strong> <small class="muted">{{ s.militaryRank }}</small></td>
                  <td>{{ s.platoonName }}</td><td class="num">{{ s.sessions }}</td><td class="num">{{ s.present }}</td><td class="num">{{ s.late }}</td>
                  <td class="num">{{ s.absent }}</td><td class="num">{{ s.excused }}</td>
                  <td class="bar-cell"><div class="progress small" [class.low]="s.attendanceRate < 80"><i [style.width.%]="s.attendanceRate"></i></div><span class="ltr">{{ s.attendanceRate }}%</span></td>
                  <td>{{ s.mainMethod ? (s.mainMethod | t:'method') : '—' }}</td></tr>
              }
            </tbody>
          </table>
        </div>
      }
    }

    @if (mode() === 'daily' && daily(); as d) {
      @if (!d.sessions.length) { <app-empty icon="report" [text]="'noData' | t"></app-empty> }
      @else {
        <div class="card table-wrap">
          <table class="table">
            <thead><tr><th>{{ 'platoon' | t }}</th><th>{{ 'sess.type' | t }}</th><th>{{ 'status' | t }}</th><th class="num">{{ 'total' | t }}</th>
              <th class="num">{{ 'status.PRESENT' | t }}</th><th class="num">{{ 'status.LATE' | t }}</th><th class="num">{{ 'status.ABSENT' | t }}</th><th class="num">{{ 'rate' | t }}</th></tr></thead>
            <tbody>@for (s of d.sessions; track s.id) {
              <tr><td>{{ s.platoonName }}</td><td>{{ s.sessionType | t:'type' }}</td><td><app-session-status [status]="s.status"></app-session-status></td>
                <td class="num">{{ s.counts.total }}</td><td class="num">{{ s.counts.present }}</td><td class="num">{{ s.counts.late }}</td>
                <td class="num">{{ s.counts.absent }}</td><td class="num">{{ s.counts.attendanceRate }}%</td></tr>
            }</tbody>
          </table>
        </div>
        <h2 class="section-title">{{ 'rep.exceptions' | t }}</h2>
        <div class="card table-wrap">
          <table class="table"><thead><tr><th>{{ 'name' | t }}</th><th>{{ 'militaryNumber' | t }}</th><th>{{ 'status' | t }}</th><th>{{ 'note' | t }}</th></tr></thead>
            <tbody>@for (a of d.exceptions; track $index) {
              <tr><td>{{ a.fullName }}</td><td class="ltr-cell">{{ a.militaryNumber }}</td><td><app-status [status]="a.status"></app-status></td><td>{{ a.note || '—' }}</td></tr>
            }</tbody></table>
        </div>
      }
    }
  </section>`,
})
export class ReportsPage implements OnInit {
  private http = inject(HttpClient);
  private auth = inject(AuthService);
  private toast = inject(ToastService);
  mode = signal<'monthly' | 'daily'>('monthly');
  platoons = signal<Platoon[]>([]);
  monthly = signal<MonthlyReport | null>(null);
  daily = signal<DailyReport | null>(null);
  loading = signal(false);
  exporting = signal(false);
  error = signal<string | null>(null);
  month = thisMonth();
  date = todayIso();
  platoonId: number | null = this.auth.user()?.platoonId ?? null;
  totals = computed<Totals | null>(() => this.mode() === 'monthly' ? this.monthly()?.totals ?? null : this.daily()?.totals ?? null);
  prev = computed<Totals | null>(() => this.mode() === 'monthly' ? this.monthly()?.previousMonth ?? null : null);

  ngOnInit() {
    this.http.get<Platoon[]>('/api/platoons').subscribe(p => this.platoons.set(p));
    this.load();
  }

  private params(): Record<string, string | number> {
    const p: Record<string, string | number> = this.mode() === 'monthly' ? { month: this.month } : { date: this.date };
    if (this.platoonId) p['platoonId'] = this.platoonId;
    return p;
  }

  load() {
    this.loading.set(true);
    this.error.set(null);
    const url = `/api/reports/${this.mode()}`;
    this.http.get<MonthlyReport & DailyReport>(url, { params: this.params() }).subscribe({
      next: r => { if (this.mode() === 'monthly') this.monthly.set(r); else this.daily.set(r); this.loading.set(false); },
      error: e => { this.error.set(errorMessage(e)); this.loading.set(false); },
    });
  }

  export(format: 'pdf' | 'xlsx') {
    this.exporting.set(true);
    const q = new URLSearchParams({ ...Object.fromEntries(Object.entries(this.params()).map(([k, v]) => [k, String(v)])), format });
    downloadFile(this.http, `/api/reports/${this.mode()}/export?${q}`, `report.${format}`).subscribe({
      next: () => this.exporting.set(false),
      error: e => { this.exporting.set(false); this.toast.error(errorMessage(e)); },
    });
  }

  delta(a: number, b: number): string {
    const d = Math.round((a - b) * 10) / 10;
    return `${d >= 0 ? '▲' : '▼'} ${Math.abs(d)}%`;
  }
}
