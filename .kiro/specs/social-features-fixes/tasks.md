# Implementation Plan

- [ ] 1. Fix folder visibility persistence in backend
  - [x] 1.1 Update folder service to correctly persistility field
    - Modify `Update` method in `backend/internal/folder/service.go` to ensure visibility is saved
    - Verify the update query includes the visibility field in the $set operation
    - _Requirements: 1.1, 1.2, 1.3_
  
  - [ ] 1.2 Write property test for folder visibility persistence
    - **Property 1: Folder visibility persistence**
    - **Validates: Requirements 1.1, 1.2, 1.3**
  
  - [x] 1.3 Update folder handler to pass visibility to service
    - Verify `UpdateFolder` in `backend/internal/folder/handler.go` correctly converts proto visibility to string
    - Ensure the visibility value is passed to the service layer
    - _Requirements: 1.1, 1.2, 1.3_
  
  - [ ] 1.4 Write property test for public folders in community listings
    - **Property 2: Public folders in community listings**
    - **Validates: Requirements 1.5**

- [ ] 2. Fix folder deletion in backend
  - [x] 2.1 Verify folder deletion authorization
    - Check that `Delete` method in `backend/internal/folder/service.go` verifies owner_id
    - Ensure non-owners cannot delete folders
    - _Requirements: 2.1, 2.2_
  
  - [ ] 2.2 Write property test for folder deletion
    - **Property 3: Folder deletion removes from database**
    - **Validates: Requirements 2.1**
  
  - [ ] 2.3 Write property test for folder deletion authorization
    - **Property 4: Folder deletion authorization**
    - **Validates: Requirements 2.2**
  
  - [x] 2.4 Handle associated links when folder is deleted
    - Decide strategy: orphan links (set folder_id to null) or delete links
    - Implement the chosen strategy in the Delete method
    - _Requirements: 2.5_
  
  - [ ] 2.5 Write property test for folder deletion link handling
    - **Property 5: Folder deletion handles links**
    - **Validates: Requirements 2.5**

- [ ] 3. Add visibility field to links
  - [x] 3.1 Update link proto definition
    - Add `visibility` field to Link message in `backend/proto/tribbae/v1/link.proto`
    - Add visibility to CreateLinkRequest and UpdateLinkRequest
    - Regenerate proto code with `task backend:proto`
    - _Requirements: 6.1, 6.3_
  
  - [x] 3.2 Update link service to handle visibility
    - Add `Visibility` field to Link struct in `backend/internal/link/service.go`
    - Update Create and Update methods to handle visibility
    - Default to "private" for existing links
    - _Requirements: 6.1_
  
  - [x] 3.3 Update link handler to include visibility in responses
    - Modify `toProto` method in `backend/internal/link/handler.go` to include visibility
    - Ensure visibility is passed in Create and Update operations
    - _Requirements: 6.1_
  
  - [ ] 3.4 Write property test for link visibility persistence
    - **Property 14: Link visibility persistence**
    - **Validates: Requirements 6.1**
  
  - [x] 3.5 Update community link listings to filter by visibility
    - Modify `ListCommunity` and `ListNew` in `backend/internal/link/service.go`
    - Add filter for visibility="public"
    - _Requirements: 6.3_
  
  - [x] 3.6 Write property test for public links in community listings
    - **Property 15: Public links in community listings**
    - **Validates: Requirements 6.3**
  
  - [x] 3.7 Write property test for link update timestamp
    - **Property 16: Link update timestamp**
    - **Validates: Requirements 6.5**

- [x] 4. Fix owner display name in link responses
  - [x] 4.1 Update link handler to include owner display name
    - Modify `toProto` method in `backend/internal/link/handler.go`
    - Call `GetOwnerInfo` to retrieve display name and admin status
    - Add owner_display_name and owner_is_admin to proto response
    - _Requirements: 3.1, 7.1_
  
  - [x] 4.2 Write property test for owner display name inclusion
    - **Property 6: Link responses include owner display name**
    - **Validates: Requirements 3.1, 7.1**
  
  - [x] 4.3 Handle empty display name edge case
    - Ensure GetOwnerInfo returns empty string (not null) when display_name is not set
    - Document the behavior in code comments
    - _Requirements: 3.3, 7.3_

