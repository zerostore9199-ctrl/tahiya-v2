import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { AttendanceStatus } from './models';
import { ToastService } from './toast.service';

interface QueuedMark { sessionId: number; soldierId: number; status: AttendanceStatus; note?: string; at: number; }
const KEY = 'mtc.offline.marks';

/**
 * Tracks connectivity and keeps a small queue of manual attendance marks made while
 * offline (e.g. weak Wi-Fi on the parade ground). Marks are replayed in order when the
 * connection returns; the server re-validates each one.
 */
@Injectable({ providedIn: 'root' })
export class NetworkService {
  private http = inject(HttpClient);
  private toast = inject(ToastService);

  readonly online = signal(navigator.onLine);
  readonly queued = signal<number>(this.read().length);

  constructor() {
    window.addEventListener('online', () => { this.online.set(true); this.flush(); });
    window.addEventListener('offline', () => this.online.set(false));
  }

  enqueue(m: Omit<QueuedMark, 'at'>): void {
    const q = this.read().filter(x => !(x.sessionId === m.sessionId && x.soldierId === m.soldierId));
    q.push({ ...m, at: Date.now() });
    this.write(q);
  }

  pendingFor(sessionId: number): QueuedMark[] { return this.read().filter(x => x.sessionId === sessionId); }

  async flush(): Promise<void> {
    const q = this.read();
    if (!q.length || !navigator.onLine) return;
    const failed: QueuedMark[] = [];
    let sent = 0;
    for (const m of q) {
      try {
        await firstValueFrom(this.http.post(`/api/sessions/${m.sessionId}/manual`,
          { soldierId: m.soldierId, status: m.status, note: m.note }));
        sent++;
      } catch (e: any) {
        // Network failures stay queued; business rejections (closed session…) are dropped.
        if (e?.status === 0) failed.push(m);
      }
    }
    this.write(failed);
    if (sent) this.toast.success(`تم إرسال ${sent} من عمليات التحضير المحفوظة`);
  }

  private read(): QueuedMark[] {
    try { return JSON.parse(localStorage.getItem(KEY) || '[]'); } catch { return []; }
  }

  private write(q: QueuedMark[]): void {
    try { localStorage.setItem(KEY, JSON.stringify(q)); } catch { /* storage unavailable */ }
    this.queued.set(q.length);
  }
}
