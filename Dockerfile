# Image multi-architecture (linux/amd64 + linux/arm64) :
#   docker buildx build --platform linux/amd64,linux/arm64 -t jplanckeel/tribbae:latest --push .
# (cf. `task docker:push`). Les étapes de build tournent sur l'architecture de la
# machine ($BUILDPLATFORM) : le front est construit une seule fois et Go compile
# en croisé pour la cible ($TARGETARCH), sans émulation QEMU.

# Stage 1 : Build frontend (indépendant de l'architecture cible)
FROM --platform=$BUILDPLATFORM node:26-alpine AS frontend
WORKDIR /app
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ .
ARG VITE_API_URL=/v1
ENV VITE_API_URL=$VITE_API_URL
RUN npm run build

# Stage 2 : Build backend (compilation croisée vers l'architecture cible)
FROM --platform=$BUILDPLATFORM golang:1.27-alpine AS backend
ARG TARGETOS=linux
ARG TARGETARCH
WORKDIR /app
COPY backend/go.mod backend/go.sum ./
RUN go mod download
COPY backend/ .
RUN CGO_ENABLED=0 GOOS=$TARGETOS GOARCH=$TARGETARCH go build -trimpath -ldflags="-s -w" -o /server ./cmd/server

# Stage 3 : Image finale, pour l'architecture cible
FROM alpine:3.24
RUN apk add --no-cache ca-certificates
COPY --from=backend /server /server
COPY --from=frontend /app/dist /public
# Exécution sans privilèges root
USER 65532:65532
EXPOSE 8080
CMD ["/server"]
