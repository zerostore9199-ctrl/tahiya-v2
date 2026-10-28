import { HttpErrorResponse } from '@angular/common/http';
import { ApiError } from './models';

/** Extracts a user-facing message from an API error (the server sends Arabic messages). */
export function errorMessage(e: unknown, fallback = 'حدث خطأ غير متوقع'): string {
  if (e instanceof HttpErrorResponse) {
    if (e.status === 0) return 'تعذّر الاتصال بالخادم، تحقق من الشبكة';
    const body = e.error as Partial<ApiError> | null;
    if (body && typeof body === 'object' && body.message) {
      if (body.fieldErrors) {
        const first = Object.values(body.fieldErrors)[0];
        if (first) return `${body.message}: ${first}`;
      }
      return body.message;
    }
  }
  return fallback;
}

export function errorCode(e: unknown): string | null {
  if (e instanceof HttpErrorResponse && e.error && typeof e.error === 'object') {
    return (e.error as ApiError).code ?? null;
  }
  return null;
}
