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
	"go.mongodb.org/mongo-driver/mongo"
	"go.mongodb.org/mongo-driver/mongo/options"
)

// setupTestDB creates a test database connection
func setupTestDB(t *testing.T) (*mongo.Client, *mongo.Database, func()) {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	
	// Connect to MongoDB (assumes MongoDB is running locally for tests)
	clientOpts := options.Client().
		ApplyURI("mongodb://localhost:27017").
		SetServerSelectionTimeout(5 * time.Second)
	
	client, err := mongo.Connect(ctx, clientOpts)
	if err != nil {
		t.Skipf("Skipping test: Failed to connect to MongoDB: %v", err)
	}
	
	// Ping to verify connection
	if err := client.Ping(ctx, nil); err != nil {
		t.Skipf("Skipping test: MongoDB not available: %v", err)
	}
	
	// Use a test database
	dbName := "tribbae_test_" + primitive.NewObjectID().Hex()
	db := client.Database(dbName)
	
	// Cleanup function
	cleanup := func() {
		ctx := context.Background()
		if err := db.Drop(ctx); err != nil {
			t.Logf("Failed to drop test database: %v", err)
		}
		if err := client.Disconnect(ctx); err != nil {
			t.Logf("Failed to disconnect from MongoDB: %v", err)
		}
	}
	
	return client, db, cleanup
}

// **Feature: social-features-fixes, Property 6: Link responses include owner display name**
// Property 6: Link responses include owner display name
// For any link returned by the backend, the response should include the owner_display_name field populated from the users collection
// **Validates: Requirements 3.1, 7.1**
func TestProperty_LinkResponsesIncludeOwnerDisplayName(t *testing.T) {
	_, db, cleanup := setupTestDB(t)
	defer cleanup()
	
	ctx := context.Background()
	linksCol := db.Collection("links")
	foldersCol := db.Collection("folders")
	usersCol := db.Collection("users")
	
	svc := NewService(linksCol, foldersCol)
	
	parameters := gopter.DefaultTestParameters()
	parameters.MinSuccessfulTests = 100
	properties := gopter.NewProperties(parameters)
	
	properties.Property("link responses include owner display name from users collection", prop.ForAll(
		func(displayName string, isAdmin bool) bool {
			// Clean up collections before each test
			linksCol.DeleteMany(ctx, bson.M{})
			foldersCol.DeleteMany(ctx, bson.M{})
			usersCol.DeleteMany(ctx, bson.M{})
			
			// Create a test user with the generated display name
			userID := primitive.NewObjectID()
			user := bson.M{
				"_id":          userID,
				"email":        "test@example.com",
				"display_name": displayName,
				"is_admin":     isAdmin,
				"created_at":   time.Now(),
			}
			if _, err := usersCol.InsertOne(ctx, user); err != nil {
				t.Logf("Failed to insert user: %v", err)
				return false
			}
			
			// Create a folder for the link
			folderID := primitive.NewObjectID()
			folder := bson.M{
				"_id":        folderID,
				"owner_id":   userID.Hex(),
				"name":       "Test Folder",
				"visibility": "private",
				"created_at": time.Now(),
				"updated_at": time.Now(),
			}
			if _, err := foldersCol.InsertOne(ctx, folder); err != nil {
				t.Logf("Failed to insert folder: %v", err)
				return false
			}
			
			// Create a link owned by this user
			link := &Link{
				OwnerID:     userID.Hex(),
				FolderID:    folderID.Hex(),
				Title:       "Test Link",
				URL:         "https://example.com",
				Description: "Test Description",
				Category:    "LINK_CATEGORY_IDEE",
				Tags:        []string{},
				Ingredients: []string{},
				Visibility:  "private",
			}
			
			createdLink, err := svc.Create(ctx, userID.Hex(), link)
			if err != nil {
				t.Logf("Failed to create link: %v", err)
				return false
			}
			
			// Call GetOwnerInfo to retrieve the owner's display name
			retrievedDisplayName, retrievedIsAdmin := svc.GetOwnerInfo(ctx, createdLink.OwnerID)
			
			// Verify the display name matches what we stored
			if retrievedDisplayName != displayName {
				t.Logf("Display name mismatch: expected=%q, got=%q", displayName, retrievedDisplayName)
				return false
			}
			
			// Verify the admin status matches
			if retrievedIsAdmin != isAdmin {
				t.Logf("Admin status mismatch: expected=%v, got=%v", isAdmin, retrievedIsAdmin)
				return false
			}
			
			// Verify that empty display names are returned as empty strings (not null)
			if displayName == "" && retrievedDisplayName != "" {
				t.Logf("Empty display name should return empty string, got=%q", retrievedDisplayName)
				return false
			}
			
			return true
		},
		// Generate random display names (including empty strings to test edge case)
		gen.OneGenOf(
			gen.AlphaString(),
			gen.Const(""),
			gen.Const("John Doe"),
			gen.Const("User123"),
		),
		// Generate random admin status
		gen.Bool(),
	))
	
	properties.TestingRun(t)
}