- [x] 5. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 6. Implement follow system backend
  - [x] 6.1 Create follow proto definition
    - Create `backend/proto/tribbae/v1/follow.proto` with Follow service
    - Define UserProfile, FollowRequest, UnfollowRequest, and response messages
    - Add HTTP annotations for REST endpoints
    - Regenerate proto code
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7_
  
  - [x] 6.2 Implement follow service
    - Create `backend/internal/follow/service.go`
    - Implement Follow, Unfollow, IsFollowing methods
    - Implement GetFollowers, GetFollowing, GetFollowerCount, GetFollowingCount
    - Add validation to prevent self-follow
    - Handle duplicate follow attempts idempotently
    - _Requirements: 4.1, 4.2, 4.6, 4.7_
  
  - [x] 6.3 Write property test for follow relationship creation
    - **Property 7: Follow relationship creation**
    - **Validates: Requirements 4.1**
  
  - [x] 6.4 Write property test for follow/unfollow round trip
    - **Property 8: Follow/unfollow round trip**
    - **Validates: Requirements 4.2**
  
  - [x] 6.5 Write property test for follower count consistency
    - **Property 9: Follower count consistency**
    - **Validates: Requirements 4.6, 4.7**
  
  - [x] 6.6 Implement follow handler
    - Create `backend/internal/follow/handler.go`
    - Implement gRPC handlers for all follow service methods
    - Add authentication checks using interceptor
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5_
  
  - [x] 6.7 Register follow service in main server
    - Update `backend/cmd/server/main.go` to register FollowService
    - Wire up the service with MongoDB collections
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5_
  
  - [x] 6.8 Create database indexes for follows collection
    - Add index on {follower_id: 1, following_id: 1} (unique)
    - Add index on {following_id: 1}
    - Update `backend/internal/db/indexes.go`
    - _Requirements: 4.1, 4.2_

- [-] 7. Implement comment system backend
  - [x] 7.1 Create comment proto definition
    - Create `backend/proto/tribbae/v1/comment.proto` with Comment service
    - Define Comment, CreateCommentRequest, GetCommentsRequest, DeleteCommentRequest
    - Add HTTP annotations for REST endpoints
    - Regenerate proto code
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6_
  
  - [x] 7.2 Implement comment service
    - Create `backend/internal/comment/service.go`
    - Implement CreateComment, GetComments, DeleteComment, GetCommentCount
    - Add authorization: author or link owner can delete
    - Sort comments by created_at descending
    - _Requirements: 5.1, 5.4, 5.5, 5.6_
  
  - [x] 7.3 Write property test for comment storage completeness
    - **Property 10: Comment storage completeness**
    - **Validates: Requirements 5.1**
  
  - [x] 7.4 Write property test for comment deletion by author
    - **Property 11: Comment deletion by author**
    - **Validates: Requirements 5.4**
  
  - [x] 7.5 Write property test for comment deletion by link owner
    - **Property 12: Comment deletion by link owner**
    - **Validates: Requirements 5.5**
  
  - [x] 7.6 Write property test for comment sorting
    - **Property 13: Comment sorting by date**
    - **Validates: Requirements 5.6**
  
  - [x] 7.7 Implement comment handler
    - Create `backend/internal/comment/handler.go`
    - Implement gRPC handlers for all comment service methods
    - Include user display name in comment responses
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5_
  
    - [x] 7.8 Register comment service in main server
    - Update `backend/cmd/server/main.go` to register CommentService
    - Wire up the service with MongoDB collections
    - _Requirements: 5.1, 5.2, 5.4, 5.5_
  
  - [x] 7.9 Create database indexes for comments collection
    - Add index on {link_id: 1, created_at: -1}
    - Add index on {user_id: 1}
    - Update `backend/internal/db/indexes.go`
    - _Requirements: 5.1, 5.6_

