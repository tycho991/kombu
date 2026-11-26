# Spec Delta

## Purpose

Lets the user create, change and delete recipes in Kombu, also without network, and decide conflicts with the server, without ever losing a change silently.

## ADDED Requirements

### Requirement: Create a recipe
The recipe list SHALL offer to create a recipe. It SHALL open the editor with empty fields. Saving SHALL store the recipe on the device at once, show it in the list and mark it as not uploaded yet.

#### Scenario: New recipe offline
- **WHEN** the user creates a recipe named `Linsensuppe` without network and saves it
- **THEN** `Linsensuppe` appears in the list with the "not uploaded yet" marker

#### Scenario: Uploaded by the next sync
- **WHEN** a sync runs after the user saved a new recipe
- **THEN** the recipe exists on the server and its marker is gone

#### Scenario: Cancelled before saving
- **WHEN** the user opens the editor for a new recipe and leaves it without entering anything
- **THEN** no recipe is created

### Requirement: Edit a recipe
The recipe view SHALL offer to edit the recipe. The editor SHALL show the recipe's current values. Saving SHALL store the change on the device at once, show it in the view and mark the recipe as not uploaded yet.

#### Scenario: Change saved offline
- **WHEN** the user changes the description of a synced recipe without network and saves
- **THEN** the view shows the new description and the "not uploaded yet" banner

#### Scenario: Saved without changes
- **WHEN** the user opens the editor and saves without changing anything
- **THEN** the recipe is not marked as changed

### Requirement: Editable fields
The editor SHALL let the user change the name, description, category, keywords, servings, preparation time, cooking time, total time, ingredient lines and steps. It SHALL NOT offer to change the image.

#### Scenario: All fields
- **WHEN** the user opens the editor for a recipe
- **THEN** each of these fields can be changed, and the image is not offered for editing

### Requirement: Only edited fields change
Saving SHALL change only the fields the user edited. Every other field of the recipe, including the image and fields Kombu does not show, SHALL stay exactly as it was, also in its stored form.

#### Scenario: Name changed
- **WHEN** the user changes only the name of a recipe that also has nutrition data and a source URL
- **THEN** the saved recipe differs from the previous one only in its name

#### Scenario: Steps untouched
- **WHEN** a recipe stores its steps in a form Kombu does not write itself, and the user changes only the servings
- **THEN** the steps stay in their stored form

### Requirement: Recipe name
The name SHALL be required. Leading and trailing spaces SHALL be removed. The editor SHALL NOT save a name that another recipe in the local copy already has, ignoring case, and SHALL say why.

#### Scenario: Empty name
- **WHEN** the user tries to save a recipe without a name
- **THEN** the editor does not save and marks the name as required

#### Scenario: Name already used
- **WHEN** the user names a recipe `Linsensuppe` and another local recipe is called `linsensuppe`
- **THEN** the editor does not save and says that another recipe has this name

### Requirement: Category
The editor SHALL offer the categories that occur in the local copy and SHALL accept a new category typed by the user. A recipe SHALL be allowed to have no category.

#### Scenario: Existing category
- **WHEN** the user opens the category choice
- **THEN** the categories of all local recipes are offered, each once, in alphabetical order

#### Scenario: New category
- **WHEN** the user types `Fermentiertes` as the category and saves
- **THEN** the recipe has this category, and it is offered for other recipes from then on

### Requirement: Keywords
The editor SHALL let the user add and remove keywords and SHALL suggest keywords that occur in the local copy. Keywords SHALL be trimmed. A keyword SHALL NOT contain a comma; typing one SHALL start the next keyword. Duplicates SHALL be merged, ignoring case.

#### Scenario: Suggestion
- **WHEN** the user types `veg` and the local copy has the keyword `vegetarisch`
- **THEN** `vegetarisch` is suggested

#### Scenario: Comma
- **WHEN** the user types `scharf, indisch`
- **THEN** the recipe gets the two keywords `scharf` and `indisch`

#### Scenario: Duplicate
- **WHEN** the user adds `Vegan` to a recipe that has the keyword `vegan`
- **THEN** the recipe still has the keyword only once

### Requirement: Servings and times
Servings SHALL be a whole number of at least 1, or empty. Each time SHALL be entered as hours and minutes, or left empty. The editor SHALL reject other values and say why. A time the recipe stores in a form Kombu cannot read SHALL stay unchanged unless the user replaces it.

#### Scenario: Invalid servings
- **WHEN** the user enters `0` as servings
- **THEN** the editor does not save and says that servings must be at least 1

#### Scenario: Time entered
- **WHEN** the user enters 1 hour and 5 minutes as the cooking time and saves
- **THEN** the view shows a cooking time of 1 hour 5 minutes

#### Scenario: Time cleared
- **WHEN** the user clears the preparation time and saves
- **THEN** the view no longer shows a preparation time

#### Scenario: Unreadable time kept
- **WHEN** a recipe stores its total time in a form Kombu cannot read, and the user changes only the name
- **THEN** the total time stays as stored

### Requirement: Ingredients and steps
The editor SHALL show the ingredient lines one per line and the steps as a numbered list. The user SHALL be able to add, change, remove and reorder steps. On saving, empty ingredient lines and empty steps SHALL be dropped, and the order SHALL be kept.

