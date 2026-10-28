import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TPipe } from '../core/i18n';
import { EmptyStateComponent } from '../shared/ui';

@Component({
  standalone: true,
  imports: [RouterLink, TPipe, EmptyStateComponent],
  template: `<app-empty icon="flag" [text]="'nf.title' | t"><a class="btn btn-primary" routerLink="/">{{ 'nav.home' | t }}</a></app-empty>`,
})
export class NotFoundPage {}
