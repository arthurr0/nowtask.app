# nowtask frontend

The Angular 22 interface: standalone components, signals, zoneless change detection and
Tailwind 4. The layout of the source tree, the data flow and the theming are described in
[docs/architecture.md](../docs/architecture.md).

```bash
pnpm install
pnpm start                          # http://localhost:4200, proxies /api to :8081
pnpm exec prettier --check .
pnpm exec ng build --configuration production
```

The development server expects the backend on port 8081, see `proxy.conf.json`. The design
system page at `/app/system` is only compiled into development builds.
