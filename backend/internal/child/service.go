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
	UpdatedAt int64              `bson:"updatedAt"`
}

type Service struct {
	coll  *mongo.Collection
	peers func(ctx context.Context, userID string) []string
}

func NewService(db *mongo.Database) *Service {
	return &Service{coll: db.Collection("children")}
}

// SetTribePeers branche la tribu : les enfants sont ceux de toute la tribu.
func (s *Service) SetTribePeers(peers func(ctx context.Context, userID string) []string) {
	s.peers = peers
}

// ownedByTribe : filtre « enfant de userID ou de sa tribu ».
func (s *Service) ownedByTribe(ctx context.Context, userID primitive.ObjectID) bson.M {
	ids := []primitive.ObjectID{userID}
	if s.peers != nil {
		ids = ids[:0]
		for _, p := range s.peers(ctx, userID.Hex()) {
			if oid, err := primitive.ObjectIDFromHex(p); err == nil {
				ids = append(ids, oid)
			}
		}
	}
	return bson.M{"ownerId": bson.M{"$in": ids}}
}

func (s *Service) Create(ctx context.Context, ownerID primitive.ObjectID, name string, birthDate int64) (*Child, error) {
	child := &Child{
		ID:        primitive.NewObjectID(),
		OwnerID:   ownerID,
		Name:      name,
		BirthDate: birthDate,
		CreatedAt: time.Now().Unix(),
		UpdatedAt: time.Now().Unix(),
	}
	_, err := s.coll.InsertOne(ctx, child)
	if err != nil {
		return nil, err
	}
	return child, nil
}

func (s *Service) List(ctx context.Context, ownerID primitive.ObjectID) ([]*Child, error) {
	cursor, err := s.coll.Find(ctx, s.ownedByTribe(ctx, ownerID))
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
	filter := s.ownedByTribe(ctx, ownerID)
	filter["_id"] = childID
	update := bson.M{"$set": bson.M{
		"name":      name,
		"birthDate": birthDate,
		"updatedAt": time.Now().Unix(),
	}}

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
	filter := s.ownedByTribe(ctx, ownerID)
	filter["_id"] = childID
	_, err := s.coll.DeleteOne(ctx, filter)
	return err
}
