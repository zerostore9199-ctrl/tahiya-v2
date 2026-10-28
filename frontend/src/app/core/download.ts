import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable, tap } from 'rxjs';

/** Downloads an authenticated binary response (reports) as a file. */
export function downloadFile(http: HttpClient, url: string, fallbackName: string): Observable<HttpResponse<Blob>> {
  return http.get(url, { responseType: 'blob', observe: 'response' }).pipe(tap(res => {
    const cd = res.headers.get('Content-Disposition') || '';
    const m = /filename\*=UTF-8''([^;]+)|filename="?([^";]+)"?/i.exec(cd);
    const name = m ? decodeURIComponent(m[1] || m[2]) : fallbackName;
    const blobUrl = URL.createObjectURL(res.body!);
    const a = document.createElement('a');
    a.href = blobUrl;
    a.download = name;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(blobUrl), 1000);
  }));
}
