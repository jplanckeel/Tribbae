package child

import (
	"context"

	pb "github.com/tribbae/backend/gen/tribbae/v1"
	"github.com/tribbae/backend/internal/interceptor"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
	"google.golang.org/protobuf/types/known/timestamppb"
)

type Handler struct {
	pb.UnimplementedChildServiceServer
	svc *Service
}

func NewHandler(svc *Service) *Handler { return &Handler{svc: svc} }

func toProto(c *Child) *pb.Child {
	return &pb.Child{
		Id:        c.ID.Hex(),
		OwnerId:   c.OwnerID,
		Name:      c.Name,
		BirthDate: c.BirthDate,
		CreatedAt: timestamppb.New(c.CreatedAt),
	}
}

func (h *Handler) CreateChild(ctx context.Context, req *pb.CreateChildRequest) (*pb.CreateChildResponse, error) {
	ownerID, err := interceptor.UserIDFromContext(ctx)
	if err != nil {
		return nil, status.Errorf(codes.Unauthenticated, "unauthenticated")
	}
	c, err := h.svc.Create(ctx, ownerID, req.Name, req.BirthDate)
	if err != nil {
		return nil, status.Errorf(codes.Internal, err.Error())
	}
	return &pb.CreateChildResponse{Child: toProto(c)}, nil
}

func (h *Handler) ListChildren(ctx context.Context, _ *pb.ListChildrenRequest) (*pb.ListChildrenResponse, error) {
	ownerID, err := interceptor.UserIDFromContext(ctx)
	if err != nil {
		return nil, status.Errorf(codes.Unauthenticated, "unauthenticated")
	}
	children, err := h.svc.List(ctx, ownerID)
	if err != nil {
		return nil, status.Errorf(codes.Internal, err.Error())
	}
	var pbChildren []*pb.Child
	for _, c := range children {
		pbChildren = append(pbChildren, toProto(c))
	}
	return &pb.ListChildrenResponse{Children: pbChildren}, nil
}

func (h *Handler) UpdateChild(ctx context.Context, req *pb.UpdateChildRequest) (*pb.UpdateChildResponse, error) {
	ownerID, err := interceptor.UserIDFromContext(ctx)
	if err != nil {
		return nil, status.Errorf(codes.Unauthenticated, "unauthenticated")
	}
	c, err := h.svc.Update(ctx, req.ChildId, ownerID, req.Name, req.BirthDate)
	if err != nil {
		return nil, status.Errorf(codes.Internal, err.Error())
	}
	return &pb.UpdateChildResponse{Child: toProto(c)}, nil
}

func (h *Handler) DeleteChild(ctx context.Context, req *pb.DeleteChildRequest) (*pb.DeleteChildResponse, error) {
	ownerID, err := interceptor.UserIDFromContext(ctx)
	if err != nil {
		return nil, status.Errorf(codes.Unauthenticated, "unauthenticated")
	}
	if err := h.svc.Delete(ctx, req.ChildId, ownerID); err != nil {
		return nil, status.Errorf(codes.Internal, err.Error())
	}
	return &pb.DeleteChildResponse{}, nil
}
