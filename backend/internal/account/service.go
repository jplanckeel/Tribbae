// Package account met en œuvre les droits RGPD sur le compte : accès et
// portabilité (export de toutes ses données) et effacement (suppression
// définitive du compte et de tout ce qui lui appartient).
package account

import (
	"context"
	"errors"
	"fmt"
	"strings"
	"time"

	"go.mongodb.org/mongo-driver/v2/bson"
	"go.mongodb.org/mongo-driver/v2/mongo"
	"go.mongodb.org/mongo-driver/v2/mongo/options"

	"github.com/tribbae/backend/internal/photo"
)

var ErrNotFound = errors.New("account not found")

// TribeLeaver retire un compte de sa tribu (transfert au plus ancien membre
// si c'était son créateur). Implémenté par tribe.Service.
type TribeLeaver interface {
	Leave(ctx context.Context, userID string) error
}

// Notifier prévient par email (mail.Mailer).
type Notifier interface {
	AccountDeleted(email, name string)
}

type Service struct {
	db     *mongo.Database
	tribe  TribeLeaver
	notify Notifier
}

func NewService(db *mongo.Database, tribe TribeLeaver) *Service {
	return &Service{db: db, tribe: tribe}
}

// SetNotifier active l'email confirmant la suppression du compte.
func (s *Service) SetNotifier(n Notifier) { s.notify = n }

func (s *Service) col(name string) *mongo.Collection { return s.db.Collection(name) }

// Export rassemble toutes les données liées au compte, dans un format lisible
// et réutilisable (JSON). Le mot de passe (haché) n'en fait pas partie.
func (s *Service) Export(ctx context.Context, userID string) (map[string]any, error) {
	oid, err := bson.ObjectIDFromHex(userID)
	if err != nil {
		return nil, ErrNotFound
	}
	var user bson.M
	if err := s.col("users").FindOne(ctx, bson.M{"_id": oid}).Decode(&user); err != nil {
		if errors.Is(err, mongo.ErrNoDocuments) {
			return nil, ErrNotFound
		}
		return nil, err
	}
	delete(user, "password")

	// Sous-documents en bson.M (et non bson.D, défaut du driver v2) : l'export
	// est écrit en JSON, où un bson.D deviendrait une liste de {Key, Value}
	asMaps := s.db.Client().Database(s.db.Name(), options.Database().SetBSONOptions(&options.BSONOptions{DefaultDocumentM: true}))
	find := func(name string, filter bson.M) ([]bson.M, error) {
		cur, err := asMaps.Collection(name).Find(ctx, filter)
		if err != nil {
			return nil, fmt.Errorf("%s: %w", name, err)
		}
		out := []bson.M{}
		return out, cur.All(ctx, &out)
	}
	out := map[string]any{
		"exportedAt": time.Now().UTC().Format(time.RFC3339),
		"about":      "Export Tribbae de vos données personnelles (RGPD, articles 15 et 20).",
		"account":    user,
	}
	sections := []struct {
		key, col string
		filter   bson.M
	}{
		{"ideas", "links", bson.M{"owner_id": userID}},
		{"folders", "folders", bson.M{"owner_id": userID}},
		{"sharedWithMe", "folders", bson.M{"collaborators.user_id": userID}},
		{"children", "children", bson.M{"ownerId": oid}},
		{"comments", "comments", bson.M{"user_id": userID}},
		{"ideaLikes", "link_likes", bson.M{"user_id": userID}},
		{"following", "follows", bson.M{"follower_id": userID}},
		{"followers", "follows", bson.M{"following_id": userID}},
		{"tribe", "tribes", bson.M{"members.user_id": userID}},
		{"photos", photo.Bucket + ".files", bson.M{"metadata.owner_id": userID}},
		// Applications connectées (Claude…) : l'accès donné, jamais les jetons
		{"connectedApps", "oauth_grants", bson.M{"user_id": userID}},
		// Journal des appels des applications (outil, résultat, date)
		{"connectedAppsLog", "mcp_audit", bson.M{"user_id": userID}},
	}
	// Demande de mot de passe oublié en cours : sa date seulement, pas le jeton
	var reset bson.M
	if err := s.col("password_resets").FindOne(ctx, bson.M{"_id": oid}).Decode(&reset); err == nil {
		out["passwordReset"] = bson.M{"createdAt": reset["created_at"], "expiresAt": reset["expires_at"]}
	} else if !errors.Is(err, mongo.ErrNoDocuments) {
		return nil, fmt.Errorf("password_resets: %w", err)
	}
	// Confirmation d'adresse en attente : idem, ses dates seulement
	var verification bson.M
	if err := s.col("email_verifications").FindOne(ctx, bson.M{"_id": oid}).Decode(&verification); err == nil {
		out["emailVerification"] = bson.M{"createdAt": verification["created_at"], "expiresAt": verification["expires_at"]}
	} else if !errors.Is(err, mongo.ErrNoDocuments) {
		return nil, fmt.Errorf("email_verifications: %w", err)
	}
	for _, sec := range sections {
		docs, err := find(sec.col, sec.filter)
		if err != nil {
			return nil, err
		}
		out[sec.key] = docs
	}
	// Dossiers des autres : seulement le nom et son rôle, pas leur contenu
	if shared, ok := out["sharedWithMe"].([]bson.M); ok {
		slim := make([]bson.M, 0, len(shared))
		for _, f := range shared {
			slim = append(slim, bson.M{"_id": f["_id"], "name": f["name"]})
		}
		out["sharedWithMe"] = slim
	}
	liked, err := find("folders", bson.M{"liked_by": userID})
	if err != nil {
		return nil, err
	}
	folderLikes := make([]bson.M, 0, len(liked))
	for _, f := range liked {
		folderLikes = append(folderLikes, bson.M{"_id": f["_id"], "name": f["name"]})
	}
	out["folderLikes"] = folderLikes

	// Invitations dans une tribu, envoyées ou reçues (sans le jeton)
	email, _ := user["email"].(string)
	invs, err := find("tribe_invitations", bson.M{"$or": bson.A{bson.M{"invited_by": userID}, bson.M{"email": strings.ToLower(email)}}})
	if err != nil {
		return nil, err
	}
	for _, inv := range invs {
		delete(inv, "token_hash")
	}
	out["tribeInvitations"] = invs
	// Invitations dans le cercle (dossiers choisis), envoyées ou reçues, idem
	circle, err := find("folder_invitations", bson.M{"$or": bson.A{bson.M{"owner_id": userID}, bson.M{"email": strings.ToLower(email)}}})
	if err != nil {
		return nil, err
	}
	for _, inv := range circle {
		delete(inv, "token_hash")
	}
	out["circleInvitations"] = circle
	return out, nil
}

