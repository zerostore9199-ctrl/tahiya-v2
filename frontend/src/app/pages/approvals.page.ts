import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { TPipe } from '../core/i18n';
import { DtPipe } from '../core/format';
import { Page, SessionSummary } from '../core/models';
import { errorMessage } from '../core/errors';
import { EmptyStateComponent, SpinnerComponent } from '../shared/ui';

@Component({
  standalone: true,
  imports: [RouterLink, TPipe, DtPipe, EmptyStateComponent, SpinnerComponent],
  template: `
  <section class="page">
    <header class="page-head"><h1>{{ 'appr.title' | t }}</h1></header>
    @if (loading()) { <app-spinner [label]="'loading' | t"></app-spinner> }
    @else if (error()) { <div class="alert alert-error">{{ error() }}</div> }
    @else if (!items().length) { <app-empty icon="approve" [text]="'appr.none' | t"></app-empty> }
    <div class="list">
      @for (s of items(); track s.id) {
        <a class="list-row" [routerLink]="['/sessions', s.id]">
          <div><strong>{{ s.platoonName }} — {{ s.sessionType | t:'type' }}</strong>
            <small class="muted">{{ s.sessionDate | dt:'date' }} · {{ s.openedBy }}</small></div>
          <div class="row-end">
            <span class="badge s-ABSENT">{{ 'status.ABSENT' | t }} {{ s.counts.absent }}</span>
            <span class="badge s-LATE">{{ 'status.LATE' | t }} {{ s.counts.late }}</span>
          </div>
        </a>
      }
    </div>
  </section>`,
})
export class ApprovalsPage implements OnInit {
  private http = inject(HttpClient);
  items = signal<SessionSummary[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);

  ngOnInit() {
    const from = new Date(Date.now() - 60 * 86400000).toISOString().slice(0, 10);
    this.http.get<Page<SessionSummary>>('/api/sessions', { params: { status: 'CLOSED', from, size: 100 } }).subscribe({
      next: p => { this.items.set(p.items); this.loading.set(false); },
      error: e => { this.error.set(errorMessage(e)); this.loading.set(false); },
    });
  }
}
