import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { TPipe } from '../core/i18n';
import { todayIso } from '../core/format';
import { Platoon, SESSION_TYPES, SessionSummary, SessionType } from '../core/models';
import { errorCode, errorMessage } from '../core/errors';

@Component({
  standalone: true,
  imports: [FormsModule, RouterLink, TPipe],
  template: `
  <section class="page narrow">
    <header class="page-head"><h1>{{ 'sess.new' | t }}</h1></header>
    <form class="card form" (ngSubmit)="submit()">
      <label class="field"><span>{{ 'platoon' | t }}</span>
        <select name="platoon" [(ngModel)]="platoonId" required [disabled]="platoons().length <= 1">
          @for (p of platoons(); track p.id) { <option [ngValue]="p.id">{{ p.name }} ({{ p.activeSoldiers }})</option> }
        </select></label>
      <fieldset class="field"><legend>{{ 'sess.type' | t }}</legend>
        <div class="seg">
          @for (st of types; track st) {
            <label class="seg-item" [class.on]="type === st"><input type="radio" name="type" [value]="st" [(ngModel)]="type">{{ st | t:'type' }}</label>
          }
        </div></fieldset>
      <label class="field"><span>{{ 'sess.title' | t }}</span><input name="title" [(ngModel)]="title" maxlength="120"></label>
      <label class="field"><span>{{ 'sess.scheduled' | t }}</span><input type="time" name="time" [(ngModel)]="time" dir="ltr"></label>
      @if (error()) { <div class="alert alert-error">{{ error() }}
        @if (openId()) { <a [routerLink]="['/sessions', openId()]">{{ 'home.continue' | t }}</a> }</div> }
      <div class="form-actions">
        <a class="btn btn-ghost" routerLink="/sessions">{{ 'cancel' | t }}</a>
        <button class="btn btn-primary btn-lg" [disabled]="busy() || !platoonId">{{ 'sess.open' | t }}</button>
      </div>
    </form>
  </section>`,
})
export class NewSessionPage implements OnInit {
  private http = inject(HttpClient);
  private router = inject(Router);
  private auth = inject(AuthService);
  types = SESSION_TYPES;
  platoons = signal<Platoon[]>([]);
  platoonId: number | null = null;
  type: SessionType = 'MORNING_PARADE';
  title = '';
  time = '';
  busy = signal(false);
  error = signal<string | null>(null);
  openId = signal<number | null>(null);

  ngOnInit() {
    const h = Number(new Intl.DateTimeFormat('en-GB', { hour: 'numeric', hour12: false, timeZone: 'Asia/Muscat' }).format(new Date()));
    this.type = h < 11 ? 'MORNING_PARADE' : h >= 17 ? 'EVENING_PARADE' : 'TRAINING';
    this.http.get<Platoon[]>('/api/platoons').subscribe({
      next: ps => {
        const list = ps.filter(p => p.active);
        this.platoons.set(list);
        this.platoonId = this.auth.user()?.platoonId ?? list[0]?.id ?? null;
      },
      error: e => this.error.set(errorMessage(e)),
    });
  }

  submit() {
    if (!this.platoonId) return;
    this.busy.set(true);
    this.error.set(null);
    // The time is interpreted in the unit's zone (Asia/Muscat, UTC+4, no DST).
    const scheduledAt = this.time ? new Date(`${todayIso()}T${this.time}:00+04:00`).toISOString() : undefined;
    this.http.post<SessionSummary>('/api/sessions', { platoonId: this.platoonId, sessionType: this.type,
      title: this.title || undefined, scheduledAt }).subscribe({
      next: s => this.router.navigate(['/sessions', s.id]),
      error: e => {
        this.busy.set(false);
        this.error.set(errorMessage(e));
        if (errorCode(e) === 'SESSION_ALREADY_OPEN') {
          this.http.get<SessionSummary>('/api/sessions/open', { params: { platoonId: this.platoonId! } })
            .subscribe(s => this.openId.set(s?.id ?? null));
        }
      },
    });
  }
}
