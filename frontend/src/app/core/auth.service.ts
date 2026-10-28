import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, firstValueFrom, tap, finalize, shareReplay, map } from 'rxjs';
import { Role, TokenResponse, UserView } from './models';

/**
 * Holds the access token in memory only (never localStorage). The refresh token is an
 * HttpOnly cookie the browser sends to /api/auth/refresh, so a page reload restores the
 * session silently without exposing any token to JavaScript.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private router = inject(Router);

  private token: string | null = null;
  private refreshing$: Observable<string> | null = null;

  readonly user = signal<UserView | null>(null);
  readonly role = computed(() => this.user()?.role ?? null);
  readonly isLoggedIn = computed(() => this.user() !== null);

  accessToken(): string | null { return this.token; }

  hasRole(...roles: Role[]): boolean {
    const r = this.role();
    return r !== null && roles.includes(r);
  }

  login(username: string, password: string): Observable<UserView> {
    return this.http.post<TokenResponse>('/api/auth/login', { username, password }, { withCredentials: true })
      .pipe(tap(r => this.accept(r)), map(r => r.user));
  }

  changePassword(currentPassword: string, newPassword: string): Observable<UserView> {
    return this.http.post<TokenResponse>('/api/auth/change-password', { currentPassword, newPassword },
      { withCredentials: true }).pipe(tap(r => this.accept(r)), map(r => r.user));
  }

  /** Single-flight refresh: concurrent 401s share one refresh call. */
  refresh(): Observable<string> {
    if (!this.refreshing$) {
      this.refreshing$ = this.http.post<TokenResponse>('/api/auth/refresh', {}, { withCredentials: true }).pipe(
        tap(r => this.accept(r)),
        map(r => r.accessToken),
        finalize(() => (this.refreshing$ = null)),
        shareReplay(1),
      );
    }
    return this.refreshing$;
  }

  /** Called once at startup to restore a session from the refresh cookie. */
  async restore(): Promise<void> {
    try { await firstValueFrom(this.refresh()); } catch { this.clear(); }
  }

  logout(): void {
    this.http.post('/api/auth/logout', {}, { withCredentials: true }).subscribe({ error: () => {} });
    this.clear();
    this.router.navigateByUrl('/login');
  }

  /** Replace the signed-in user's profile (e.g. after changing the photo). */
  updateUser(u: UserView): void { this.user.set(u); }

  clear(): void {
    this.token = null;
    this.user.set(null);
  }

  private accept(r: TokenResponse): void {
    this.token = r.accessToken;
    this.user.set(r.user);
  }
}
