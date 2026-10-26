package admin

import (
	"context"
	"net"
	"testing"

	pb "github.com/tribbae/backend/gen/tribbae/v1"
	"github.com/tribbae/backend/internal/auth"
	"github.com/tribbae/backend/internal/interceptor"
	"go.mongodb.org/mongo-driver/v2/bson"
	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/credentials/insecure"
	"google.golang.org/grpc/metadata"
	"google.golang.org/grpc/status"
	"google.golang.org/grpc/test/bufconn"
	"google.golang.org/protobuf/types/known/emptypb"
)

// TestAdminAccess passe par les mêmes intercepteurs que le serveur (main.go) :
// chaque méthode d'AdminService, GetStats compris, est refusée sans session
// et à un compte non admin.
func TestAdminAccess(t *testing.T) {
	db := setupDB(t)
	ctx := context.Background()
	authSvc := auth.NewService(db.Collection("users"), "test-secret-test-secret-test-secret")
	_, userToken, err := authSvc.Register(ctx, "alice@example.com", "mot-de-passe", "Alice")
	if err != nil {
		t.Fatal(err)
	}
	admin, adminToken, err := authSvc.Register(ctx, "admin@example.com", "mot-de-passe", "Admin")
	if err != nil {
		t.Fatal(err)
	}
	if _, err := db.Collection("users").UpdateByID(ctx, admin.ID, bson.M{"$set": bson.M{"is_admin": true}}); err != nil {
		t.Fatal(err)
	}

	lis := bufconn.Listen(1 << 20)
	srv := grpc.NewServer(grpc.ChainUnaryInterceptor(
		interceptor.UnaryAuth(authSvc),
		interceptor.UnaryAdminAuth(authSvc),
	))
	pb.RegisterAdminServiceServer(srv, NewHandler(authSvc, NewStatsService(db.Collection("links"), db.Collection("users"))))
	go func() { _ = srv.Serve(lis) }()
	t.Cleanup(srv.Stop)
	conn, err := grpc.NewClient("passthrough:///bufnet",
		grpc.WithContextDialer(func(ctx context.Context, _ string) (net.Conn, error) { return lis.DialContext(ctx) }),
		grpc.WithTransportCredentials(insecure.NewCredentials()))
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = conn.Close() })

	as := func(token string) context.Context {
		return metadata.AppendToOutgoingContext(ctx, "authorization", "Bearer "+token)
	}
	for _, m := range pb.AdminService_ServiceDesc.Methods {
		method := "/" + pb.AdminService_ServiceDesc.ServiceName + "/" + m.MethodName
		// Requête vide : le refus doit tomber avant le handler
		if err := conn.Invoke(ctx, method, &emptypb.Empty{}, &emptypb.Empty{}); status.Code(err) != codes.Unauthenticated {
			t.Errorf("%s sans session : %v", m.MethodName, err)
		}
		if err := conn.Invoke(as(userToken), method, &emptypb.Empty{}, &emptypb.Empty{}); status.Code(err) != codes.PermissionDenied {
			t.Errorf("%s par un compte non admin : %v", m.MethodName, err)
		}
	}

	client := pb.NewAdminServiceClient(conn)
	res, err := client.GetStats(as(adminToken), &pb.GetStatsRequest{Period: PeriodMonth, Count: 6})
	if err != nil {
		t.Fatalf("GetStats par l'admin : %v", err)
	}
	// Les deux inscriptions comptent comme nouveaux comptes actifs ce mois-ci
	if len(res.NewUsers) != 6 || res.Totals.NewUsers != 2 || res.Totals.ActiveUsers != 2 {
		t.Errorf("GetStats : %d points, totaux %+v", len(res.NewUsers), res.Totals)
	}
	if _, err := client.GetStats(as(adminToken), &pb.GetStatsRequest{Count: 99}); status.Code(err) != codes.InvalidArgument {
		t.Errorf("count hors bornes : %v", err)
	}
}
