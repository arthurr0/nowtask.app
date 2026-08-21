import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Icon } from '../../ui/icon';
import { Logo } from '../../ui/logo';
import { LandingDemo } from './landing-demo';
import { LANDING_COPY, LANDING_LINKS } from './landing.copy';

@Component({
  selector: 'app-landing',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icon, Logo, LandingDemo],
  templateUrl: './landing.html',
})
export class Landing {
  protected readonly copy = LANDING_COPY;
  protected readonly links = LANDING_LINKS;
  protected readonly year = new Date().getFullYear();
  protected readonly openFaq = signal(0);

  protected stateLabel(state: string): string {
    return this.copy.compare.legend.find((item) => item.state === state)?.label ?? state;
  }

  toggleFaq(index: number): void {
    this.openFaq.set(this.openFaq() === index ? -1 : index);
  }
}
