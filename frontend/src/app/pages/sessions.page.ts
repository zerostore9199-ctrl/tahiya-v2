import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { TPipe } from '../core/i18n';
import { DtPipe, todayIso } from '../core/format';
import { Page, Platoon, SessionStatus, SessionSummary } from '../core/models';
import { errorMessage } from '../core/errors';
import { IconComponent } from '../shared/icon.component';
import { EmptyStateComponent, PagerComponent, SessionStatusComponent, SpinnerComponent } from '../shared/ui';

@Component({
  standalone: true,
  imports: [FormsModule, RouterLink, TPipe, DtPipe, IconComponent, EmptyStateComponent, PagerComponent, SessionStatusComponent, SpinnerComponent],
  template: `
  <section class="page">
    <header class="page-head">
      <h1>{{ 'nav.sessions' | t }}</h1>
      @if (canTake) { <a class="btn btn-primary" routerLink="/sessions/new"><app-icon name="plus" [size]="18"></app-icon>{{ 'sess.new' | t }}</a> }
    </header>
    <div class="filters card">
      @if (platoons().length > 1) {
        <label class="field"><span>{{ 'platoon' | t }}</span>
          <select [(ngModel)]="platoonId" (ngModelChange)="reload()"><option [ngValue]="null">{{ 'all' | t }}</option>
            @for (p of platoons(); track p.id) { <option [ngValue]="p.id">{{ p.name }}</option> }</select></label>
      }
      <label class="field"><span>{{ 'status' | t }}</span>
        <select [(ngModel)]="status" (ngModelChange)="reload()"><option [ngValue]="null">{{ 'all' | t }}</option>
          @for (s of statuses; track s) { <option [ngValue]="s">{{ s | t:'session' }}</option> }</select></label>
      <label class="field"><span>{{ 'date' | t }} ←</span><input type="date" [(ngModel)]="from" (change)="reload()" dir="ltr"></label>
      <label class="field"><span>→</span><input type="date" [(ngModel)]="to" (change)="reload()" dir="ltr"></label>
    </div>

    @if (loading()) { <app-spinner [label]="'loading' | t"></app-spinner> }
    @else if (error()) { <div class="alert alert-error">{{ error() }}</div> }
    @else if (!page()?.items?.length) { <app-empty [text]="'noData' | t"></app-empty> }
    @else {
      <div class="card table-wrap">
        <table class="table">
          <thead><tr><th>{{ 'date' | t }}</th><th>{{ 'platoon' | t }}</th><th>{{ 'sess.type' | t }}</th><th>{{ 'sess.openedBy' | t }}</th>
            <th class="num">{{ 'status.PRESENT' | t }}</th><th class="num">{{ 'status.LATE' | t }}</th><th class="num">{{ 'status.ABSENT' | t }}</th>
            <th class="num">{{ 'rate' | t }}</th><th>{{ 'status' | t }}</th></tr></thead>
          <tbody>
            @for (s of page()!.items; track s.id) {
              <tr class="clickable" [routerLink]="['/sessions', s.id]">
                <td>{{ s.openedAt | dt }}</td><td>{{ s.platoonName }}</td><td>{{ s.sessionType | t:'type' }}</td>
                <td>{{ s.openedBy }}</td><td class="num">{{ s.counts.present }}</td><td class="num">{{ s.counts.late }}</td>
                <td class="num">{{ s.counts.absent }}</td><td class="num">{{ s.counts.attendanceRate }}%</td>
                <td><app-session-status [status]="s.status"></app-session-status></td>
              </tr>
            }
          </tbody>
        </table>
      </div>
      <app-pager [page]="page()!.page" [totalPages]="page()!.totalPages" (go)="load($event)"></app-pager>
    }
  </section>`,
})
export class SessionsPage implements OnInit {
  private http = inject(HttpClient);
  private auth = inject(AuthService);
  canTake = this.auth.hasRole('SENIOR', 'PLATOON_LEADER', 'COMPANY_AGENT');
  statuses: SessionStatus[] = ['OPEN', 'CLOSED', 'APPROVED'];
  platoons = signal<Platoon[]>([]);
  page = signal<Page<SessionSummary> | null>(null);
  loading = signal(true);
  error = signal<string | null>(null);
  platoonId: number | null = null;
  status: SessionStatus | null = null;
  to = todayIso();
  from = new Date(Date.now() - 14 * 86400000).toISOString().slice(0, 10);

  ngOnInit() {
    this.http.get<Platoon[]>('/api/platoons').subscribe(p => this.platoons.set(p));
    this.load(0);
  }

  reload() { this.load(0); }

  load(pageNo: number) {
    this.loading.set(true);
    const params: Record<string, string | number> = { page: pageNo, size: 20, from: this.from, to: this.to };
    if (this.platoonId) params['platoonId'] = this.platoonId;
    if (this.status) params['status'] = this.status;
    this.http.get<Page<SessionSummary>>('/api/sessions', { params }).subscribe({
      next: p => { this.page.set(p); this.loading.set(false); this.error.set(null); },
      error: e => { this.error.set(errorMessage(e)); this.loading.set(false); },
    });
  }
}
