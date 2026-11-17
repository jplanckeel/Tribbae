package link

import (
	"context"
	"errors"
	"log"
	"net/url"
	"strconv"
	"strings"
	"time"

	"go.mongodb.org/mongo-driver/v2/bson"
	"go.mongodb.org/mongo-driver/v2/mongo/options"
)

// Images qui expirent. Instagram, Facebook et TikTok signent les adresses de
// leurs images pour quelques jours : passé ce délai, l'image d'une idée
// disparaît. Dès qu'une idée reçoit une telle image, le serveur en garde une
// copie (comme une photo envoyée, au nom de l'auteur de l'idée) et l'idée
// pointe vers cette copie. L'adresse d'origine reste dans image_source : un
// client qui la renvoie (il ne connaît pas encore la copie) ne la remet pas.
// Une image déjà expirée est retirée, et le rattrapage relit la page pour en
// trouver une fraîche, copiée à son tour.

// expiringHosts : domaines dont les images expirent (le domaine lui-même ou un
// sous-domaine).
var expiringHosts = []string{"cdninstagram.com", "fbcdn.net", "tiktokcdn.com", "tiktokcdn-eu.com", "tiktokcdn-us.com"}

// ExpiringImage indique si l'image est servie par un CDN dont les adresses expirent.
func ExpiringImage(raw string) bool {
	u, err := url.Parse(raw)
	if err != nil || (u.Scheme != "https" && u.Scheme != "http") {
		return false
	}
	host := strings.ToLower(u.Hostname())
	// Images génériques du site (logo, icônes) : jamais retenues comme image d'idée
	if strings.HasPrefix(host, "static.") {
		return false
	}
	for _, h := range expiringHosts {
		if host == h || strings.HasSuffix(host, "."+h) {
			return true
		}
	}
	return false
}

// imageExpired indique si l'adresse signée a dépassé sa date limite :
// paramètre oe (Instagram, Facebook : secondes en hexadécimal) ou x-expires
// (TikTok : secondes). Sans date lisible : non expirée.
func imageExpired(raw string, now time.Time) bool {
	u, err := url.Parse(raw)
	if err != nil {
		return false
	}
	q := u.Query()
	if oe := q.Get("oe"); oe != "" {
		if sec, err := strconv.ParseInt(oe, 16, 64); err == nil {
			return now.Unix() >= sec
		}
	}
	if xe := q.Get("x-expires"); xe != "" {
		if sec, err := strconv.ParseInt(xe, 10, 64); err == nil {
			return now.Unix() >= sec
		}
	}
	return false
}

// ImageMirror garde une copie de l'image au nom de ownerID et renvoie son
// adresse chez Tribbae. Erreur ErrImageGone : l'image n'est plus servie.
type ImageMirror func(ctx context.Context, ownerID, imageURL string) (string, error)

// ErrImageGone : l'image d'origine n'existe plus (expirée, supprimée).
var ErrImageGone = errors.New("image is gone")

// SetImageMirror branche la copie des images qui expirent (photo.Service).
func (s *Service) SetImageMirror(m ImageMirror) { s.mirror = m }

const (
	mirrorTimeout    = 30 * time.Second
	mirrorRetryAfter = 6 * time.Hour // échec passager (réseau) : nouvel essai plus tard
)

// imageFields : champs à écrire avec image_url. Une image qui expire est
// marquée à copier (image_expiring, servi par l'index partiel
// idx_links_expiring_image) ; une autre efface la marque et l'adresse d'origine.
func imageFields(imageURL string) (set, unset bson.M) {
	if ExpiringImage(imageURL) {
		return bson.M{"image_expiring": true}, bson.M{"image_source": ""}
	}
	return bson.M{}, bson.M{"image_expiring": "", "image_source": ""}
}

// mirrorSoon copie tout de suite l'image d'une idée qui vient de la recevoir,
// sans retarder la réponse à l'utilisateur.
func (s *Service) mirrorSoon(id bson.ObjectID, ownerID, imageURL, pageURL string) {
	if s.mirror == nil {
		return
	}
	go func() {
		ctx, cancel := context.WithTimeout(context.Background(), 2*mirrorTimeout)
		defer cancel()
		s.mirrorOne(ctx, id, ownerID, imageURL, pageURL)
	}()
}

