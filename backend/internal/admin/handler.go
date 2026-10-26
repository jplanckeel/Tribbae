package admin

import (
	"context"
	"errors"
	"log"

	pb "github.com/tribbae/backend/gen/tribbae/v1"
	"github.com/tribbae/backend/internal/auth"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
	"google.golang.org/protobuf/types/known/timestamppb"
)

type Handler struct {
	pb.UnimplementedAdminServiceServer
	authSvc  *auth.Service
	statsSvc *StatsService
}

func NewHandler(authSvc *auth.Service, statsSvc *StatsService) *Handler {
	return &Handler{authSvc: authSvc, statsSvc: statsSvc}
}

func (h *Handler) ListUsers(ctx context.Context, req *pb.ListUsersRequest) (*pb.ListUsersResponse, error) {
	users, err := h.authSvc.ListUsers(ctx)
	if err != nil {
		return nil, status.Errorf(codes.Internal, "failed to list users: %v", err)
	}

	var pbUsers []*pb.User
	for _, u := range users {
		pbUsers = append(pbUsers, &pb.User{
			Id:          u.ID.Hex(),
			Email:       u.Email,
			DisplayName: u.DisplayName,
			IsAdmin:     u.IsAdmin,
			IsPremium:   u.IsPremium,
			CreatedAt:   timestamppb.New(u.CreatedAt).AsTime().Unix(),
		})
	}

	return &pb.ListUsersResponse{Users: pbUsers}, nil
}

func (h *Handler) UpdateUserPremium(ctx context.Context, req *pb.UpdateUserPremiumRequest) (*pb.UpdateUserPremiumResponse, error) {
	if err := h.authSvc.UpdateUserPremium(ctx, req.UserId, req.IsPremium); err != nil {
		return nil, status.Errorf(codes.Internal, "failed to update user: %v", err)
	}

	user, err := h.authSvc.GetUser(ctx, req.UserId)
	if err != nil {
		return nil, status.Errorf(codes.Internal, "failed to get user: %v", err)
	}

	return &pb.UpdateUserPremiumResponse{
		User: &pb.User{
			Id:          user.ID.Hex(),
			Email:       user.Email,
			DisplayName: user.DisplayName,
			IsAdmin:     user.IsAdmin,
			IsPremium:   user.IsPremium,
			CreatedAt:   timestamppb.New(user.CreatedAt).AsTime().Unix(),
		},
	}, nil
}

func (h *Handler) GetStats(ctx context.Context, req *pb.GetStatsRequest) (*pb.GetStatsResponse, error) {
	st, err := h.statsSvc.Stats(ctx, req.Period, int(req.Count))
	if err != nil {
		if errors.Is(err, ErrInvalidStatsRequest) {
			return nil, status.Error(codes.InvalidArgument, err.Error())
		}
		log.Printf("admin: stats: %v", err)
		return nil, status.Error(codes.Internal, "failed to compute stats")
	}
	return &pb.GetStatsResponse{
		Period:       st.Period,
		Ideas:        toPB(st.Ideas),
		PublicIdeas:  toPB(st.PublicIdeas),
		PrivateIdeas: toPB(st.PrivateIdeas),
		NewUsers:     toPB(st.NewUsers),
		ActiveUsers:  toPB(st.ActiveUsers),
		Totals: &pb.StatsTotals{
			Ideas:        Total(st.Ideas),
			PublicIdeas:  Total(st.PublicIdeas),
			PrivateIdeas: Total(st.PrivateIdeas),
			NewUsers:     Total(st.NewUsers),
			ActiveUsers:  Total(st.ActiveUsers),
		},
	}, nil
}

func toPB(points []Point) []*pb.StatsPoint {
	out := make([]*pb.StatsPoint, len(points))
	for i, p := range points {
		out[i] = &pb.StatsPoint{BucketStart: timestamppb.New(p.Start), Count: p.Count}
	}
	return out
}
