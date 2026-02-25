package child

import (
	"context"
	"errors"
	"time"

	"go.mongodb.org/mongo-driver/bson"
	"go.mongodb.org/mongo-driver/bson/primitive"
	"go.mongodb.org/mongo-driver/mongo"
)

type Child struct {
	ID        primitive.ObjectID `bson:"_id,omitempty"`
	OwnerID   string             `bson:"owner_id"`
	Name      string             `bson:"name"`
	BirthDate int64              `bson:"birth_date"` // ms
	CreatedAt time.Time          `bson:"created_at"`
}

type Service struct{ col *mongo.Collection }

func NewService(col *mongo.Collection) *Service { return &Service{col: col} }

func (s *Service) Create(ctx context.Context, ownerID, name string, birthDate int64) (*Child, error) {
	c := &Child{
		ID: primitive.NewObjectID(), OwnerID: ownerID,
		Name: name, BirthDate: birthDate, CreatedAt: time.Now(),
	}
	if _, err := s.col.InsertOne(ctx, c); err != nil {
		return nil, err
	}
	return c, nil
}

func (s *Service) List(ctx context.Context, ownerID string) ([]*Child, error) {
	cursor, err := s.col.Find(ctx, bson.M{"owner_id": ownerID})
	if err != nil {
		return nil, err
	}
	defer cursor.Close(ctx)
	var children []*Child
	return children, cursor.All(ctx, &children)
}

func (s *Service) Update(ctx context.Context, childID, ownerID, name string, birthDate int64) (*Child, error) {
	id, err := primitive.ObjectIDFromHex(childID)
	if err != nil {
		return nil, errors.New("invalid child id")
	}
	_, err = s.col.UpdateOne(ctx,
		bson.M{"_id": id, "owner_id": ownerID},
		bson.M{"$set": bson.M{"name": name, "birth_date": birthDate}},
	)
	if err != nil {
		return nil, err
	}
	var c Child
	s.col.FindOne(ctx, bson.M{"_id": id}).Decode(&c)
	return &c, nil
}

func (s *Service) Delete(ctx context.Context, childID, ownerID string) error {
	id, err := primitive.ObjectIDFromHex(childID)
	if err != nil {
		return errors.New("invalid child id")
	}
	_, err = s.col.DeleteOne(ctx, bson.M{"_id": id, "owner_id": ownerID})
	return err
}
