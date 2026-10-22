package folder

import (
	"context"
	"errors"
	"log"
	"time"

	pb "github.com/tribbae/backend/gen/tribbae/v1"
	"github.com/tribbae/backend/internal/interceptor"
	"go.mongodb.org/mongo-driver/bson/primitive"
	"go.mongodb.org/mongo-driver/mongo"
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

// toProto sérialise un dossier pour l'utilisateur courant. Le jeton de partage
// et les emails des collaborateurs ne sont visibles que du propriétaire ; les
// autres collaborateurs voient seulement les noms, les visiteurs rien.
func (h *Handler) toProto(ctx context.Context, f *Folder, st *Stats) *pb.Folder {
	vis := pb.Visibility_VISIBILITY_PRIVATE
	switch f.Visibility {
	case "public":
		vis = pb.Visibility_VISIBILITY_PUBLIC
	case "shared":
		vis = pb.Visibility_VISIBILITY_SHARED
	}

	viewerID, _ := interceptor.UserIDFromContext(ctx)
	isOwner := viewerID != "" && viewerID == f.OwnerID
	isCollaborator := false
	likedByMe := false
	for _, c := range f.Collaborators {
		if viewerID != "" && c.UserID == viewerID {
			isCollaborator = true
		}
	}
	for _, u := range f.LikedBy {
		if viewerID != "" && u == viewerID {
			likedByMe = true
		}
	}

	var collabs []*pb.Collaborator
	if isOwner || isCollaborator {
		for _, c := range f.Collaborators {
			pc := &pb.Collaborator{
				UserId:      c.UserID,
				DisplayName: c.DisplayName,
				Role:        collabRoleToProto(c.Role),
				AddedAt:     timestamppb.New(c.AddedAt),
			}
			if isOwner {
				pc.Email = c.Email
			}
			collabs = append(collabs, pc)
		}
	}
	shareToken := ""
	if isOwner {
		shareToken = f.ShareToken
	}

	owner := st.Owners[f.OwnerID]
	pf := &pb.Folder{
		Id:               f.ID.Hex(),
		OwnerId:          f.OwnerID,
		Name:             f.Name,
		Icon:             f.Icon,
		Color:            f.Color,
		BannerUrl:        f.BannerURL,
		Tags:             f.Tags,
		Visibility:       vis,
		ShareToken:       shareToken,
		CreatedAt:        timestamppb.New(f.CreatedAt),
		UpdatedAt:        timestamppb.New(f.UpdatedAt),
		Collaborators:    collabs,
		OwnerDisplayName: owner.DisplayName,
		LinkCount:        st.LinkCount[f.ID.Hex()],
		LikeCount:        f.LikeCount,
		LikedByMe:        likedByMe,
		AiGenerated:      f.AiGenerated,
		OwnerIsAdmin:     owner.IsAdmin,
		Description:      f.Description,
		Cover:            f.Cover,
		Illustration:     f.Illustration,
		ViewMode:         f.ViewMode,
		Sort:             f.Sort,
	}
	// Date clé, enfants concernés et épinglage restent dans la famille
	// (et chez les collaborateurs invités) : pas pour un visiteur d'un dossier public.
	if isOwner || isCollaborator || h.svc.IsPeer(ctx, viewerID, f.OwnerID) {
		pf.KeyDate = f.KeyDate
		pf.KeyDateType = f.KeyDateType
		pf.ChildIds = f.ChildIDs
		pf.Pinned = f.Pinned
	}
	return pf
}

func (h *Handler) one(ctx context.Context, f *Folder) *pb.Folder {
	return h.toProto(ctx, f, h.svc.LoadStats(ctx, []*Folder{f}))
}

func (h *Handler) many(ctx context.Context, folders []*Folder) []*pb.Folder {
	st := h.svc.LoadStats(ctx, folders)
	out := make([]*pb.Folder, 0, len(folders))
	for _, f := range folders {
		out = append(out, h.toProto(ctx, f, st))
	}
	return out
}

// toStatus traduit les erreurs métier en codes gRPC sans exposer les détails internes.
func toStatus(op string, err error) error {
	switch {
	case errors.Is(err, ErrInvalidInput):
		return status.Error(codes.InvalidArgument, err.Error())
	case errors.Is(err, ErrNotFound), errors.Is(err, mongo.ErrNoDocuments):
		return status.Error(codes.NotFound, "folder not found")
	default:
		log.Printf("folder: %s: %v", op, err)
		return status.Errorf(codes.Internal, "failed to %s", op)
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
		return nil, status.Error(codes.Unauthenticated, "unauthenticated")
	}
	f, err := h.svc.Create(ctx, ownerID, req.Name, req.Icon, req.Color, visibilityStr(req.Visibility), req.BannerUrl, req.Tags, Style{
		Description: req.Description, Cover: req.Cover, Illustration: req.Illustration,
		KeyDate: req.KeyDate, KeyDateType: req.KeyDateType, ChildIDs: req.ChildIds,
		ViewMode: req.ViewMode, Sort: req.Sort, Pinned: req.Pinned,
	})
	if err != nil {
		return nil, toStatus("create folder", err)
	}
	return &pb.CreateFolderResponse{Folder: h.one(ctx, f)}, nil
}

func (h *Handler) GetFolder(ctx context.Context, req *pb.GetFolderRequest) (*pb.GetFolderResponse, error) {
	// Méthode publique : dossier public, ou propriétaire / collaborateur.
	viewerID, _ := interceptor.UserIDFromContext(ctx)
	f, err := h.svc.Get(ctx, req.FolderId, viewerID)
	if err != nil {
		return nil, toStatus("get folder", err)
	}
	return &pb.GetFolderResponse{Folder: h.one(ctx, f)}, nil
}

func (h *Handler) ListFolders(ctx context.Context, _ *pb.ListFoldersRequest) (*pb.ListFoldersResponse, error) {
	ownerID, err := interceptor.UserIDFromContext(ctx)
	if err != nil {
		return nil, status.Error(codes.Unauthenticated, "unauthenticated")
	}
	folders, err := h.svc.List(ctx, ownerID)
	if err != nil {
		return nil, toStatus("list folders", err)
	}
	pbFolders := h.many(ctx, folders)
	return &pb.ListFoldersResponse{Folders: pbFolders}, nil
}

func (h *Handler) UpdateFolder(ctx context.Context, req *pb.UpdateFolderRequest) (*pb.UpdateFolderResponse, error) {
	ownerID, err := interceptor.UserIDFromContext(ctx)
	if err != nil {
		return nil, status.Error(codes.Unauthenticated, "unauthenticated")
	}
	f, err := h.svc.Update(ctx, req.FolderId, ownerID, req.Name, req.Icon, req.Color, visibilityStr(req.Visibility), req.BannerUrl, req.Tags, stylePatch(req))
	if err != nil {
		return nil, toStatus("update folder", err)
	}
	return &pb.UpdateFolderResponse{Folder: h.one(ctx, f)}, nil
}

func stylePatch(req *pb.UpdateFolderRequest) StylePatch {
	p := StylePatch{
		Description: req.Description, Cover: req.Cover, Illustration: req.Illustration,
		KeyDate: req.KeyDate, KeyDateType: req.KeyDateType,
		ViewMode: req.ViewMode, Sort: req.Sort, Pinned: req.Pinned,
	}
	if req.SetChildIds {
		p.ChildIDs = &req.ChildIds
	}
	return p
}

func (h *Handler) DeleteFolder(ctx context.Context, req *pb.DeleteFolderRequest) (*pb.DeleteFolderResponse, error) {
	ownerID, err := interceptor.UserIDFromContext(ctx)
	if err != nil {
		return nil, status.Error(codes.Unauthenticated, "unauthenticated")
	}
	if err := h.svc.Delete(ctx, req.FolderId, ownerID); err != nil {
		return nil, toStatus("delete folder", err)
	}
	return &pb.DeleteFolderResponse{}, nil
}

func (h *Handler) GenerateShareToken(ctx context.Context, req *pb.GenerateShareTokenRequest) (*pb.GenerateShareTokenResponse, error) {
	ownerID, err := interceptor.UserIDFromContext(ctx)
	if err != nil {
		return nil, status.Error(codes.Unauthenticated, "unauthenticated")
	}
	token, url, err := h.svc.GenerateShareToken(ctx, req.FolderId, ownerID)
	if err != nil {
		return nil, toStatus("share folder", err)
	}
	return &pb.GenerateShareTokenResponse{ShareToken: token, ShareUrl: url}, nil
}

func (h *Handler) GetSharedFolder(ctx context.Context, req *pb.GetSharedFolderRequest) (*pb.GetSharedFolderResponse, error) {
	f, rawLinks, err := h.svc.GetByShareToken(ctx, req.ShareToken)
	if err != nil {
		return nil, toStatus("get shared folder", err)
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
		l.Category = pb.LinkCategory(pb.LinkCategory_value[toString(m["category"])])
		l.Tags = toStrings(m["tags"])
		l.AgeRange = toString(m["age_range"])
		l.Location = toString(m["location"])
		l.Price = toString(m["price"])
		if v, ok := m["created_at"]; ok {
			if t, ok2 := v.(time.Time); ok2 {
				l.CreatedAt = timestamppb.New(t)
			}
		}
		pbLinks = append(pbLinks, l)
	}
	return &pb.GetSharedFolderResponse{Folder: h.one(ctx, f), Links: pbLinks}, nil
}

func (h *Handler) AddCollaborator(ctx context.Context, req *pb.AddCollaboratorRequest) (*pb.AddCollaboratorResponse, error) {
	ownerID, err := interceptor.UserIDFromContext(ctx)
	if err != nil {
		return nil, status.Error(codes.Unauthenticated, "unauthenticated")
	}
	f, err := h.svc.AddCollaborator(ctx, req.FolderId, ownerID, req.Email, collabRoleToStr(req.Role))
	if err != nil {
		return nil, status.Error(codes.InvalidArgument, err.Error())
	}
	return &pb.AddCollaboratorResponse{Folder: h.one(ctx, f)}, nil
}

func (h *Handler) RemoveCollaborator(ctx context.Context, req *pb.RemoveCollaboratorRequest) (*pb.RemoveCollaboratorResponse, error) {
	ownerID, err := interceptor.UserIDFromContext(ctx)
	if err != nil {
		return nil, status.Error(codes.Unauthenticated, "unauthenticated")
	}
	f, err := h.svc.RemoveCollaborator(ctx, req.FolderId, ownerID, req.UserId)
	if err != nil {
		return nil, toStatus("remove collaborator", err)
	}
	return &pb.RemoveCollaboratorResponse{Folder: h.one(ctx, f)}, nil
}

func (h *Handler) ListCommunityFolders(ctx context.Context, req *pb.ListCommunityFoldersRequest) (*pb.ListCommunityFoldersResponse, error) {
	folders, nextToken, err := h.svc.ListCommunity(ctx, req.Search, req.PageSize, req.PageToken)
	if err != nil {
		return nil, toStatus("list community folders", err)
	}
	pbFolders := h.many(ctx, folders)
	return &pb.ListCommunityFoldersResponse{Folders: pbFolders, NextPageToken: nextToken}, nil
}

func (h *Handler) LikeFolder(ctx context.Context, req *pb.LikeFolderRequest) (*pb.LikeFolderResponse, error) {
	userID, err := interceptor.UserIDFromContext(ctx)
	if err != nil {
		return nil, status.Errorf(codes.Unauthenticated, "unauthenticated")
	}
	count, err := h.svc.Like(ctx, req.FolderId, userID)
	if err != nil {
		return nil, toStatus("like folder", err)
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
		return nil, toStatus("unlike folder", err)
	}
	return &pb.UnlikeFolderResponse{LikeCount: count}, nil
}

func (h *Handler) ListTopFolders(ctx context.Context, req *pb.ListTopFoldersRequest) (*pb.ListTopFoldersResponse, error) {
	folders, err := h.svc.ListTop(ctx, req.Limit)
	if err != nil {
		return nil, toStatus("list top folders", err)
	}
	pbFolders := h.many(ctx, folders)
	return &pb.ListTopFoldersResponse{Folders: pbFolders}, nil
}

func toString(v any) string {
	switch t := v.(type) {
	case string:
		return t
	case primitive.ObjectID:
		return t.Hex()
	}
	return ""
}

func toStrings(v any) []string {
	arr, ok := v.(primitive.A)
	if !ok {
		return nil
	}
	out := make([]string, 0, len(arr))
	for _, x := range arr {
		if s, ok := x.(string); ok {
			out = append(out, s)
		}
	}
	return out
}
