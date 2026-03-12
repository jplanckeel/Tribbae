package interceptor

import (
	"context"
	"errors"
	"strings"

	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/metadata"
	"google.golang.org/grpc/status"
)

type contextKey string

const userIDKey contextKey = "user_id"

// TokenValidator est implémenté par auth.Service.
type TokenValidator interface {
	ValidateToken(token string) (string, error)
}

// publicMethods sont les méthodes qui ne nécessitent pas d'authentification.
var publicMethods = map[string]bool{
	"/tribbae.v1.AuthService/Register":              true,
	"/tribbae.v1.AuthService/Login":                  true,
	"/tribbae.v1.AuthService/RefreshToken":           true,
	"/tribbae.v1.FolderService/GetSharedFolder":      true,
	"/tribbae.v1.FolderService/ListCommunityFolders": true,
	"/tribbae.v1.FolderService/ListTopFolders":       true,
	"/tribbae.v1.LinkService/ListCommunityLinks":     true,
	"/tribbae.v1.LinkService/ListNewLinks":            true,
}

// UnaryAuth vérifie le token JWT pour les méthodes protégées.
// Pour les méthodes publiques, le token est optionnel (si présent, le userID est extrait).
func UnaryAuth(validator TokenValidator) grpc.UnaryServerInterceptor {
	return func(ctx context.Context, req any, info *grpc.UnaryServerInfo, handler grpc.UnaryHandler) (any, error) {
		if publicMethods[info.FullMethod] {
			// Méthode publique : token optionnel, on tente de l'extraire sans erreur
			ctx, _ = tryAuthenticate(ctx, validator)
			return handler(ctx, req)
		}
		ctx, err := authenticate(ctx, validator)
		if err != nil {
			return nil, err
		}
		return handler(ctx, req)
	}
}

func authenticate(ctx context.Context, validator TokenValidator) (context.Context, error) {
	md, ok := metadata.FromIncomingContext(ctx)
	if !ok {
		return ctx, status.Errorf(codes.Unauthenticated, "missing metadata")
	}
	vals := md.Get("authorization")
	if len(vals) == 0 {
		return ctx, status.Errorf(codes.Unauthenticated, "missing authorization header")
	}
	token := strings.TrimPrefix(vals[0], "Bearer ")
	userID, err := validator.ValidateToken(token)
	if err != nil {
		return ctx, status.Errorf(codes.Unauthenticated, "invalid token")
	}
	return context.WithValue(ctx, userIDKey, userID), nil
}

// tryAuthenticate tente d'extraire le userID sans retourner d'erreur si absent/invalide
func tryAuthenticate(ctx context.Context, validator TokenValidator) (context.Context, error) {
	md, ok := metadata.FromIncomingContext(ctx)
	if !ok {
		return ctx, nil
	}
	vals := md.Get("authorization")
	if len(vals) == 0 {
		return ctx, nil
	}
	token := strings.TrimPrefix(vals[0], "Bearer ")
	userID, err := validator.ValidateToken(token)
	if err != nil {
		return ctx, nil
	}
	return context.WithValue(ctx, userIDKey, userID), nil
}

func UserIDFromContext(ctx context.Context) (string, error) {
	v, ok := ctx.Value(userIDKey).(string)
	if !ok || v == "" {
		return "", errors.New("no user in context")
	}
	return v, nil
}

// AdminChecker vérifie si un utilisateur est admin
type AdminChecker interface {
	IsAdmin(ctx context.Context, userID string) (bool, error)
}

// adminMethods sont les méthodes qui nécessitent des droits admin
var adminMethods = map[string]bool{
	"/tribbae.v1.AdminService/ListUsers":         true,
	"/tribbae.v1.AdminService/UpdateUserPremium": true,
}

// UnaryAdminAuth vérifie que l'utilisateur est admin pour les méthodes admin
func UnaryAdminAuth(checker AdminChecker) grpc.UnaryServerInterceptor {
	return func(ctx context.Context, req any, info *grpc.UnaryServerInfo, handler grpc.UnaryHandler) (any, error) {
		if !adminMethods[info.FullMethod] {
			return handler(ctx, req)
		}

		userID, err := UserIDFromContext(ctx)
		if err != nil {
			return nil, status.Errorf(codes.Unauthenticated, "unauthenticated")
		}

		isAdmin, err := checker.IsAdmin(ctx, userID)
		if err != nil || !isAdmin {
			return nil, status.Errorf(codes.PermissionDenied, "admin access required")
		}

		return handler(ctx, req)
	}
}
