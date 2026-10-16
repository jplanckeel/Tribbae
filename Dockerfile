# Stage 1 : Build frontend
FROM node:24-alpine AS frontend
WORKDIR /app
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ .
ARG VITE_API_URL=/v1
ENV VITE_API_URL=$VITE_API_URL
RUN npm run build

# Stage 2 : Build backend
FROM golang:1.26-alpine AS backend
WORKDIR /app
COPY backend/go.mod backend/go.sum ./
RUN go mod download
COPY backend/ .
RUN CGO_ENABLED=0 GOOS=linux go build -trimpath -ldflags="-s -w" -o /server ./cmd/server

# Stage 3 : Image finale
FROM alpine:3.22
RUN apk add --no-cache ca-certificates
COPY --from=backend /server /server
COPY --from=frontend /app/dist /public
# Exécution sans privilèges root
USER 65532:65532
EXPOSE 8080
CMD ["/server"]
