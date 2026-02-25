package folder

import (
	"context"
	"time"

	pb "github.com/tribbae/backend/gen/tribbae/v1"
	"github.com/tribbae/backend/internal/interceptor"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
	"google.golang.org/protobuf/types/known/timestamppb"
)

type Handler struct {
	pb.UnimplementedFolderServiceServer
	svc *Service
}

func NewHandler(svc *Service) *Handler {
	return &Handler{svc: svc}
}

func collabRoleToProto(role string) pb.CollaboratorRole {
	if role == "editor" {
		return pb.CollaboratorRole_COLLABORATOR_ROLE_EDITOR
	}
	return pb.CollaboratorRole_COLLABORATOR_ROLE_VIEWER
}

func collabRoleToStr(role pb.CollaboratorRole) string {
	if role == pb.CollaboratorRole_COLLABORATOR_ROLE_EDITOR {
		return "editor"
	}
	return "viewer"
}

func (h *Handler) toProto(ctx context.Context, f *Folder) *pb.Folder {
	vis := pb.Visibility_VISIBILITY_PRIVATE
	switch f.Visibility {
	case "public":
		vis = pb.Visibility_VISIBILITY_PUBLIC
	case "shared":
		vis = pb.Visibility_VISIBILITY_SHARED
	}

	var collabs []*pb.Collaborator
	for _, c := range f.Collaborators {
		collabs = append(collabs, &pb.Collaborator{
			UserId:      c.UserID,
			Email:       c.Email,
			DisplayName: c.DisplayName,
			Role:        collabRoleToProto(c.Role),
			AddedAt:     timestamppb.New(c.AddedAt),
		})
	}

	// Vérifier si l'utilisateur courant a liké
	likedByMe := false
	if userID, err := interceptor.UserIDFromContext(ctx); err == nil {
		likedByMe = h.svc.IsLikedBy(ctx, f.ID.Hex(), userID)
	}

	return &pb.Folder{
		Id:               f.ID.Hex(),
		OwnerId:          f.OwnerID,
		Name:             f.Name,
		Icon:             f.Icon,
		Color:            f.Color,
		Visibility:       vis,
		ShareToken:       f.ShareToken,
		CreatedAt:        timestamppb.New(f.CreatedAt),
		UpdatedAt:        timestamppb.New(f.UpdatedAt),
		Collaborators:    collabs,
		OwnerDisplayName: h.svc.GetOwnerDisplayName(ctx, f.OwnerID),
		LinkCount:        h.svc.CountLinks(ctx, f.ID.Hex()),
		LikeCount:        f.LikeCount,
		LikedByMe:        likedByMe,
		AiGenerated:      f.AiGenerated,
	}
}

func visibilityStr(v pb.Visibility) string {
	switch v {
	case pb.Visibility_VISIBILITY_PUBLIC:
		return "public"
	case pb.Visibility_VISIBILITY_SHARED:
		return "shared"
	default:
		return "private"
	}
}

func (h *Handler) CreateFolder(ctx context.Context, req *pb.CreateFolderRequest) (*pb.CreateFolderResponse, error) {
	ownerID, err := interceptor.UserIDFromContext(ctx)
	if err != nil {
		return nil, status.Errorf(codes.Unauthenticated, "unauthenticated")
	}
	f, err := h.svc.Create(ctx, ownerID, req.Name, req.Icon, req.Color, visibilityStr(req.Visibility))
	if err != nil {
		return nil, status.Errorf(codes.Internal, err.Error())
	}
	return &pb.CreateFolderResponse{Folder: h.toProto(ctx, f)}, nil
}

func (h *Handler) GetFolder(ctx context.Context, req *pb.GetFolderRequest) (*pb.GetFolderResponse, error) {
	ownerID, err := interceptor.UserIDFromContext(ctx)
	if err != nil {
		return nil, status.Errorf(codes.Unauthenticated, "unauthenticated")
	}
	f, err := h.svc.Get(ctx, req.FolderId, ownerID)
	if err != nil {
		return nil, status.Errorf(codes.NotFound, err.Error())
	}
	return &pb.GetFolderResponse{Folder: h.toProto(ctx, f)}, nil
}


