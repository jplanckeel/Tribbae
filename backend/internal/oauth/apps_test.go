package oauth

import (
	"context"
	"testing"

	"go.mongodb.org/mongo-driver/v2/bson"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"

	pb "github.com/tribbae/backend/gen/tribbae/v1"
	"github.com/tribbae/backend/internal/interceptor"
	"github.com/tribbae/backend/internal/link"
)

// Alice voit et retire l'accès qu'elle a donné ; Bob ne peut ni le voir ni
// le retirer. Retirer l'accès peut effacer les idées ajoutées par
// l'application, et seulement celles-là.
func TestConnectedApps(t *testing.T) {
	svc, db := newTestService(t)
	links := link.NewService(db.Collection("links"), db.Collection("folders"))
	h := NewAppsHandler(svc, links)
	alice, bob := bson.NewObjectID().Hex(), bson.NewObjectID().Hex()
	c := register(t, svc)
	tok, err := exchange(svc, c, approve(t, svc, c, alice, AuthorizeParams{}), testVerifier)
	if err != nil {
		t.Fatal(err)
	}
	ctx := context.Background()
	ideas := []any{
		bson.M{"owner_id": alice, "title": "Par Claude", "created_via": "mcp", "oauth_client_id": c.ID},
		bson.M{"owner_id": alice, "title": "Par une autre connexion", "created_via": "mcp", "oauth_client_id": "tbm_client_other"},
		bson.M{"owner_id": alice, "title": "Dans l'app"},
		bson.M{"owner_id": bob, "title": "Bob, même client", "created_via": "mcp", "oauth_client_id": c.ID},
	}
	res, err := db.Collection("links").InsertMany(ctx, ideas)
	if err != nil {
		t.Fatal(err)
	}
	byClaude := res.InsertedIDs[0].(bson.ObjectID).Hex()
	if _, err := db.Collection("link_likes").InsertOne(ctx, bson.M{"link_id": byClaude, "user_id": bob}); err != nil {
		t.Fatal(err)
	}

	aliceCtx := interceptor.WithUserID(ctx, alice)
	bobCtx := interceptor.WithUserID(ctx, bob)
	list, err := h.ListConnectedApps(aliceCtx, &pb.ListConnectedAppsRequest{})
	if err != nil {
		t.Fatal(err)
	}
	if len(list.Apps) != 1 || list.Apps[0].Name != "Claude" || list.Apps[0].Site != "claude.ai" || list.Apps[0].IdeaCount != 1 || len(list.Apps[0].Scopes) != 3 {
		t.Fatalf("applications d'Alice = %v", list.Apps)
	}
	appID := list.Apps[0].Id
	if l, _ := h.ListConnectedApps(bobCtx, &pb.ListConnectedAppsRequest{}); len(l.Apps) != 0 {
		t.Errorf("Bob voit l'accès d'Alice : %v", l.Apps)
	}
	if _, err := h.RevokeConnectedApp(bobCtx, &pb.RevokeConnectedAppRequest{Id: appID, DeleteIdeas: true}); status.Code(err) != codes.NotFound {
		t.Errorf("Bob retire l'accès d'Alice : %v", err)
	}
	if _, err := h.ListConnectedApps(ctx, &pb.ListConnectedAppsRequest{}); status.Code(err) != codes.Unauthenticated {
		t.Errorf("sans session : %v", err)
	}

	out, err := h.RevokeConnectedApp(aliceCtx, &pb.RevokeConnectedAppRequest{Id: appID, DeleteIdeas: true})
	if err != nil || out.DeletedIdeas != 1 {
		t.Fatalf("révocation : %v %v", out, err)
	}
	if _, err := svc.VerifyAccessToken(ctx, tok.AccessToken); err == nil {
		t.Error("jeton encore valide après la révocation")
	}
	if n, _ := db.Collection("links").CountDocuments(ctx, bson.M{}); n != 3 {
		t.Errorf("%d idées restantes, want 3 (seule celle de cette connexion est effacée)", n)
	}
	if n, _ := db.Collection("link_likes").CountDocuments(ctx, bson.M{}); n != 0 {
		t.Errorf("« j'aime » de l'idée effacée encore là")
	}
	if l, _ := h.ListConnectedApps(aliceCtx, &pb.ListConnectedAppsRequest{}); len(l.Apps) != 0 {
		t.Errorf("accès encore listé : %v", l.Apps)
	}
}
