package auth

import (
	"context"
	"errors"
	"log"
	"net/mail"
	"strings"
	"time"
	"unicode/utf8"

	"github.com/golang-jwt/jwt/v5"
	"go.mongodb.org/mongo-driver/v2/bson"
	"go.mongodb.org/mongo-driver/v2/mongo"
	"go.mongodb.org/mongo-driver/v2/mongo/options"
	"golang.org/x/crypto/bcrypt"
)

type User struct {
	ID          bson.ObjectID `bson:"_id,omitempty"`
	Email       string        `bson:"email"`
	Password    string        `bson:"password"` // bcrypt hash
	DisplayName string        `bson:"display_name"`
	IsAdmin     bool          `bson:"is_admin"`
	IsPremium   bool          `bson:"is_premium"` // Tribbae+ (accès Perplexity)
	CreatedAt   time.Time     `bson:"created_at"`
	// PasswordChangedAt : les sessions ouvertes avant sont refusées
	PasswordChangedAt *time.Time `bson:"password_changed_at,omitempty"`
	// EmailVerifiedAt : adresse confirmée par un lien reçu par email (verify.go)
	EmailVerifiedAt *time.Time `bson:"email_verified_at,omitempty"`
	// LastSeenAt : dernière connexion ou dernier renouvellement de session
	// (à l'heure près, voir TouchLastSeen). Seule la dernière est gardée.
	LastSeenAt *time.Time `bson:"last_seen_at,omitempty"`
}

type Service struct {
	col       *mongo.Collection
	jwtSecret []byte
	notify    Notifier

	// Mot de passe oublié (reset.go)
	resets      *mongo.Collection
	resetURL    string
	resetNotify ResetNotifier

	// Confirmation de l'adresse email (verify.go)
	verifications *mongo.Collection
	verifyURL     string
	verifyNotify  VerifyNotifier
}

// Notifier prévient par email (mail.Mailer). verifyURL, le lien de
// confirmation de l'adresse, est vide si la confirmation n'est pas activée.
type Notifier interface {
	Welcome(email, name, verifyURL string, validity time.Duration)
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

// Register crée un compte (adresse pas encore confirmée) et ouvre une session.
func (s *Service) Register(ctx context.Context, email, password, displayName string) (*User, string, error) {
	email = NormalizeEmail(email)
	displayName = strings.TrimSpace(displayName)
	if err := validateRegistration(email, password, displayName); err != nil {
		return nil, "", err
	}
	// Vérifie si l'email existe déjà
	var existing User
	err := s.col.FindOne(ctx, bson.M{"email": email}).Decode(&existing)
	if err == nil {
		return nil, "", errors.New("email already registered")
	}
	if !errors.Is(err, mongo.ErrNoDocuments) {
		return nil, "", err
	}

	hash, err := bcrypt.GenerateFromPassword([]byte(password), bcrypt.DefaultCost)
	if err != nil {
		return nil, "", err
	}

	now := time.Now()
	user := User{
		ID:          bson.NewObjectID(),
		Email:       email,
		Password:    string(hash),
		DisplayName: displayName,
		CreatedAt:   now,
		LastSeenAt:  &now,
	}
	if _, err := s.col.InsertOne(ctx, user); err != nil {
		return nil, "", err
	}
	// Le lien de confirmation part dans l'email de bienvenue. Sans lui, le
	// compte reste utilisable : « Renvoyer l'email » en refait un.
	verifyURL, err := s.newVerification(ctx, &user, user.CreatedAt)
	if err != nil {
		log.Printf("auth: email verification link: %v", err)
	}
	if s.notify != nil {
		s.notify.Welcome(user.Email, user.DisplayName, verifyURL, VerifyValidity)
	}

	token, err := s.generateToken(user.ID.Hex())
	return &user, token, err
}

// Login ouvre une session pour l'email et le mot de passe donnés.
func (s *Service) Login(ctx context.Context, email, password string) (*User, string, error) {
	var user User
	// Les comptes historiques peuvent avoir un email avec majuscules : on accepte les deux formes.
	raw := strings.TrimSpace(email)
	filter := bson.M{"email": bson.M{"$in": bson.A{NormalizeEmail(raw), raw}}}
	if err := s.col.FindOne(ctx, filter).Decode(&user); err != nil {
		_ = bcrypt.CompareHashAndPassword(dummyHash, []byte(password))
		return nil, "", ErrInvalidCredentials
	}
	if err := bcrypt.CompareHashAndPassword([]byte(user.Password), []byte(password)); err != nil {
		return nil, "", ErrInvalidCredentials
	}
	s.TouchLastSeen(ctx, user.ID.Hex())
	token, err := s.generateToken(user.ID.Hex())
	return &user, token, err
}

// lastSeenEvery : au plus une écriture de last_seen_at par heure et par compte
const lastSeenEvery = time.Hour

// TouchLastSeen note l'activité d'un compte (connexion, renouvellement de
// session) pour les statistiques admin. Le filtre n'écrit que si la dernière
// activité notée date de plus d'une heure ; un échec est seulement journalisé.
func (s *Service) TouchLastSeen(ctx context.Context, userID string) {
	id, err := bson.ObjectIDFromHex(userID)
	if err != nil {
		return
	}
	now := time.Now()
	filter := bson.M{"_id": id, "$or": bson.A{
		bson.M{"last_seen_at": bson.M{"$exists": false}},
		bson.M{"last_seen_at": bson.M{"$lt": now.Add(-lastSeenEvery)}},
	}}
	if _, err := s.col.UpdateOne(ctx, filter, bson.M{"$set": bson.M{"last_seen_at": now}}); err != nil {
		log.Printf("auth: last seen: %v", err)
	}
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
	issuedAt, _ := claims["iat"].(float64)
	// Un compte supprimé (RGPD) ne doit plus rien pouvoir faire, même avec un
	// jeton encore valide : sinon il recréerait des données orphelines. Après
	// un changement de mot de passe, les sessions ouvertes avant sont fermées.
	changedAt, ok := s.passwordChangedAt(userID)
	if !ok {
		return "", errors.New("account no longer exists")
	}
	if changedAt != nil && int64(issuedAt) < changedAt.Unix() {
		return "", errors.New("session closed by a password change")
	}
	return userID, nil
}

// passwordChangedAt retourne la date du dernier changement de mot de passe
// (nil si jamais) et false si le compte n'existe pas.
func (s *Service) passwordChangedAt(userID string) (*time.Time, bool) {
	id, err := bson.ObjectIDFromHex(userID)
	if err != nil {
		return nil, false
	}
	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()
	var u struct {
		PasswordChangedAt *time.Time `bson:"password_changed_at"`
	}
	err = s.col.FindOne(ctx, bson.M{"_id": id}, options.FindOne().SetProjection(bson.M{"password_changed_at": 1})).Decode(&u)
	if err != nil {
		return nil, false
	}
	return u.PasswordChangedAt, true
}

// CheckPassword vérifie le mot de passe d'un compte connecté, avant une
// action irréversible (suppression du compte).
func (s *Service) CheckPassword(ctx context.Context, userID, password string) error {
	id, err := bson.ObjectIDFromHex(userID)
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
	id, err := bson.ObjectIDFromHex(userID)
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
	cursor, err := s.col.Find(ctx, bson.M{}, options.Find().SetProjection(bson.M{"password": 0}))
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
	id, err := bson.ObjectIDFromHex(userID)
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