- [x] 8. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 9. Update mobile data models
  - [x] 9.1 Add visibility field to Link data class
    - Update `composeApp/src/commonMain/kotlin/data/Link.kt`
    - Add `visibility: String = "private"` to Link data class
    - _Requirements: 6.1, 6.2, 6.3, 6.4_
  
  - [x] 9.2 Create UserProfile data class
    - Add UserProfile data class in `composeApp/src/commonMain/kotlin/data/Link.kt`
    - Include id, displayName, email, isAdmin, followerCount, followingCount
    - _Requirements: 4.3, 4.4, 4.5_
  
  - [x] 9.3 Create Comment data classes
    - Add Comment and CommentWithUser data classes
    - Include all required fields from proto definition
    - _Requirements: 5.1, 5.2, 5.3_

- [x] 10. Implement follow repository in mobile
  - [x] 10.1 Create FollowRepository class
    - Create `composeApp/src/commonMain/kotlin/data/FollowRepository.kt`
    - Implement follow, unfollow, isFollowing methods
    - Implement getFollowers, getFollowing, getFollowerCount, getFollowingCount
    - Use AuthenticatedApiClient for API calls
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7_
  
  - [x] 10.2 Write unit tests for FollowRepository
    - Test API call construction
    - Test error handling
    - Test response parsing
    - _Requirements: 4.1, 4.2_

- [x] 11. Implement comment repository in mobile
  - [x] 11.1 Create CommentRepository class
    - Create `composeApp/src/commonMain/kotlin/data/CommentRepository.kt`
    - Implement createComment, getComments, deleteComment, getCommentCount
    - Use AuthenticatedApiClient for API calls
    - _Requirements: 5.1, 5.2, 5.4_
  
  - [x] 11.2 Write unit tests for CommentRepository
    - Test API call construction
    - Test error handling
    - Test response parsing
    - _Requirements: 5.1, 5.2, 5.4_

- [x] 12. Update folder editing in mobile
  - [x] 12.1 Fix visibility update in ModernEditFolderScreen
    - Update `composeApp/src/commonMain/kotlin/ui/ModernEditFolderScreen.kt`
    - Ensure visibility changes are sent to the API
    - Add confirmation message after successful update
    - _Requirements: 1.1, 1.2, 1.3, 1.4_
  
  - [x] 12.2 Fix folder deletion
    - Verify delete API call is correctly implemented
    - Add error handling for deletion failures
    - Show success/error messages to user
    - _Requirements: 2.1, 2.2, 2.3, 2.4_

- [-] 13. Update ExploreScreen to show owner information
  - [x] 13.1 Display owner display name on link cards
    - Update `composeApp/src/commonMain/kotlin/ui/ExploreScreen.kt`
    - Show `link.ownerDisplayName` instead of hardcoded "anonyme"
    - Handle empty display name with "Anonyme" fallback
    - _Requirements: 3.1, 3.2, 3.3, 3.4_
  
  - [x] 13.2 Add follow button to link cards
    - Add follow/unfollow button to IdeaCard component
    - Show "Suivre" or "Abonné" based on follow status
    - Call FollowRepository when button is clicked
    - _Requirements: 4.1, 4.2, 4.5_
  
  - [x] 13.3 Add comment button to link cards
    - Add comment button with count to IdeaCard component
    - Navigate to link detail screen when clicked
    - _Requirements: 5.2_
  
  - [x] 13.4 Filter public links correctly
    - Update link filtering to check visibility="public"
    - Remove incorrect filter using likedByMe
    - _Requirements: 6.3, 6.4_

- [x] 14. Create or update LinkDetailScreen
  - [x] 14.1 Display owner information
    - Show owner display name and admin badge
    - Add follow button for the link creator
    - _Requirements: 3.1, 3.2, 4.1, 4.5_
  
  - [x] 14.2 Implement comments section
    - Display list of comments with user names and timestamps
    - Add input field to create new comment
    - Add delete button for own comments and link owner
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5_
  
  - [x] 14.3 Handle comment interactions
    - Implement comment creation with CommentRepository
    - Implement comment deletion with authorization check
    - Refresh comment list after create/delete
    - _Requirements: 5.1, 5.4, 5.5_

