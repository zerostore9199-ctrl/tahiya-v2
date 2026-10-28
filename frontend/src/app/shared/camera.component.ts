import { Component, ElementRef, EventEmitter, Input, OnDestroy, Output, ViewChild, inject, signal } from '@angular/core';
import { I18n, TPipe } from '../core/i18n';
import { IconComponent } from './icon.component';

/**
 * Live camera capture (getUserMedia) with a file-upload fallback.
 * Emits a JPEG Blob; nothing is stored on the device.
 */
@Component({
  selector: 'app-camera',
  standalone: true,
  imports: [TPipe, IconComponent],
  template: `
  <div class="camera">
    <div class="camera-view" [class.idle]="!streaming()">
      <video #video playsinline muted [hidden]="!streaming()"></video>
      @if (!streaming()) {
        <div class="camera-placeholder">
          <app-icon name="face" [size]="56"></app-icon>
          <p>{{ hint || ('cam.tip' | t) }}</p>
          @if (error()) { <p class="alert alert-error">{{ error() }}</p> }
        </div>
      }
      @if (streaming()) { <div class="frame-corners" aria-hidden="true"><i></i><i></i><i></i><i></i></div> }
    </div>
    <div class="camera-actions">
      @if (!streaming()) {
        <button type="button" class="btn btn-primary btn-lg" (click)="start()" [disabled]="disabled"><app-icon name="camera"></app-icon>{{ 'cam.start' | t }}</button>
      } @else {
        <button type="button" class="btn btn-ghost" (click)="switchCam()" [title]="'cam.switch' | t"><app-icon name="switch"></app-icon></button>
        <button type="button" class="btn btn-primary btn-xl shutter" (click)="capture()" [disabled]="disabled"><app-icon name="camera" [size]="26"></app-icon>{{ 'cam.capture' | t }}</button>
        <button type="button" class="btn btn-ghost" (click)="stop()"><app-icon name="x"></app-icon></button>
      }
      <label class="btn btn-ghost" [class.disabled]="disabled">
        <app-icon name="upload"></app-icon>{{ 'cam.upload' | t }}
        <input type="file" accept="image/jpeg,image/png,image/webp" capture="environment" hidden (change)="onFile($event)" [disabled]="disabled">
      </label>
    </div>
  </div>`,
})
export class CameraComponent implements OnDestroy {
  @ViewChild('video', { static: true }) video!: ElementRef<HTMLVideoElement>;
  @Input() hint = '';
  @Input() disabled = false;
  /** 'environment' for the parade (rear camera), 'user' for selfie-style enrollment. */
  @Input() facing: 'environment' | 'user' = 'environment';
  @Input() maxSide = 2560;
  @Output() captured = new EventEmitter<Blob>();

  private i18n = inject(I18n);
  private stream: MediaStream | null = null;
  streaming = signal(false);
  error = signal<string | null>(null);

  async start() {
    this.error.set(null);
    if (!window.isSecureContext) { this.error.set(this.i18n.t('cam.secure')); return; }
    if (!navigator.mediaDevices?.getUserMedia) { this.error.set(this.i18n.t('cam.denied')); return; }
    try {
      this.stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: this.facing, width: { ideal: 1920 }, height: { ideal: 1080 } }, audio: false,
      });
      const v = this.video.nativeElement;
      v.srcObject = this.stream;
      await v.play();
      this.streaming.set(true);
    } catch {
      this.error.set(this.i18n.t('cam.denied'));
      this.stop();
    }
  }

  async switchCam() {
    this.facing = this.facing === 'environment' ? 'user' : 'environment';
    this.stop();
    await this.start();
  }

  stop() {
    this.stream?.getTracks().forEach(t => t.stop());
    this.stream = null;
    this.streaming.set(false);
  }

  capture() {
    const v = this.video.nativeElement;
    if (!v.videoWidth) return;
    const scale = Math.min(1, this.maxSide / Math.max(v.videoWidth, v.videoHeight));
    const c = document.createElement('canvas');
    c.width = Math.round(v.videoWidth * scale);
    c.height = Math.round(v.videoHeight * scale);
    c.getContext('2d')!.drawImage(v, 0, 0, c.width, c.height);
    c.toBlob(b => b && this.captured.emit(b), 'image/jpeg', 0.92);
  }

  onFile(ev: Event) {
    const input = ev.target as HTMLInputElement;
    const f = input.files?.[0];
    input.value = '';
    if (f) this.captured.emit(f);
  }

  ngOnDestroy() { this.stop(); }
}
