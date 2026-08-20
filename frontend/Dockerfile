FROM node:24-alpine AS build
WORKDIR /app

RUN corepack enable

COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
# pnpm 11 domyslnie blokuje skrypty postinstall i konczy instalacje bledem.
# Lockfile jest przypiety, a etap budowania jest jednorazowy, wiec zdejmujemy blokade tutaj.
ENV PNPM_CONFIG_DANGEROUSLY_ALLOW_ALL_BUILDS=true
RUN pnpm install --frozen-lockfile

COPY . .
RUN pnpm exec ng build --configuration production

FROM nginx:1.29-alpine AS runtime
RUN rm /etc/nginx/conf.d/default.conf
COPY nginx.conf /etc/nginx/conf.d/kadra.conf
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s CMD wget -qO- http://localhost:8080/ > /dev/null || exit 1
CMD ["nginx", "-g", "daemon off;"]