- [x] 15. Fix NewHomeScreen statistics
  - [x] 15.1 Fix "Idées sauvegardées" count
    - Update count to `links.count { it.favorite }`
    - Make StatCard clickable to navigate to favorites
    - _Requirements: 8.1, 8.4_
  
  - [x] 15.2 Write property test for favorite count accuracy
    - **Property 17: Favorite count accuracy**
    - **Validates: Requirements 8.1**
  
  - [x] 15.3 Fix "Partagées" count
    - Update count to `links.count { it.visibility == "public" }`
    - Make StatCard clickable to navigate to public links
    - _Requirements: 8.2, 8.5_
  
  - [x] 15.4 Write property test for public links count accuracy
    - **Property 18: Public links count accuracy**
    - **Validates: Requirements 8.2**
  
  - [x] 15.5 Fix "Ma tribu" count
    - Call `followRepository.getFollowingCount()` to get count
    - Make StatCard clickable to navigate to following list
    - _Requirements: 8.3, 8.6_
  
  - [x] 15.6 Write property test for following count accuracy
    - **Property 19: Following count accuracy**
    - **Validates: Requirements 8.3**
  
  - [x] 15.7 Make StatCards interactive
    - Add onClick handlers to navigate to relevant screens
    - Update UI to indicate cards are clickable
    - _Requirements: 8.1, 8.2, 8.3_

- [x] 16. Update IdeaCard component
  - [x] 16.1 Add owner display name to card
    - Update `composeApp/src/commonMain/kotlin/ui/components/IdeaCard.kt`
    - Display owner name with admin badge if applicable
    - Handle empty display name gracefully
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 7.1, 7.2_
  
  - [x] 16.2 Add follow button to card
    - Add follow/unfollow button
    - Update button state based on follow status
    - Handle follow/unfollow actions
    - _Requirements: 4.1, 4.2, 4.5_
  
  - [x] 16.3 Add comment count indicator
    - Display comment count with icon
    - Make it clickable to view comments
    - _Requirements: 5.2_

- [x] 17. Update LinkViewModel for new features
  - [x] 17.1 Add follow state management
    - Add StateFlow for follow status
    - Implement follow/unfollow methods
    - Update UI state after follow actions
    - _Requirements: 4.1, 4.2, 4.5_
  
  - [x] 17.2 Add comment state management
    - Add StateFlow for comments list
    - Implement create/delete comment methods
    - Update UI state after comment actions
    - _Requirements: 5.1, 5.2, 5.4, 5.5_
  
  - [x] 17.3 Add visibility management
    - Add methods to update link/folder visibility
    - Refresh data after visibility changes
    - _Requirements: 1.1, 1.2, 1.3, 6.1_

- [x] 18. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 19. Integration testing and bug fixes
  - [ ] 19.1 Test folder visibility flow end-to-end
    - Create folder → Change visibility → Refresh → Verify persistence
    - Test all visibility values (private, public, shared)
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5_
  
  - [ ] 19.2 Test folder deletion flow end-to-end
    - Create folder → Delete → Verify removal → Test authorization
    - _Requirements: 2.1, 2.2, 2.3_
  
  - [ ] 19.3 Test follow flow end-to-end
    - Follow user → Verify count → Unfollow → Verify count
    - Test from multiple screens
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7_
  
  - [ ] 19.4 Test comment flow end-to-end
    - Create comment → View → Delete → Verify removal
    - Test authorization (author and link owner)
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6_
  
  - [ ] 19.5 Test public link visibility flow end-to-end
    - Create link → Set public → Verify in Explorer → Refresh → Verify persistence
    - _Requirements: 6.1, 6.2, 6.3, 6.4_
  
  - [ ] 19.6 Test home screen statistics
    - Verify all three counts are accurate
    - Test that counts update after actions
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6_

