import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ToastService } from './core/toast.service';
import { ConfirmDialogComponent } from './shared/ui';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, ConfirmDialogComponent],
  template: `
    <router-outlet></router-outlet>
    <app-confirm></app-confirm>
    <div class="toasts" aria-live="polite">
      @for (t of toast.toasts(); track t.id) {
        <div class="toast" [class]="'toast toast-' + t.kind" (click)="toast.dismiss(t.id)">{{ t.text }}</div>
      }
    </div>`,
})
export class AppComponent { toast = inject(ToastService); }
