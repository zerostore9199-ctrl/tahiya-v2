import { Component, EventEmitter, Injectable, Input, Output, inject, signal } from '@angular/core';
import { TPipe } from '../core/i18n';
import { IconComponent } from './icon.component';

/** Coloured pill for an attendance status (or "not recorded"). */
@Component({
  selector: 'app-status',
  standalone: true,
  imports: [TPipe],
  template: `<span class="badge" [class]="'badge s-' + (status || 'NONE')">{{ (status || 'NONE') | t:'status' }}</span>`,
})
export class StatusBadgeComponent { @Input() status?: string | null; }

@Component({
  selector: 'app-session-status',
  standalone: true,
  imports: [TPipe],
  template: `<span class="badge" [class]="'badge ss-' + status">{{ status | t:'session' }}</span>`,
})
export class SessionStatusComponent { @Input() status!: string; }

@Component({
  selector: 'app-empty',
  standalone: true,
  imports: [IconComponent],
  template: `<div class="empty"><app-icon [name]="icon" [size]="40"></app-icon><p>{{ text }}</p><ng-content></ng-content></div>`,
})
export class EmptyStateComponent { @Input() text = ''; @Input() icon = 'list'; }

@Component({
  selector: 'app-spinner',
  standalone: true,
  template: `<div class="spinner-wrap" role="status" [attr.aria-label]="label"><span class="spinner"></span><span>{{ label }}</span></div>`,
})
export class SpinnerComponent { @Input() label = ''; }

@Component({
  selector: 'app-pager',
  standalone: true,
  imports: [TPipe],
  template: `@if (totalPages > 1) {
    <nav class="pager">
      <button class="btn btn-ghost btn-sm" [disabled]="page <= 0" (click)="go.emit(page - 1)">{{ 'prev' | t }}</button>
      <span class="ltr">{{ page + 1 }} / {{ totalPages }}</span>
      <button class="btn btn-ghost btn-sm" [disabled]="page >= totalPages - 1" (click)="go.emit(page + 1)">{{ 'next' | t }}</button>
    </nav>}`,
})
export class PagerComponent {
  @Input() page = 0;
  @Input() totalPages = 0;
  @Output() go = new EventEmitter<number>();
}

/** Promise-based confirmation dialog rendered once in the shell. */
@Injectable({ providedIn: 'root' })
export class ConfirmService {
  readonly open = signal<{ text: string; danger: boolean; resolve: (v: boolean) => void } | null>(null);
  ask(text: string, danger = false): Promise<boolean> {
    return new Promise(resolve => this.open.set({ text, danger, resolve }));
  }
  answer(v: boolean) {
    const o = this.open();
    this.open.set(null);
    o?.resolve(v);
  }
}

@Component({
  selector: 'app-confirm',
  standalone: true,
  imports: [TPipe],
  template: `@if (c.open(); as o) {
    <div class="modal-backdrop" (click)="c.answer(false)">
      <div class="modal" role="alertdialog" aria-modal="true" (click)="$event.stopPropagation()">
        <p class="modal-text">{{ o.text }}</p>
        <div class="modal-actions">
          <button class="btn btn-ghost" (click)="c.answer(false)">{{ 'cancel' | t }}</button>
          <button class="btn" [class.btn-danger]="o.danger" [class.btn-primary]="!o.danger" (click)="c.answer(true)">{{ 'confirm' | t }}</button>
        </div>
      </div>
    </div>}`,
})
export class ConfirmDialogComponent { c = inject(ConfirmService); }
