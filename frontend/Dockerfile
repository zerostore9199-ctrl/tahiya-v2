FROM node:22-alpine AS build
WORKDIR /src
COPY package*.json ./
RUN npm ci
COPY . .
RUN npx ng build --configuration production

FROM nginx:1.27-alpine
RUN apk add --no-cache openssl
COPY nginx-main.conf /etc/nginx/nginx.conf
COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY security-headers.conf /etc/nginx/security-headers.conf
COPY docker-entrypoint-certs.sh /docker-entrypoint.d/05-certs.sh
COPY --from=build /src/dist/frontend/browser /usr/share/nginx/html
ENV NGINX_ENVSUBST_FILTER=HTTPS_PUBLIC_PORT HTTPS_PUBLIC_PORT=8443
EXPOSE 8080 8443
