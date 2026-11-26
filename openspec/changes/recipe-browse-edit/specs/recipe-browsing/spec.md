# Spec Delta

## Purpose

Lets the user find and read every recipe of the local copy, also without network: the recipe list as the start screen with search, filters and the sync status, and the recipe view with images and scaled servings.

## ADDED Requirements

### Requirement: Recipe list is the start screen
When the user is signed in, Kombu SHALL open on the recipe list. The list SHALL show every recipe of the local copy except the ones deleted here, each with its thumbnail if it has one, its name and its category. It SHALL update as soon as the local copy changes.

#### Scenario: After the first sync
- **WHEN** the first sync has stored 66 recipes
- **THEN** the list shows 66 entries, each with name and category, and a thumbnail for every recipe with an image

#### Scenario: Recipe without image
- **WHEN** a recipe has no image
- **THEN** its entry shows a neutral placeholder instead of a thumbnail

#### Scenario: Recipe deleted here
- **WHEN** the user deletes a recipe and its deletion is not uploaded yet
- **THEN** the recipe is no longer in the list

#### Scenario: Recipe added by a sync
- **WHEN** a sync downloads a new recipe while the list is open
- **THEN** the new recipe appears without any action by the user

### Requirement: Empty list
Kombu SHALL explain an empty list. Before the first sync has completed, it SHALL say that recipes are being downloaded, or why they cannot be. When the account has no recipes, it SHALL say so and offer to create one.

#### Scenario: Before the first sync
- **WHEN** the user has just signed in and the first sync is running
- **THEN** the list says that the recipes are being downloaded

#### Scenario: First sync failed
- **WHEN** the first sync failed because the server cannot be reached
- **THEN** the list says that no recipes are on the device yet and shows the reason

#### Scenario: Account without recipes
- **WHEN** a sync completed and the account has no recipes
- **THEN** the list says that there are no recipes yet and offers to create one

### Requirement: Recipe markers in the list
Each list entry SHALL show whether the recipe has a change that is not uploaded yet, is in conflict, or was refused by the server. A conflict and a refused upload SHALL be distinguishable from a plain pending change.

#### Scenario: Pending change
- **WHEN** a recipe was changed in Kombu and the change is not uploaded yet
- **THEN** its entry shows the "not uploaded yet" marker

#### Scenario: Conflict
- **WHEN** a recipe is in conflict
- **THEN** its entry shows the conflict marker, not the pending marker

#### Scenario: Refused upload
- **WHEN** the server refused the upload because the name already exists
- **THEN** its entry shows the "needs attention" marker

### Requirement: Search
The list SHALL offer a search that matches the search text in the name, category, keywords and ingredient lines. Matching SHALL ignore case and accents. An empty search text SHALL show all recipes. When nothing matches, the list SHALL say so and offer to clear the search.

#### Scenario: Match in an ingredient
- **WHEN** the user searches for `kichererbsen` and a recipe has the ingredient line `400 g Kichererbsen`
- **THEN** that recipe is in the result

#### Scenario: Case and accents ignored
- **WHEN** the user searches for `creme brulee`
- **THEN** a recipe named `Crème brûlée` is in the result

#### Scenario: No match
- **WHEN** no recipe matches the search text
- **THEN** the list says that nothing was found and offers to clear the search

### Requirement: Filters
The list SHALL offer filters by category and by keyword. The choices SHALL be the categories and keywords that occur in the local copy. Choosing a category SHALL show only its recipes. Choosing several keywords SHALL show only recipes that have all of them. Filters and search SHALL combine.

#### Scenario: Category filter
- **WHEN** the user chooses the category `Suppe & Eintopf`
- **THEN** the list shows only recipes of that category

#### Scenario: Two keywords
- **WHEN** the user chooses the keywords `vegetarisch` and `indisch`
- **THEN** the list shows only recipes that have both keywords

#### Scenario: Filter and search
- **WHEN** a category filter is set and the user searches
- **THEN** the result contains only recipes that match both

#### Scenario: Choices come from the recipes
- **WHEN** a sync adds a recipe with a new category
- **THEN** the new category appears among the filter choices

### Requirement: Attention filter
The list SHALL offer a filter that shows only recipes that need the user: conflicts and refused uploads. The sync status bar SHALL open the list with this filter when it reports conflicts or refused uploads.

#### Scenario: Two conflicts
- **WHEN** two recipes are in conflict and the user chooses the attention filter
- **THEN** the list shows exactly those two recipes

