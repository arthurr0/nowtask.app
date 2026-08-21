import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import type { MetaDto } from '../../core/api-types';
import { AuthService } from '../../core/auth.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { Icon } from '../../ui/icon';
import { Logo } from '../../ui/logo';
import { ViewControls } from '../../ui/view-controls';

@Component({
  selector: 'app-login',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink, Icon, Logo, ViewControls],
  templateUrl: './login.html',
})
export class Login implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly http = inject(HttpClient);
  protected readonly t = inject(I18nService).t;

  protected readonly email = signal('');
  protected readonly password = signal('');
  protected readonly showPassword = signal(false);
  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly version = signal('…');
  protected readonly apiState = signal('…');

  async ngOnInit(): Promise<void> {
    try {
      const meta = await firstValueFrom(this.http.get<MetaDto>('/api/meta'));
      this.version.set(meta.version);
      this.apiState.set(this.t('login.serverUp'));
    } catch {
      this.apiState.set(this.t('login.serverDown'));
    }
  }

  async submit(event: Event): Promise<void> {
    event.preventDefault();
    if (this.submitting()) {
      return;
    }

    this.submitting.set(true);
    this.error.set(null);

    try {
      await this.auth.login(this.email(), this.password());
      const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
      await this.router.navigateByUrl(returnUrl ?? '/app/board');
    } catch (error) {
      const unauthorised = error instanceof HttpErrorResponse && error.status === 401;
      this.error.set(unauthorised ? this.t('login.badCredentials') : this.t('state.errorTitle'));
    } finally {
      this.submitting.set(false);
    }
  }
}
