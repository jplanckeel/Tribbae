package auth

import (
	"context"
	"log"
	"time"

	"go.mongodb.org/mongo-driver/bson"
	"go.mongodb.org/mongo-driver/bson/primitive"
	"go.mongodb.org/mongo-driver/mongo"
	"golang.org/x/crypto/bcrypt"
)

const (
	AdminEmail       = "tribbae@bananaops.cloud"
	AdminDisplayName = "Tribbae"
)

// EnsureAdminAccount crée ou met à jour le compte admin Tribbae au démarrage.
// Idempotent : si l'email existe déjà, active juste is_admin.
func EnsureAdminAccount(ctx context.Context, col *mongo.Collection, password string) error {
	var existing User
	err := col.FindOne(ctx, bson.M{"email": AdminEmail}).Decode(&existing)

	if err == nil {
		// L'utilisateur existe déjà — s'assurer qu'il est admin
		if !existing.IsAdmin {
			_, err = col.UpdateOne(ctx, bson.M{"_id": existing.ID}, bson.M{"$set": bson.M{"is_admin": true}})
			if err != nil {
				return err
			}
			log.Printf("Admin account %s updated to admin", AdminEmail)
		} else {
			log.Printf("Admin account %s already exists", AdminEmail)
		}
		return nil
	}

	if err != mongo.ErrNoDocuments {
		return err
	}

	// Créer le compte admin
	hash, err := bcrypt.GenerateFromPassword([]byte(password), bcrypt.DefaultCost)
	if err != nil {
		return err
	}

	user := User{
		ID:          primitive.NewObjectID(),
		Email:       AdminEmail,
		Password:    string(hash),
		DisplayName: AdminDisplayName,
		IsAdmin:     true,
		CreatedAt:   time.Now(),
	}
	if _, err := col.InsertOne(ctx, user); err != nil {
		return err
	}
	log.Printf("Admin account %s created (displayName: %s)", AdminEmail, AdminDisplayName)
	return nil
}
