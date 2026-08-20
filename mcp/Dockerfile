FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY tsconfig.json ./
COPY src ./src
RUN npm run build

FROM node:22-alpine
ENV NODE_ENV=production
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --omit=dev && npm cache clean --force
COPY --from=build /app/dist ./dist
ENV NOWTASK_MCP_TRANSPORT=http \
    NOWTASK_MCP_HTTP_HOST=0.0.0.0 \
    NOWTASK_MCP_HTTP_PORT=8765
EXPOSE 8765
USER node
CMD ["node", "dist/index.js"]