func (h *Handler) ListFolders(ctx context.Context, _ *pb.ListFoldersRequest) (*pb.ListFoldersResponse, error) {
	ownerID, err := interceptor.UserIDFromContext(ctx)
	if err != nil {
		return nil, status.Errorf(codes.Unauthenticated, "unauthenticated")
	}
	folders, err := h.svc.List(ctx, ownerID)
	if err != nil {
		return nil, status.Errorf(codes.Internal, err.Error())
	}
	var pbFolders []*pb.Folder
	for _, f := range folders {
		pbFolders = append(pbFolders, h.toProto(ctx, f))
	}
	return &pb.ListFoldersResponse{Folders: pbFolders}, nil
}

func (h *Handler) UpdateFolder(ctx context.Context, req *pb.UpdateFolderRequest) (*pb.UpdateFolderResponse, error) {
	ownerID, err := interceptor.UserIDFromContext(ctx)
	if err != nil {
		return nil, status.Errorf(codes.Unauthenticated, "unauthenticated")
	}
	f, err := h.svc.Update(ctx, req.FolderId, ownerID, req.Name, req.Icon, req.Color, visibilityStr(req.Visibility))
	if err != nil {
		return nil, status.Errorf(codes.Internal, err.Error())
	}
	return &pb.UpdateFolderResponse{Folder: h.toProto(ctx, f)}, nil
}

func (h *Handler) DeleteFolder(ctx context.Context, req *pb.DeleteFolderRequest) (*pb.DeleteFolderResponse, error) {
	ownerID, err := interceptor.UserIDFromContext(ctx)
	if err != nil {
		return nil, status.Errorf(codes.Unauthenticated, "unauthenticated")
	}
	if err := h.svc.Delete(ctx, req.FolderId, ownerID); err != nil {
		return nil, status.Errorf(codes.Internal, err.Error())
	}
	return &pb.DeleteFolderResponse{}, nil
}

func (h *Handler) GenerateShareToken(ctx context.Context, req *pb.GenerateShareTokenRequest) (*pb.GenerateShareTokenResponse, error) {
	ownerID, err := interceptor.UserIDFromContext(ctx)
	if err != nil {
		return nil, status.Errorf(codes.Unauthenticated, "unauthenticated")
	}
	token, url, err := h.svc.GenerateShareToken(ctx, req.FolderId, ownerID)
	if err != nil {
		return nil, status.Errorf(codes.Internal, err.Error())
	}
	return &pb.GenerateShareTokenResponse{ShareToken: token, ShareUrl: url}, nil
}

func (h *Handler) GetSharedFolder(ctx context.Context, req *pb.GetSharedFolderRequest) (*pb.GetSharedFolderResponse, error) {
	f, rawLinks, err := h.svc.GetByShareToken(ctx, req.ShareToken)
	if err != nil {
		return nil, status.Errorf(codes.NotFound, err.Error())
	}
	var pbLinks []*pb.Link
	for _, m := range rawLinks {
		l := &pb.Link{}
		if v, ok := m["_id"]; ok {
			l.Id = toString(v)
		}
		if v, ok := m["title"]; ok {
			l.Title = toString(v)
		}
		if v, ok := m["url"]; ok {
			l.Url = toString(v)
		}
		if v, ok := m["description"]; ok {
			l.Description = toString(v)
		}
		if v, ok := m["image_url"]; ok {
			l.ImageUrl = toString(v)
		}
		if v, ok := m["created_at"]; ok {
			if t, ok2 := v.(time.Time); ok2 {
				l.CreatedAt = timestamppb.New(t)
			}
		}
		pbLinks = append(pbLinks, l)
	}
	return &pb.GetSharedFolderResponse{Folder: h.toProto(ctx, f), Links: pbLinks}, nil
}

