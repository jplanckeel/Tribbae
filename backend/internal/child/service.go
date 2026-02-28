package child

import (
	"context"
	"time"

	"go.mongodb.org/mongo-driver/bson"
	"go.mongodb.org/mongo-driver/bson/primitive"
	"go.mongodb.org/mongo-driver/mongo"
)

type Child struct {
	ID        primitive.ObjectID `bson:"_id,omitempty"`
	OwnerID   primitive.ObjectID `bson:"ownerId"`
	Name      string             `bson:"name"`
	BirthDate int64              `bson:"birthDate"`
	CreatedAt int64              `bson:"createdAt"`
}

type Service struct {
	coll *mongo.Collection
}

func NewService(db *mongo.Database) *Service {
	return &Service{coll: db.Collection("children")}
}

func (s *Service) Create(ctx context.Context, ownerID primitive.ObjectID, name string, birthDate int64) (*Child, error) {
	child := &Child{
		ID:        primitive.NewObjectID(),
		OwnerID:   ownerID,
		Name:      name,
		BirthDate: birthDate,
		CreatedAt: time.Now().Unix(),
	}
	_, err := s.coll.InsertOne(ctx, child)
	if err != nil {
		return nil, err
	}
	return child, nil
}

func (s *Service) List(ctx context.Context, ownerID primitive.ObjectID) ([]*Child, error) {
	cursor, err := s.coll.Find(ctx, bson.M{"ownerId": ownerID})
	if err != nil {
		return nil, err
	}
	defer cursor.Close(ctx)

	var children []*Child
	if err := cursor.All(ctx, &children); err != nil {
		return nil, err
	}
	return children, nil
}

func (s *Service) Update(ctx context.Context, childID, ownerID primitive.ObjectID, name string, birthDate int64) (*Child, error) {
	filter := bson.M{"_id": childID, "ownerId": ownerID}
	update := bson.M{"$set": bson.M{"name": name, "birthDate": birthDate}}
	
	var child Child
	err := s.coll.FindOneAndUpdate(ctx, filter, update).Decode(&child)
	if err != nil {
		return nil, err
	}
	
	// Récupérer le document mis à jour
	err = s.coll.FindOne(ctx, filter).Decode(&child)
	if err != nil {
		return nil, err
	}
	return &child, nil
}

func (s *Service) Delete(ctx context.Context, childID, ownerID primitive.ObjectID) error {
	_, err := s.coll.DeleteOne(ctx, bson.M{"_id": childID, "ownerId": ownerID})
	return err
}
