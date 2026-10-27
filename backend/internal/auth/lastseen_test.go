package auth

import (
	"context"
	"testing"
	"time"

	"go.mongodb.org/mongo-driver/v2/bson"
)

func TestTouchLastSeenAtMostHourly(t *testing.T) {
	svc, db, _ := setupReset(t)
	ctx := context.Background()
	alice, _, err := svc.Register(ctx, "alice@example.com", "mot-de-passe", "Alice")
	if err != nil {
		t.Fatal(err)
	}
	lastSeen := func() time.Time {
		t.Helper()
		var u User
		if err := db.Collection("users").FindOne(ctx, bson.M{"_id": alice.ID}).Decode(&u); err != nil {
			t.Fatal(err)
		}
		if u.LastSeenAt == nil {
			t.Fatal("last_seen_at absent")
		}
		return *u.LastSeenAt
	}
	set := func(at time.Time) {
		t.Helper()
		if _, err := db.Collection("users").UpdateByID(ctx, alice.ID, bson.M{"$set": bson.M{"last_seen_at": at}}); err != nil {
			t.Fatal(err)
		}
	}

	// Moins d'une heure : pas de nouvelle écriture
	recent := time.Now().Add(-30 * time.Minute).Truncate(time.Millisecond)
	set(recent)
	if _, _, err := svc.Login(ctx, "alice@example.com", "mot-de-passe"); err != nil {
		t.Fatal(err)
	}
	if got := lastSeen(); !got.Equal(recent) {
		t.Errorf("réécrit dans l'heure : %s", got)
	}

	// Plus d'une heure : la connexion est notée
	set(time.Now().Add(-2 * time.Hour))
	before := time.Now().Add(-time.Second)
	svc.TouchLastSeen(ctx, alice.ID.Hex())
	if got := lastSeen(); got.Before(before) {
		t.Errorf("pas mis à jour : %s", got)
	}

	// Compte d'avant la mesure (sans last_seen_at)
	if _, err := db.Collection("users").UpdateByID(ctx, alice.ID, bson.M{"$unset": bson.M{"last_seen_at": ""}}); err != nil {
		t.Fatal(err)
	}
	svc.TouchLastSeen(ctx, alice.ID.Hex())
	lastSeen()
}
