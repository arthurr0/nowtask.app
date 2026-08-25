import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'ui-icon-sprite',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg width="0" height="0" style="position: absolute" aria-hidden="true" focusable="false">
      <defs>
        <symbol id="i-home" viewBox="0 0 24 24">
          <path d="M4 11 12 4l8 7" />
          <path d="M6.5 9.8V20h11V9.8" />
        </symbol>
        <symbol id="i-check" viewBox="0 0 24 24"><path d="m5 12.5 4.5 4.5L19 7" /></symbol>
        <symbol id="i-board" viewBox="0 0 24 24">
          <rect x="4" y="5" width="4.4" height="14" rx="1.2" />
          <rect x="9.8" y="5" width="4.4" height="10" rx="1.2" />
          <rect x="15.6" y="5" width="4.4" height="7" rx="1.2" />
        </symbol>
        <symbol id="i-list" viewBox="0 0 24 24"><path d="M4 7h16M4 12h16M4 17h10" /></symbol>
        <symbol id="i-git-branch" viewBox="0 0 24 24">
          <circle cx="7" cy="6" r="2.4" />
          <circle cx="7" cy="18" r="2.4" />
          <circle cx="17" cy="9" r="2.4" />
          <path d="M7 8.4v7.2" />
          <path d="M17 11.4c0 2.6-2.1 4.7-4.7 4.7H9.4" />
        </symbol>
        <symbol id="i-timeline" viewBox="0 0 24 24"><path d="M4 7h9M7 12h11M4 17h7" /></symbol>
        <symbol id="i-calendar" viewBox="0 0 24 24">
          <rect x="4" y="6" width="16" height="14" rx="2" />
          <path d="M4 10.5h16M9 4v4M15 4v4" />
        </symbol>
        <symbol id="i-bolt" viewBox="0 0 24 24"><path d="M13 3 5 14h6l-1 7 8-11h-6l1-7Z" /></symbol>
        <symbol id="i-chart" viewBox="0 0 24 24">
          <path d="M3 20h18" />
          <path d="M6.5 20v-6M11.5 20v-10M16.5 20v-4" />
        </symbol>
        <symbol id="i-search" viewBox="0 0 24 24">
          <circle cx="11" cy="11" r="6" />
          <path d="m20 20-4.3-4.3" />
        </symbol>
        <symbol id="i-sliders" viewBox="0 0 24 24">
          <path d="M4 8h9M19 8h1M4 16h4M14 16h6" />
          <circle cx="16" cy="8" r="2.4" />
          <circle cx="11" cy="16" r="2.4" />
        </symbol>
        <symbol id="i-sun" viewBox="0 0 24 24">
          <circle cx="12" cy="12" r="4" />
          <path
            d="M12 3v2M12 19v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M3 12h2M19 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"
          />
        </symbol>
        <symbol id="i-moon" viewBox="0 0 24 24">
          <path d="M20 14.5A8 8 0 0 1 9.5 4 8.2 8.2 0 1 0 20 14.5Z" />
        </symbol>
        <symbol id="i-plus" viewBox="0 0 24 24"><path d="M12 5v14M5 12h14" /></symbol>
        <symbol id="i-filter" viewBox="0 0 24 24">
          <path d="M4 6h16l-6.4 7.4V19l-3.2-1.8v-3.8L4 6Z" />
        </symbol>
        <symbol id="i-chevron-down" viewBox="0 0 24 24"><path d="m7 10 5 5 5-5" /></symbol>
        <symbol id="i-chevron-right" viewBox="0 0 24 24"><path d="m10 7 5 5-5 5" /></symbol>
        <symbol id="i-chevron-left" viewBox="0 0 24 24"><path d="m14 7-5 5 5 5" /></symbol>
        <symbol id="i-dots" viewBox="0 0 24 24">
          <circle cx="6" cy="12" r="1.3" style="fill: currentColor; stroke: none" />
          <circle cx="12" cy="12" r="1.3" style="fill: currentColor; stroke: none" />
          <circle cx="18" cy="12" r="1.3" style="fill: currentColor; stroke: none" />
        </symbol>
        <symbol id="i-clock" viewBox="0 0 24 24">
          <circle cx="12" cy="12" r="8" />
          <path d="M12 7.5V12l3 2" />
        </symbol>
        <symbol id="i-message" viewBox="0 0 24 24">
          <path d="M20 15a1 1 0 0 1-1 1H8l-4 3.5V5a1 1 0 0 1 1-1h14a1 1 0 0 1 1 1Z" />
        </symbol>
        <symbol id="i-bell" viewBox="0 0 24 24">
          <path d="M18 15V10a6 6 0 1 0-12 0v5l-2 3h16l-2-3Z" />
          <path d="M10 20a2 2 0 0 0 4 0" />
        </symbol>
        <symbol id="i-flag" viewBox="0 0 24 24">
          <path d="M6 21V4" />
          <path d="M6 4h11l-2 3.5L17 11H6" />
        </symbol>
        <symbol id="i-save" viewBox="0 0 24 24">
          <path d="M5 5h11l3 3v11H5z" />
          <path d="M9 5v5h6V5M8 19v-5h8v5" />
        </symbol>
        <symbol id="i-sort" viewBox="0 0 24 24">
          <path d="M7 5v14M7 19l-3-3M7 19l3-3M17 19V5M17 5l-3 3M17 5l3 3" />
        </symbol>
        <symbol id="i-columns" viewBox="0 0 24 24">
          <rect x="4" y="5" width="16" height="14" rx="2" />
          <path d="M10 5v14M15 5v14" />
        </symbol>
        <symbol id="i-x" viewBox="0 0 24 24"><path d="M6 6l12 12M18 6 6 18" /></symbol>
        <symbol id="i-user" viewBox="0 0 24 24">
          <circle cx="12" cy="8" r="3.2" />
          <path d="M5 20c1.2-3.6 4-5.2 7-5.2s5.8 1.6 7 5.2" />
        </symbol>
        <symbol id="i-users" viewBox="0 0 24 24">
          <circle cx="9" cy="8" r="3" />
          <path d="M3 19c1-3.2 3.4-4.7 6-4.7s5 1.5 6 4.7" />
          <path d="M16 5.5a3 3 0 0 1 0 5.6M17.5 14.6c2 .7 3.4 2.1 4 4.4" />
        </symbol>
        <symbol id="i-trash" viewBox="0 0 24 24">
          <path d="M5 7h14M10 7V5h4v2M7 7l1 13h8l1-13" />
        </symbol>
        <symbol id="i-lock" viewBox="0 0 24 24">
          <rect x="5" y="10.5" width="14" height="9" rx="2" />
          <path d="M8.5 10.5V8a3.5 3.5 0 0 1 7 0v2.5" />
        </symbol>
        <symbol id="i-key" viewBox="0 0 24 24">
          <circle cx="8" cy="12" r="3.5" />
          <path d="M11.5 12H21M18.5 12v3" />
        </symbol>
        <symbol id="i-shield" viewBox="0 0 24 24">
          <path d="M12 3.5 5 6v6c0 4 3 7 7 8.5 4-1.5 7-4.5 7-8.5V6l-7-2.5Z" />
        </symbol>
        <symbol id="i-agent" viewBox="0 0 24 24">
          <rect x="4" y="8" width="16" height="11" rx="3" />
          <path d="M12 3.5V8M2.5 13.5v2M21.5 13.5v2" />
          <circle cx="9.3" cy="13" r="1" style="fill: currentColor; stroke: none" />
          <circle cx="14.7" cy="13" r="1" style="fill: currentColor; stroke: none" />
        </symbol>
        <symbol id="i-log" viewBox="0 0 24 24">
          <path d="M6 4h12v16H6z" />
          <path d="M9 9h6M9 13h6M9 17h3" />
        </symbol>
        <symbol id="i-grip" viewBox="0 0 24 24">
          <circle cx="9" cy="6" r="1.2" style="fill: currentColor; stroke: none" />
          <circle cx="15" cy="6" r="1.2" style="fill: currentColor; stroke: none" />
          <circle cx="9" cy="12" r="1.2" style="fill: currentColor; stroke: none" />
          <circle cx="15" cy="12" r="1.2" style="fill: currentColor; stroke: none" />
          <circle cx="9" cy="18" r="1.2" style="fill: currentColor; stroke: none" />
          <circle cx="15" cy="18" r="1.2" style="fill: currentColor; stroke: none" />
        </symbol>
        <symbol id="i-arrow-right" viewBox="0 0 24 24"><path d="M5 12h13M13 7l5 5-5 5" /></symbol>
        <symbol id="i-globe" viewBox="0 0 24 24">
          <circle cx="12" cy="12" r="8" />
          <path d="M4 12h16" />
          <path d="M12 4c2.2 2.4 3.3 5 3.3 8s-1.1 5.6-3.3 8c-2.2-2.4-3.3-5-3.3-8s1.1-5.6 3.3-8Z" />
        </symbol>
        <symbol id="i-eye" viewBox="0 0 24 24">
          <path d="M2.5 12S6 6 12 6s9.5 6 9.5 6-3.5 6-9.5 6-9.5-6-9.5-6Z" />
          <circle cx="12" cy="12" r="2.6" />
        </symbol>
        <symbol id="i-play" viewBox="0 0 24 24"><path d="M8 5.5 18 12 8 18.5v-13Z" /></symbol>
        <symbol id="i-alert" viewBox="0 0 24 24">
          <path d="M12 4 2.5 20h19L12 4Z" />
          <path d="M12 10v4.5M12 17.2v.1" />
        </symbol>
        <symbol id="i-link" viewBox="0 0 24 24">
          <path d="M10.5 13.8a4 4 0 0 0 5.7 0l2.3-2.3a4 4 0 1 0-5.7-5.7l-1 1" />
          <path d="M13.5 10.2a4 4 0 0 0-5.7 0l-2.3 2.3a4 4 0 1 0 5.7 5.7l1-1" />
        </symbol>
        <symbol id="i-clip" viewBox="0 0 24 24">
          <path
            d="M18 11.5 12.2 17.3a4 4 0 0 1-5.7-5.7l7-7a2.8 2.8 0 0 1 4 4l-7 7a1.6 1.6 0 0 1-2.2-2.2l6.2-6.2"
          />
        </symbol>
        <symbol id="i-layers" viewBox="0 0 24 24">
          <path d="m12 4 8 4-8 4-8-4 8-4Z" />
          <path d="m4 12 8 4 8-4" />
          <path d="m4 16 8 4 8-4" />
        </symbol>
        <symbol id="i-up" viewBox="0 0 24 24"><path d="M12 19V5M6 11l6-6 6 6" /></symbol>
        <symbol id="i-down" viewBox="0 0 24 24"><path d="M12 5v14M6 13l6 6 6-6" /></symbol>
        <symbol id="i-pencil" viewBox="0 0 24 24">
          <path d="M4 20h4L19.5 8.5a2.1 2.1 0 0 0-3-3L5 17v3Z" />
          <path d="m15 7 2.5 2.5" />
        </symbol>
        <symbol id="i-copy" viewBox="0 0 24 24">
          <rect x="9" y="9" width="11" height="11" rx="2" />
          <path d="M15 6.5A2.5 2.5 0 0 0 12.5 4H6a2 2 0 0 0-2 2v6.5A2.5 2.5 0 0 0 6.5 15" />
        </symbol>
        <symbol id="i-archive" viewBox="0 0 24 24">
          <rect x="3.5" y="5" width="17" height="4" rx="1.2" />
          <path d="M5.5 9v9a2 2 0 0 0 2 2h9a2 2 0 0 0 2-2V9" />
          <path d="M10 13h4" />
        </symbol>
      </defs>
    </svg>
  `,
})
export class IconSprite {}
