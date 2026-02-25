package folder

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"errors"
	"time"

	"go.mongodb.org/mongo-driver/bson"
	"go.mongodb.org/mongo-driver/bson/primitive"
	"go.mongodb.org/mongo-driver/mongo"
	"go.mongodb.org/mongo-driver/mongo/options"
)

type CollaboratorEntry struct {
	UserID      string    `bson:"user_id"`
	Email       string    `bson:"email"`
	DisplayName string    `bson:"display_name"`
	Role        string    `bson:"role"` // "viewer" | "editor"
	AddedAt     time.Time `bson:"added_at"`
}

type Folder struct {
	ID            primitive.ObjectID  `bson:"_id,omitempty"`
	OwnerID       string              `bson:"owner_id"`
	Name          string              `bson:"name"`
	Icon          string              `bson:"icon"`
	Color         string              `bson:"color"`
	Visibility    string              `bson:"visibility"` // "private" | "public" | "shared"
	ShareToken    string              `bson:"share_token,omitempty"`
	Collaborators []CollaboratorEntry `bson:"collaborators,omitempty"`
	CreatedAt     time.Time           `bson:"created_at"`
	UpdatedAt     time.Time           `bson:"updated_at"`
}

type Service struct {
	col     *mongo.Collection
	linkCol *mongo.Collection
	userCol *mongo.Collection
	baseURL string
}

func NewService(col *mongo.Collection, linkCol *mongo.Collection, userCol *mongo.Collection, baseURL string) *Service {
	return &Service{col: col, linkCol: linkCol, userCol: userCol, baseURL: baseURL}
}

func (s *Service) Create(ctx context.Context, ownerID, name, icon, color, visibility string) (*Folder, error) {
	f := &Folder{
		ID:         primitive.NewObjectID(),
		OwnerID:    ownerID,
		Name:       name,
		Icon:       icon,
		Color:      color,
		Visibility: visibility,
		CreatedAt:  time.Now(),
		UpdatedAt:  time.Now(),
	}
	if _, err := s.col.InsertOne(ctx, f); err != nil {
		return nil, err
	}
	return f, nil
}

func (s *Service) Get(ctx context.Context, folderID, ownerID string) (*Folder, error) {
	id, err := primitive.ObjectIDFromHex(folderID)
	if err != nil {
		return nil, errors.New("invalid folder id")
	}
	var f Folder
	// Owner OU collaborateur peut accéder
	filter := bson.M{
		"_id": id,
		"$or": bson.A{
			bson.M{"owner_id": ownerID},
			bson.M{"collaborators.user_id": ownerID},
		},
	}
	if err := s.col.FindOne(ctx, filter).Decode(&f); err != nil {
		return nil, err
	}
	return &f, nil
}

func (s *Service) List(ctx context.Context, ownerID string) ([]*Folder, error) {
	// Retourne les dossiers dont l'utilisateur est owner OU collaborateur
	filter := bson.M{
		"$or": bson.A{
			bson.M{"owner_id": ownerID},
			bson.M{"collaborators.user_id": ownerID},
		},
	}
	cursor, err := s.col.Find(ctx, filter)
	if err != nil {
		return nil, err
	}
	defer cursor.Close(ctx)
	var folders []*Folder
	return folders, cursor.All(ctx, &folders)
}

func (s *Service) Update(ctx context.Context, folderID, ownerID, name, icon, color, visibility string) (*Folder, error) {
	id, err := primitive.ObjectIDFromHex(folderID)
	if err != nil {
		return nil, errors.New("invalid folder id")
	}
	// Owner OU éditeur peut modifier
	filter := bson.M{
		"_id": id,
		"$or": bson.A{
			bson.M{"owner_id": ownerID},
			bson.M{"collaborators": bson.M{"$elemMatch": bson.M{"user_id": ownerID, "role": "editor"}}},
		},
	}
	update := bson.M{"$set": bson.M{
		"name": name, "icon": icon, "color": color,
		"visibility": visibility, "updated_at": time.Now(),
	}}
	res, err := s.col.UpdateOne(ctx, filter, update)
	if err != nil {
		return nil, err
	}
	if res.MatchedCount == 0 {
		return nil, errors.New("not found or not authorized")
	}
	return s.Get(ctx, folderID, ownerID)
}

func (s *Service) Delete(ctx context.Context, folderID, ownerID string) error {
	id, err := primitive.ObjectIDFromHex(folderID)
	if err != nil {
		return errors.New("invalid folder id")
	}
	// Seul le owner peut supprimer
	_, err = s.col.DeleteOne(ctx, bson.M{"_id": id, "owner_id": ownerID})
	return err
}

func (s *Service) GenerateShareToken(ctx context.Context, folderID, ownerID string) (string, string, error) {
	id, err := primitive.ObjectIDFromHex(folderID)
	if err != nil {
		return "", "", errors.New("invalid folder id")
	}
	b := make([]byte, 16)
	if _, err := rand.Read(b); err != nil {
		return "", "", err
	}
	token := hex.EncodeToString(b)
	_, err = s.col.UpdateOne(ctx,
		bson.M{"_id": id, "owner_id": ownerID},
		bson.M{"$set": bson.M{"share_token": token, "updated_at": time.Now()}},
	)
	if err != nil {
		return "", "", err
	}
	return token, s.baseURL + "/share/" + token, nil
}

