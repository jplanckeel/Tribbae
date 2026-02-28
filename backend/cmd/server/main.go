package main

import (
	"context"
	"log"
	"net"
	"net/http"
	"os"
	"path/filepath"
	"strings"

	"github.com/grpc-ecosystem/grpc-gateway/v2/runtime"
	"github.com/tribbae/backend/internal/admin"
	"github.com/tribbae/backend/internal/ai"
	"github.com/tribbae/backend/internal/auth"
	"github.com/tribbae/backend/internal/child"
	"github.com/tribbae/backend/internal/config"
	"github.com/tribbae/backend/internal/db"
	"github.com/tribbae/backend/internal/folder"
	"github.com/tribbae/backend/internal/interceptor"
	"github.com/tribbae/backend/internal/link"
	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"
	"google.golang.org/grpc/reflection"
	"google.golang.org/protobuf/encoding/protojson"

	pb "github.com/tribbae/backend/gen/tribbae/v1"
)

func main() {
	cfg := config.Load()

	database, err := db.Connect(cfg.MongoURI, cfg.MongoDB)
	if err != nil {
		log.Fatalf("mongo connect: %v", err)
	}
	log.Println("connected to MongoDB")

	// Ensure MongoDB indexes exist
	if err := db.EnsureIndexes(context.Background(), database.DB()); err != nil {
		log.Fatalf("ensure indexes: %v", err)
	}

	// Services
	authSvc := auth.NewService(database.Col("users"), cfg.JWTSecret)
	folderSvc := folder.NewService(database.Col("folders"), database.Col("links"), database.Col("users"), cfg.BaseURL)
	linkSvc := link.NewService(database.Col("links"), database.Col("folders"))
	childSvc := child.NewService(database.DB())
	aiSvc := ai.NewService(cfg.OllamaURL, cfg.OllamaModel, cfg.SearxURL, cfg.GeminiAPIKey)

	// Handlers (gRPC servers)
	authH := auth.NewHandler(authSvc)
	folderH := folder.NewHandler(folderSvc)
	linkH := link.NewHandler(linkSvc)
	childH := child.NewHandler(childSvc)
	adminH := admin.NewHandler(authSvc)
	
	// Adaptateur pour récupérer le statut premium d'un utilisateur
	userGetter := &userGetterAdapter{authSvc: authSvc}
	
	aiH := ai.NewHandler(aiSvc, userGetter,
		// Token parser : extrait le userID du header Authorization
		func(r *http.Request) (string, error) {
			h := r.Header.Get("Authorization")
			if h == "" {
				return "", nil
			}
			token := h
			if len(h) > 7 && h[:7] == "Bearer " {
				token = h[7:]
			}
			return authSvc.ValidateToken(token)
		},
		// Folder creator : crée un dossier communautaire IA
		func(ctx context.Context, ownerID, name string) (string, error) {
			f, err := folderSvc.CreateAiFolder(ctx, ownerID, name)
			if err != nil {
				return "", err
			}
			return f.ID.Hex(), nil
		},
		// Link creator : crée un lien dans le dossier
		func(ctx context.Context, ownerID string, idea ai.SuggestedLink, folderID string) error {
			l := &link.Link{
				FolderID:    folderID,
				Title:       idea.Title,
				URL:         idea.URL,
				Description: idea.Description,
				Category:    idea.Category,
				Tags:        idea.Tags,
				AgeRange:    idea.AgeRange,
				Location:    idea.Location,
				Price:       idea.Price,
				ImageURL:    idea.ImageURL,
				Ingredients: idea.Ingredients,
			}
			_, err := linkSvc.Create(ctx, ownerID, l)
			return err
		},
	)

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
	pb.RegisterAdminServiceServer(grpcServer, adminH)
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
	if err := pb.RegisterAdminServiceHandlerFromEndpoint(ctx, mux, grpcAddr, opts); err != nil {
		log.Fatalf("register admin gateway: %v", err)
	}

	httpAddr := ":" + cfg.Port
	log.Printf("HTTP server listening on %s", httpAddr)
	handler := cors(withAI(aiH, withPreview(withSPA(mux))))
	log.Fatal(http.ListenAndServe(httpAddr, handler))
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

func withPreview(next http.Handler) http.Handler {
	preview := link.PreviewHandler()
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path == "/v1/links/preview" && r.Method == http.MethodGet {
			preview(w, r)
			return
		}
		next.ServeHTTP(w, r)
	})
}

func cors(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Access-Control-Allow-Origin", "*")
		w.Header().Set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
		w.Header().Set("Access-Control-Allow-Headers", "Content-Type, Authorization")
		if r.Method == http.MethodOptions {
			w.WriteHeader(http.StatusNoContent)
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
		path := filepath.Join(staticDir, r.URL.Path)
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
