package link

import (
	"context"
	"testing"
	"time"

	"github.com/leanovate/gopter"
	"github.com/leanovate/gopter/gen"
	"github.com/leanovate/gopter/prop"
	"go.mongodb.org/mongo-driver/bson"
	"go.mongodb.org/mongo-driver/bson/primitive"
)

// **Feature: social-features-fixes, Property 17: Favorite count accuracy**
// Property 17: Favorite count accuracy
// For any user, the count of favorite links should equal the number of links where favorite=true and owner_id equals the user's id
// **Validates: Requirements 8.1**
func TestProperty_FavoriteCountAccuracy(t *testing.T) {
	_, db, cleanup := setupTestDB(t)
	defer cleanup()
	
	ctx := context.Background()
	linksCol := db.Collection("links")
	foldersCol := db.Collection("folders")
	
	parameters := gopter.DefaultTestParameters()
	parameters.MinSuccessfulTests = 100
	properties := gopter.NewProperties(parameters)
	
	properties.Property("favorite count equals number of links with favorite=true", prop.ForAll(
		func(favoriteFlags []bool) bool {
			// Clean up collections before each test
			linksCol.DeleteMany(ctx, bson.M{})
			foldersCol.DeleteMany(ctx, bson.M{})
			
			// Create a test user
			userID := "test_user_" + primitive.NewObjectID().Hex()
			
			// Create a folder for the links
			folderID := primitive.NewObjectID()
			folder := bson.M{
				"_id":        folderID,
				"owner_id":   userID,
				"name":       "Test Folder",
				"visibility": "private",
				"created_at": time.Now(),
				"updated_at": time.Now(),
			}
			if _, err := foldersCol.InsertOne(ctx, folder); err != nil {
				t.Logf("Failed to insert folder: %v", err)
				return false
			}
			
			// Track expected favorite count
			expectedFavoriteCount := 0
			
			// Create links with the generated favorite flags
			for i, isFavorite := range favoriteFlags {
				link := &Link{
					ID:          primitive.NewObjectID(),
					OwnerID:     userID,
					FolderID:    folderID.Hex(),
					Title:       "Link " + string(rune(i)),
					URL:         "https://example.com/" + string(rune(i)),
					Description: "Test link",
					Category:    "LINK_CATEGORY_IDEE",
					Tags:        []string{},
					Ingredients: []string{},
					Visibility:  "private",
					Favorite:    isFavorite,
					CreatedAt:   time.Now(),
					UpdatedAt:   time.Now(),
				}
				
				if _, err := linksCol.InsertOne(ctx, link); err != nil {
					t.Logf("Failed to insert link: %v", err)
					return false
				}
				
				if isFavorite {
					expectedFavoriteCount++
				}
			}
			
			// Query links owned by the user
			cursor, err := linksCol.Find(ctx, bson.M{"owner_id": userID})
			if err != nil {
				t.Logf("Failed to query links: %v", err)
				return false
			}
			defer cursor.Close(ctx)
			
			// Count favorites
			actualFavoriteCount := 0
			for cursor.Next(ctx) {
				var link Link
				if err := cursor.Decode(&link); err != nil {
					t.Logf("Failed to decode link: %v", err)
					return false
				}
				if link.Favorite {
					actualFavoriteCount++
				}
			}
			
			// Verify count matches expected
			if actualFavoriteCount != expectedFavoriteCount {
				t.Logf("Favorite count mismatch: expected=%d, got=%d", expectedFavoriteCount, actualFavoriteCount)
				return false
			}
			
			return true
		},
		// Generate a list of boolean flags for favorite status
		gen.SliceOfN(20, gen.Bool()),
	))
	
	properties.TestingRun(t)
}

// **Feature: social-features-fixes, Property 18: Public links count accuracy**
// Property 18: Public links count accuracy
// For any user, the count of shared links should equal the number of links where visibility="public" and owner_id equals the user's id
// **Validates: Requirements 8.2**
func TestProperty_PublicLinksCountAccuracy(t *testing.T) {
	_, db, cleanup := setupTestDB(t)
	defer cleanup()
	
	ctx := context.Background()
	linksCol := db.Collection("links")
	foldersCol := db.Collection("folders")
	
	parameters := gopter.DefaultTestParameters()
	parameters.MinSuccessfulTests = 100
	properties := gopter.NewProperties(parameters)
	
	properties.Property("public links count equals number of links with visibility=public", prop.ForAll(
		func(visibilities []string) bool {
			// Clean up collections before each test
			linksCol.DeleteMany(ctx, bson.M{})
			foldersCol.DeleteMany(ctx, bson.M{})
			
			// Create a test user
			userID := "test_user_" + primitive.NewObjectID().Hex()
			
			// Create a folder for the links
			folderID := primitive.NewObjectID()
			folder := bson.M{
				"_id":        folderID,
				"owner_id":   userID,
				"name":       "Test Folder",
				"visibility": "private",
				"created_at": time.Now(),
				"updated_at": time.Now(),
			}
			if _, err := foldersCol.InsertOne(ctx, folder); err != nil {
				t.Logf("Failed to insert folder: %v", err)
				return false
			}
			
			// Track expected public count
			expectedPublicCount := 0
			
			// Create links with the generated visibilities
			for i, visibility := range visibilities {
				// Normalize visibility to "public" or "private"
				normalizedVisibility := "private"
				if visibility == "public" {
					normalizedVisibility = "public"
					expectedPublicCount++
				}
				
				link := &Link{
					ID:          primitive.NewObjectID(),
					OwnerID:     userID,
					FolderID:    folderID.Hex(),
					Title:       "Link " + string(rune(i)),
					URL:         "https://example.com/" + string(rune(i)),
					Description: "Test link",
					Category:    "LINK_CATEGORY_IDEE",
					Tags:        []string{},
					Ingredients: []string{},
					Visibility:  normalizedVisibility,
					CreatedAt:   time.Now(),
					UpdatedAt:   time.Now(),
				}
				
				if _, err := linksCol.InsertOne(ctx, link); err != nil {
					t.Logf("Failed to insert link: %v", err)
					return false
				}
			}
			
			// Query links owned by the user
			cursor, err := linksCol.Find(ctx, bson.M{"owner_id": userID})
			if err != nil {
				t.Logf("Failed to query links: %v", err)
				return false
			}
			defer cursor.Close(ctx)
			
			// Count public links
			actualPublicCount := 0
			for cursor.Next(ctx) {
				var link Link
				if err := cursor.Decode(&link); err != nil {
					t.Logf("Failed to decode link: %v", err)
					return false
				}
				if link.Visibility == "public" {
					actualPublicCount++
				}
			}
			
			// Verify count matches expected
			if actualPublicCount != expectedPublicCount {
				t.Logf("Public links count mismatch: expected=%d, got=%d", expectedPublicCount, actualPublicCount)
				return false
			}
			
			return true
		},
		// Generate a list of visibilities (public or private)
		gen.SliceOfN(20, gen.OneConstOf("public", "private")),
	))
	
	properties.TestingRun(t)
}
