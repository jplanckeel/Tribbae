package db

import (
	"context"
	"errors"
	"log"

	"go.mongodb.org/mongo-driver/v2/bson"
	"go.mongodb.org/mongo-driver/v2/mongo"
	"go.mongodb.org/mongo-driver/v2/mongo/options"
)

// indexDef décrit un index à créer sur une collection.
type indexDef struct {
	Collection string
	Model      mongo.IndexModel
}

// EnsureIndexes crée les index MongoDB nécessaires s'ils n'existent pas déjà.
// L'opération est idempotente : si l'index existe déjà, CreateOne ne fait rien.
func EnsureIndexes(ctx context.Context, db *mongo.Database) error {
	indexes := []indexDef{
		// ── users ──────────────────────────────────────────────
		{
			Collection: "users",
			Model: mongo.IndexModel{
				Keys:    bson.D{{Key: "email", Value: 1}},
				Options: options.Index().SetUnique(true).SetName("idx_users_email_unique"),
			},
		},
		{
			// Statistiques admin : nouveaux comptes par semaine / mois
			Collection: "users",
			Model: mongo.IndexModel{
				Keys:    bson.D{{Key: "created_at", Value: 1}},
				Options: options.Index().SetName("idx_users_created_at"),
			},
		},
		{
			// Statistiques admin : comptes actifs par semaine / mois
			Collection: "users",
			Model: mongo.IndexModel{
				Keys:    bson.D{{Key: "last_seen_at", Value: 1}},
				Options: options.Index().SetName("idx_users_last_seen_at"),
			},
		},

		// ── tribe_invitations : invitations dans une tribu ───────
		{
			Collection: "tribe_invitations",
			Model: mongo.IndexModel{
				Keys:    bson.D{{Key: "token_hash", Value: 1}},
				Options: options.Index().SetUnique(true).SetName("idx_tribe_invitations_token_hash"),
			},
		},
		{
			// Une invitation par tribu et par adresse
			Collection: "tribe_invitations",
			Model: mongo.IndexModel{
				Keys:    bson.D{{Key: "tribe_id", Value: 1}, {Key: "email", Value: 1}},
				Options: options.Index().SetUnique(true).SetName("idx_tribe_invitations_tribe_email"),
			},
		},
		{
			// Efface les invitations expirées
			Collection: "tribe_invitations",
			Model: mongo.IndexModel{
				Keys:    bson.D{{Key: "expires_at", Value: 1}},
				Options: options.Index().SetExpireAfterSeconds(0).SetName("idx_tribe_invitations_ttl"),
			},
		},

		// ── password_resets : liens du mot de passe oublié ───────
		{
			Collection: "password_resets",
			Model: mongo.IndexModel{
				Keys:    bson.D{{Key: "token_hash", Value: 1}},
				Options: options.Index().SetUnique(true).SetName("idx_password_resets_token_hash"),
			},
		},
		{
			// Efface les demandes expirées
			Collection: "password_resets",
			Model: mongo.IndexModel{
				Keys:    bson.D{{Key: "expires_at", Value: 1}},
				Options: options.Index().SetExpireAfterSeconds(0).SetName("idx_password_resets_ttl"),
			},
		},

		// ── folder_invitations : invitations dans le cercle ───────
		{
			Collection: "folder_invitations",
			Model: mongo.IndexModel{
				Keys:    bson.D{{Key: "token_hash", Value: 1}},
				Options: options.Index().SetUnique(true).SetName("idx_folder_invitations_token_hash"),
			},
		},
		{
			// Une par propriétaire et par adresse
			Collection: "folder_invitations",
			Model: mongo.IndexModel{
				Keys:    bson.D{{Key: "owner_id", Value: 1}, {Key: "email", Value: 1}},
				Options: options.Index().SetUnique(true).SetName("idx_folder_invitations_owner_email"),
			},
		},
		{
			// « Mes invitations en attente », des plus anciennes aux plus récentes
			Collection: "folder_invitations",
			Model: mongo.IndexModel{
				Keys:    bson.D{{Key: "owner_id", Value: 1}, {Key: "created_at", Value: 1}},
				Options: options.Index().SetName("idx_folder_invitations_owner_created"),
			},
		},
		{
			// Dossier supprimé : retiré des invitations qui l'ouvraient
			Collection: "folder_invitations",
			Model: mongo.IndexModel{
				Keys:    bson.D{{Key: "folder_ids", Value: 1}},
				Options: options.Index().SetName("idx_folder_invitations_folder_ids"),
			},
		},
		{
			// Efface les invitations expirées
			Collection: "folder_invitations",
			Model: mongo.IndexModel{
				Keys:    bson.D{{Key: "expires_at", Value: 1}},
				Options: options.Index().SetExpireAfterSeconds(0).SetName("idx_folder_invitations_ttl"),
			},
		},

		// ── email_verifications : liens de confirmation d'adresse ─
		{
			Collection: "email_verifications",
			Model: mongo.IndexModel{
				Keys:    bson.D{{Key: "token_hash", Value: 1}},
				Options: options.Index().SetUnique(true).SetName("idx_email_verifications_token_hash"),
			},
		},
		{
			// Efface les demandes expirées
			Collection: "email_verifications",
			Model: mongo.IndexModel{
				Keys:    bson.D{{Key: "expires_at", Value: 1}},
				Options: options.Index().SetExpireAfterSeconds(0).SetName("idx_email_verifications_ttl"),
			},
		},

		// ── tribes : un compte appartient à au plus une tribu ──────
		{
			Collection: "tribes",
			Model: mongo.IndexModel{
				Keys:    bson.D{{Key: "members.user_id", Value: 1}},
				Options: options.Index().SetUnique(true).SetName("idx_tribes_member_unique"),
			},
		},

		// ── folders ───────────────────────────────────────────
		{
			Collection: "folders",
			Model: mongo.IndexModel{
				Keys:    bson.D{{Key: "owner_id", Value: 1}},
				Options: options.Index().SetName("idx_folders_owner_id"),
			},
		},
		{
			Collection: "folders",
			Model: mongo.IndexModel{
				// Dossiers de la communauté, paginés par _id décroissant (recherche comprise)
				Keys:    bson.D{{Key: "visibility", Value: 1}, {Key: "_id", Value: -1}},
				Options: options.Index().SetName("idx_folders_visibility_id"),
			},
		},
		{
			Collection: "folders",
			Model: mongo.IndexModel{
				Keys:    bson.D{{Key: "share_token", Value: 1}},
				Options: options.Index().SetSparse(true).SetName("idx_folders_share_token"),
			},
		},
		{
			Collection: "folders",
			Model: mongo.IndexModel{
				Keys:    bson.D{{Key: "collaborators.user_id", Value: 1}},
				Options: options.Index().SetName("idx_folders_collaborators_user_id"),
			},
		},
		{
			Collection: "folders",
			Model: mongo.IndexModel{
				Keys: bson.D{
					{Key: "visibility", Value: 1},
					{Key: "like_count", Value: -1},
					{Key: "updated_at", Value: -1},
				},
				Options: options.Index().SetName("idx_folders_visibility_likes_updated"),
			},
		},
		{
			// Plan du site : dossiers publics, les plus récemment modifiés d'abord
			Collection: "folders",
			Model: mongo.IndexModel{
				Keys:    bson.D{{Key: "visibility", Value: 1}, {Key: "updated_at", Value: -1}},
				Options: options.Index().SetName("idx_folders_visibility_updated"),
			},
		},

		// ── links ─────────────────────────────────────────────
		{
			Collection: "links",
			Model: mongo.IndexModel{
				Keys:    bson.D{{Key: "owner_id", Value: 1}},
				Options: options.Index().SetName("idx_links_owner_id"),
			},
		},
		{
			Collection: "links",
			Model: mongo.IndexModel{
				Keys: bson.D{
					{Key: "folder_id", Value: 1},
					{Key: "created_at", Value: -1},
				},
				Options: options.Index().SetName("idx_links_folder_id_created_at"),
			},
		},
		{
			// Pages publiques et plan du site : idées publiées, par catégorie, récentes d'abord
			Collection: "links",
			Model: mongo.IndexModel{
				Keys: bson.D{
					{Key: "visibility", Value: 1},
					{Key: "category", Value: 1},
					{Key: "updated_at", Value: -1},
				},
				Options: options.Index().SetName("idx_links_visibility_category_updated"),
			},
		},
		{
			// Explorer toutes catégories et plan du site : idées publiées, récentes d'abord
			Collection: "links",
			Model: mongo.IndexModel{
				Keys:    bson.D{{Key: "visibility", Value: 1}, {Key: "updated_at", Value: -1}},
				Options: options.Index().SetName("idx_links_visibility_updated"),
			},
		},
		{
			// « Nouveautés » : idées publiées, les plus récemment créées d'abord
			Collection: "links",
			Model: mongo.IndexModel{
				Keys:    bson.D{{Key: "visibility", Value: 1}, {Key: "created_at", Value: -1}},
				Options: options.Index().SetName("idx_links_visibility_created"),
			},
		},
		{
			// Statistiques admin : nouvelles idées par semaine / mois (les idées
			// publiques passent par idx_links_visibility_created). Croissant pour
			// ne pas entrer en conflit avec idx_links_missing_image, partiel.
			Collection: "links",
			Model: mongo.IndexModel{
				Keys:    bson.D{{Key: "created_at", Value: 1}},
				Options: options.Index().SetName("idx_links_created_at"),
			},
		},
		{
			// Rattrapage des images (link.backfillBatch) : seulement les idées sans image
			Collection: "links",
			Model: mongo.IndexModel{
				Keys: bson.D{{Key: "created_at", Value: -1}},
				Options: options.Index().SetName("idx_links_missing_image").
					SetPartialFilterExpression(bson.M{"image_url": ""}),
			},
		},

		// ── link_likes ────────────────────────────────────────
		{
			Collection: "link_likes",
			Model: mongo.IndexModel{
				Keys: bson.D{
					{Key: "link_id", Value: 1},
					{Key: "user_id", Value: 1},
				},
				Options: options.Index().SetUnique(true).SetName("idx_link_likes_link_user_unique"),
			},
		},
		{
			Collection: "link_likes",
			Model: mongo.IndexModel{
				// « J'aime » d'un compte (suppression du compte) ; le compte par idée
				// passe par l'index unique (link_id, user_id)
				Keys:    bson.D{{Key: "user_id", Value: 1}},
				Options: options.Index().SetName("idx_link_likes_user_id"),
			},
		},

		// ── children ──────────────────────────────────────────
		{
			Collection: "children",
			Model: mongo.IndexModel{
				Keys:    bson.D{{Key: "ownerId", Value: 1}},
				Options: options.Index().SetName("idx_children_owner_id"),
			},
		},

		// ── follows ───────────────────────────────────────────
		{
			Collection: "follows",
			Model: mongo.IndexModel{
				Keys: bson.D{
					{Key: "follower_id", Value: 1},
					{Key: "following_id", Value: 1},
				},
				Options: options.Index().SetUnique(true).SetName("idx_follows_follower_following_unique"),
			},
		},
		{
			Collection: "follows",
			Model: mongo.IndexModel{
				Keys:    bson.D{{Key: "following_id", Value: 1}},
				Options: options.Index().SetName("idx_follows_following_id"),
			},
		},

		// ── comments ──────────────────────────────────────────
		{
			Collection: "comments",
			Model: mongo.IndexModel{
				Keys: bson.D{
					{Key: "link_id", Value: 1},
					{Key: "created_at", Value: -1},
				},
				Options: options.Index().SetName("idx_comments_link_id_created_at"),
			},
		},
		{
			Collection: "comments",
			Model: mongo.IndexModel{
				Keys:    bson.D{{Key: "user_id", Value: 1}},
				Options: options.Index().SetName("idx_comments_user_id"),
			},
		},

		// ── photos (GridFS) : une photo par jeton, retrouvées par compte ──
		{
			Collection: "photos.files",
			Model: mongo.IndexModel{
				Keys:    bson.D{{Key: "filename", Value: 1}},
				Options: options.Index().SetUnique(true).SetName("idx_photos_filename_unique"),
			},
		},
		{
			Collection: "photos.files",
			Model: mongo.IndexModel{
				Keys:    bson.D{{Key: "metadata.owner_id", Value: 1}},
				Options: options.Index().SetName("idx_photos_owner_id"),
			},
		},
		// ── OAuth des applications connectées (internal/oauth) ───
		{
			// Un client enregistré que personne n'a autorisé est effacé après un jour
			Collection: "oauth_clients",
			Model: mongo.IndexModel{
				Keys:    bson.D{{Key: "unused_expires_at", Value: 1}},
				Options: options.Index().SetExpireAfterSeconds(0).SetName("idx_oauth_clients_unused_ttl"),
			},
		},
		{
			// Échange du code contre les jetons
			Collection: "oauth_requests",
			Model: mongo.IndexModel{
				Keys: bson.D{{Key: "code_hash", Value: 1}},
				Options: options.Index().SetUnique(true).SetName("idx_oauth_requests_code_hash").
					SetPartialFilterExpression(bson.M{"code_hash": bson.M{"$exists": true}}),
			},
		},
		{
			// Suppression du compte
			Collection: "oauth_requests",
			Model: mongo.IndexModel{
				Keys:    bson.D{{Key: "user_id", Value: 1}},
				Options: options.Index().SetName("idx_oauth_requests_user_id"),
			},
		},
		{
			// Demandes et codes expirés (les codes utilisés restent jusque-là,
			// pour reconnaître un code présenté deux fois)
			Collection: "oauth_requests",
			Model: mongo.IndexModel{
				Keys:    bson.D{{Key: "expires_at", Value: 1}},
				Options: options.Index().SetExpireAfterSeconds(0).SetName("idx_oauth_requests_ttl"),
			},
		},
		{
			// « Applications connectées » d'un compte, les plus récentes d'abord
			Collection: "oauth_grants",
			Model: mongo.IndexModel{
				Keys:    bson.D{{Key: "user_id", Value: 1}, {Key: "created_at", Value: -1}},
				Options: options.Index().SetName("idx_oauth_grants_user_created"),
			},
		},
		{
			// Accès arrivés au bout de leurs 90 jours
			Collection: "oauth_grants",
			Model: mongo.IndexModel{
				Keys:    bson.D{{Key: "expires_at", Value: 1}},
				Options: options.Index().SetExpireAfterSeconds(0).SetName("idx_oauth_grants_ttl"),
			},
		},
		{
			// Révocation d'un accès : tous ses jetons
			Collection: "oauth_tokens",
			Model: mongo.IndexModel{
				Keys:    bson.D{{Key: "grant_id", Value: 1}},
				Options: options.Index().SetName("idx_oauth_tokens_grant_id"),
			},
		},
		{
			// Suppression du compte
			Collection: "oauth_tokens",
			Model: mongo.IndexModel{
				Keys:    bson.D{{Key: "user_id", Value: 1}},
				Options: options.Index().SetName("idx_oauth_tokens_user_id"),
			},
		},
		{
			// Jetons expirés (les refresh tokens remplacés restent jusque-là,
			// pour reconnaître une réutilisation)
			Collection: "oauth_tokens",
			Model: mongo.IndexModel{
				Keys:    bson.D{{Key: "expires_at", Value: 1}},
				Options: options.Index().SetExpireAfterSeconds(0).SetName("idx_oauth_tokens_ttl"),
			},
		},
	}

	// Index remplacés : préfixes d'un index composé, ou jamais utilisés seuls
	obsolete := []struct{ collection, name string }{
		{"links", "idx_links_folder_id"},         // préfixe de idx_links_folder_id_created_at
		{"links", "idx_links_category"},          // la catégorie est toujours filtrée avec la visibilité
		{"link_likes", "idx_link_likes_link_id"}, // préfixe de idx_link_likes_link_user_unique
		{"folders", "idx_folders_visibility"},    // remplacé par idx_folders_visibility_id
	}
	for _, o := range obsolete {
		if err := db.Collection(o.collection).Indexes().DropOne(ctx, o.name); err != nil {
			var cmdErr mongo.CommandError
			if errors.As(err, &cmdErr) && (cmdErr.Code == 27 || cmdErr.Code == 26) { // IndexNotFound, NamespaceNotFound
				continue
			}
			log.Printf("⚠️  drop index %s on %s: %v", o.name, o.collection, err)
			return err
		}
		log.Printf("🗑️  index %s on %s: dropped", o.name, o.collection)
	}

	for _, idx := range indexes {
		col := db.Collection(idx.Collection)
		name, err := col.Indexes().CreateOne(ctx, idx.Model)
		if err != nil {
			log.Printf("⚠️  index %v on %s: %v", idx.Model.Keys, idx.Collection, err)
			return err
		}
		log.Printf("✅ index %s on %s: ok", name, idx.Collection)
	}

	return nil
}
