package moderation

import (
	"context"
	"errors"
	"log"

	pb "github.com/tribbae/backend/gen/tribbae/v1"
	"github.com/tribbae/backend/internal/interceptor"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

type Handler struct {
	pb.UnimplementedModerationServiceServer
	svc *Service
}

func NewHandler(svc *Service) *Handler { return &Handler{svc: svc} }

func toStatus(op string, err error) error {
	switch {
	case errors.Is(err, ErrInvalidInput):
		return status.Error(codes.InvalidArgument, "invalid input")
	case errors.Is(err, ErrNotFound):
		return status.Error(codes.NotFound, "not found")
	case errors.Is(err, ErrTooMany):
		return status.Error(codes.ResourceExhausted, "too many reports, try again tomorrow")
	case errors.Is(err, ErrOwnContent):
		return status.Error(codes.InvalidArgument, err.Error())
	case errors.Is(err, ErrProtected):
		return status.Error(codes.FailedPrecondition, err.Error())
	default:
		log.Printf("moderation: %s: %v", op, err)
		return status.Errorf(codes.Internal, "failed to %s", op)
	}
}

func (h *Handler) ReportContent(ctx context.Context, req *pb.ReportContentRequest) (*pb.ReportContentResponse, error) {
	userID, err := interceptor.UserIDFromContext(ctx)
	if err != nil {
		return nil, status.Error(codes.Unauthenticated, "unauthenticated")
	}
	if err := h.svc.Report(ctx, userID, req.TargetType, req.TargetId, req.Reason, req.Details); err != nil {
		return nil, toStatus("report", err)
	}
	return &pb.ReportContentResponse{}, nil
}

func (h *Handler) BlockUser(ctx context.Context, req *pb.BlockUserRequest) (*pb.BlockUserResponse, error) {
	userID, err := interceptor.UserIDFromContext(ctx)
	if err != nil {
		return nil, status.Error(codes.Unauthenticated, "unauthenticated")
	}
	if err := h.svc.Block(ctx, userID, req.UserId); err != nil {
		return nil, toStatus("block", err)
	}
	return &pb.BlockUserResponse{}, nil
}

func (h *Handler) UnblockUser(ctx context.Context, req *pb.UnblockUserRequest) (*pb.UnblockUserResponse, error) {
	userID, err := interceptor.UserIDFromContext(ctx)
	if err != nil {
		return nil, status.Error(codes.Unauthenticated, "unauthenticated")
	}
	if err := h.svc.Unblock(ctx, userID, req.UserId); err != nil {
		return nil, toStatus("unblock", err)
	}
	return &pb.UnblockUserResponse{}, nil
}

func (h *Handler) ListBlockedUsers(ctx context.Context, _ *pb.ListBlockedUsersRequest) (*pb.ListBlockedUsersResponse, error) {
	userID, err := interceptor.UserIDFromContext(ctx)
	if err != nil {
		return nil, status.Error(codes.Unauthenticated, "unauthenticated")
	}
	list, err := h.svc.ListBlocked(ctx, userID)
	if err != nil {
		return nil, toStatus("list blocked users", err)
	}
	out := make([]*pb.BlockedUser, 0, len(list))
	for _, b := range list {
		out = append(out, &pb.BlockedUser{UserId: b.UserID, DisplayName: b.DisplayName, BlockedAt: b.BlockedAt.Unix()})
	}
	return &pb.ListBlockedUsersResponse{Users: out}, nil
}

// ToPB : signalement pour l'API d'administration.
func ToPB(r AdminReport) *pb.Report {
	return &pb.Report{
		Id: r.ID.Hex(), TargetType: r.TargetType, TargetId: r.TargetID, TargetOwnerId: r.TargetOwnerID,
		TargetOwnerName: r.OwnerName, TargetSummary: r.TargetSummary, Reason: r.Reason, Details: r.Details,
		ReporterName: r.ReporterName, Status: r.Status, Action: r.Action, CreatedAt: r.CreatedAt.Unix(), LinkId: r.LinkID,
	}
}

// StatusFor : erreurs du service pour l'API d'administration.
func StatusFor(op string, err error) error { return toStatus(op, err) }