func (h *Handler) AddCollaborator(ctx context.Context, req *pb.AddCollaboratorRequest) (*pb.AddCollaboratorResponse, error) {
	ownerID, err := interceptor.UserIDFromContext(ctx)
	if err != nil {
		return nil, status.Errorf(codes.Unauthenticated, "unauthenticated")
	}
	f, err := h.svc.AddCollaborator(ctx, req.FolderId, ownerID, req.Email, collabRoleToStr(req.Role))
	if err != nil {
		return nil, status.Errorf(codes.InvalidArgument, err.Error())
	}
	return &pb.AddCollaboratorResponse{Folder: h.toProto(ctx, f)}, nil
}

func (h *Handler) RemoveCollaborator(ctx context.Context, req *pb.RemoveCollaboratorRequest) (*pb.RemoveCollaboratorResponse, error) {
	ownerID, err := interceptor.UserIDFromContext(ctx)
	if err != nil {
		return nil, status.Errorf(codes.Unauthenticated, "unauthenticated")
	}
	f, err := h.svc.RemoveCollaborator(ctx, req.FolderId, ownerID, req.UserId)
	if err != nil {
		return nil, status.Errorf(codes.Internal, err.Error())
	}
	return &pb.RemoveCollaboratorResponse{Folder: h.toProto(ctx, f)}, nil
}

func (h *Handler) ListCommunityFolders(ctx context.Context, req *pb.ListCommunityFoldersRequest) (*pb.ListCommunityFoldersResponse, error) {
	folders, nextToken, err := h.svc.ListCommunity(ctx, req.Search, req.PageSize, req.PageToken)
	if err != nil {
		return nil, status.Errorf(codes.Internal, err.Error())
	}
	var pbFolders []*pb.Folder
	for _, f := range folders {
		pbFolders = append(pbFolders, h.toProto(ctx, f))
	}
	return &pb.ListCommunityFoldersResponse{Folders: pbFolders, NextPageToken: nextToken}, nil
}

func (h *Handler) LikeFolder(ctx context.Context, req *pb.LikeFolderRequest) (*pb.LikeFolderResponse, error) {
	userID, err := interceptor.UserIDFromContext(ctx)
	if err != nil {
		return nil, status.Errorf(codes.Unauthenticated, "unauthenticated")
	}
	count, err := h.svc.Like(ctx, req.FolderId, userID)
	if err != nil {
		return nil, status.Errorf(codes.Internal, err.Error())
	}
	return &pb.LikeFolderResponse{LikeCount: count}, nil
}

func (h *Handler) UnlikeFolder(ctx context.Context, req *pb.UnlikeFolderRequest) (*pb.UnlikeFolderResponse, error) {
	userID, err := interceptor.UserIDFromContext(ctx)
	if err != nil {
		return nil, status.Errorf(codes.Unauthenticated, "unauthenticated")
	}
	count, err := h.svc.Unlike(ctx, req.FolderId, userID)
	if err != nil {
		return nil, status.Errorf(codes.Internal, err.Error())
	}
	return &pb.UnlikeFolderResponse{LikeCount: count}, nil
}

func (h *Handler) ListTopFolders(ctx context.Context, req *pb.ListTopFoldersRequest) (*pb.ListTopFoldersResponse, error) {
	folders, err := h.svc.ListTop(ctx, req.Limit)
	if err != nil {
		return nil, status.Errorf(codes.Internal, err.Error())
	}
	var pbFolders []*pb.Folder
	for _, f := range folders {
		pbFolders = append(pbFolders, h.toProto(ctx, f))
	}
	return &pb.ListTopFoldersResponse{Folders: pbFolders}, nil
}

func toString(v any) string {
	if s, ok := v.(string); ok {
		return s
	}
	return ""
}
