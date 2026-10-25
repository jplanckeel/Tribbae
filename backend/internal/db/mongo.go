package db

import (
	"context"
	"time"

	"go.mongodb.org/mongo-driver/v2/mongo"
	"go.mongodb.org/mongo-driver/v2/mongo/options"
)

type DB struct {
	Client   *mongo.Client
	Database *mongo.Database
}

// OperationTimeout borne chaque opération MongoDB (écriture d'une photo comprise).
const OperationTimeout = 15 * time.Second

func Connect(uri, dbName string) (*DB, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	// Timeout : délai maximal de chaque opération (requête, écriture, lecture
	// d'un curseur), pour qu'une requête lente ne bloque pas indéfiniment.
	client, err := mongo.Connect(options.Client().ApplyURI(uri).SetTimeout(OperationTimeout))
	if err != nil {
		return nil, err
	}
	if err := client.Ping(ctx, nil); err != nil {
		return nil, err
	}
	return &DB{Client: client, Database: client.Database(dbName)}, nil
}

func (d *DB) Col(name string) *mongo.Collection {
	return d.Database.Collection(name)
}

func (d *DB) DB() *mongo.Database {
	return d.Database
}
