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
	"github.com/tribbae/backend/internal/account"
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
	"github.com/tribbae/backend/internal/mail"
	"github.com/tribbae/backend/internal/photo"
	"github.com/tribbae/backend/internal/preview"
	"github.com/tribbae/backend/internal/seo"
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
	folderSvc := folder.NewService(database.Col("folders"), database.Col("links"), database.Col("users"), strings.TrimRight(cfg.SiteURL, "/"))
	linkSvc := link.NewService(database.Col("links"), database.Col("folders"))
	childSvc := child.NewService(database.DB())
	// Tribu : ses membres partagent idées, dossiers et enfants
	tribeSvc := tribe.NewService(database.Col("tribes"), database.Col("users"), database.Col("tribe_invitations"))
	linkSvc.SetTribePeers(tribeSvc.Peers)
	folderSvc.SetTribePeers(tribeSvc.Peers)
	childSvc.SetTribePeers(tribeSvc.Peers)
	// Emails transactionnels (Brevo) : bienvenue, ajout à une tribu, suppression du compte
	mailer := mail.New(mail.NewSender(cfg.BrevoAPIKey, cfg.MailFrom, cfg.MailFromName), cfg.SiteURL, cfg.SupportEmail)
	authSvc.SetNotifier(mailer)
	// Mot de passe oublié : lien vers l'écran « Nouveau mot de passe » du site
	// (ouvert dans l'application Android quand elle est installée)
	authSvc.SetPasswordReset(database.Col("password_resets"), strings.TrimRight(cfg.SiteURL, "/")+"/mot-de-passe/nouveau", mailer)
	// Confirmation de l'adresse : lien vers l'écran « Confirmer mon adresse »
	// du site (ou l'application). Publier et écrire à d'autres l'exigent.
	authSvc.SetEmailVerification(database.Col("email_verifications"), strings.TrimRight(cfg.SiteURL, "/")+"/verifier-email", mailer)
	linkSvc.SetEmailCheck(authSvc.RequireVerifiedEmail)
	folderSvc.SetEmailCheck(authSvc.RequireVerifiedEmail)
	tribeSvc.SetEmailVerifier(authSvc)
	// Invitations dans le cercle : lien vers l'écran « Rejoindre le cercle » du site
	folderSvc.SetInvitations(database.Col("folder_invitations"), strings.TrimRight(cfg.SiteURL, "/")+"/invitation-cercle", mailer, authSvc.MarkEmailVerified)
	// Invitations dans une tribu : lien vers l'écran « Rejoindre la tribu » du site
	tribeSvc.SetNotifier(mailer, strings.TrimRight(cfg.SiteURL, "/")+"/invitation")
	followSvc := follow.NewService(database.Col("follows"), database.Col("users"))
	commentSvc := comment.NewService(database.Col("comments"), database.Col("links"), database.Col("users"))
	aiSvc := ai.NewService(ai.Config{
		OllamaURL:       cfg.OllamaURL,
		OllamaModel:     cfg.OllamaModel,
		OllamaKeepAlive: cfg.OllamaKeepAlive,
		OllamaNumThread: cfg.OllamaNumThread,
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

	// Extrait le userID du header Authorization pour les routes HTTP hors gateway
	bearerUser := func(r *http.Request) (string, error) {
		token, ok := strings.CutPrefix(r.Header.Get("Authorization"), "Bearer ")
		if !ok || token == "" {
			return "", errors.New("missing bearer token")
		}
		return authSvc.ValidateToken(token)
	}

	aiH := ai.NewHandler(aiSvc, bearerUser)
	// Droits RGPD : export des données et suppression du compte
	accountSvc := account.NewService(database.DB(), tribeSvc)
	accountSvc.SetNotifier(mailer)
	accountH := account.NewHandler(accountSvc, authSvc, bearerUser)
	// Photos des idées (GridFS) ; celles qu'aucune idée n'utilise sont effacées après un jour
	photoSvc := photo.NewService(database.DB())
	photoH := photo.NewHandler(photoSvc, bearerUser)
	go photoSvc.RunCleanup(context.Background(), 6*time.Hour, 24*time.Hour)

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
	photoLimiter := httpx.NewRateLimiter(30, time.Minute)

	var handler http.Handler = withSPA(mux, cfg.SiteURL, seo.NewSource(linkSvc, folderSvc))
	handler = withPreview(preview.Handler(bearerUser), handler)
	handler = withAI(aiH, handler)
	handler = withAccount(accountH, handler)
	handler = httpx.Limit(costlyLimiter, map[string]bool{"/v1/ai/generate": true, "/v1/links/preview": true, "/v1/tribe/members": true, "/v1/circle/invitations": true}, handler)
	handler = httpx.Limit(authLimiter, map[string]bool{"/v1/auth/login": true, "/v1/auth/register": true, "/v1/auth/refresh": true, "/v1/auth/password/forgot": true, "/v1/auth/password/reset": true, "/v1/auth/email/verify": true, "/v1/auth/email/resend": true, account.DeletePath: true}, handler)
	handler = httpx.MaxBody(1<<20, handler)
	// Les photos ont leur propre limite de taille (photo.MaxUpload), hors MaxBody
	handler = httpx.Limit(photoLimiter, map[string]bool{photo.UploadPath: true}, withPhotos(photoH, handler))
	handler = httpx.CORS(cfg.AllowedOrigins, handler)
	// Ancien domaine et www : tout redirige vers SITE_URL
	handler = httpx.CanonicalHost(cfg.SiteURL, cfg.RedirectHosts, handler)
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
	// Laisse partir les emails en cours (confirmation de suppression…)
	mailer.Wait(shutdownCtx)
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

func withAccount(accountH http.Handler, next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path == account.ExportPath || r.URL.Path == account.DeletePath {
			accountH.ServeHTTP(w, r)
			return
		}
		next.ServeHTTP(w, r)
	})
}