func (s *Service) GetByShareToken(ctx context.Context, token string) (*Folder, []map[string]any, error) {
	var f Folder
	if err := s.col.FindOne(ctx, bson.M{"share_token": token}).Decode(&f); err != nil {
		return nil, nil, err
	}
	cursor, err := s.linkCol.Find(ctx, bson.M{"folder_id": f.ID.Hex()})
	if err != nil {
		return &f, nil, nil
	}
	defer cursor.Close(ctx)
	var links []map[string]any
	cursor.All(ctx, &links)
	return &f, links, nil
}


// --- Collaborateurs ---

func (s *Service) AddCollaborator(ctx context.Context, folderID, ownerID, email, role string) (*Folder, error) {
	id, err := primitive.ObjectIDFromHex(folderID)
	if err != nil {
		return nil, errors.New("invalid folder id")
	}

	// Vérifier que le demandeur est le owner
	var f Folder
	if err := s.col.FindOne(ctx, bson.M{"_id": id, "owner_id": ownerID}).Decode(&f); err != nil {
		return nil, errors.New("not found or not authorized")
	}

	// Trouver l'utilisateur par email
	var user struct {
		ID          primitive.ObjectID `bson:"_id"`
		Email       string             `bson:"email"`
		DisplayName string             `bson:"display_name"`
	}
	if err := s.userCol.FindOne(ctx, bson.M{"email": email}).Decode(&user); err != nil {
		return nil, errors.New("user not found with this email")
	}

	// Vérifier qu'il n'est pas déjà collaborateur
	for _, c := range f.Collaborators {
		if c.UserID == user.ID.Hex() {
			return nil, errors.New("user is already a collaborator")
		}
	}

	collab := CollaboratorEntry{
		UserID:      user.ID.Hex(),
		Email:       user.Email,
		DisplayName: user.DisplayName,
		Role:        role,
		AddedAt:     time.Now(),
	}

	_, err = s.col.UpdateOne(ctx,
		bson.M{"_id": id, "owner_id": ownerID},
		bson.M{
			"$push": bson.M{"collaborators": collab},
			"$set":  bson.M{"updated_at": time.Now()},
		},
	)
	if err != nil {
		return nil, err
	}

	// Si la visibilité est encore "private", passer en "shared"
	if f.Visibility == "private" {
		s.col.UpdateOne(ctx,
			bson.M{"_id": id},
			bson.M{"$set": bson.M{"visibility": "shared"}},
		)
	}

	return s.Get(ctx, folderID, ownerID)
}

func (s *Service) RemoveCollaborator(ctx context.Context, folderID, ownerID, targetUserID string) (*Folder, error) {
	id, err := primitive.ObjectIDFromHex(folderID)
	if err != nil {
		return nil, errors.New("invalid folder id")
	}

	_, err = s.col.UpdateOne(ctx,
		bson.M{"_id": id, "owner_id": ownerID},
		bson.M{
			"$pull": bson.M{"collaborators": bson.M{"user_id": targetUserID}},
			"$set":  bson.M{"updated_at": time.Now()},
		},
	)
	if err != nil {
		return nil, err
	}
	return s.Get(ctx, folderID, ownerID)
}

// --- Communautaire ---

func (s *Service) ListCommunity(ctx context.Context, search string, pageSize int32, pageToken string) ([]*Folder, string, error) {
	if pageSize <= 0 || pageSize > 50 {
		pageSize = 20
	}

	filter := bson.M{"visibility": "public"}
	if search != "" {
		filter["name"] = bson.M{"$regex": search, "$options": "i"}
	}

	// Pagination par _id
	if pageToken != "" {
		if oid, err := primitive.ObjectIDFromHex(pageToken); err == nil {
			filter["_id"] = bson.M{"$gt": oid}
		}
	}

	opts := options.Find().
		SetSort(bson.M{"updated_at": -1}).
		SetLimit(int64(pageSize + 1))

	cursor, err := s.col.Find(ctx, filter, opts)
	if err != nil {
		return nil, "", err
	}
	defer cursor.Close(ctx)

	var folders []*Folder
	if err := cursor.All(ctx, &folders); err != nil {
		return nil, "", err
	}

	var nextToken string
	if len(folders) > int(pageSize) {
		nextToken = folders[pageSize].ID.Hex()
		folders = folders[:pageSize]
	}

	return folders, nextToken, nil
}

// GetOwnerDisplayName retourne le display_name d'un user par son ID
func (s *Service) GetOwnerDisplayName(ctx context.Context, ownerID string) string {
	oid, err := primitive.ObjectIDFromHex(ownerID)
	if err != nil {
		return ""
	}
	var user struct {
		DisplayName string `bson:"display_name"`
	}
	if err := s.userCol.FindOne(ctx, bson.M{"_id": oid}).Decode(&user); err != nil {
		return ""
	}
	return user.DisplayName
}

// CountLinks retourne le nombre de liens dans un dossier
func (s *Service) CountLinks(ctx context.Context, folderID string) int32 {
	count, err := s.linkCol.CountDocuments(ctx, bson.M{"folder_id": folderID})
	if err != nil {
		return 0
	}
	return int32(count)
}
