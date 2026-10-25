package link

import (
	"context"
	"log"
	"time"

	"go.mongodb.org/mongo-driver/v2/bson"
	"go.mongodb.org/mongo-driver/v2/mongo/options"

	"github.com/tribbae/backend/internal/preview"
)

// Rattrapage des images : une idée créée sans image (site bloqué à ce moment,
// client hors ligne, image retirée d'une source…) en reçoit une dès qu'une
// tentative aboutit. Les tentatives sont datées (`image_checked_at`) pour ne
// pas solliciter sans cesse les mêmes sites.

const (
	backfillRetryAfter = 7 * 24 * time.Hour
	backfillPause      = 2 * time.Second // entre deux sites, pour rester discret
)

// fetchImage retourne la meilleure image d'une page (remplaçable en test).
var fetchImage = func(ctx context.Context, url string) string { return preview.Image(ctx, url) }

// RunImageBackfill traite périodiquement un lot d'idées sans image, jusqu'à
// l'annulation du contexte. À lancer dans une goroutine au démarrage.
func (s *Service) RunImageBackfill(ctx context.Context, every time.Duration, batch int) {
	// Anciennes idées sans champ image_url : vides, pour que la recherche des
	// idées sans image (égalité sur "") les voie
	if res, err := s.col.UpdateMany(ctx, bson.M{"image_url": nil}, bson.M{"$set": bson.M{"image_url": ""}}); err != nil {
		log.Printf("link: image_url normalization: %v", err)
	} else if res.ModifiedCount > 0 {
		log.Printf("link: image_url normalization: %d links", res.ModifiedCount)
	}
	for {
		if filled, tried := s.backfillBatch(ctx, batch); tried > 0 {
			log.Printf("link: image backfill: %d/%d links got an image", filled, tried)
		}
		select {
		case <-ctx.Done():
			return
		case <-time.After(every):
		}
	}
}

// backfillBatch tente les idées sans image jamais vérifiées (ou pas depuis une
// semaine), les plus récentes d'abord.
func (s *Service) backfillBatch(ctx context.Context, batch int) (filled, tried int) {
	filter := bson.M{
		"url": bson.M{"$nin": bson.A{"", nil}},
		// Égalité exacte : c'est ce que couvre l'index partiel idx_links_missing_image
		// (image_url est toujours écrit, vide quand l'idée n'a pas d'image)
		"image_url": "",
		"$or": bson.A{
			bson.M{"image_checked_at": bson.M{"$exists": false}},
			bson.M{"image_checked_at": bson.M{"$lt": time.Now().Add(-backfillRetryAfter)}},
		},
	}
	opts := options.Find().SetSort(bson.D{{Key: "created_at", Value: -1}}).SetLimit(int64(batch)).
		SetProjection(bson.M{"_id": 1, "url": 1})
	cur, err := s.col.Find(ctx, filter, opts)
	if err != nil {
		log.Printf("link: image backfill query: %v", err)
		return 0, 0
	}
	var todo []struct {
		ID  bson.ObjectID `bson:"_id"`
		URL string        `bson:"url"`
	}
	if err := cur.All(ctx, &todo); err != nil {
		return 0, 0
	}
	for i, l := range todo {
		if i > 0 {
			select {
			case <-ctx.Done():
				return filled, tried
			case <-time.After(backfillPause):
			}
		}
		tried++
		if s.backfillOne(ctx, l.ID, l.URL) {
			filled++
		}
	}
	return filled, tried
}

// backfillOne cherche l'image d'une idée et l'enregistre si l'idée n'en a
// toujours pas (l'utilisateur a pu en choisir une entre-temps).
func (s *Service) backfillOne(ctx context.Context, id bson.ObjectID, url string) bool {
	fctx, cancel := context.WithTimeout(ctx, 20*time.Second)
	img := fetchImage(fctx, url)
	cancel()
	set := bson.M{"image_checked_at": time.Now()}
	if img != "" {
		set["image_url"] = img
	}
	res, err := s.col.UpdateOne(ctx, bson.M{"_id": id, "image_url": bson.M{"$in": bson.A{"", nil}}}, bson.M{"$set": set})
	return err == nil && img != "" && res.ModifiedCount > 0
}

// backfillSoon relance tout de suite la recherche d'image d'une idée (adresse
// modifiée, image retirée), sans retarder la réponse à l'utilisateur.
func (s *Service) backfillSoon(id bson.ObjectID, url string) {
	go func() {
		ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
		defer cancel()
		s.backfillOne(ctx, id, url)
	}()
}
