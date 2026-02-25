package link

import (
	"context"
	"errors"
	"time"

	"go.mongodb.org/mongo-driver/bson"
	"go.mongodb.org/mongo-driver/bson/primitive"
	"go.mongodb.org/mongo-driver/mongo"
)

type Link struct {
	ID              primitive.ObjectID `bson:"_id,omitempty"    json:"id"`
	OwnerID         string             `bson:"owner_id"         json:"owner_id"`
	FolderID        string             `bson:"folder_id"        json:"folder_id"`
	Title           string             `bson:"title"            json:"title"`
	URL             string             `bson:"url"              json:"url"`
	Description     string             `bson:"description"      json:"description"`
	Category        string             `bson:"category"         json:"category"`
	Tags            []string           `bson:"tags"             json:"tags"`
	AgeRange        string             `bson:"age_range"        json:"age_range"`
	Location        string             `bson:"location"         json:"location"`
	Price           string             `bson:"price"            json:"price"`
	ImageURL        string             `bson:"image_url"        json:"image_url"`
	EventDate       int64              `bson:"event_date"       json:"event_date"`
	ReminderEnabled bool               `bson:"reminder_enabled" json:"reminder_enabled"`
	Rating          int32              `bson:"rating"           json:"rating"`
	Ingredients     []string           `bson:"ingredients"      json:"ingredients"`
	CreatedAt       time.Time          `bson:"created_at"       json:"created_at"`
	UpdatedAt       time.Time          `bson:"updated_at"       json:"updated_at"`
}

type Service struct {
	col       *mongo.Collection
	folderCol *mongo.Collection
}

func NewService(col *mongo.Collection, folderCol *mongo.Collection) *Service {
	return &Service{col: col, folderCol: folderCol}
}

// accessibleFolderIDs retourne les IDs de dossiers auxquels l'utilisateur a accès
func (s *Service) accessibleFolderIDs(ctx context.Context, userID string) ([]string, error) {
	filter := bson.M{
		"$or": bson.A{
			bson.M{"owner_id": userID},
			bson.M{"collaborators.user_id": userID},
		},
	}
	cursor, err := s.folderCol.Find(ctx, filter)
	if err != nil {
		return nil, err
	}
	defer cursor.Close(ctx)

	var ids []string
	for cursor.Next(ctx) {
		var f struct {
			ID primitive.ObjectID `bson:"_id"`
		}
		if err := cursor.Decode(&f); err == nil {
			ids = append(ids, f.ID.Hex())
		}
	}
	return ids, nil
}

// canAccessLink vérifie si l'utilisateur peut accéder à un lien
func (s *Service) canAccessLink(ctx context.Context, l *Link, userID string) bool {
	if l.OwnerID == userID {
		return true
	}
	if l.FolderID == "" {
		return false
	}
	// Vérifier si l'utilisateur est collaborateur du dossier
	fid, err := primitive.ObjectIDFromHex(l.FolderID)
	if err != nil {
		return false
	}
	count, _ := s.folderCol.CountDocuments(ctx, bson.M{
		"_id":                    fid,
		"collaborators.user_id": userID,
	})
	return count > 0
}

// canEditFolder vérifie si l'utilisateur peut éditer dans un dossier
func (s *Service) canEditFolder(ctx context.Context, folderID, userID string) bool {
	if folderID == "" {
		return false
	}
	fid, err := primitive.ObjectIDFromHex(folderID)
	if err != nil {
		return false
	}
	count, _ := s.folderCol.CountDocuments(ctx, bson.M{
		"_id": fid,
		"$or": bson.A{
			bson.M{"owner_id": userID},
			bson.M{"collaborators": bson.M{"$elemMatch": bson.M{"user_id": userID, "role": "editor"}}},
		},
	})
	return count > 0
}

