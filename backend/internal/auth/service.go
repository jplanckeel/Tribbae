package auth

import (
	"context"
	"errors"
	"net/mail"
	"strings"
	"time"
	"unicode/utf8"

	"github.com/golang-jwt/jwt/v5"
	"go.mongodb.org/mongo-driver/bson"
	"go.mongodb.org/mongo-driver/bson/primitive"
	"go.mongodb.org/mongo-driver/mongo"
	"go.mongodb.org/mongo-driver/mongo/options"
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
	notify    Notifier
}

// Notifier prévient par email (mail.Mailer).
type Notifier interface {
	Welcome(email, name string)
}

func NewService(col *mongo.Collection, jwtSecret string) *Service {
	return &Service{col: col, jwtSecret: []byte(jwtSecret)}
}

// SetNotifier active l'email de bienvenue à l'inscription.
func (s *Service) SetNotifier(n Notifier) { s.notify = n }

// ErrInvalidInput signale une saisie d'inscription invalide.
var ErrInvalidInput = errors.New("invalid input")

// ErrInvalidCredentials est volontairement identique que l'email existe ou non.
var ErrInvalidCredentials = errors.New("invalid credentials")

// dummyHash sert à garder un temps de réponse constant quand l'email est inconnu
// (sinon le temps de réponse révèle quels emails ont un compte).
var dummyHash, _ = bcrypt.GenerateFromPassword([]byte("tribbae-timing-equalizer"), bcrypt.DefaultCost)

// NormalizeEmail met l'email en minuscules sans espaces.
func NormalizeEmail(email string) string {
	return strings.ToLower(strings.TrimSpace(email))
}

func validateRegistration(email, password, displayName string) error {
	if a, err := mail.ParseAddress(email); err != nil || a.Address != email || len(email) > 254 {
		return errors.Join(ErrInvalidInput, errors.New("a valid email is required"))
	}
	if utf8.RuneCountInString(password) < 8 || len(password) > 72 {
		return errors.Join(ErrInvalidInput, errors.New("password must be between 8 and 72 characters"))
	}
	if utf8.RuneCountInString(displayName) > 50 {
		return errors.Join(ErrInvalidInput, errors.New("display name is too long"))
	}
	return nil
}

func (s *Service) Register(ctx context.Context, email, password, displayName string) (string, string, bool, error) {
	email = NormalizeEmail(email)
	displayName = strings.TrimSpace(displayName)
	if err := validateRegistration(email, password, displayName); err != nil {
		return "", "", false, err
	}
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
	if s.notify != nil {
		s.notify.Welcome(user.Email, user.DisplayName)
	}

	token, err := s.generateToken(user.ID.Hex())
	return user.ID.Hex(), token, user.IsAdmin, err
}

func (s *Service) Login(ctx context.Context, email, password string) (string, string, string, bool, error) {
	var user User
	// Les comptes historiques peuvent avoir un email avec majuscules : on accepte les deux formes.
	raw := strings.TrimSpace(email)
	filter := bson.M{"email": bson.M{"$in": bson.A{NormalizeEmail(raw), raw}}}
	if err := s.col.FindOne(ctx, filter).Decode(&user); err != nil {
		_ = bcrypt.CompareHashAndPassword(dummyHash, []byte(password))
		return "", "", "", false, ErrInvalidCredentials
	}
	if err := bcrypt.CompareHashAndPassword([]byte(user.Password), []byte(password)); err != nil {
		return "", "", "", false, ErrInvalidCredentials
	}
	token, err := s.generateToken(user.ID.Hex())
	return user.ID.Hex(), token, user.DisplayName, user.IsAdmin, err
}

func (s *Service) ValidateToken(tokenStr string) (string, error) {
	token, err := jwt.Parse(tokenStr, func(t *jwt.Token) (interface{}, error) {
		return s.jwtSecret, nil
	}, jwt.WithValidMethods([]string{jwt.SigningMethodHS256.Alg()}), jwt.WithExpirationRequired())
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
	// Un compte supprimé (RGPD) ne doit plus rien pouvoir faire, même avec un
	// jeton encore valide : sinon il recréerait des données orphelines.
	if !s.exists(userID) {
		return "", errors.New("account no longer exists")
	}
	return userID, nil
}

func (s *Service) exists(userID string) bool {
	id, err := primitive.ObjectIDFromHex(userID)
	if err != nil {
		return false
	}
	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()
	n, err := s.col.CountDocuments(ctx, bson.M{"_id": id}, options.Count().SetLimit(1))
	return err == nil && n > 0
}

// CheckPassword vérifie le mot de passe d'un compte connecté, avant une
// action irréversible (suppression du compte).
func (s *Service) CheckPassword(ctx context.Context, userID, password string) error {
	id, err := primitive.ObjectIDFromHex(userID)
	if err != nil {
		return ErrInvalidCredentials
	}
	var user User
	if err := s.col.FindOne(ctx, bson.M{"_id": id}).Decode(&user); err != nil {
		_ = bcrypt.CompareHashAndPassword(dummyHash, []byte(password))
		return ErrInvalidCredentials
	}
	if bcrypt.CompareHashAndPassword([]byte(user.Password), []byte(password)) != nil {
		return ErrInvalidCredentials
	}
	return nil
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