// Delete efface définitivement le compte et tout ce qui lui appartient :
// idées (et leurs commentaires et « j'aime »), photos, dossiers, profils enfants,
// commentaires, « j'aime », abonnements, place dans la tribu et dans les
// dossiers partagés. Les idées que d'autres ont rangées dans ses dossiers leur
// restent (elles redeviennent « non rangées »).
//
// Le compte lui-même est supprimé en dernier : si une étape échoue, une
// nouvelle demande reprend là où la précédente s'est arrêtée. Une fois le
// compte effacé, un email le confirme à son adresse, qui n'est gardée qu'en
// mémoire le temps de cet envoi.
func (s *Service) Delete(ctx context.Context, userID string) error {
	oid, err := bson.ObjectIDFromHex(userID)
	if err != nil {
		return ErrNotFound
	}
	var user struct {
		Email       string `bson:"email"`
		DisplayName string `bson:"display_name"`
	}
	if err := s.col("users").FindOne(ctx, bson.M{"_id": oid}).Decode(&user); err != nil {
		if errors.Is(err, mongo.ErrNoDocuments) {
			return ErrNotFound
		}
		return err
	}

	// 1. Tribu : on la quitte (le contenu des autres membres reste le leur)
	if s.tribe != nil {
		if err := s.tribe.Leave(ctx, userID); err != nil {
			return fmt.Errorf("tribe: %w", err)
		}
	}

	// 2. Ses dossiers : les idées des autres qui s'y trouvent sont gardées, hors dossier
	folderIDs, err := s.ids(ctx, "folders", bson.M{"owner_id": userID})
	if err != nil {
		return err
	}
	if len(folderIDs) > 0 {
		if _, err := s.col("links").UpdateMany(ctx,
			bson.M{"folder_id": bson.M{"$in": folderIDs}, "owner_id": bson.M{"$ne": userID}},
			bson.M{"$set": bson.M{"folder_id": "", "updated_at": time.Now()}}); err != nil {
			return fmt.Errorf("links in folders: %w", err)
		}
	}

	// 3. Ses idées, avec les commentaires et « j'aime » qu'elles ont reçus
	linkIDs, err := s.ids(ctx, "links", bson.M{"owner_id": userID})
	if err != nil {
		return err
	}
	if len(linkIDs) > 0 {
		for _, c := range []string{"comments", "link_likes"} {
			if _, err := s.col(c).DeleteMany(ctx, bson.M{"link_id": bson.M{"$in": linkIDs}}); err != nil {
				return fmt.Errorf("%s on ideas: %w", c, err)
			}
		}
	}

	// 4. Ses « j'aime » sur les idées des autres (leur nombre est compté dans
	// link_likes, il n'y a pas de compteur à mettre à jour)
	if _, err := s.col("link_likes").DeleteMany(ctx, bson.M{"user_id": userID}); err != nil {
		return err
	}

	// 5. Ses « j'aime » sur des dossiers, et sa place dans les dossiers des autres
	if _, err := s.col("folders").UpdateMany(ctx, bson.M{"liked_by": userID},
		bson.M{"$pull": bson.M{"liked_by": userID}, "$inc": bson.M{"like_count": -1}}); err != nil {
		return fmt.Errorf("folder likes: %w", err)
	}
	if _, err := s.col("folders").UpdateMany(ctx, bson.M{"collaborators.user_id": userID},
		bson.M{"$pull": bson.M{"collaborators": bson.M{"user_id": userID}}}); err != nil {
		return fmt.Errorf("collaborations: %w", err)
	}

	// 6. Ses photos, puis tout le reste de ce qui lui appartient
	if err := photo.DeleteOwnedBy(ctx, s.db, userID); err != nil {
		return fmt.Errorf("photos: %w", err)
	}
	deletes := []struct {
		col    string
		filter bson.M
	}{
		{"links", bson.M{"owner_id": userID}},
		{"folders", bson.M{"owner_id": userID}},
		{"comments", bson.M{"user_id": userID}},
		{"follows", bson.M{"$or": bson.A{bson.M{"follower_id": userID}, bson.M{"following_id": userID}}}},
		{"children", bson.M{"ownerId": oid}},
		{"tribe_invitations", bson.M{"$or": bson.A{bson.M{"invited_by": userID}, bson.M{"email": strings.ToLower(user.Email)}}}},
		{"password_resets", bson.M{"_id": oid}},
		{"email_verifications", bson.M{"_id": oid}},
		{"folder_invitations", bson.M{"$or": bson.A{bson.M{"owner_id": userID}, bson.M{"email": strings.ToLower(user.Email)}}}},
		// Applications connectées : accès, jetons (hachés) et demandes en cours
		{"oauth_grants", bson.M{"user_id": userID}},
		{"oauth_tokens", bson.M{"user_id": userID}},
		{"oauth_requests", bson.M{"user_id": userID}},
		{"mcp_audit", bson.M{"user_id": userID}},
	}
	for _, d := range deletes {
		if _, err := s.col(d.col).DeleteMany(ctx, d.filter); err != nil {
			return fmt.Errorf("%s: %w", d.col, err)
		}
	}

	// 7. Le compte
	res, err := s.col("users").DeleteOne(ctx, bson.M{"_id": oid})
	if err != nil {
		return err
	}
	if res.DeletedCount == 1 && s.notify != nil {
		s.notify.AccountDeleted(user.Email, user.DisplayName)
	}
	return nil
}

func (s *Service) ids(ctx context.Context, col string, filter bson.M) ([]string, error) {
	cur, err := s.col(col).Find(ctx, filter)
	if err != nil {
		return nil, err
	}
	var docs []struct {
		ID bson.ObjectID `bson:"_id"`
	}
	if err := cur.All(ctx, &docs); err != nil {
		return nil, err
	}
	out := make([]string, 0, len(docs))
	for _, d := range docs {
		out = append(out, d.ID.Hex())
	}
	return out, nil
}