func withPhotos(photoH http.Handler, next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path == photo.UploadPath || strings.HasPrefix(r.URL.Path, photo.Path) {
			photoH.ServeHTTP(w, r)
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
func withSPA(apiHandler http.Handler, siteURL string, content seo.Source) http.Handler {
	// STATIC_DIR : pour lancer le serveur hors Docker sur un build local du frontend
	staticDir := os.Getenv("STATIC_DIR")
	if staticDir == "" {
		staticDir = "/public"
	}

	// Si le dossier n'existe pas, on ne sert que l'API
	if _, err := os.Stat(staticDir); os.IsNotExist(err) {
		log.Println("No /public directory found, serving API only")
		return apiHandler
	}

	log.Printf("Serving frontend from %s", staticDir)
	fs := http.FileServer(http.Dir(staticDir))
	// Pages rendues pour les moteurs et assistants IA (en-tête et contenu propres
	// à chaque adresse), plan du site et llms.txt
	pages, err := seo.New(siteURL, staticDir, content)
	if err != nil {
		log.Fatalf("seo: %v", err)
	}
	sitemap, llms := pages.Sitemap(), pages.LLMs()

	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		// Les routes API passent directement au handler gRPC-gateway
		if strings.HasPrefix(r.URL.Path, "/v1/") {
			apiHandler.ServeHTTP(w, r)
			return
		}

		switch r.URL.Path {
		case "/sitemap.xml":
			sitemap(w, r)
			return
		case "/llms.txt":
			llms(w, r)
			return
		}

		// Essayer de servir le fichier statique
		// path.Clean sur un chemin absolu supprime les "..": on reste dans staticDir
		path := filepath.Join(staticDir, filepath.FromSlash(pathpkg.Clean("/"+r.URL.Path)))
		if info, err := os.Stat(path); err == nil && !info.IsDir() {
			fs.ServeHTTP(w, r)
			return
		}

		// Application : index.html avec l'en-tête et le contenu de l'adresse
		pages.ServeHTML(w, r)
	})
}
