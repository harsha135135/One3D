# Dependencies, then a standalone Next.js build, then a small runtime image.
FROM node:24-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

FROM node:24-alpine AS build
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .
ENV ONE3D_STANDALONE=true \
    NEXT_TELEMETRY_DISABLED=1
RUN npm run build

# Runtime: only the standalone server and its static files, non-root.
FROM node:24-alpine
WORKDIR /app
ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1 \
    PORT=3000 \
    HOSTNAME=0.0.0.0
RUN addgroup -S -g 10001 one3d && adduser -S -u 10001 -G one3d one3d
COPY --from=build --chown=one3d:one3d /app/.next/standalone ./
COPY --from=build --chown=one3d:one3d /app/.next/static ./.next/static
USER one3d
EXPOSE 3000
HEALTHCHECK --interval=10s --timeout=3s --start-period=20s --retries=6 \
    CMD ["wget", "-q", "-O", "/dev/null", "http://127.0.0.1:3000/"]
CMD ["node", "server.js"]
