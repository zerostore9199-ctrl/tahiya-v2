import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { TPipe } from '../core/i18n';
import { DtPipe } from '../core/format';
import { AuditView, Page } from '../core/models';
import { errorMessage } from '../core/errors';
import { EmptyStateComponent, PagerComponent, SpinnerComponent } from '../shared/ui';

const ACTIONS = ['LOGIN_SUCCESS', 'LOGIN_FAILED', 'SESSION_OPENED', 'FACE_RECOGNITION', 'MANUAL_MARK', 'SESSION_CLOSED',
  'RECORD_REVIEWED', 'SESSION_APPROVED', 'SOLDIER_CREATED', 'SOLDIER_UPDATED', 'SOLDIER_DEACTIVATED', 'FACE_ENROLLED',
  'FACE_ENROLL_REJECTED', 'BIOMETRIC_ERASED', 'USER_CREATED', 'USER_UPDATED', 'USER_PASSWORD_RESET', 'PASSWORD_CHANGED',
  'REPORT_EXPORTED', 'SETTINGS_UPDATED', 'REFRESH_TOKEN_REUSE'];

@Component({
  standalone: true,
  imports: [FormsModule, TPipe, DtPipe, EmptyStateComponent, PagerComponent, SpinnerComponent],
  template: `
  <section class="page wide">
    <header class="page-head"><h1>{{ 'nav.audit' | t }}</h1></header>
    <div class="filters card">
      <label class="field"><span>{{ 'aud.action' | t }}</span>
        <select [(ngModel)]="action" (ngModelChange)="load(0)"><option [ngValue]="null">{{ 'all' | t }}</option>
          @for (a of actions; track a) { <option [ngValue]="a">{{ a }}</option> }</select></label>
      <label class="field"><span>{{ 'aud.user' | t }}</span><input [(ngModel)]="username" (change)="load(0)" dir="ltr"></label>
      <label class="field"><span>{{ 'date' | t }} ←</span><input type="date" [(ngModel)]="from" (change)="load(0)" dir="ltr"></label>
      <label class="field"><span>→</span><input type="date" [(ngModel)]="to" (change)="load(0)" dir="ltr"></label>
    </div>
    @if (loading()) { <app-spinner [label]="'loading' | t"></app-spinner> }
    @else if (error()) { <div class="alert alert-error">{{ error() }}</div> }
    @else if (!page()?.items?.length) { <app-empty icon="audit" [text]="'noData' | t"></app-empty> }
    @else {
      <div class="card table-wrap">
        <table class="table dense">
          <thead><tr><th>{{ 'aud.time' | t }}</th><th>{{ 'aud.user' | t }}</th><th>{{ 'aud.action' | t }}</th><th>{{ 'aud.details' | t }}</th><th>IP</th></tr></thead>
          <tbody>@for (a of page()!.items; track a.id) {
            <tr [class.row-fail]="!a.success"><td>{{ a.occurredAt | dt }}</td><td class="ltr-cell">{{ a.username || '—' }}</td>
              <td><code>{{ a.action }}</code></td><td class="details ltr-cell">{{ a.entityType }} {{ a.entityId }} {{ a.details }}</td>
              <td class="ltr-cell muted">{{ a.ipAddress }}</td></tr>
          }</tbody>
        </table>
      </div>
      <app-pager [page]="page()!.page" [totalPages]="page()!.totalPages" (go)="load($event)"></app-pager>
    }
  </section>`,
})
export class AuditPage implements OnInit {
  private http = inject(HttpClient);
  actions = ACTIONS;
  page = signal<Page<AuditView> | null>(null);
  loading = signal(true);
  error = signal<string | null>(null);
  action: string | null = null;
  username = '';
  from = new Date(Date.now() - 7 * 86400000).toISOString().slice(0, 10);
  to = new Date().toISOString().slice(0, 10);

  ngOnInit() { this.load(0); }

  load(p: number) {
    this.loading.set(true);
    const params: Record<string, string | number> = { page: p, size: 50,
      from: new Date(this.from + 'T00:00:00+04:00').toISOString(), to: new Date(this.to + 'T23:59:59+04:00').toISOString() };
    if (this.action) params['action'] = this.action;
    if (this.username.trim()) params['username'] = this.username.trim();
    this.http.get<Page<AuditView>>('/api/audit', { params }).subscribe({
      next: r => { this.page.set(r); this.loading.set(false); this.error.set(null); },
      error: e => { this.error.set(errorMessage(e)); this.loading.set(false); },
    });
  }
}
