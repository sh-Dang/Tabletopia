FROM node:20-alpine AS build
WORKDIR /app

RUN apk add --no-cache python3 g++ make

COPY user/ .
RUN npm install
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]