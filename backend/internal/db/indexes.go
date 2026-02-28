package db

import (
	"context"
	"log"

	"go.mongodb.org/mongo-driver/bson"
	"go.mongodb.org/mongo-driver/mongo"
	"go.mongodb.org/mongo-driver/mongo/options"
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
				Keys:    bson.D{{Key: "visibility", Value: 1}},
				Options: options.Index().SetName("idx_folders_visibility"),
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
				Keys:    bson.D{{Key: "folder_id", Value: 1}},
				Options: options.Index().SetName("idx_links_folder_id"),
			},
		},
		{
			Collection: "links",
			Model: mongo.IndexModel{
				Keys:    bson.D{{Key: "category", Value: 1}},
				Options: options.Index().SetName("idx_links_category"),
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
				Keys:    bson.D{{Key: "link_id", Value: 1}},
				Options: options.Index().SetName("idx_link_likes_link_id"),
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
	}

	for _, idx := range indexes {
		col := db.Collection(idx.Collection)
		name, err := col.Indexes().CreateOne(ctx, idx.Model)
		if err != nil {
			log.Printf("⚠️  index %s on %s: %v", *idx.Model.Options.Name, idx.Collection, err)
			return err
		}
		log.Printf("✅ index %s on %s: ok", name, idx.Collection)
	}

	return nil
}
