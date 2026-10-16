package main

import (
	"context"
	"errors"
	"log"
	"net"
	"net/http"
	"os"
	"os/signal"
	pathpkg "path"
	"path/filepath"
	"strings"
	"syscall"
	"time"

	"github.com/grpc-ecosystem/grpc-gateway/v2/runtime"
	"github.com/tribbae/backend/internal/admin"
	"github.com/tribbae/backend/internal/ai"
	"github.com/tribbae/backend/internal/auth"
	"github.com/tribbae/backend/internal/child"
	"github.com/tribbae/backend/internal/comment"
	"github.com/tribbae/backend/internal/config"
	"github.com/tribbae/backend/internal/db"
	"github.com/tribbae/backend/internal/folder"
	"github.com/tribbae/backend/internal/follow"
	"github.com/tribbae/backend/internal/httpx"
	"github.com/tribbae/backend/internal/interceptor"
	"github.com/tribbae/backend/internal/link"
	"github.com/tribbae/backend/internal/preview"
	"github.com/tribbae/backend/internal/tribe"
	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"
	"google.golang.org/grpc/reflection"
	"google.golang.org/protobuf/encoding/protojson"

	pb "github.com/tribbae/backend/gen/tribbae/v1"
)

func main() {
	cfg := config.Load()
	if err := cfg.Validate(); err != nil {
		log.Fatalf("config: %v", err)
	}

	database, err := db.Connect(cfg.MongoURI, cfg.MongoDB)
	if err != nil {
		log.Fatalf("mongo connect: %v", err)
	}
	log.Println("connected to MongoDB")

	// Ensure MongoDB indexes exist
	if err := db.EnsureIndexes(context.Background(), database.DB()); err != nil {
		log.Fatalf("ensure indexes: %v", err)
	}

	// Ensure admin account exists
	if err := auth.EnsureAdminAccount(context.Background(), database.Col("users"), cfg.AdminPassword); err != nil {
		log.Fatalf("ensure admin account: %v", err)
	}

	// Services
	authSvc := auth.NewService(database.Col("users"), cfg.JWTSecret)
	folderSvc := folder.NewService(database.Col("folders"), database.Col("links"), database.Col("users"), cfg.BaseURL)
	linkSvc := link.NewService(database.Col("links"), database.Col("folders"))
	childSvc := child.NewService(database.DB())
	// Tribu : ses membres partagent idées, dossiers et enfants
	tribeSvc := tribe.NewService(database.Col("tribes"), database.Col("users"))
	linkSvc.SetTribePeers(tribeSvc.Peers)
	folderSvc.SetTribePeers(tribeSvc.Peers)
	childSvc.SetTribePeers(tribeSvc.Peers)
	followSvc := follow.NewService(database.Col("follows"), database.Col("users"))
	commentSvc := comment.NewService(database.Col("comments"), database.Col("links"), database.Col("users"))
	aiSvc := ai.NewService(ai.Config{
		OllamaURL:       cfg.OllamaURL,
		OllamaModel:     cfg.OllamaModel,
		OllamaKeepAlive: cfg.OllamaKeepAlive,
		OllamaNumThread: cfg.OllamaNumThread,
		SearxURL:        cfg.SearxURL,
		WebSearchAPIKey: cfg.OllamaAPIKey,
		GeminiAPIKey:    cfg.GeminiAPIKey,
		GeminiModel:     cfg.GeminiModel,
	})
	// Télécharge (si besoin) et charge le modèle local sans retarder le démarrage
	go aiSvc.Warmup(context.Background())
	// Rattrape en continu l'image des idées qui n'en ont pas (lots de 20 toutes les 10 min)
	go linkSvc.RunImageBackfill(context.Background(), 10*time.Minute, 20)

	// Handlers (gRPC servers)
	authH := auth.NewHandler(authSvc)
	folderH := folder.NewHandler(folderSvc)
	linkH := link.NewHandler(linkSvc)
	childH := child.NewHandler(childSvc)
	followH := follow.NewHandler(followSvc)
	commentH := comment.NewHandler(commentSvc, linkSvc.CanView)
	adminH := admin.NewHandler(authSvc)
	tribeH := tribe.NewHandler(tribeSvc)

	// Adaptateur pour récupérer le statut premium d'un utilisateur
	userGetter := &userGetterAdapter{authSvc: authSvc}

	// Extrait le userID du header Authorization pour les routes HTTP hors gateway
	bearerUser := func(r *http.Request) (string, error) {
		token, ok := strings.CutPrefix(r.Header.Get("Authorization"), "Bearer ")
		if !ok || token == "" {
			return "", errors.New("missing bearer token")
		}
		return authSvc.ValidateToken(token)
	}

	aiH := ai.NewHandler(aiSvc, userGetter, bearerUser)

	// Serveur gRPC
	grpcServer := grpc.NewServer(
		grpc.ChainUnaryInterceptor(
			interceptor.UnaryAuth(authSvc),
			interceptor.UnaryAdminAuth(authSvc),
		),
	)
	pb.RegisterAuthServiceServer(grpcServer, authH)
	pb.RegisterFolderServiceServer(grpcServer, folderH)
	pb.RegisterLinkServiceServer(grpcServer, linkH)
	pb.RegisterChildServiceServer(grpcServer, childH)
	pb.RegisterFollowServiceServer(grpcServer, followH)
	pb.RegisterCommentServiceServer(grpcServer, commentH)
	pb.RegisterAdminServiceServer(grpcServer, adminH)
	pb.RegisterTribeServiceServer(grpcServer, tribeH)
	reflection.Register(grpcServer)

	grpcAddr := ":" + cfg.GRPCPort
	lis, err := net.Listen("tcp", grpcAddr)
	if err != nil {
		log.Fatalf("failed to listen: %v", err)
	}
	go func() {
		log.Printf("gRPC server listening on %s", grpcAddr)
		if err := grpcServer.Serve(lis); err != nil {
			log.Fatalf("gRPC serve: %v", err)
		}
	}()

	// grpc-gateway HTTP mux
	ctx := context.Background()
	mux := runtime.NewServeMux(
		runtime.WithMarshalerOption(runtime.MIMEWildcard, &runtime.JSONPb{
			MarshalOptions: protojson.MarshalOptions{
				UseEnumNumbers:  false,
				EmitUnpopulated: false,
			},
			UnmarshalOptions: protojson.UnmarshalOptions{
				DiscardUnknown: true,
			},
		}),
		runtime.WithErrorHandler(runtime.DefaultHTTPErrorHandler),
	)
	opts := []grpc.DialOption{grpc.WithTransportCredentials(insecure.NewCredentials())}

	if err := pb.RegisterAuthServiceHandlerFromEndpoint(ctx, mux, grpcAddr, opts); err != nil {
		log.Fatalf("register auth gateway: %v", err)
	}
	if err := pb.RegisterFolderServiceHandlerFromEndpoint(ctx, mux, grpcAddr, opts); err != nil {
		log.Fatalf("register folder gateway: %v", err)
	}
	if err := pb.RegisterLinkServiceHandlerFromEndpoint(ctx, mux, grpcAddr, opts); err != nil {
		log.Fatalf("register link gateway: %v", err)
	}
	if err := pb.RegisterChildServiceHandlerFromEndpoint(ctx, mux, grpcAddr, opts); err != nil {
		log.Fatalf("register child gateway: %v", err)
	}
	if err := pb.RegisterFollowServiceHandlerFromEndpoint(ctx, mux, grpcAddr, opts); err != nil {
		log.Fatalf("register follow gateway: %v", err)
	}
	if err := pb.RegisterCommentServiceHandlerFromEndpoint(ctx, mux, grpcAddr, opts); err != nil {
		log.Fatalf("register comment gateway: %v", err)
	}
	if err := pb.RegisterAdminServiceHandlerFromEndpoint(ctx, mux, grpcAddr, opts); err != nil {
		log.Fatalf("register admin gateway: %v", err)
	}
	if err := pb.RegisterTribeServiceHandlerFromEndpoint(ctx, mux, grpcAddr, opts); err != nil {
		log.Fatalf("register tribe gateway: %v", err)
	}

	// Limites de débit par IP sur les routes sensibles (force brute, coût IA, requêtes sortantes)
	authLimiter := httpx.NewRateLimiter(10, time.Minute)
	costlyLimiter := httpx.NewRateLimiter(20, time.Minute)

	var handler http.Handler = withSPA(mux)
	handler = withPreview(preview.Handler(bearerUser), handler)
	handler = withAI(aiH, handler)
	handler = httpx.Limit(costlyLimiter, map[string]bool{"/v1/ai/generate": true, "/v1/links/preview": true}, handler)
	handler = httpx.Limit(authLimiter, map[string]bool{"/v1/auth/login": true, "/v1/auth/register": true, "/v1/auth/refresh": true}, handler)
	handler = httpx.MaxBody(1<<20, handler)
	handler = httpx.CORS(cfg.AllowedOrigins, handler)
	handler = httpx.SecurityHeaders(handler)

	srv := &http.Server{
		Addr:              ":" + cfg.Port,
		Handler:           handler,
		ReadHeaderTimeout: 10 * time.Second,
		ReadTimeout:       30 * time.Second,
		// La génération IA peut durer jusqu'à 2 minutes
		WriteTimeout: 3 * time.Minute,
		IdleTimeout:  2 * time.Minute,
	}

	go func() {
		log.Printf("HTTP server listening on %s", srv.Addr)
		if err := srv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			log.Fatalf("http serve: %v", err)
		}
	}()

	// Arrêt propre sur SIGINT/SIGTERM (déploiements, docker stop)
	stop := make(chan os.Signal, 1)
	signal.Notify(stop, os.Interrupt, syscall.SIGTERM)
	<-stop
	log.Println("shutting down...")
	shutdownCtx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()
	_ = srv.Shutdown(shutdownCtx)
	grpcServer.GracefulStop()
}

