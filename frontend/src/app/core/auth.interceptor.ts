import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from './auth.service';
import { errorCode } from './errors';

/** Adds the bearer token, transparently refreshes once on 401, and routes forced password changes. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const isAuthCall = req.url.startsWith('/api/auth/login') || req.url.startsWith('/api/auth/refresh')
    || req.url.startsWith('/api/auth/logout');

  const withToken = (token: string | null) =>
    token && !isAuthCall ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

  return next(withToken(auth.accessToken())).pipe(
    catchError((err: unknown) => {
      if (err instanceof HttpErrorResponse) {
        if (err.status === 401 && !isAuthCall && auth.isLoggedIn()) {
          return auth.refresh().pipe(
            switchMap(t => next(withToken(t))),
            catchError(e2 => {
              auth.clear();
              router.navigateByUrl('/login');
              return throwError(() => e2);
            }),
          );
        }
        if (err.status === 403 && errorCode(err) === 'PASSWORD_CHANGE_REQUIRED') {
          router.navigateByUrl('/change-password');
        }
      }
      return throwError(() => err);
    }),
  );
};
