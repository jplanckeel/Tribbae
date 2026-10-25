// Package photo garde les photos envoyées par les clients (« Une photo » à
// l'ajout d'une idée) dans MongoDB, avec GridFS (seau « photos »). Chaque
// photo est redressée, réduite et réencodée sans métadonnées, puis servie
// publiquement à une adresse impossible à deviner (jeton aléatoire), comme
// une image de n'importe quel site : la fiche d'une idée privée ne la montre
// qu'à ceux qui voient l'idée.
package photo

import (
	"bytes"
	"context"
	"crypto/rand"
	"encoding/hex"
	"errors"
	"fmt"
	"log"
	"regexp"
	"strings"
	"time"

	"go.mongodb.org/mongo-driver/v2/bson"
	"go.mongodb.org/mongo-driver/v2/mongo"
	"go.mongodb.org/mongo-driver/v2/mongo/options"
)

// Bucket : seau GridFS (collections photos.files et photos.chunks).
const Bucket = "photos"

var ErrNotFound = errors.New("photo not found")

// tokenRe : forme des jetons (128 bits en hexadécimal).
var tokenRe = regexp.MustCompile(`^[0-9a-f]{32}$`)

// ValidToken indique si s a la forme d'un jeton de photo.
func ValidToken(s string) bool { return tokenRe.MatchString(s) }

type Service struct {
	db *mongo.Database
	// sem borne les décodages simultanés (mémoire et CPU du petit VPS)
	sem chan struct{}
}

func NewService(db *mongo.Database) *Service {
	return &Service{db: db, sem: make(chan struct{}, 2)}
}

func bucket(db *mongo.Database) *mongo.GridFSBucket {
	return db.GridFSBucket(options.GridFSBucket().SetName(Bucket))
}

func (s *Service) files() *mongo.Collection { return s.db.Collection(Bucket + ".files") }

// Save normalise la photo et l'enregistre au nom de ownerID ; renvoie son jeton.
func (s *Service) Save(ctx context.Context, ownerID string, data []byte) (string, error) {
	select {
	case s.sem <- struct{}{}:
	case <-ctx.Done():
		return "", ctx.Err()
	}
	jpg, err := Normalize(data)
	<-s.sem
	if err != nil {
		return "", err
	}

	raw := make([]byte, 16)
	if _, err := rand.Read(raw); err != nil {
		return "", err
	}
	token := hex.EncodeToString(raw)
	meta := bson.M{"owner_id": ownerID, "content_type": "image/jpeg"}
	if _, err := bucket(s.db).UploadFromStream(ctx, token, bytes.NewReader(jpg), options.GridFSUpload().SetMetadata(meta)); err != nil {
		return "", fmt.Errorf("upload: %w", err)
	}
	return token, nil
}

// Open ouvre la photo du jeton ; l'appelant ferme le flux.
func (s *Service) Open(ctx context.Context, token string) (*mongo.GridFSDownloadStream, error) {
	if !ValidToken(token) {
		return nil, ErrNotFound
	}
	ds, err := bucket(s.db).OpenDownloadStreamByName(ctx, token)
	if errors.Is(err, mongo.ErrFileNotFound) {
		return nil, ErrNotFound
	}
	return ds, err
}

// DeleteOwnedBy efface toutes les photos envoyées par userID (suppression du compte).
func DeleteOwnedBy(ctx context.Context, db *mongo.Database, userID string) error {
	b := bucket(db)
	cur, err := db.Collection(Bucket+".files").Find(ctx, bson.M{"metadata.owner_id": userID}, options.Find().SetProjection(bson.M{"_id": 1}))
	if err != nil {
		return err
	}
	var docs []struct {
		ID bson.ObjectID `bson:"_id"`
	}
	if err := cur.All(ctx, &docs); err != nil {
		return err
	}
	for _, d := range docs {
		if err := b.Delete(ctx, d.ID); err != nil && !errors.Is(err, mongo.ErrFileNotFound) {
			return err
		}
	}
	return nil
}

// RunCleanup efface régulièrement les photos qu'aucune idée ni aucun dossier
// n'utilise plus (idée abandonnée avant d'être enregistrée, photo remplacée,
// idée supprimée). Une photo récente (moins de grace) est toujours gardée :
// l'app Android l'envoie juste avant d'enregistrer l'idée.
func (s *Service) RunCleanup(ctx context.Context, every, grace time.Duration) {
	t := time.NewTicker(every)
	defer t.Stop()
	for {
		if n, err := s.cleanup(ctx, time.Now().Add(-grace)); err != nil {
			log.Printf("photo: cleanup failed: %v", err)
		} else if n > 0 {
			log.Printf("photo: %d unused photos removed", n)
		}
		select {
		case <-ctx.Done():
			return
		case <-t.C:
		}
	}
}

const cleanupBatch = 100

func (s *Service) cleanup(ctx context.Context, before time.Time) (int, error) {
	b := bucket(s.db)
	removed := 0
	var last bson.ObjectID
	for {
		filter := bson.M{"uploadDate": bson.M{"$lt": before}}
		if !last.IsZero() {
			filter["_id"] = bson.M{"$gt": last}
		}
		cur, err := s.files().Find(ctx, filter, options.Find().
			SetSort(bson.M{"_id": 1}).SetLimit(cleanupBatch).SetProjection(bson.M{"_id": 1, "filename": 1}))
		if err != nil {
			return removed, err
		}
		var docs []struct {
			ID       bson.ObjectID `bson:"_id"`
			Filename string        `bson:"filename"`
		}
		if err := cur.All(ctx, &docs); err != nil {
			return removed, err
		}
		if len(docs) == 0 {
			return removed, nil
		}
		last = docs[len(docs)-1].ID

		tokens := make([]string, 0, len(docs))
		for _, d := range docs {
			if ValidToken(d.Filename) {
				tokens = append(tokens, d.Filename)
			}
		}
		used, err := s.used(ctx, tokens)
		if err != nil {
			return removed, err
		}
		for _, d := range docs {
			if used[d.Filename] {
				continue
			}
			if err := b.Delete(ctx, d.ID); err != nil && !errors.Is(err, mongo.ErrFileNotFound) {
				return removed, err
			}
			removed++
		}
		if len(docs) < cleanupBatch {
			return removed, nil
		}
	}
}

// used renvoie les jetons encore cités par une idée (image) ou un dossier (bannière).
func (s *Service) used(ctx context.Context, tokens []string) (map[string]bool, error) {
	out := map[string]bool{}
	if len(tokens) == 0 {
		return out, nil
	}
	re := regexp.MustCompile(Path + `(` + strings.Join(tokens, "|") + `)`)
	pattern := bson.Regex{Pattern: re.String()}
	for _, c := range []struct{ col, field string }{{"links", "image_url"}, {"folders", "banner_url"}} {
		cur, err := s.db.Collection(c.col).Find(ctx, bson.M{c.field: pattern}, options.Find().SetProjection(bson.M{c.field: 1}))
		if err != nil {
			return nil, fmt.Errorf("%s: %w", c.col, err)
		}
		var docs []bson.M
		if err := cur.All(ctx, &docs); err != nil {
			return nil, err
		}
		for _, d := range docs {
			v, _ := d[c.field].(string)
			if m := re.FindStringSubmatch(v); m != nil {
				out[m[1]] = true
			}
		}
	}
	return out, nil
}