#### Scenario: Step moved
- **WHEN** the user moves the third step to the first position and saves
- **THEN** the view shows that step as step 1

#### Scenario: Empty lines dropped
- **WHEN** the ingredient text contains empty lines
- **THEN** the saved recipe has no empty ingredient lines

### Requirement: Unsaved changes
When the user leaves the editor with unsaved changes, Kombu SHALL ask whether to discard them. Leaving without changes SHALL NOT ask. The values being edited SHALL survive a screen rotation and the app being stopped in the background by the system.

#### Scenario: Back with changes
- **WHEN** the user changed a field and goes back
- **THEN** Kombu asks whether to discard the changes, and keeps editing if the user declines

#### Scenario: Background stop
- **WHEN** the system stops Kombu in the background while the editor is open and the user returns
- **THEN** the editor shows the values the user had entered

### Requirement: Sync updates during editing
If a sync changes the recipe while the editor is open, saving SHALL keep the sync's changes to fields the user did not edit. If the sync changed a field the user also edited, Kombu SHALL NOT save at once: it SHALL name those fields and let the user save their own values anyway or discard their edits of those fields.

#### Scenario: Different fields
- **WHEN** the user edits the steps while a sync downloads a new description from the server, and then saves
- **THEN** the recipe has the new description and the user's steps

#### Scenario: Same field
- **WHEN** the user edits the name while a sync downloads a different name from the server, and then saves
- **THEN** Kombu says that the name was changed in Nextcloud meanwhile and asks which value to keep

#### Scenario: Recipe deleted on the server meanwhile
- **WHEN** the recipe is deleted on the server and removed from the device while the user edits it, and the user saves
- **THEN** Kombu says that the recipe was deleted in Nextcloud and offers to save the edited values as a new recipe

### Requirement: Recipes in conflict are resolved first
A recipe in conflict SHALL NOT be edited or deleted until the conflict is resolved. Trying to edit or delete it SHALL open the conflict view.

#### Scenario: Edit a conflict
- **WHEN** the user tries to edit a recipe in conflict
- **THEN** the conflict view opens instead of the editor

### Requirement: Refused uploads can be fixed
For a recipe whose upload the server refused, the recipe view SHALL name the reason. Saving the recipe again SHALL clear the reason and upload the recipe on the next sync.

#### Scenario: Name fixed
- **WHEN** a new recipe was refused because the name exists on the server, and the user renames it and saves
- **THEN** the reason is gone and the next sync uploads the recipe

### Requirement: Delete a recipe
The recipe view SHALL offer to delete the recipe. Kombu SHALL ask for confirmation and name the recipe. After confirmation the recipe SHALL disappear from the list at once, and the next sync SHALL delete it on the server.

#### Scenario: Deleted offline
- **WHEN** the user deletes a synced recipe without network and confirms
- **THEN** the recipe is gone from the list, and the next sync with network deletes it on the server

#### Scenario: Deletion cancelled
- **WHEN** the user starts deleting a recipe and does not confirm
- **THEN** the recipe stays as it was

### Requirement: Conflict view
For a recipe in conflict, Kombu SHALL show a conflict view that explains the kind of conflict. When both versions exist, it SHALL list each field that differs, with the value in Kombu and the value in Nextcloud, and SHALL count other differing fields that it does not show.

#### Scenario: Changed in both places
- **WHEN** the description was changed in Kombu and the name and description in Nextcloud
- **THEN** the conflict view lists the name and the description with both values each

#### Scenario: Deleted in Nextcloud
- **WHEN** a recipe changed in Kombu was deleted in Nextcloud
- **THEN** the conflict view says so and shows the version in Kombu

#### Scenario: Deleted here
- **WHEN** a recipe deleted in Kombu was changed in Nextcloud
- **THEN** the conflict view says so and shows the version in Nextcloud

### Requirement: Choosing a conflict version
The conflict view SHALL offer the two choices of the conflict kind: keep the version in Kombu or take the one in Nextcloud, and for a deletion conflict, keep or delete the recipe. Before applying a choice, Kombu SHALL say what it discards and ask for confirmation. If the conflict changed while the view was open, Kombu SHALL NOT apply the choice and SHALL show the conflict as it is now.

#### Scenario: Keep mine
- **WHEN** the user keeps the version in Kombu and confirms
- **THEN** the conflict is gone, the recipe is marked as not uploaded yet, and the next sync uploads it

#### Scenario: Take Nextcloud's
- **WHEN** the user takes the version in Nextcloud and confirms
- **THEN** the conflict is gone, the recipe shows the server version, and nothing is uploaded

#### Scenario: Changed again meanwhile
- **WHEN** a sync stores a newer server version while the conflict view is open, and the user then confirms a choice
- **THEN** nothing is applied, and the view shows the newer server version and asks again

#### Scenario: Offline change meets a web change
- **WHEN** the user edits a recipe offline, the same recipe is changed in the Nextcloud web interface, and the next sync runs
- **THEN** neither version is overwritten, the recipe is marked as a conflict, and the conflict view shows both versions
