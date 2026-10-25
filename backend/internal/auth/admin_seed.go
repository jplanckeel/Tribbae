package auth

import (
	"context"
	"errors"
	"log"
	"time"

	"go.mongodb.org/mongo-driver/v2/bson"
	"go.mongodb.org/mongo-driver/v2/mongo"
	"golang.org/x/crypto/bcrypt"
)

const (
	// Identifiant du compte admin existant, indépendant du domaine du site :
	// le changer créerait un second compte admin au démarrage.
	AdminEmail       = "tribbae@bananaops.cloud"
	AdminDisplayName = "Tribbae"

	// legacyDefaultAdminPassword était le mot de passe par défaut (public, dans le
	// dépôt) des anciennes versions. Un compte qui l'utilise encore est réinitialisé
	// dès qu'ADMIN_PASSWORD est fourni.
	legacyDefaultAdminPassword = "tribbae-admin" // #nosec G101 -- valeur publique, sert uniquement à détecter et remplacer l'ancien défaut
	minAdminPasswordLen        = 12
)

// EnsureAdminAccount crée le compte admin Tribbae au démarrage si ADMIN_PASSWORD
// est défini. Il ne promeut jamais un compte existant : sinon n'importe qui
// pourrait s'inscrire avec l'email admin avant le premier démarrage.
func EnsureAdminAccount(ctx context.Context, col *mongo.Collection, password string) error {
	if password != "" && len(password) < minAdminPasswordLen {
		return errors.New("ADMIN_PASSWORD must be at least 12 characters")
	}

	var existing User
	err := col.FindOne(ctx, bson.M{"email": AdminEmail}).Decode(&existing)
	if err == nil {
		if !existing.IsAdmin {
			log.Printf("WARNING: account %s exists but is not admin; it will not be promoted automatically", AdminEmail)
			return nil
		}
		if bcrypt.CompareHashAndPassword([]byte(existing.Password), []byte(legacyDefaultAdminPassword)) != nil {
			return nil
		}
		if password == "" {
			log.Printf("WARNING: admin account %s still uses the public default password; set ADMIN_PASSWORD to rotate it", AdminEmail)
			return nil
		}
		hash, err := bcrypt.GenerateFromPassword([]byte(password), bcrypt.DefaultCost)
		if err != nil {
			return err
		}
		if _, err := col.UpdateOne(ctx, bson.M{"_id": existing.ID}, bson.M{"$set": bson.M{"password": string(hash)}}); err != nil {
			return err
		}
		log.Printf("Admin account %s: default password replaced by ADMIN_PASSWORD", AdminEmail)
		return nil
	}
	if !errors.Is(err, mongo.ErrNoDocuments) {
		return err
	}

	if password == "" {
		log.Printf("ADMIN_PASSWORD not set: admin account %s not created", AdminEmail)
		return nil
	}
	hash, err := bcrypt.GenerateFromPassword([]byte(password), bcrypt.DefaultCost)
	if err != nil {
		return err
	}
	user := User{
		ID:          bson.NewObjectID(),
		Email:       AdminEmail,
		Password:    string(hash),
		DisplayName: AdminDisplayName,
		IsAdmin:     true,
		CreatedAt:   time.Now(),
	}
	if _, err := col.InsertOne(ctx, user); err != nil {
		return err
	}
	log.Printf("Admin account %s created", AdminEmail)
	return nil
}
