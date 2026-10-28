import { Component, Injectable, Input, OnChanges, OnDestroy, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, Subscription, finalize, map, of, shareReplay } from 'rxjs';

/**
 * Photos need the bearer token, so they are fetched with HttpClient (not <img src>) and shown through
 * object URLs. The version number is part of the URL, so a replaced photo is fetched again automatically,
 * and the browser HTTP cache serves unchanged photos without another round trip.
 */
@Injectable({ providedIn: 'root' })
export class PhotoCache {
  private http = inject(HttpClient);
  private done = new Map<string, string>();
  private inflight = new Map<string, Observable<string>>();
  private readonly max = 400;

  get(url: string): Observable<string> {
    const hit = this.done.get(url);
    if (hit) return of(hit);
    let req = this.inflight.get(url);
    if (!req) {
      req = this.http.get(url, { responseType: 'blob' }).pipe(
        map(b => { const o = URL.createObjectURL(b); this.remember(url, o); return o; }),
        finalize(() => this.inflight.delete(url)),
        shareReplay(1),
      );
      this.inflight.set(url, req);
    }
    return req;
  }

  private remember(url: string, objectUrl: string) {
    this.done.set(url, objectUrl);
    if (this.done.size > this.max) {
      const oldest = this.done.keys().next().value as string;
      URL.revokeObjectURL(this.done.get(oldest)!);
      this.done.delete(oldest);
    }
  }
}

/** Round profile picture; falls back to the initials of the name when there is no photo. */
@Component({
  selector: 'app-avatar',
  standalone: true,
  template: `
  <span class="avatar" [style.width.px]="size" [style.height.px]="size" [style.fontSize.px]="size * 0.38" aria-hidden="true">
    @if (src(); as s) { <img [src]="s" alt="" [attr.width]="size" [attr.height]="size" loading="lazy"> } @else { {{ initials() }} }
  </span>`,
})
export class AvatarComponent implements OnChanges, OnDestroy {
  @Input() kind: 'soldiers' | 'users' = 'soldiers';
  @Input() id?: number | null;
  /** photoVersion from the API; empty means "no photo". */
  @Input() version?: number | null;
  @Input() name = '';
  @Input() size = 36;

  private cache = inject(PhotoCache);
  private sub?: Subscription;
  src = signal<string | null>(null);

  ngOnChanges() {
    this.sub?.unsubscribe();
    if (!this.id || !this.version) { this.src.set(null); return; }
    this.sub = this.cache.get(`/api/${this.kind}/${this.id}/photo?v=${this.version}`)
      .subscribe({ next: u => this.src.set(u), error: () => this.src.set(null) });
  }

  initials(): string {
    const parts = (this.name || '').trim().split(/\s+/).filter(Boolean);
    return parts.slice(0, 2).map(p => Array.from(p)[0]).join('');
  }

  ngOnDestroy() { this.sub?.unsubscribe(); }
}
