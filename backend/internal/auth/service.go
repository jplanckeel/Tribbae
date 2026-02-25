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
	CreatedAt   time.Time          `bson:"created_at"`
}

type Service struct {
	col       *mongo.Collection
	jwtSecret []byte
}

func NewService(col *mongo.Collection, jwtSecret string) *Service {
	return &Service{col: col, jwtSecret: []byte(jwtSecret)}
}

func (s *Service) Register(ctx context.Context, email, password, displayName string) (string, string, error) {
	// Vérifie si l'email existe déjà
	var existing User
	err := s.col.FindOne(ctx, bson.M{"email": email}).Decode(&existing)
	if err == nil {
		return "", "", errors.New("email already registered")
	}
	if !errors.Is(err, mongo.ErrNoDocuments) {
		return "", "", err
	}

	hash, err := bcrypt.GenerateFromPassword([]byte(password), bcrypt.DefaultCost)
	if err != nil {
		return "", "", err
	}

	user := User{
		ID:          primitive.NewObjectID(),
		Email:       email,
		Password:    string(hash),
		DisplayName: displayName,
		CreatedAt:   time.Now(),
	}
	if _, err := s.col.InsertOne(ctx, user); err != nil {
		return "", "", err
	}

	token, err := s.generateToken(user.ID.Hex())
	return user.ID.Hex(), token, err
}

func (s *Service) Login(ctx context.Context, email, password string) (string, string, string, error) {
	var user User
	if err := s.col.FindOne(ctx, bson.M{"email": email}).Decode(&user); err != nil {
		return "", "", "", errors.New("invalid credentials")
	}
	if err := bcrypt.CompareHashAndPassword([]byte(user.Password), []byte(password)); err != nil {
		return "", "", "", errors.New("invalid credentials")
	}
	token, err := s.generateToken(user.ID.Hex())
	return user.ID.Hex(), token, user.DisplayName, err
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
