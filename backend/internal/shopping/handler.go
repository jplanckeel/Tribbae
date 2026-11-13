package shopping

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
	pb.UnimplementedShoppingServiceServer
	svc *Service
}

func NewHandler(svc *Service) *Handler { return &Handler{svc: svc} }

func toStatus(op string, err error) error {
	switch {
	case errors.Is(err, ErrInvalidInput):
		return status.Error(codes.InvalidArgument, err.Error())
	case errors.Is(err, ErrNotFound):
		return status.Error(codes.NotFound, "recipe not found")
	case errors.Is(err, ErrConflict):
		return status.Error(codes.Aborted, err.Error())
	default:
		log.Printf("shopping: %s: %v", op, err)
		return status.Errorf(codes.Internal, "failed to %s", op)
	}
}

func (h *Handler) GetShoppingList(ctx context.Context, req *pb.GetShoppingListRequest) (*pb.GetShoppingListResponse, error) {
	userID, err := interceptor.UserIDFromContext(ctx)
	if err != nil {
		return nil, status.Error(codes.Unauthenticated, "unauthenticated")
	}
	v, unchanged, err := h.svc.Get(ctx, userID, req.SinceVersion)
	if err != nil {
		return nil, toStatus("get shopping list", err)
	}
	if unchanged {
		return &pb.GetShoppingListResponse{Unchanged: true}, nil
	}
	return &pb.GetShoppingListResponse{List: toProto(v)}, nil
}

func (h *Handler) ApplyShoppingOps(ctx context.Context, req *pb.ApplyShoppingOpsRequest) (*pb.ApplyShoppingOpsResponse, error) {
	userID, err := interceptor.UserIDFromContext(ctx)
	if err != nil {
		return nil, status.Error(codes.Unauthenticated, "unauthenticated")
	}
	ops := make([]Op, 0, len(req.Ops))
	for _, o := range req.Ops {
		ops = append(ops, Op{
			Type: o.Type, LinkID: o.LinkId, Servings: o.Servings, ID: o.Id, Label: o.Label,
			Quantity: o.Quantity, Aisle: o.Aisle, Key: o.Key, Value: o.Value, Order: o.Order,
		})
	}
	v, err := h.svc.Apply(ctx, userID, ops)
	if err != nil {
		return nil, toStatus("update shopping list", err)
	}
	return &pb.ApplyShoppingOpsResponse{List: toProto(v)}, nil
}

func toProto(v *View) *pb.ShoppingList {
	out := &pb.ShoppingList{
		Version: v.Version, AisleOrder: v.AisleOrder, Pantry: v.Pantry, Removed: v.Removed, ShopperId: v.ShopperID,
	}
	if v.ShoppingSince != nil {
		out.ShoppingSince = v.ShoppingSince.Unix()
	}
	for _, e := range v.Menu {
		out.Menu = append(out.Menu, &pb.ShoppingMenuEntry{
			LinkId: e.LinkID, Title: e.Title, ImageUrl: e.ImageURL, Ingredients: e.Ingredients,
			BaseServings: e.BaseServings, Servings: e.Servings, AddedBy: e.AddedBy, AddedAt: e.AddedAt.Unix(), Cooked: e.Cooked,
		})
	}
	for _, it := range v.Items {
		out.Items = append(out.Items, &pb.ShoppingItem{
			Id: it.ID, Label: it.Label, Quantity: it.Quantity, Aisle: it.Aisle, AddedBy: it.AddedBy, AddedAt: it.AddedAt.Unix(),
		})
	}
	for _, c := range v.Checked {
		out.Checked = append(out.Checked, &pb.ShoppingCheck{Key: c.Key, By: c.By, At: c.At.Unix()})
	}
	for _, q := range v.Quantities {
		out.Quantities = append(out.Quantities, &pb.ShoppingProductValue{Key: q.Key, Value: q.Value})
	}
	for _, a := range v.Aisles {
		out.Aisles = append(out.Aisles, &pb.ShoppingProductValue{Key: a.Key, Value: a.Value})
	}
	return out
}
