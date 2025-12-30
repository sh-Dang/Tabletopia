# 1️⃣ build stage
FROM node:20-alpine AS build
WORKDIR /app

# package.json만 복사
COPY package*.json ./
RUN npm install

# admin 전체 코드 복사 (컨텍스트 기준)
COPY . .
RUN npm run build

# 2️⃣ run stage
FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
