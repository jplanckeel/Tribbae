package auth

import (
	"context"
	"errors"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"go.mongodb.org/mongo-driver/bson"
	"go.mongodb.org/mongo-driver/bson/primitive"
	"go.mongodb.org/mongo-driver/mongo"
	"golang.org/x/crypto/bcrypt"
)

type User struct {
	ID          primitive.ObjectID `bson:"_id,omitempty"`
	Email       string             `bson:"email"`
	Password    string             `bson:"password"` // bcrypt hash
	DisplayName string             `bson:"display_name"`
	IsAdmin     bool               `bson:"is_admin"`
	IsPremium   bool               `bson:"is_premium"` // Tribbae+ (accès Perplexity)
	CreatedAt   time.Time          `bson:"created_at"`
}

type Service struct {
	col       *mongo.Collection
	jwtSecret []byte
}

func NewService(col *mongo.Collection, jwtSecret string) *Service {
	return &Service{col: col, jwtSecret: []byte(jwtSecret)}
}

func (s *Service) Register(ctx context.Context, email, password, displayName string) (string, string, bool, error) {
	// Vérifie si l'email existe déjà
	var existing User
	err := s.col.FindOne(ctx, bson.M{"email": email}).Decode(&existing)
	if err == nil {
		return "", "", false, errors.New("email already registered")
	}
	if !errors.Is(err, mongo.ErrNoDocuments) {
		return "", "", false, err
	}

	hash, err := bcrypt.GenerateFromPassword([]byte(password), bcrypt.DefaultCost)
	if err != nil {
		return "", "", false, err
	}

	user := User{
		ID:          primitive.NewObjectID(),
		Email:       email,
		Password:    string(hash),
		DisplayName: displayName,
		CreatedAt:   time.Now(),
	}
	if _, err := s.col.InsertOne(ctx, user); err != nil {
		return "", "", false, err
	}

	token, err := s.generateToken(user.ID.Hex())
	return user.ID.Hex(), token, user.IsAdmin, err
}

func (s *Service) Login(ctx context.Context, email, password string) (string, string, string, bool, error) {
	var user User
	if err := s.col.FindOne(ctx, bson.M{"email": email}).Decode(&user); err != nil {
		return "", "", "", false, errors.New("invalid credentials")
	}
	if err := bcrypt.CompareHashAndPassword([]byte(user.Password), []byte(password)); err != nil {
		return "", "", "", false, errors.New("invalid credentials")
	}
	token, err := s.generateToken(user.ID.Hex())
	return user.ID.Hex(), token, user.DisplayName, user.IsAdmin, err
}

func (s *Service) ValidateToken(tokenStr string) (string, error) {
	token, err := jwt.Parse(tokenStr, func(t *jwt.Token) (interface{}, error) {
		if _, ok := t.Method.(*jwt.SigningMethodHMAC); !ok {
			return nil, errors.New("unexpected signing method")
		}
		return s.jwtSecret, nil
	})
	if err != nil || !token.Valid {
		return "", errors.New("invalid token")
	}
	claims, ok := token.Claims.(jwt.MapClaims)
	if !ok {
		return "", errors.New("invalid claims")
	}
	userID, ok := claims["sub"].(string)
	if !ok {
		return "", errors.New("invalid subject")
	}
	return userID, nil
}

func (s *Service) generateToken(userID string) (string, error) {
	claims := jwt.MapClaims{
		"sub": userID,
		"exp": time.Now().Add(30 * 24 * time.Hour).Unix(),
		"iat": time.Now().Unix(),
	}
	return jwt.NewWithClaims(jwt.SigningMethodHS256, claims).SignedString(s.jwtSecret)
}

// GetUser récupère un utilisateur par son ID
func (s *Service) GetUser(ctx context.Context, userID string) (*User, error) {
	id, err := primitive.ObjectIDFromHex(userID)
	if err != nil {
		return nil, errors.New("invalid user id")
	}
	var user User
	if err := s.col.FindOne(ctx, bson.M{"_id": id}).Decode(&user); err != nil {
		return nil, err
	}
	return &user, nil
}

// ListUsers retourne tous les utilisateurs (admin only)
func (s *Service) ListUsers(ctx context.Context) ([]*User, error) {
	cursor, err := s.col.Find(ctx, bson.M{})
	if err != nil {
		return nil, err
	}
	defer cursor.Close(ctx)

	var users []*User
	if err := cursor.All(ctx, &users); err != nil {
		return nil, err
	}
	return users, nil
}

// UpdateUserPremium met à jour le statut premium d'un utilisateur (admin only)
func (s *Service) UpdateUserPremium(ctx context.Context, userID string, isPremium bool) error {
	id, err := primitive.ObjectIDFromHex(userID)
	if err != nil {
		return errors.New("invalid user id")
	}
	_, err = s.col.UpdateOne(ctx, bson.M{"_id": id}, bson.M{"$set": bson.M{"is_premium": isPremium}})
	return err
}

// IsAdmin vérifie si un utilisateur est admin
func (s *Service) IsAdmin(ctx context.Context, userID string) (bool, error) {
	user, err := s.GetUser(ctx, userID)
	if err != nil {
		return false, err
	}
	return user.IsAdmin, nil
}

// GetUserForAI retourne un utilisateur simplifié pour le service AI (évite les imports circulaires)
type AIUser struct {
	ID        string
	IsPremium bool
}

func (s *Service) GetUserForAI(ctx context.Context, userID string) (*AIUser, error) {
	user, err := s.GetUser(ctx, userID)
	if err != nil {
		return nil, err
	}
	return &AIUser{
		ID:        user.ID.Hex(),
		IsPremium: user.IsPremium,
	}, nil
}
