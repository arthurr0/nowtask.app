import { Injectable, inject } from '@angular/core';
import { PrefsService } from '../prefs.service';
import type { Lang } from '../models';
import { pl, type Dictionary, type TranslationKey } from './pl';
import { en } from './en';
import { de } from './de';

const DICTIONARIES: Record<Lang, Dictionary> = { pl, en, de };

export const LANGUAGES: ReadonlyArray<{ code: Lang; short: string }> = [
  { code: 'pl', short: 'PL' },
  { code: 'en', short: 'EN' },
  { code: 'de', short: 'DE' },
];

@Injectable({ providedIn: 'root' })
export class I18nService {
  private readonly prefs = inject(PrefsService);

  readonly lang = this.prefs.lang.asReadonly();

  readonly t = (key: string, params?: Record<string, string | number>): string => {
    const dictionary = DICTIONARIES[this.prefs.lang()] ?? pl;
    const lookup = key as TranslationKey;
    const template = dictionary[lookup] ?? pl[lookup] ?? key;
    if (!params) return template;
    return template.replace(/\{(\w+)\}/g, (match, name: string) =>
      name in params ? String(params[name]) : match,
    );
  };

  readonly label = (key: string, fallback: string): string => {
    const dictionary = DICTIONARIES[this.prefs.lang()] ?? pl;
    const lookup = key as TranslationKey;
    const value = dictionary[lookup] ?? pl[lookup];
    return value ?? fallback;
  };

  setLang(lang: Lang): void {
    this.prefs.lang.set(lang);
  }
}
