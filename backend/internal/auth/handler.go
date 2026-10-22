package auth

import (
	"context"
	"errors"
	"log"

	pb "github.com/tribbae/backend/gen/tribbae/v1"
	"github.com/tribbae/backend/internal/mail"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/metadata"
	"google.golang.org/grpc/status"
)

type Handler struct {
	pb.UnimplementedAuthServiceServer
	svc *Service
}

func NewHandler(svc *Service) *Handler {
	return &Handler{svc: svc}
}

func (h *Handler) Register(ctx context.Context, req *pb.RegisterRequest) (*pb.RegisterResponse, error) {
	userID, token, isAdmin, err := h.svc.Register(ctx, req.Email, req.Password, req.DisplayName)
	if err != nil {
		if errors.Is(err, ErrInvalidInput) || err.Error() == "email already registered" {
			return nil, status.Error(codes.InvalidArgument, err.Error())
		}
		log.Printf("auth: register: %v", err)
		return nil, status.Error(codes.Internal, "registration failed")
	}
	return &pb.RegisterResponse{UserId: userID, Token: token, IsAdmin: isAdmin}, nil
}

func (h *Handler) Login(ctx context.Context, req *pb.LoginRequest) (*pb.LoginResponse, error) {
	userID, token, displayName, isAdmin, err := h.svc.Login(ctx, req.Email, req.Password)
	if err != nil {
		return nil, status.Error(codes.Unauthenticated, ErrInvalidCredentials.Error())
	}
	return &pb.LoginResponse{UserId: userID, Token: token, DisplayName: displayName, IsAdmin: isAdmin}, nil
}

func (h *Handler) RefreshToken(ctx context.Context, req *pb.RefreshTokenRequest) (*pb.RefreshTokenResponse, error) {
	userID, err := h.svc.ValidateToken(req.Token)
	if err != nil {
		return nil, status.Errorf(codes.Unauthenticated, "invalid token")
	}
	token, err := h.svc.generateToken(userID)
	if err != nil {
		return nil, status.Errorf(codes.Internal, "could not generate token")
	}
	return &pb.RefreshTokenResponse{Token: token}, nil
}

// device décrit l'appareil de la requête (en-tête User-Agent relayé par la passerelle).
func device(ctx context.Context) string {
	md, _ := metadata.FromIncomingContext(ctx)
	for _, key := range []string{"grpcgateway-user-agent", "user-agent"} {
		if v := md.Get(key); len(v) > 0 {
			return mail.Device(v[0])
		}
	}
	return ""
}

func (h *Handler) RequestPasswordReset(ctx context.Context, req *pb.RequestPasswordResetRequest) (*pb.RequestPasswordResetResponse, error) {
	if err := h.svc.RequestPasswordReset(ctx, req.Email, device(ctx)); err != nil {
		// Même réponse qu'en cas de succès : rien ne doit révéler si l'adresse a un compte
		log.Printf("auth: password reset request: %v", err)
	}
	return &pb.RequestPasswordResetResponse{}, nil
}

func (h *Handler) ResetPassword(ctx context.Context, req *pb.ResetPasswordRequest) (*pb.ResetPasswordResponse, error) {
	userID, token, displayName, isAdmin, err := h.svc.ResetPassword(ctx, req.Token, req.Password, device(ctx))
	if err != nil {
		if errors.Is(err, ErrInvalidInput) || errors.Is(err, ErrInvalidResetToken) {
			return nil, status.Error(codes.InvalidArgument, err.Error())
		}
		log.Printf("auth: reset password: %v", err)
		return nil, status.Error(codes.Internal, "password reset failed")
	}
	return &pb.ResetPasswordResponse{UserId: userID, Token: token, DisplayName: displayName, IsAdmin: isAdmin}, nil
}
