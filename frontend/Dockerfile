FROM node:20.19.0-alpine3.21 AS build
WORKDIR /app
COPY package*.json ./
RUN if [ -f package-lock.json ]; then npm ci; else npm install --package-lock; fi
COPY . .
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.28.0-alpine AS runtime
COPY --from=build --chown=nginx:nginx /app/dist /usr/share/nginx/html
USER nginx
EXPOSE 8080
ENTRYPOINT ["nginx"]
CMD ["-g", "daemon off;"]