### Requirement: Sort order
The list SHALL be sorted by name in the alphabetical order of the app language by default. The user SHALL be able to sort by last change instead, newest first. Kombu SHALL keep the chosen order when the app is restarted.

#### Scenario: Default order
- **WHEN** the user opens the list for the first time
- **THEN** recipes are sorted by name, and `Äpfel im Schlafrock` sorts with the `A` names in German

#### Scenario: Last change
- **WHEN** the user sorts by last change
- **THEN** the recipe changed most recently, here or on the server, comes first

### Requirement: Sync status bar
Above the list, Kombu SHALL show a compact sync status: the time of the last successful sync, whether a sync is running, the number of pending changes and conflicts, and the current problem. Tapping it SHALL open the full sync status, which offers to sync now and, when the server rejected the credentials, to sign in again.

#### Scenario: Everything synced
- **WHEN** the last sync completed and nothing is pending
- **THEN** the bar shows the time of that sync and no counts

#### Scenario: Offline with pending changes
- **WHEN** the device is offline and two changes are pending
- **THEN** the bar shows that Kombu is offline and that two changes are waiting

#### Scenario: Credentials rejected
- **WHEN** the server rejected the stored credentials
- **THEN** the bar shows that the user must sign in again, and the full status offers the sign-in

### Requirement: Recipe view
Tapping a list entry SHALL open the recipe view. It SHALL show the image, name, category, keywords, description, servings, preparation, cooking and total time, the ingredient lines, the numbered steps and the source link. A field the recipe does not have SHALL be left out without a placeholder.

#### Scenario: Complete recipe
- **WHEN** the user opens a recipe with all fields
- **THEN** the view shows all of them, times in the units of the app language, for example `1 Std. 5 Min.` for `PT1H5M` in German

#### Scenario: Sparse recipe
- **WHEN** the user opens a recipe that has only a name and ingredients without quantities
- **THEN** the view shows the name and the ingredient lines and no empty sections

#### Scenario: Ingredient headings
- **WHEN** an ingredient line is a heading such as `Für die Füllung:`
- **THEN** the view shows it as a heading of the following lines

#### Scenario: Unreadable time
- **WHEN** a recipe's cooking time is not a valid duration
- **THEN** the view shows the value as it is stored

### Requirement: Reading without network
The list, the search, the filters and the recipe view including images SHALL work without network. Opening the source link SHALL be the only action on these screens that needs the network.

#### Scenario: Flight mode
- **WHEN** the device is in flight mode after a completed sync
- **THEN** the user can search, filter and open every recipe with its image

### Requirement: Scaled servings
The recipe view SHALL let the user change the number of servings when the recipe states how many it makes. The ingredient lines SHALL then show quantities scaled by the new number divided by the stored one. Lines without a quantity at their start SHALL stay as written. Scaling SHALL NOT change the stored recipe.

#### Scenario: Four to six servings
- **WHEN** a recipe for 4 servings has the line `200 g Mehl` and the user sets 6 servings
- **THEN** the line shows `300 g Mehl`

#### Scenario: Line without quantity
- **WHEN** the user scales a recipe with the line `Salz und Pfeffer`
- **THEN** that line stays as written

#### Scenario: Stored recipe unchanged
- **WHEN** the user scales a recipe and leaves the view
- **THEN** the recipe is not marked as changed, and opening it again shows the stored servings

#### Scenario: No servings stated
- **WHEN** a recipe does not state its servings
- **THEN** the view shows no servings control

### Requirement: Unscaled ingredient lines are marked
While the servings differ from the stored number, every ingredient line without a quantity at its start SHALL be shown as written with a subtle hint that it was not scaled. Headings SHALL get no hint. At the stored number of servings no line SHALL carry the hint.

#### Scenario: Number inside the line
- **WHEN** the user scales a recipe with the line `Saft von 3 Orangen`
- **THEN** the line is shown as written with the "not scaled" hint

#### Scenario: Stored servings
- **WHEN** the servings equal the stored number
- **THEN** no ingredient line shows the hint

### Requirement: Recipe state in the view
The recipe view SHALL tell the user when the recipe has a change that is not uploaded yet, is in conflict, or was refused by the server. For a conflict it SHALL offer to open the conflict view, and for a refused upload it SHALL name the reason and offer to edit the recipe.

#### Scenario: Conflict banner
- **WHEN** the user opens a recipe in conflict
- **THEN** the view says that the recipe was changed in both places and offers to resolve it

#### Scenario: Name already taken
- **WHEN** the user opens a recipe whose upload was refused because the name already exists on the server
- **THEN** the view says so and offers to edit the name