func withAI(aiH http.Handler, next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path == "/v1/ai/generate" && r.Method == http.MethodPost {
			aiH.ServeHTTP(w, r)
			return
		}
		next.ServeHTTP(w, r)
	})
}

func withPreview(preview http.HandlerFunc, next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path == "/v1/links/preview" && r.Method == http.MethodGet {
			preview(w, r)
			return
		}
		next.ServeHTTP(w, r)
	})
}

// withSPA sert les fichiers statiques du frontend depuis /public.
// Si le fichier n'existe pas, renvoie index.html (SPA fallback).
func withSPA(apiHandler http.Handler) http.Handler {
	const staticDir = "/public"

	// Si le dossier n'existe pas, on ne sert que l'API
	if _, err := os.Stat(staticDir); os.IsNotExist(err) {
		log.Println("No /public directory found, serving API only")
		return apiHandler
	}

	log.Println("Serving frontend from /public")
	fs := http.FileServer(http.Dir(staticDir))

	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		// Les routes API passent directement au handler gRPC-gateway
		if strings.HasPrefix(r.URL.Path, "/v1/") {
			apiHandler.ServeHTTP(w, r)
			return
		}

		// Essayer de servir le fichier statique
		// path.Clean sur un chemin absolu supprime les "..": on reste dans staticDir
		path := filepath.Join(staticDir, filepath.FromSlash(pathpkg.Clean("/"+r.URL.Path)))
		if info, err := os.Stat(path); err == nil && !info.IsDir() {
			fs.ServeHTTP(w, r)
			return
		}

		// SPA fallback : renvoyer index.html
		http.ServeFile(w, r, filepath.Join(staticDir, "index.html"))
	})
}

// userGetterAdapter adapte auth.Service pour l'interface ai.UserGetter
type userGetterAdapter struct {
	authSvc *auth.Service
}

func (u *userGetterAdapter) GetUser(ctx context.Context, userID string) (bool, error) {
	user, err := u.authSvc.GetUser(ctx, userID)
	if err != nil {
		return false, err
	}
	return user.IsPremium, nil
}
