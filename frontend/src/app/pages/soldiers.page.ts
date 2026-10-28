import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Subject, debounceTime } from 'rxjs';
import { AuthService } from '../core/auth.service';
import { TPipe } from '../core/i18n';
import { Page, Platoon, Soldier } from '../core/models';
import { errorMessage } from '../core/errors';
import { AvatarComponent } from '../shared/avatar.component';
import { IconComponent } from '../shared/icon.component';
import { EmptyStateComponent, PagerComponent, SpinnerComponent } from '../shared/ui';

@Component({
  standalone: true,
  imports: [FormsModule, RouterLink, TPipe, AvatarComponent, IconComponent, EmptyStateComponent, PagerComponent, SpinnerComponent],
  template: `
  <section class="page">
    <header class="page-head">
      <h1>{{ 'nav.soldiers' | t }} <small class="muted">({{ page()?.totalItems ?? 0 }})</small></h1>
      @if (canManage) { <a class="btn btn-primary" routerLink="/soldiers/new"><app-icon name="plus" [size]="18"></app-icon>{{ 'sol.new' | t }}</a> }
    </header>
    <div class="filters card">
      <label class="search grow"><app-icon name="search" [size]="18"></app-icon>
        <input [(ngModel)]="q" (ngModelChange)="search$.next()" [placeholder]="'sol.searchPh' | t"></label>
      @if (platoons().length > 1) {
        <label class="field"><span>{{ 'platoon' | t }}</span>
          <select [(ngModel)]="platoonId" (ngModelChange)="load(0)"><option [ngValue]="null">{{ 'all' | t }}</option>
            @for (p of platoons(); track p.id) { <option [ngValue]="p.id">{{ p.name }}</option> }</select></label>
      }
      <label class="field"><span>{{ 'status' | t }}</span>
        <select [(ngModel)]="active" (ngModelChange)="load(0)">
          <option [ngValue]="true">{{ 'active' | t }}</option><option [ngValue]="false">{{ 'inactive' | t }}</option><option [ngValue]="null">{{ 'all' | t }}</option></select></label>
    </div>
    @if (loading()) { <app-spinner [label]="'loading' | t"></app-spinner> }
    @else if (error()) { <div class="alert alert-error">{{ error() }}</div> }
    @else if (!page()?.items?.length) { <app-empty icon="soldier" [text]="'sol.empty' | t"></app-empty> }
    @else {
      <div class="card table-wrap">
        <table class="table">
          <thead><tr>
            <th><button class="th-sort" (click)="sortBy('militaryNumber')">{{ 'militaryNumber' | t }} {{ arrow('militaryNumber') }}</button></th>
            <th><button class="th-sort" (click)="sortBy('fullName')">{{ 'name' | t }} {{ arrow('fullName') }}</button></th>
            <th><button class="th-sort" (click)="sortBy('militaryRank')">{{ 'rank' | t }} {{ arrow('militaryRank') }}</button></th>
            <th>{{ 'platoon' | t }}</th><th>{{ 'sol.faces' | t }}</th><th>{{ 'status' | t }}</th></tr></thead>
          <tbody>
            @for (s of page()!.items; track s.id) {
              <tr class="clickable" [routerLink]="['/soldiers', s.id]">
                <td class="ltr-cell">{{ s.militaryNumber }}</td>
                <td><span class="who-cell"><app-avatar kind="soldiers" [id]="s.id" [version]="s.photoVersion" [name]="s.fullName" [size]="34"></app-avatar><strong>{{ s.fullName }}</strong></span></td><td>{{ s.militaryRank }}</td>
                <td>{{ s.platoonName }}</td>
                <td><span class="badge" [class.s-PRESENT]="s.enrolledFaces >= 3" [class.s-LATE]="s.enrolledFaces > 0 && s.enrolledFaces < 3" [class.s-NONE]="!s.enrolledFaces">
                  <app-icon name="face" [size]="14"></app-icon> {{ s.enrolledFaces }}</span></td>
                <td>{{ (s.active ? 'active' : 'inactive') | t }}</td>
              </tr>
            }
          </tbody>
        </table>
      </div>
      <app-pager [page]="page()!.page" [totalPages]="page()!.totalPages" (go)="load($event)"></app-pager>
    }
  </section>`,
})
export class SoldiersPage implements OnInit {
  private http = inject(HttpClient);
  private auth = inject(AuthService);
  canManage = this.auth.hasRole('PLATOON_LEADER', 'COMPANY_AGENT');
  platoons = signal<Platoon[]>([]);
  page = signal<Page<Soldier> | null>(null);
  loading = signal(true);
  error = signal<string | null>(null);
  q = '';
  platoonId: number | null = null;
  active: boolean | null = true;
  sort = 'fullName';
  dir: 'asc' | 'desc' = 'asc';
  search$ = new Subject<void>();

  constructor() { this.search$.pipe(debounceTime(300)).subscribe(() => this.load(0)); }

  ngOnInit() {
    this.http.get<Platoon[]>('/api/platoons').subscribe(p => this.platoons.set(p));
    this.load(0);
  }

  sortBy(f: string) {
    this.dir = this.sort === f && this.dir === 'asc' ? 'desc' : 'asc';
    this.sort = f;
    this.load(0);
  }

  arrow(f: string) { return this.sort === f ? (this.dir === 'asc' ? '▲' : '▼') : ''; }

  load(p: number) {
    this.loading.set(true);
    const params: Record<string, string | number | boolean> = { page: p, size: 25, sort: this.sort, dir: this.dir };
    if (this.q.trim()) params['q'] = this.q.trim();
    if (this.platoonId) params['platoonId'] = this.platoonId;
    if (this.active !== null) params['active'] = this.active;
    this.http.get<Page<Soldier>>('/api/soldiers', { params }).subscribe({
      next: r => { this.page.set(r); this.loading.set(false); this.error.set(null); },
      error: e => { this.error.set(errorMessage(e)); this.loading.set(false); },
    });
  }
}
