package auth

import (
	"context"

	pb "github.com/tribbae/backend/gen/tribbae/v1"
	"google.golang.org/grpc/codes"
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
		return nil, status.Errorf(codes.InvalidArgument, err.Error())
	}
	return &pb.RegisterResponse{UserId: userID, Token: token, IsAdmin: isAdmin}, nil
}

func (h *Handler) Login(ctx context.Context, req *pb.LoginRequest) (*pb.LoginResponse, error) {
	userID, token, displayName, isAdmin, err := h.svc.Login(ctx, req.Email, req.Password)
	if err != nil {
		return nil, status.Errorf(codes.Unauthenticated, err.Error())
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
