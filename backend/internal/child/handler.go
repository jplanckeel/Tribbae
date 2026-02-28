package child

import (
	"context"

	pb "github.com/tribbae/backend/gen/tribbae/v1"
	"go.mongodb.org/mongo-driver/bson/primitive"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

type Handler struct {
	pb.UnimplementedChildServiceServer
	svc *Service
}

func NewHandler(svc *Service) *Handler {
	return &Handler{svc: svc}
}

func (h *Handler) CreateChild(ctx context.Context, req *pb.CreateChildRequest) (*pb.CreateChildResponse, error) {
	ownerID, ok := ctx.Value("userID").(primitive.ObjectID)
	if !ok {
		return nil, status.Error(codes.Unauthenticated, "user not authenticated")
	}

	child, err := h.svc.Create(ctx, ownerID, req.Name, req.BirthDate)
	if err != nil {
		return nil, status.Error(codes.Internal, err.Error())
	}

	return &pb.CreateChildResponse{
		Child: &pb.Child{
			Id:        child.ID.Hex(),
			OwnerId:   child.OwnerID.Hex(),
			Name:      child.Name,
			BirthDate: child.BirthDate,
			CreatedAt: child.CreatedAt,
		},
	}, nil
}

func (h *Handler) ListChildren(ctx context.Context, req *pb.ListChildrenRequest) (*pb.ListChildrenResponse, error) {
	ownerID, ok := ctx.Value("userID").(primitive.ObjectID)
	if !ok {
		return nil, status.Error(codes.Unauthenticated, "user not authenticated")
	}

	children, err := h.svc.List(ctx, ownerID)
	if err != nil {
		return nil, status.Error(codes.Internal, err.Error())
	}

	pbChildren := make([]*pb.Child, len(children))
	for i, c := range children {
		pbChildren[i] = &pb.Child{
			Id:        c.ID.Hex(),
			OwnerId:   c.OwnerID.Hex(),
			Name:      c.Name,
			BirthDate: c.BirthDate,
			CreatedAt: c.CreatedAt,
		}
	}

	return &pb.ListChildrenResponse{Children: pbChildren}, nil
}

func (h *Handler) UpdateChild(ctx context.Context, req *pb.UpdateChildRequest) (*pb.UpdateChildResponse, error) {
	ownerID, ok := ctx.Value("userID").(primitive.ObjectID)
	if !ok {
		return nil, status.Error(codes.Unauthenticated, "user not authenticated")
	}

	childID, err := primitive.ObjectIDFromHex(req.ChildId)
	if err != nil {
		return nil, status.Error(codes.InvalidArgument, "invalid child ID")
	}

	child, err := h.svc.Update(ctx, childID, ownerID, req.Name, req.BirthDate)
	if err != nil {
		return nil, status.Error(codes.Internal, err.Error())
	}

	return &pb.UpdateChildResponse{
		Child: &pb.Child{
			Id:        child.ID.Hex(),
			OwnerId:   child.OwnerID.Hex(),
			Name:      child.Name,
			BirthDate: child.BirthDate,
			CreatedAt: child.CreatedAt,
		},
	}, nil
}

func (h *Handler) DeleteChild(ctx context.Context, req *pb.DeleteChildRequest) (*pb.DeleteChildResponse, error) {
	ownerID, ok := ctx.Value("userID").(primitive.ObjectID)
	if !ok {
		return nil, status.Error(codes.Unauthenticated, "user not authenticated")
	}

	childID, err := primitive.ObjectIDFromHex(req.ChildId)
	if err != nil {
		return nil, status.Error(codes.InvalidArgument, "invalid child ID")
	}

	if err := h.svc.Delete(ctx, childID, ownerID); err != nil {
		return nil, status.Error(codes.Internal, err.Error())
	}

	return &pb.DeleteChildResponse{}, nil
}
