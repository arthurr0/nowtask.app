import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Icon } from '../../ui/icon';
import { Logo } from '../../ui/logo';
import { LandingArt } from './landing-art';
import { LandingDemo } from './landing-demo';
import { LandingDots } from './landing-dots';
import { LANDING_COPY, LANDING_LINKS } from './landing.copy';

@Component({
  selector: 'app-landing',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icon, Logo, LandingArt, LandingDemo, LandingDots],
  templateUrl: './landing.html',
  styles: `
    @keyframes ld-rise {
      from {
        opacity: 0;
        transform: translateY(14px);
      }
      to {
        opacity: 1;
        transform: none;
      }
    }

    .ld-rise {
      animation: ld-rise 640ms cubic-bezier(0.2, 0.8, 0.3, 1) both;
      animation-delay: var(--ld-delay, 0ms);
    }
  `,
})
export class Landing {
  protected readonly copy = LANDING_COPY;
  protected readonly links = LANDING_LINKS;
  protected readonly year = new Date().getFullYear();
  protected readonly openFaq = signal(0);
  protected readonly menuOpen = signal(false);

  protected stateLabel(state: string): string {
    return this.copy.compare.legend.find((item) => item.state === state)?.label ?? state;
  }

  protected artSpan(index: number): string {
    return index === 0 ? 'lg:col-span-4' : index === 5 ? 'lg:col-span-3' : 'lg:col-span-2';
  }

  toggleFaq(index: number): void {
    this.openFaq.set(this.openFaq() === index ? -1 : index);
  }

  toggleMenu(): void {
    this.menuOpen.update((open) => !open);
  }

  closeMenu(): void {
    this.menuOpen.set(false);
  }
}
