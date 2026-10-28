import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../core/auth.service';
import { I18n, TPipe } from '../core/i18n';
import { Platoon } from '../core/models';
import { errorMessage } from '../core/errors';
import { ToastService } from '../core/toast.service';
import { IconComponent } from '../shared/icon.component';
import { EmptyStateComponent, SpinnerComponent } from '../shared/ui';

@Component({
  standalone: true,
  imports: [FormsModule, TPipe, IconComponent, EmptyStateComponent, SpinnerComponent],
  template: `
  <section class="page">
    <header class="page-head"><h1>{{ 'nav.platoons' | t }}</h1>
      @if (isAgent) { <button class="btn btn-primary" (click)="edit(null)"><app-icon name="plus" [size]="18"></app-icon>{{ 'plt.new' | t }}</button> }
    </header>
    @if (form(); as m) {
      <form class="card form" (ngSubmit)="save()">
        <div class="form-grid">
          <label class="field"><span>{{ 'plt.code' | t }} *</span><input name="code" [(ngModel)]="m.code" required pattern="[A-Za-z0-9-]{1,20}" dir="ltr"></label>
          <label class="field"><span>{{ 'name' | t }} *</span><input name="name" [(ngModel)]="m.name" required maxlength="100"></label>
          <label class="field"><span>{{ 'plt.company' | t }}</span><input name="company" [(ngModel)]="m.companyName" maxlength="100"></label>
        </div>
        <label class="check"><input type="checkbox" name="active" [(ngModel)]="m.active">{{ 'active' | t }}</label>
        @if (error()) { <div class="alert alert-error">{{ error() }}</div> }
        <div class="form-actions"><button type="button" class="btn btn-ghost" (click)="form.set(null)">{{ 'cancel' | t }}</button>
          <button class="btn btn-primary">{{ 'save' | t }}</button></div>
      </form>
    }
    @if (loading()) { <app-spinner [label]="'loading' | t"></app-spinner> }
    @else if (!items().length) { <app-empty icon="flag" [text]="'noData' | t"></app-empty> }
    <div class="card-grid">
      @for (p of items(); track p.id) {
        <div class="card platoon-card" [class.muted-card]="!p.active">
          <div class="platoon-code ltr">{{ p.code }}</div>
          <h3>{{ p.name }}</h3>
          <p class="muted small">{{ p.companyName }}</p>
          <p><strong>{{ p.activeSoldiers }}</strong> {{ 'plt.soldiers' | t }} · {{ (p.active ? 'active' : 'inactive') | t }}</p>
          @if (isAgent) { <button class="btn btn-ghost btn-sm" (click)="edit(p)"><app-icon name="edit" [size]="16"></app-icon>{{ 'edit' | t }}</button> }
        </div>
      }
    </div>
  </section>`,
})
export class PlatoonsPage implements OnInit {
  private http = inject(HttpClient);
  private toast = inject(ToastService);
  isAgent = inject(AuthService).hasRole('COMPANY_AGENT');
  private saved = '✓ ' + inject(I18n).t('save');
  items = signal<Platoon[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);
  form = signal<{ id?: number; code: string; name: string; companyName: string; active: boolean } | null>(null);

  ngOnInit() { this.load(); }

  load() {
    this.http.get<Platoon[]>('/api/platoons').subscribe({
      next: p => { this.items.set(p); this.loading.set(false); },
      error: e => { this.toast.error(errorMessage(e)); this.loading.set(false); },
    });
  }

  edit(p: Platoon | null) {
    this.error.set(null);
    this.form.set(p ? { id: p.id, code: p.code, name: p.name, companyName: p.companyName, active: p.active }
      : { code: '', name: '', companyName: '', active: true });
  }

  save() {
    const m = this.form()!;
    const req = m.id ? this.http.put(`/api/platoons/${m.id}`, m) : this.http.post('/api/platoons', m);
    req.subscribe({
      next: () => { this.form.set(null); this.toast.success(this.saved); this.load(); },
      error: e => this.error.set(errorMessage(e)),
    });
  }
}
