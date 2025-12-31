FROM node:20-alpine AS build

RUN apk add --no-cache python3 g++ make

# frontend 전체 구조 복사
WORKDIR /app/frontend
COPY admin/ ./admin/
COPY user/ ./user/

# admin 디렉토리로 이동
WORKDIR /app/frontend/admin

# 설치 및 빌드
RUN npm install
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/frontend/admin/dist /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]