import { Pipe, PipeTransform, inject } from '@angular/core';
import { I18n } from './i18n';

/** Locale-aware date/time formatting in the unit's time zone. */
@Pipe({ name: 'dt', standalone: true, pure: false })
export class DtPipe implements PipeTransform {
  private i18n = inject(I18n);
  transform(v: string | null | undefined, mode: 'date' | 'time' | 'datetime' = 'datetime'): string {
    if (!v) return '—';
    const d = new Date(v.length === 10 ? v + 'T00:00:00' : v);
    if (isNaN(d.getTime())) return v;
    const opts: Intl.DateTimeFormatOptions =
      mode === 'date' ? { year: 'numeric', month: 'short', day: 'numeric' }
        : mode === 'time' ? { hour: '2-digit', minute: '2-digit' }
          : { year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' };
    return new Intl.DateTimeFormat(this.i18n.locale(), { ...opts, timeZone: 'Asia/Muscat',
      numberingSystem: 'latn' }).format(d);
  }
}

export function todayIso(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Muscat' }).format(new Date());
}

export function thisMonth(): string { return todayIso().slice(0, 7); }