func (s *Service) Create(ctx context.Context, ownerID string, l *Link) (*Link, error) {
	l.ID = primitive.NewObjectID()
	l.OwnerID = ownerID
	l.CreatedAt = time.Now()
	l.UpdatedAt = time.Now()
	if l.Tags == nil {
		l.Tags = []string{}
	}
	if l.Ingredients == nil {
		l.Ingredients = []string{}
	}
	if _, err := s.col.InsertOne(ctx, l); err != nil {
		return nil, err
	}
	return l, nil
}

func (s *Service) Get(ctx context.Context, linkID, userID string) (*Link, error) {
	id, err := primitive.ObjectIDFromHex(linkID)
	if err != nil {
		return nil, errors.New("invalid link id")
	}
	var l Link
	if err := s.col.FindOne(ctx, bson.M{"_id": id}).Decode(&l); err != nil {
		return nil, err
	}
	if !s.canAccessLink(ctx, &l, userID) {
		return nil, errors.New("not found")
	}
	return &l, nil
}

func (s *Service) List(ctx context.Context, ownerID, folderID string) ([]*Link, error) {
	if folderID != "" {
		// Si un dossier est spécifié, retourner ses liens (si accès)
		return s.listByFolder(ctx, folderID)
	}
	// Sinon, retourner les liens propres + ceux des dossiers partagés
	folderIDs, err := s.accessibleFolderIDs(ctx, ownerID)
	if err != nil {
		return nil, err
	}

	filter := bson.M{
		"$or": bson.A{
			bson.M{"owner_id": ownerID},
			bson.M{"folder_id": bson.M{"$in": folderIDs}},
		},
	}
	cursor, err := s.col.Find(ctx, filter)
	if err != nil {
		return nil, err
	}
	defer cursor.Close(ctx)
	var links []*Link
	return links, cursor.All(ctx, &links)
}

func (s *Service) listByFolder(ctx context.Context, folderID string) ([]*Link, error) {
	cursor, err := s.col.Find(ctx, bson.M{"folder_id": folderID})
	if err != nil {
		return nil, err
	}
	defer cursor.Close(ctx)
	var links []*Link
	return links, cursor.All(ctx, &links)
}

func (s *Service) Update(ctx context.Context, linkID, userID string, l *Link) (*Link, error) {
	id, err := primitive.ObjectIDFromHex(linkID)
	if err != nil {
		return nil, errors.New("invalid link id")
	}

	// Charger le lien existant
	var existing Link
	if err := s.col.FindOne(ctx, bson.M{"_id": id}).Decode(&existing); err != nil {
		return nil, err
	}

	// Vérifier : owner OU éditeur du dossier
	if existing.OwnerID != userID && !s.canEditFolder(ctx, existing.FolderID, userID) {
		return nil, errors.New("not authorized")
	}

	l.UpdatedAt = time.Now()
	update := bson.M{"$set": bson.M{
		"folder_id": l.FolderID, "title": l.Title, "url": l.URL,
		"description": l.Description, "category": l.Category,
		"tags": l.Tags, "age_range": l.AgeRange, "location": l.Location,
		"price": l.Price, "image_url": l.ImageURL, "event_date": l.EventDate,
		"reminder_enabled": l.ReminderEnabled, "rating": l.Rating,
		"ingredients": l.Ingredients, "updated_at": l.UpdatedAt,
	}}
	if _, err := s.col.UpdateOne(ctx, bson.M{"_id": id}, update); err != nil {
		return nil, err
	}
	return s.Get(ctx, linkID, userID)
}

func (s *Service) Delete(ctx context.Context, linkID, userID string) error {
	id, err := primitive.ObjectIDFromHex(linkID)
	if err != nil {
		return errors.New("invalid link id")
	}

	var existing Link
	if err := s.col.FindOne(ctx, bson.M{"_id": id}).Decode(&existing); err != nil {
		return err
	}

	if existing.OwnerID != userID && !s.canEditFolder(ctx, existing.FolderID, userID) {
		return errors.New("not authorized")
	}

	_, err = s.col.DeleteOne(ctx, bson.M{"_id": id})
	return err
}
