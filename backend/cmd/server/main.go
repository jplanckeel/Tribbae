package main

import (
	"context"
	"log"
	"net"
	"net/http"

	"github.com/grpc-ecosystem/grpc-gateway/v2/runtime"
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

	// Services
	authSvc := auth.NewService(database.Col("users"), cfg.JWTSecret)
	folderSvc := folder.NewService(database.Col("folders"), database.Col("links"), database.Col("users"), cfg.BaseURL)
	linkSvc := link.NewService(database.Col("links"), database.Col("folders"))
	childSvc := child.NewService(database.Col("children"))

	// Handlers (gRPC servers)
	authH := auth.NewHandler(authSvc)
	folderH := folder.NewHandler(folderSvc)
	linkH := link.NewHandler(linkSvc)
	childH := child.NewHandler(childSvc)

	// Serveur gRPC
	grpcServer := grpc.NewServer(
		grpc.UnaryInterceptor(interceptor.UnaryAuth(authSvc)),
	)
	pb.RegisterAuthServiceServer(grpcServer, authH)
	pb.RegisterFolderServiceServer(grpcServer, folderH)
	pb.RegisterLinkServiceServer(grpcServer, linkH)
	pb.RegisterChildServiceServer(grpcServer, childH)
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

	httpAddr := ":" + cfg.Port
	log.Printf("HTTP gateway listening on %s", httpAddr)
	log.Fatal(http.ListenAndServe(httpAddr, cors(withPreview(mux))))
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
