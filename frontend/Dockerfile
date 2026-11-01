FROM node:20-alpine AS deps
WORKDIR /app
COPY package*.json ./
RUN npm ci

FROM node:20-alpine AS build
WORKDIR /app
ARG BACKEND_API_ORIGIN=http://localhost:8080
ENV BACKEND_API_ORIGIN=$BACKEND_API_ORIGIN
# NEXT_PUBLIC_* 是构建期变量：令牌在镜像构建时写入客户端 bundle
ARG NEXT_PUBLIC_API_TOKEN=
ENV NEXT_PUBLIC_API_TOKEN=$NEXT_PUBLIC_API_TOKEN
COPY --from=deps /app/node_modules ./node_modules
COPY . .
RUN npm run build

FROM node:20-alpine AS runtime
WORKDIR /app
ENV NODE_ENV=production

COPY --from=build /app/package*.json ./
COPY --from=build /app/.next ./.next
COPY --from=build /app/public ./public
COPY --from=build /app/node_modules ./node_modules

EXPOSE 3000
CMD ["npm", "run", "start"]
