# syntax=docker/dockerfile:1
# 웹 이미지 = React 정적 파일 + Nginx 리버스 프록시(HTTPS 종료, /api → Spring Boot)

FROM node:24-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY . .
RUN npm run build

FROM nginx:1.29-alpine
# 공식 이미지는 /etc/nginx/templates/*.template 의 ${DOMAIN} 등 "정의된 환경변수만" 치환해 conf.d 에 넣는다
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY nginx/security-headers.conf /etc/nginx/snippets/security-headers.conf
COPY --from=build /src/dist /usr/share/nginx/html
