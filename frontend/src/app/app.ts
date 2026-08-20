import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { PrefsService } from './core/prefs.service';
import { IconSprite } from './ui/icon-sprite';

@Component({
  selector: 'app-root',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, IconSprite],
  template: `
    <ui-icon-sprite />
    <router-outlet />
  `,
})
export class App {
  private readonly prefs = inject(PrefsService);
}
