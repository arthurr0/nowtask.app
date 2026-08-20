import { Injectable, computed, effect, signal } from '@angular/core';
import type { Accent, Density, Lang, RadiusStep, ThemeChoice } from './models';

const STORAGE_KEY = 'nowtask.prefs';

interface StoredPrefs {
  lang: Lang;
  theme: ThemeChoice;
  accent: Accent;
  density: Density;
  radius: RadiusStep;
  animations: boolean;
  avatarsOnCards: boolean;
  rowHighlight: boolean;
  singleKeyShortcuts: boolean;
}

const DEFAULTS: StoredPrefs = {
  lang: 'en',
  theme: 'dark',
  accent: 'graphite',
  density: 'roomy',
  radius: '8',
  animations: true,
  avatarsOnCards: true,
  rowHighlight: true,
  singleKeyShortcuts: true,
};

function read(): StoredPrefs {
  if (typeof localStorage === 'undefined') return DEFAULTS;
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? { ...DEFAULTS, ...(JSON.parse(raw) as Partial<StoredPrefs>) } : DEFAULTS;
  } catch {
    return DEFAULTS;
  }
}

@Injectable({ providedIn: 'root' })
export class PrefsService {
  private readonly initial = read();

  readonly lang = signal<Lang>(this.initial.lang);
  readonly theme = signal<ThemeChoice>(this.initial.theme);
  readonly accent = signal<Accent>(this.initial.accent);
  readonly density = signal<Density>(this.initial.density);
  readonly radius = signal<RadiusStep>(this.initial.radius);
  readonly animations = signal(this.initial.animations);
  readonly avatarsOnCards = signal(this.initial.avatarsOnCards);
  readonly rowHighlight = signal(this.initial.rowHighlight);
  readonly singleKeyShortcuts = signal(this.initial.singleKeyShortcuts);

  private readonly systemDark = signal(
    typeof matchMedia !== 'undefined' && matchMedia('(prefers-color-scheme: dark)').matches,
  );

  readonly resolvedTheme = computed<'light' | 'dark'>(() => {
    const choice = this.theme();
    if (choice === 'system') return this.systemDark() ? 'dark' : 'light';
    return choice;
  });

  constructor() {
    if (typeof matchMedia !== 'undefined') {
      matchMedia('(prefers-color-scheme: dark)').addEventListener('change', (e) =>
        this.systemDark.set(e.matches),
      );
    }

    effect(() => {
      const snapshot: StoredPrefs = {
        lang: this.lang(),
        theme: this.theme(),
        accent: this.accent(),
        density: this.density(),
        radius: this.radius(),
        animations: this.animations(),
        avatarsOnCards: this.avatarsOnCards(),
        rowHighlight: this.rowHighlight(),
        singleKeyShortcuts: this.singleKeyShortcuts(),
      };

      if (typeof document !== 'undefined') {
        const root = document.documentElement;
        root.setAttribute('data-theme', this.resolvedTheme());
        root.setAttribute('data-accent', snapshot.accent);
        root.setAttribute('data-density', snapshot.density);
        root.setAttribute('data-radius', snapshot.radius);
        root.setAttribute('data-anim', snapshot.animations ? 'on' : 'off');
        root.setAttribute('data-rowhover', snapshot.rowHighlight ? 'on' : 'off');
        root.setAttribute('lang', snapshot.lang);
      }

      if (typeof localStorage !== 'undefined') {
        try {
          localStorage.setItem(STORAGE_KEY, JSON.stringify(snapshot));
        } catch {}
      }
    });
  }

  toggleTheme(): void {
    this.theme.set(this.resolvedTheme() === 'dark' ? 'light' : 'dark');
  }
}
