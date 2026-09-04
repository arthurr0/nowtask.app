import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  afterNextRender,
  inject,
  viewChild,
} from '@angular/core';

interface Dot {
  hx: number;
  hy: number;
  x: number;
  y: number;
  vx: number;
  vy: number;
  alpha: number;
}

const SPACING = 26;
const RADIUS = 1.1;
const REACH = 240;
const PUSH = 2.4;
const SPRING = 0.055;
const DAMPING = 0.82;
const REST = 0.02;
const FADE_TOP = 0.18;
const FADE_BOTTOM = 0.52;
const FADE_SIDE = 0.14;

function ramp(value: number, from: number, to: number): number {
  const t = Math.min(1, Math.max(0, (value - from) / (to - from)));
  return t * t * (3 - 2 * t);
}

@Component({
  selector: 'app-landing-dots',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<canvas #canvas class="block h-full w-full"></canvas>`,
  styles: `
    :host {
      display: block;
    }
  `,
})
export class LandingDots {
  private readonly canvas = viewChild.required<ElementRef<HTMLCanvasElement>>('canvas');
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly destroyRef = inject(DestroyRef);

  private dots: Dot[] = [];
  private width = 0;
  private height = 0;
  private dpr = 1;
  private color = '#888';
  private pointer = { x: -9999, y: -9999, inside: false };
  private frame = 0;
  private animated = true;

  constructor() {
    afterNextRender(() => this.setup());
  }

  private setup(): void {
    const root = document.documentElement;
    const reduced = matchMedia('(prefers-reduced-motion: reduce)').matches;
    this.animated = !reduced && root.getAttribute('data-anim') !== 'off';

    const observer = new ResizeObserver(() => this.resize());
    observer.observe(this.host.nativeElement);

    const onMove = (event: PointerEvent): void => this.track(event.clientX, event.clientY);
    const onLeave = (): void => {
      this.pointer.inside = false;
      this.wake();
    };

    if (this.animated) {
      window.addEventListener('pointermove', onMove, { passive: true });
      window.addEventListener('pointerdown', onMove, { passive: true });
      document.addEventListener('pointerleave', onLeave);
      window.addEventListener('blur', onLeave);
    }

    this.destroyRef.onDestroy(() => {
      observer.disconnect();
      window.removeEventListener('pointermove', onMove);
      window.removeEventListener('pointerdown', onMove);
      document.removeEventListener('pointerleave', onLeave);
      window.removeEventListener('blur', onLeave);
      cancelAnimationFrame(this.frame);
    });

    this.resize();
  }

  private resize(): void {
    const element = this.host.nativeElement;
    const canvas = this.canvas().nativeElement;
    this.dpr = Math.min(window.devicePixelRatio || 1, 2);
    this.width = element.clientWidth;
    this.height = element.clientHeight;
    canvas.width = Math.round(this.width * this.dpr);
    canvas.height = Math.round(this.height * this.dpr);
    this.color = getComputedStyle(element).getPropertyValue('--c-line-strong').trim() || '#888';

    const columns = Math.ceil(this.width / SPACING) + 1;
    const rows = Math.ceil(this.height / SPACING) + 1;
    const offsetX = (this.width - (columns - 1) * SPACING) / 2;
    const dots: Dot[] = [];
    for (let row = 0; row < rows; row++) {
      for (let column = 0; column < columns; column++) {
        const hx = offsetX + column * SPACING;
        const hy = row * SPACING;
        const alpha = this.fade(hx / this.width, hy / this.height);
        if (alpha <= 0.01) continue;
        dots.push({ hx, hy, x: hx, y: hy, vx: 0, vy: 0, alpha });
      }
    }
    this.dots = dots;
    this.draw();
  }

  private fade(u: number, v: number): number {
    const vertical = ramp(v, 0, FADE_TOP) * (1 - ramp(v, FADE_BOTTOM, 1));
    const horizontal = ramp(u, 0, FADE_SIDE) * (1 - ramp(u, 1 - FADE_SIDE, 1));
    return vertical * horizontal;
  }

  private track(clientX: number, clientY: number): void {
    const rect = this.host.nativeElement.getBoundingClientRect();
    const x = clientX - rect.left;
    const y = clientY - rect.top;
    const margin = REACH;
    this.pointer.inside =
      x > -margin && y > -margin && x < rect.width + margin && y < rect.height + margin;
    this.pointer.x = x;
    this.pointer.y = y;
    if (this.pointer.inside) this.wake();
  }

  private wake(): void {
    if (this.frame) return;
    this.frame = requestAnimationFrame(() => this.tick());
  }

  private tick(): void {
    this.frame = 0;
    let moving = false;
    const { x: px, y: py, inside } = this.pointer;

    for (const dot of this.dots) {
      if (inside) {
        const dx = dot.x - px;
        const dy = dot.y - py;
        const distance = Math.hypot(dx, dy);
        if (distance < REACH && distance > 0.001) {
          const force = (1 - distance / REACH) ** 2 * PUSH;
          dot.vx += (dx / distance) * force;
          dot.vy += (dy / distance) * force;
        }
      }

      dot.vx += (dot.hx - dot.x) * SPRING;
      dot.vy += (dot.hy - dot.y) * SPRING;
      dot.vx *= DAMPING;
      dot.vy *= DAMPING;
      dot.x += dot.vx;
      dot.y += dot.vy;

      if (
        Math.abs(dot.vx) > REST ||
        Math.abs(dot.vy) > REST ||
        Math.abs(dot.x - dot.hx) > REST ||
        Math.abs(dot.y - dot.hy) > REST
      ) {
        moving = true;
      } else {
        dot.x = dot.hx;
        dot.y = dot.hy;
        dot.vx = 0;
        dot.vy = 0;
      }
    }

    this.draw();
    if (moving || inside) this.frame = requestAnimationFrame(() => this.tick());
  }

  private draw(): void {
    const context = this.canvas().nativeElement.getContext('2d');
    if (!context) return;

    context.setTransform(this.dpr, 0, 0, this.dpr, 0, 0);
    context.clearRect(0, 0, this.width, this.height);
    context.fillStyle = this.color;
    for (const dot of this.dots) {
      context.globalAlpha = dot.alpha;
      context.beginPath();
      context.arc(dot.x, dot.y, RADIUS, 0, Math.PI * 2);
      context.fill();
    }
    context.globalAlpha = 1;
  }
}
