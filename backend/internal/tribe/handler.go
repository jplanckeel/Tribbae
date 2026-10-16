package tribe

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
	pb.UnimplementedTribeServiceServer
	svc *Service
}

func NewHandler(svc *Service) *Handler { return &Handler{svc: svc} }

func toStatus(op string, err error) error {
	switch {
	case errors.Is(err, ErrInvalidInput):
		return status.Error(codes.InvalidArgument, err.Error())
	case errors.Is(err, ErrUserNotFound):
		return status.Error(codes.NotFound, "user not found with this email")
	case errors.Is(err, ErrAlreadyMember):
		return status.Error(codes.AlreadyExists, err.Error())
	case errors.Is(err, ErrInOtherTribe), errors.Is(err, ErrTribeFull):
		return status.Error(codes.FailedPrecondition, err.Error())
	case errors.Is(err, ErrForbidden):
		return status.Error(codes.PermissionDenied, err.Error())
	case errors.Is(err, ErrNotMember):
		return status.Error(codes.NotFound, err.Error())
	default:
		log.Printf("tribe: %s: %v", op, err)
		return status.Errorf(codes.Internal, "failed to %s", op)
	}
}

// toProto ajoute nom et email de chaque membre : les membres d'une tribu se
// connaissent (ils partagent déjà tout leur contenu).
func (h *Handler) toProto(ctx context.Context, t *Tribe) *pb.Tribe {
	if t == nil {
		return &pb.Tribe{}
	}
	ids := make([]string, 0, len(t.Members))
	for _, m := range t.Members {
		ids = append(ids, m.UserID)
	}
	users := h.svc.Users(ctx, ids)
	out := &pb.Tribe{Name: t.Name}
	if !t.ID.IsZero() {
		out.Id = t.ID.Hex()
	}
	for _, m := range t.Members {
		role := pb.TribeRole_TRIBE_ROLE_MEMBER
		if m.Role == RoleOwner {
			role = pb.TribeRole_TRIBE_ROLE_OWNER
		}
		pm := &pb.TribeMember{
			UserId:      m.UserID,
			DisplayName: users[m.UserID].DisplayName,
			Email:       users[m.UserID].Email,
			Role:        role,
			Sharing:     m.Sharing,
		}
		if !m.AddedAt.IsZero() {
			pm.AddedAt = m.AddedAt.UnixMilli()
		}
		out.Members = append(out.Members, pm)
	}
	return out
}

func user(ctx context.Context) (string, error) {
	id, err := interceptor.UserIDFromContext(ctx)
	if err != nil {
		return "", status.Error(codes.Unauthenticated, "unauthenticated")
	}
	return id, nil
}

func (h *Handler) GetTribe(ctx context.Context, _ *pb.GetTribeRequest) (*pb.GetTribeResponse, error) {
	uid, err := user(ctx)
	if err != nil {
		return nil, err
	}
	t, err := h.svc.Get(ctx, uid)
	if err != nil {
		return nil, toStatus("get tribe", err)
	}
	return &pb.GetTribeResponse{Tribe: h.toProto(ctx, t)}, nil
}

func (h *Handler) UpdateTribe(ctx context.Context, req *pb.UpdateTribeRequest) (*pb.UpdateTribeResponse, error) {
	uid, err := user(ctx)
	if err != nil {
		return nil, err
	}
	t, err := h.svc.Rename(ctx, uid, req.Name)
	if err != nil {
		return nil, toStatus("rename tribe", err)
	}
	return &pb.UpdateTribeResponse{Tribe: h.toProto(ctx, t)}, nil
}

func (h *Handler) AddTribeMember(ctx context.Context, req *pb.AddTribeMemberRequest) (*pb.AddTribeMemberResponse, error) {
	uid, err := user(ctx)
	if err != nil {
		return nil, err
	}
	t, err := h.svc.AddMember(ctx, uid, req.Email)
	if err != nil {
		return nil, toStatus("add tribe member", err)
	}
	return &pb.AddTribeMemberResponse{Tribe: h.toProto(ctx, t)}, nil
}

func (h *Handler) RemoveTribeMember(ctx context.Context, req *pb.RemoveTribeMemberRequest) (*pb.RemoveTribeMemberResponse, error) {
	uid, err := user(ctx)
	if err != nil {
		return nil, err
	}
	t, err := h.svc.RemoveMember(ctx, uid, req.UserId)
	if err != nil {
		return nil, toStatus("remove tribe member", err)
	}
	return &pb.RemoveTribeMemberResponse{Tribe: h.toProto(ctx, t)}, nil
}

func (h *Handler) SetTribeSharing(ctx context.Context, req *pb.SetTribeSharingRequest) (*pb.SetTribeSharingResponse, error) {
	uid, err := user(ctx)
	if err != nil {
		return nil, err
	}
	t, err := h.svc.SetSharing(ctx, uid, req.Sharing)
	if err != nil {
		return nil, toStatus("set tribe sharing", err)
	}
	return &pb.SetTribeSharingResponse{Tribe: h.toProto(ctx, t)}, nil
}