// **Feature: social-features-fixes, Property 15: Public links in community listings**
// Property 15: Public links in community listings
// For any set of links with mixed visibilities, the community link listing should include all and only links with visibility="public"
// **Validates: Requirements 6.3**
func TestProperty_PublicLinksInCommunityListings(t *testing.T) {
	_, db, cleanup := setupTestDB(t)
	defer cleanup()
	
	ctx := context.Background()
	linksCol := db.Collection("links")
	foldersCol := db.Collection("folders")
	
	svc := NewService(linksCol, foldersCol)
	
	parameters := gopter.DefaultTestParameters()
	parameters.MinSuccessfulTests = 100
	properties := gopter.NewProperties(parameters)
	
	properties.Property("community listings include all and only public links", prop.ForAll(
		func(linkVisibilities []string, numPublicFolders int) bool {
			// Clean up collections before each test
			linksCol.DeleteMany(ctx, bson.M{})
			foldersCol.DeleteMany(ctx, bson.M{})
			
			// Ensure we have at least one public folder
			if numPublicFolders < 1 {
				numPublicFolders = 1
			}
			if numPublicFolders > len(linkVisibilities) {
				numPublicFolders = len(linkVisibilities)
			}
			
			// Create public folders
			publicFolderIDs := make([]string, numPublicFolders)
			for i := 0; i < numPublicFolders; i++ {
				folderID := primitive.NewObjectID()
				folder := bson.M{
					"_id":        folderID,
					"owner_id":   "test_owner",
					"name":       "Public Folder " + folderID.Hex(),
					"visibility": "public",
					"created_at": time.Now(),
					"updated_at": time.Now(),
				}
				if _, err := foldersCol.InsertOne(ctx, folder); err != nil {
					t.Logf("Failed to insert public folder: %v", err)
					return false
				}
				publicFolderIDs[i] = folderID.Hex()
			}
			
			// Create private folders
			privateFolderID := primitive.NewObjectID()
			privateFolder := bson.M{
				"_id":        privateFolderID,
				"owner_id":   "test_owner",
				"name":       "Private Folder",
				"visibility": "private",
				"created_at": time.Now(),
				"updated_at": time.Now(),
			}
			if _, err := foldersCol.InsertOne(ctx, privateFolder); err != nil {
				t.Logf("Failed to insert private folder: %v", err)
				return false
			}
			
			// Track expected public links
			expectedPublicCount := 0
			
			// Create links with mixed visibilities
			for i, visibility := range linkVisibilities {
				// Normalize visibility to "public" or "private"
				normalizedVisibility := "private"
				if visibility == "public" {
					normalizedVisibility = "public"
				}
				
				// Assign to public or private folder
				folderID := privateFolderID.Hex()
				if i < numPublicFolders {
					folderID = publicFolderIDs[i%numPublicFolders]
				}
				
				link := &Link{
					ID:          primitive.NewObjectID(),
					OwnerID:     "test_owner",
					FolderID:    folderID,
					Title:       "Link " + primitive.NewObjectID().Hex(),
					URL:         "https://example.com/" + primitive.NewObjectID().Hex(),
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
				
				// Count expected public links (must be in public folder AND have public visibility)
				if normalizedVisibility == "public" && i < numPublicFolders {
					expectedPublicCount++
				}
			}
			
			// Call ListCommunity
			communityLinks, err := svc.ListCommunity(ctx, "", 1000)
			if err != nil {
				t.Logf("ListCommunity failed: %v", err)
				return false
			}
			
			// Verify all returned links are public
			for _, link := range communityLinks {
				if link.Visibility != "public" {
					t.Logf("Found non-public link in community listing: %s (visibility=%s)", link.ID.Hex(), link.Visibility)
					return false
				}
				
				// Verify link is in a public folder
				isInPublicFolder := false
				for _, pubFolderID := range publicFolderIDs {
					if link.FolderID == pubFolderID {
						isInPublicFolder = true
						break
					}
				}
				if !isInPublicFolder {
					t.Logf("Found link in non-public folder in community listing: %s (folder=%s)", link.ID.Hex(), link.FolderID)
					return false
				}
			}
			
			// Verify count matches expected
			if len(communityLinks) != expectedPublicCount {
				t.Logf("Expected %d public links, got %d", expectedPublicCount, len(communityLinks))
				return false
			}
			
			return true
		},
		// Generate a list of visibilities (public or private)
		gen.SliceOfN(10, gen.OneConstOf("public", "private")),
		// Generate number of public folders (1 to 5)
		gen.IntRange(1, 5),
	))
	
	properties.TestingRun(t)
}

// **Feature: social-features-fixes, Property 16: Link update timestamp**
// Property 16: Link update timestamp
// For any link, when any field is updated, the updated_at timestamp should be greater than its previous value
// **Validates: Requirements 6.5**
func TestProperty_LinkUpdateTimestamp(t *testing.T) {
	_, db, cleanup := setupTestDB(t)
	defer cleanup()
	
	ctx := context.Background()
	linksCol := db.Collection("links")
	foldersCol := db.Collection("folders")
	
	svc := NewService(linksCol, foldersCol)
	
	parameters := gopter.DefaultTestParameters()
	parameters.MinSuccessfulTests = 100
	properties := gopter.NewProperties(parameters)
	
	properties.Property("updating a link increases updated_at timestamp", prop.ForAll(
		func(title string, description string, category string) bool {
			// Clean up collections before each test
			linksCol.DeleteMany(ctx, bson.M{})
			foldersCol.DeleteMany(ctx, bson.M{})
			
			// Ensure non-empty values
			if title == "" {
				title = "Test Title"
			}
			if description == "" {
				description = "Test Description"
			}
			if category == "" {
				category = "LINK_CATEGORY_IDEE"
			}
			
			// Create a test user
			userID := "test_user_" + primitive.NewObjectID().Hex()
			
			// Create a folder for the link
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
			
			// Create initial link
			initialLink := &Link{
				OwnerID:     userID,
				FolderID:    folderID.Hex(),
				Title:       "Initial Title",
				URL:         "https://example.com",
				Description: "Initial Description",
				Category:    "LINK_CATEGORY_IDEE",
				Tags:        []string{},
				Ingredients: []string{},
				Visibility:  "private",
			}
			
			createdLink, err := svc.Create(ctx, userID, initialLink)
			if err != nil {
				t.Logf("Failed to create link: %v", err)
				return false
			}
			
			// Store the original updated_at timestamp
			originalUpdatedAt := createdLink.UpdatedAt
			
			// Wait a small amount to ensure timestamp difference
			time.Sleep(10 * time.Millisecond)
			
			// Update the link with new values
			updatedLink := &Link{
				FolderID:    folderID.Hex(),
				Title:       title,
				URL:         "https://example.com/updated",
				Description: description,
				Category:    category,
				Tags:        []string{"tag1", "tag2"},
				Ingredients: []string{},
				Visibility:  "public",
			}
			
			resultLink, err := svc.Update(ctx, createdLink.ID.Hex(), userID, updatedLink)
			if err != nil {
				t.Logf("Failed to update link: %v", err)
				return false
			}
			
			// Verify that updated_at is greater than the original
			if !resultLink.UpdatedAt.After(originalUpdatedAt) {
				t.Logf("UpdatedAt not increased: original=%v, new=%v", originalUpdatedAt, resultLink.UpdatedAt)
				return false
			}
			
			// Verify the update was persisted correctly by fetching from DB
			var dbLink Link
			if err := linksCol.FindOne(ctx, bson.M{"_id": createdLink.ID}).Decode(&dbLink); err != nil {
				t.Logf("Failed to fetch updated link from DB: %v", err)
				return false
			}
			
			// Verify DB timestamp also increased
			if !dbLink.UpdatedAt.After(originalUpdatedAt) {
				t.Logf("DB UpdatedAt not increased: original=%v, new=%v", originalUpdatedAt, dbLink.UpdatedAt)
				return false
			}
			
			return true
		},
		// Generate random title
		gen.AlphaString(),
		// Generate random description
		gen.AlphaString(),
		// Generate random category from valid options
		gen.OneConstOf(
			"LINK_CATEGORY_IDEE",
			"LINK_CATEGORY_CADEAU",
			"LINK_CATEGORY_ACTIVITE",
			"LINK_CATEGORY_EVENEMENT",
			"LINK_CATEGORY_RECETTE",
		),
	))
	
	properties.TestingRun(t)
}