// mirrorOne copie l'image d'une idée et remplace son adresse, si l'idée a
// toujours cette image (l'utilisateur a pu en choisir une autre entre-temps).
// Image expirée ou disparue : retirée, puis le rattrapage relit la page.
// Renvoie vrai si l'idée pointe maintenant vers la copie.
func (s *Service) mirrorOne(ctx context.Context, id bson.ObjectID, ownerID, imageURL, pageURL string) bool {
	same := bson.M{"_id": id, "image_url": imageURL}
	var copied string
	err := ErrImageGone
	if !imageExpired(imageURL, time.Now()) {
		mctx, cancel := context.WithTimeout(ctx, mirrorTimeout)
		copied, err = s.mirror(mctx, ownerID, imageURL)
		cancel()
	}
	switch {
	case err == nil:
		_, err = s.col.UpdateOne(ctx, same, bson.M{
			"$set":   bson.M{"image_url": copied, "image_source": imageURL},
			"$unset": bson.M{"image_expiring": ""},
		})
		return err == nil
	case errors.Is(err, ErrImageGone):
		// Relire la page pour une adresse fraîche, sauf si on en vient (pageURL
		// vide) : la date d'essai évite alors de recommencer à chaque passage
		update := bson.M{"$set": bson.M{"image_url": ""}, "$unset": bson.M{"image_expiring": "", "image_source": "", "image_checked_at": ""}}
		if pageURL == "" {
			update = bson.M{"$set": bson.M{"image_url": "", "image_checked_at": time.Now()}, "$unset": bson.M{"image_expiring": "", "image_source": ""}}
		}
		res, uerr := s.col.UpdateOne(ctx, same, update)
		if uerr == nil && res.ModifiedCount > 0 && pageURL != "" {
			s.backfillOne(ctx, id, pageURL)
		}
	default:
		log.Printf("link: image mirror: %v", err)
		_, _ = s.col.UpdateOne(ctx, same, bson.M{"$set": bson.M{"image_checked_at": time.Now()}})
	}
	return false
}

// mirrorBatch copie les images qui expirent des idées existantes (enregistrées
// avant la copie, ou dont la copie a échoué il y a plus de mirrorRetryAfter).
func (s *Service) mirrorBatch(ctx context.Context, batch int) (copied, tried int) {
	if s.mirror == nil {
		return 0, 0
	}
	filter := bson.M{
		"image_expiring": true,
		"$or": bson.A{
			bson.M{"image_checked_at": bson.M{"$exists": false}},
			bson.M{"image_checked_at": bson.M{"$lt": time.Now().Add(-mirrorRetryAfter)}},
		},
	}
	opts := options.Find().SetLimit(int64(batch)).SetProjection(bson.M{"_id": 1, "owner_id": 1, "image_url": 1, "url": 1})
	cur, err := s.col.Find(ctx, filter, opts)
	if err != nil {
		log.Printf("link: image mirror query: %v", err)
		return 0, 0
	}
	var todo []struct {
		ID       bson.ObjectID `bson:"_id"`
		OwnerID  string        `bson:"owner_id"`
		ImageURL string        `bson:"image_url"`
		URL      string        `bson:"url"`
	}
	if err := cur.All(ctx, &todo); err != nil {
		return 0, 0
	}
	for i, l := range todo {
		if i > 0 {
			select {
			case <-ctx.Done():
				return copied, tried
			case <-time.After(backfillPause):
			}
		}
		tried++
		if s.mirrorOne(ctx, l.ID, l.OwnerID, l.ImageURL, l.URL) {
			copied++
		}
	}
	return copied, tried
}

// markExpiringImages marque les idées enregistrées avant la copie des images
// qui expirent, pour que mirrorBatch les traite.
func (s *Service) markExpiringImages(ctx context.Context) {
	pattern := `^https?://([a-z0-9-]+\.)*(` + strings.ReplaceAll(strings.Join(expiringHosts, "|"), ".", `\.`) + `)/`
	res, err := s.col.UpdateMany(ctx,
		bson.M{"image_url": bson.M{"$regex": pattern, "$options": "i"}, "image_expiring": bson.M{"$exists": false}},
		bson.M{"$set": bson.M{"image_expiring": true}})
	if err != nil {
		log.Printf("link: mark expiring images: %v", err)
	} else if res.ModifiedCount > 0 {
		log.Printf("link: %d links have an image that expires", res.ModifiedCount)
	}
}
