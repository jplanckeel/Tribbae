package moderation

import (
	"context"
	"errors"
	"testing"
	"time"

	"go.mongodb.org/mongo-driver/v2/bson"
	"go.mongodb.org/mongo-driver/v2/mongo"
	"go.mongodb.org/mongo-driver/v2/mongo/options"
)

type fakeNotifier struct{ calls int }

func (f *fakeNotifier) ReportReceived(_, _ string) { f.calls++ }

func setup(t *testing.T) (*Service, *mongo.Database, *fakeNotifier) {
	t.Helper()
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	client, err := mongo.Connect(options.Client().ApplyURI("mongodb://localhost:27017").SetServerSelectionTimeout(3 * time.Second))
	if err != nil {
		t.Skipf("mongodb unavailable: %v", err)
	}
	if err := client.Ping(ctx, nil); err != nil {
		t.Skipf("mongodb unavailable: %v", err)
	}
	db := client.Database("tribbae_test_" + bson.NewObjectID().Hex())
	t.Cleanup(func() {
		_ = db.Drop(context.Background())
		_ = client.Disconnect(context.Background())
	})
	svc := NewService(db)
	n := &fakeNotifier{}
	svc.SetNotifier(n)
	return svc, db, n
}

func insert(t *testing.T, db *mongo.Database, col string, doc bson.M) string {
	t.Helper()
	id := bson.NewObjectID()
	doc["_id"] = id
	if _, err := db.Collection(col).InsertOne(context.Background(), doc); err != nil {
		t.Fatal(err)
	}
	return id.Hex()
}

func TestReportIsValidatedDeduplicatedAndNotified(t *testing.T) {
	svc, db, n := setup(t)
	ctx := context.Background()
	alice := insert(t, db, "users", bson.M{"display_name": "Alice"})
	bob := insert(t, db, "users", bson.M{"display_name": "Bob"})
	idea := insert(t, db, "links", bson.M{"owner_id": alice, "title": "Idée d'Alice", "visibility": "public"})

	if err := svc.Report(ctx, bob, TargetIdea, idea, "nope", ""); !errors.Is(err, ErrInvalidInput) {
		t.Errorf("unknown reason: %v", err)
	}
	if err := svc.Report(ctx, bob, "folder", idea, "spam", ""); !errors.Is(err, ErrInvalidInput) {
		t.Errorf("unknown target type: %v", err)
	}
	if err := svc.Report(ctx, alice, TargetIdea, idea, "spam", ""); !errors.Is(err, ErrInvalidInput) {
		t.Errorf("reporting oneself: %v", err)
	}
	if err := svc.Report(ctx, bob, TargetIdea, bson.NewObjectID().Hex(), "spam", ""); !errors.Is(err, ErrNotFound) {
		t.Errorf("missing target: %v", err)
	}
	for i := 0; i < 2; i++ {
		if err := svc.Report(ctx, bob, TargetIdea, idea, "spam", "deux fois"); err != nil {
			t.Fatal(err)
		}
	}
	if c, _ := db.Collection("reports").CountDocuments(ctx, bson.M{}); c != 1 {
		t.Errorf("%d reports, want one per reporter and content", c)
	}
	if n.calls != 1 {
		t.Errorf("admin notified %d times, want 1", n.calls)
	}
	list, err := svc.ListReports(ctx, false)
	if err != nil || len(list) != 1 {
		t.Fatalf("open reports = %v, %v", list, err)
	}
	r := list[0]
	if r.TargetSummary != "Idée d'Alice" || r.OwnerName != "Alice" || r.ReporterName != "Bob" {
		t.Errorf("report = %+v", r)
	}
}

func TestReportQuota(t *testing.T) {
	svc, db, _ := setup(t)
	ctx := context.Background()
	alice := insert(t, db, "users", bson.M{"display_name": "Alice"})
	bob := insert(t, db, "users", bson.M{"display_name": "Bob"})
	for i := 0; i < dailyReports; i++ {
		idea := insert(t, db, "links", bson.M{"owner_id": alice, "title": "Idée", "visibility": "public"})
		if err := svc.Report(ctx, bob, TargetIdea, idea, "spam", ""); err != nil {
			t.Fatal(err)
		}
	}
	idea := insert(t, db, "links", bson.M{"owner_id": alice, "title": "Une de trop", "visibility": "public"})
	if err := svc.Report(ctx, bob, TargetIdea, idea, "spam", ""); !errors.Is(err, ErrTooMany) {
		t.Errorf("over quota: %v", err)
	}
}

