package folder

import (
	"context"
	"testing"
	"time"

	pb "github.com/tribbae/backend/gen/tribbae/v1"
	"github.com/tribbae/backend/internal/interceptor"
	"go.mongodb.org/mongo-driver/v2/bson"
	"go.mongodb.org/mongo-driver/v2/mongo"
	"go.mongodb.org/mongo-driver/v2/mongo/options"
)

// « C'est une liste de cadeaux » est enregistré à la création comme à la
// modification du dossier, et se coupe (avant : jamais écrit en base).
func TestGiftListSettingIsSaved(t *testing.T) {
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()
	client, err := mongo.Connect(options.Client().ApplyURI("mongodb://localhost:27017").SetServerSelectionTimeout(3 * time.Second))
	if err != nil || client.Ping(ctx, nil) != nil {
		t.Skip("Skipping test: MongoDB unavailable")
	}
	db := client.Database("tribbae_test_" + bson.NewObjectID().Hex())
	t.Cleanup(func() {
		_ = db.Drop(context.Background())
		_ = client.Disconnect(context.Background())
	})
	h := NewHandler(NewService(db.Collection("folders"), db.Collection("links"), db.Collection("users"), "https://tribbae.example"))
	alice := bson.NewObjectID().Hex()
	ctx = interceptor.WithUserID(ctx, alice)
	stored := func(id string) bool {
		t.Helper()
		oid, _ := bson.ObjectIDFromHex(id)
		var f Folder
		if err := db.Collection("folders").FindOne(ctx, bson.M{"_id": oid}).Decode(&f); err != nil {
			t.Fatal(err)
		}
		return f.GiftReservations
	}

	created, err := h.CreateFolder(ctx, &pb.CreateFolderRequest{Name: "Noël de Léo", Icon: "STAR", Color: "RED", GiftReservations: true})
	if err != nil {
		t.Fatal(err)
	}
	if !created.Folder.GiftReservations || !stored(created.Folder.Id) {
		t.Fatal("une liste de cadeaux créée comme telle doit l'être en base")
	}

	plain, err := h.CreateFolder(ctx, &pb.CreateFolderRequest{Name: "Anniversaire", Icon: "CAKE", Color: "PINK"})
	if err != nil {
		t.Fatal(err)
	}
	update := func(id string, on *bool) *pb.Folder {
		t.Helper()
		r, err := h.UpdateFolder(ctx, &pb.UpdateFolderRequest{FolderId: id, Name: "Anniversaire", Icon: "CAKE", Color: "PINK", GiftReservations: on})
		if err != nil {
			t.Fatal(err)
		}
		return r.Folder
	}
	on, off := true, false
	if f := update(plain.Folder.Id, &on); !f.GiftReservations || !stored(plain.Folder.Id) {
		t.Fatal("activer la liste de cadeaux doit être enregistré")
	}
	// Un ancien client qui n'envoie pas le champ ne la coupe pas
	if f := update(plain.Folder.Id, nil); !f.GiftReservations || !stored(plain.Folder.Id) {
		t.Fatal("un champ absent ne doit pas couper la liste de cadeaux")
	}
	if f := update(plain.Folder.Id, &off); f.GiftReservations || stored(plain.Folder.Id) {
		t.Fatal("couper la liste de cadeaux doit être enregistré")
	}
}
