import { TestBed } from '@angular/core/testing';
import { HttpClient, HttpErrorResponse, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from './auth.service';
import { authInterceptor } from './auth.interceptor';
import { errorCode, errorMessage } from './errors';
import { I18n, TPipe } from './i18n';
import { TokenResponse } from './models';

const tokenResponse = (role: TokenResponse['user']['role'], token = 'tok-1'): TokenResponse => ({
  accessToken: token, expiresInSeconds: 1800,
  user: { id: 1, username: 'senior1', fullName: 'سنير', role, platoonId: 1, enabled: true, mustChangePassword: false, locked: false, createdAt: '' },
});

describe('errors', () => {
  it('uses the server message and the first field error', () => {
    const e = new HttpErrorResponse({ status: 400, error: { code: 'VALIDATION_FAILED', message: 'البيانات غير صحيحة', fieldErrors: { fullName: 'الاسم مطلوب' } } });
    expect(errorMessage(e)).toBe('البيانات غير صحيحة: الاسم مطلوب');
    expect(errorCode(e)).toBe('VALIDATION_FAILED');
  });
  it('explains network failures', () => {
    expect(errorMessage(new HttpErrorResponse({ status: 0 }))).toContain('تعذّر الاتصال');
  });
});

describe('I18n', () => {
  beforeEach(() => localStorage.removeItem('lang'));
  it('defaults to Arabic RTL and toggles to English LTR', () => {
    const i18n = TestBed.inject(I18n);
    expect(i18n.lang()).toBe('ar');
    expect(document.documentElement.dir).toBe('rtl');
    expect(i18n.t('status.PRESENT')).toBe('حاضر');
    i18n.toggle();
    expect(document.documentElement.dir).toBe('ltr');
    expect(new TPipe(i18n).transform('ABSENT', 'status')).toBe('Absent');
    i18n.toggle();
  });
});

describe('AuthService + interceptor', () => {
  let http: HttpClient;
  let ctrl: HttpTestingController;
  let auth: AuthService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpClient);
    ctrl = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
  });
  afterEach(() => ctrl.verify());

  it('keeps the token in memory and attaches it as a bearer header', () => {
    auth.login('senior1', 'pw').subscribe();
    const login = ctrl.expectOne('/api/auth/login');
    expect(login.request.headers.has('Authorization')).toBeFalse();
    expect(login.request.withCredentials).toBeTrue();
    login.flush(tokenResponse('SENIOR'));
    expect(auth.hasRole('SENIOR')).toBeTrue();
    expect(auth.hasRole('COMPANY_AGENT')).toBeFalse();
    expect(localStorage.getItem('token')).toBeNull();

    http.get('/api/sessions').subscribe();
    expect(ctrl.expectOne('/api/sessions').request.headers.get('Authorization')).toBe('Bearer tok-1');
  });

  it('refreshes once on 401 and retries the original request', () => {
    auth.login('senior1', 'pw').subscribe();
    ctrl.expectOne('/api/auth/login').flush(tokenResponse('SENIOR'));

    let body: unknown;
    http.get('/api/platoons').subscribe(b => (body = b));
    ctrl.expectOne('/api/platoons').flush({ code: 'UNAUTHORIZED' }, { status: 401, statusText: 'Unauthorized' });
    ctrl.expectOne('/api/auth/refresh').flush(tokenResponse('SENIOR', 'tok-2'));
    const retry = ctrl.expectOne('/api/platoons');
    expect(retry.request.headers.get('Authorization')).toBe('Bearer tok-2');
    retry.flush([{ id: 1 }]);
    expect(body).toEqual([{ id: 1 }]);
  });

  it('signs out when the refresh fails', () => {
    auth.login('senior1', 'pw').subscribe();
    ctrl.expectOne('/api/auth/login').flush(tokenResponse('SENIOR'));
    http.get('/api/platoons').subscribe({ error: () => {} });
    ctrl.expectOne('/api/platoons').flush({}, { status: 401, statusText: 'Unauthorized' });
    ctrl.expectOne('/api/auth/refresh').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(auth.isLoggedIn()).toBeFalse();
  });
});