func TestBlockAndUnblock(t *testing.T) {
	svc, db, _ := setup(t)
	ctx := context.Background()
	alice := insert(t, db, "users", bson.M{"display_name": "Alice"})
	bob := insert(t, db, "users", bson.M{"display_name": "Bob"})

	if err := svc.Block(ctx, alice, alice); !errors.Is(err, ErrInvalidInput) {
		t.Errorf("blocking oneself: %v", err)
	}
	if err := svc.Block(ctx, alice, bson.NewObjectID().Hex()); !errors.Is(err, ErrNotFound) {
		t.Errorf("blocking a missing account: %v", err)
	}
	for i := 0; i < 2; i++ {
		if err := svc.Block(ctx, alice, bob); err != nil {
			t.Fatal(err)
		}
	}
	if !svc.BlockedSet(ctx, alice)[bob] || svc.BlockedSet(ctx, bob)[alice] {
		t.Error("a block goes one way only")
	}
	list, err := svc.ListBlocked(ctx, alice)
	if err != nil || len(list) != 1 || list[0].DisplayName != "Bob" {
		t.Fatalf("blocked = %+v, %v", list, err)
	}
	if err := svc.Unblock(ctx, alice, bob); err != nil {
		t.Fatal(err)
	}
	if len(svc.BlockedSet(ctx, alice)) != 0 {
		t.Error("unblock kept the block")
	}
}

func TestResolveActions(t *testing.T) {
	svc, db, _ := setup(t)
	ctx := context.Background()
	alice := insert(t, db, "users", bson.M{"display_name": "Alice"})
	bob := insert(t, db, "users", bson.M{"display_name": "Bob"})
	carol := insert(t, db, "users", bson.M{"display_name": "Carol"})
	idea := insert(t, db, "links", bson.M{"owner_id": alice, "title": "Idée", "visibility": "public"})
	other := insert(t, db, "links", bson.M{"owner_id": alice, "title": "Autre", "visibility": "public"})
	comment := insert(t, db, "comments", bson.M{"user_id": alice, "link_id": idea, "text": "Commentaire"})

	// Deux signalements de la même idée : une décision les traite tous
	_ = svc.Report(ctx, bob, TargetIdea, idea, "spam", "")
	_ = svc.Report(ctx, carol, TargetIdea, idea, "inappropriate", "")
	_ = svc.Report(ctx, bob, TargetComment, comment, "harassment", "")
	list, _ := svc.ListReports(ctx, false)
	var ideaReport, commentReport string
	for _, r := range list {
		if r.TargetType == TargetIdea {
			ideaReport = r.ID.Hex()
		} else {
			commentReport = r.ID.Hex()
		}
	}
	if err := svc.Resolve(ctx, ideaReport, "nope", "admin"); !errors.Is(err, ErrInvalidInput) {
		t.Errorf("unknown action: %v", err)
	}
	if err := svc.Resolve(ctx, ideaReport, ActionRemove, "admin"); err != nil {
		t.Fatal(err)
	}
	var l bson.M
	oid, _ := bson.ObjectIDFromHex(idea)
	_ = db.Collection("links").FindOne(ctx, bson.M{"_id": oid}).Decode(&l)
	if l["visibility"] != "private" || l["hidden"] != true {
		t.Errorf("removed idea = %v", l)
	}
	if open, _ := db.Collection("reports").CountDocuments(ctx, bson.M{"target_id": idea, "status": StatusOpen}); open != 0 {
		t.Errorf("%d reports of the removed idea still open", open)
	}

	if err := svc.Resolve(ctx, commentReport, ActionRemove, "admin"); err != nil {
		t.Fatal(err)
	}
	if c, _ := db.Collection("comments").CountDocuments(ctx, bson.M{}); c != 0 {
		t.Error("removed comment still there")
	}

	// Suspension : compte fermé et idées publiques masquées
	_ = svc.Report(ctx, bob, TargetAccount, alice, "harassment", "")
	list, _ = svc.ListReports(ctx, false)
	if len(list) != 1 {
		t.Fatalf("open reports = %d, want the account one", len(list))
	}
	if err := svc.Resolve(ctx, list[0].ID.Hex(), ActionSuspend, "admin"); err != nil {
		t.Fatal(err)
	}
	var u bson.M
	aid, _ := bson.ObjectIDFromHex(alice)
	_ = db.Collection("users").FindOne(ctx, bson.M{"_id": aid}).Decode(&u)
	if u["suspended_at"] == nil {
		t.Error("account not suspended")
	}
	ooid, _ := bson.ObjectIDFromHex(other)
	_ = db.Collection("links").FindOne(ctx, bson.M{"_id": ooid}).Decode(&l)
	if l["visibility"] != "private" || l["hidden"] != true {
		t.Errorf("public idea of a suspended account = %v", l)
	}

	// Classer sans suite
	_ = svc.Report(ctx, carol, TargetAccount, bob, "other", "")
	list, _ = svc.ListReports(ctx, false)
	if err := svc.Resolve(ctx, list[0].ID.Hex(), ActionDismiss, "admin"); err != nil {
		t.Fatal(err)
	}
	if closed, _ := svc.ListReports(ctx, true); len(closed) != 5 {
		t.Errorf("%d closed reports, want 5", len(closed))
	}
}
