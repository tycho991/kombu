# recipe-sync Specification

## Purpose

Keeps a complete local copy of the user's Nextcloud Cookbook recipes, including images, so that recipes can be read, cooked and edited without network, and syncs it with the server without ever overwriting a change silently.

## Requirements

### Requirement: Complete local copy
After the first successful sync, Kombu SHALL hold every recipe of the account locally, with all fields and with the image if the recipe has one. Reading a recipe SHALL NOT need the network.

#### Scenario: First sync
- **WHEN** a signed-in user with recipes on the server syncs for the first time and the sync completes
- **THEN** every server recipe and every recipe image is stored on the device

#### Scenario: Reading offline
- **WHEN** the device has no network after a completed sync
- **THEN** every recipe and its image can be read from the local copy

### Requirement: Supported Cookbook versions
Kombu SHALL sync only with Cookbook 0.10.3 or newer. Before each sync it SHALL check the server's Cookbook version. If the version is older or cannot be determined, the sync SHALL stop with that reason and SHALL NOT change local or server data.

#### Scenario: Supported version
- **WHEN** the server runs Cookbook 0.11.12
- **THEN** the sync proceeds

#### Scenario: Version too old
- **WHEN** the server runs Cookbook 0.10.2
- **THEN** the sync stops, nothing is changed, and the status names the minimum version 0.10.3

### Requirement: Server changes are downloaded
On each sync Kombu SHALL fetch the server's recipe list. It SHALL download a recipe in full when it is new, when its modification date differs from the last synced one, or when the server reports no modification date. A downloaded recipe whose content equals the local copy SHALL NOT count as changed.

#### Scenario: Recipe changed on the server
- **WHEN** a recipe without local changes has a newer modification date on the server
- **THEN** Kombu replaces the local copy with the server version

#### Scenario: New recipe on the server
- **WHEN** the server list contains a recipe that Kombu does not have
- **THEN** Kombu downloads it and its image

#### Scenario: Recipe without modification date
- **WHEN** the server reports no modification date for a recipe
- **THEN** Kombu downloads it and keeps the local copy unchanged if the content is the same

#### Scenario: Nothing changed
- **WHEN** no recipe changed on the server since the last sync
- **THEN** Kombu downloads no full recipe and no image

### Requirement: Recipe images
Kombu SHALL store a recipe's image in full size and as a thumbnail. It SHALL download the image again when the recipe changed on the server or the local image is missing, and SHALL remove it when the recipe no longer has one. A failed image download SHALL NOT block the recipe and SHALL be retried on the next sync.

#### Scenario: Recipe without image
- **WHEN** a recipe has no image on the server
- **THEN** Kombu stores the recipe without an image and shows no placeholder from the server as image

#### Scenario: Image download fails
- **WHEN** a recipe downloads but its image request fails
- **THEN** the recipe is stored, and the next sync tries the image again

### Requirement: Server deletions
When a recipe Kombu has synced is no longer on the server, Kombu SHALL remove it locally if it has no local change. If it has a local change, Kombu SHALL keep the local version and mark a conflict.

#### Scenario: Deleted on the server, unchanged here
- **WHEN** a synced recipe without local changes is missing from the server list
- **THEN** Kombu removes the recipe and its image from the device

#### Scenario: Deleted on the server, changed here
- **WHEN** a recipe with a local change is missing from the server list
- **THEN** Kombu keeps the local version and marks a conflict "deleted on the server"

### Requirement: Unknown fields are kept
When Kombu uploads a recipe, it SHALL send every field of the last synced server version unchanged, except the fields the user changed in Kombu. Fields Kombu does not know SHALL survive the round trip with their value and type.

#### Scenario: Field Kombu does not show
- **WHEN** a server recipe has fields such as `author`, `nutrition` or `video`, and the user changes only the name in Kombu
- **THEN** the uploaded recipe still has those fields with their original values

### Requirement: Offline editing
Kombu SHALL let recipes be created, changed and deleted without network. Each change SHALL be stored locally at once and marked as pending until it is uploaded. A recipe SHALL have at most one pending change, which reflects its latest local state.

#### Scenario: Change without network
- **WHEN** the user changes a recipe while offline
- **THEN** the change is stored on the device, shown at once, and marked as pending

#### Scenario: Created and deleted before upload
- **WHEN** the user creates a recipe and deletes it again before it was uploaded
- **THEN** nothing is sent to the server for it

### Requirement: Pending changes are uploaded
On each sync Kombu SHALL upload every pending change: create new recipes, update changed ones and delete deleted ones on the server. After a successful upload, the server's resulting version SHALL become the new synced version, and the change SHALL no longer be pending.

#### Scenario: Offline change uploaded
- **WHEN** a recipe was changed offline and the next sync runs with network
- **THEN** the change is on the server and the recipe is no longer marked pending

#### Scenario: New recipe uploaded
- **WHEN** a recipe created offline is uploaded
- **THEN** the server creates it, and Kombu links its local copy to the new server recipe

### Requirement: Check before upload
Before uploading a change or deletion, Kombu SHALL fetch the server's current version and compare its content with the last synced version. Fields the server sets by itself, such as the modification date, SHALL NOT count. If the content differs, Kombu SHALL NOT upload and SHALL mark a conflict.

#### Scenario: Changed in both places
- **WHEN** a recipe was changed in Kombu and, since the last sync, also in the Nextcloud web interface
- **THEN** Kombu uploads nothing for it and marks a conflict

#### Scenario: Changed only in Kombu
- **WHEN** the server version still matches the last synced version
- **THEN** Kombu uploads the change

#### Scenario: Deleted here, changed on the server
- **WHEN** a recipe deleted in Kombu was changed on the server since the last sync
- **THEN** Kombu does not delete it on the server and marks a conflict

### Requirement: Conflicts keep both versions
A recipe in conflict SHALL keep the local version and the server version until the user decides. Kombu SHALL NOT upload it, SHALL NOT overwrite either version, and SHALL keep the stored server version up to date on later syncs.

#### Scenario: Sync with an open conflict
- **WHEN** a sync runs while a recipe is in conflict
- **THEN** both versions are still available afterwards and nothing was uploaded for the recipe

### Requirement: Resolving a conflict
For a conflict, the user SHALL be able to keep the local version or take the server version. Keeping the local version SHALL make it a pending change based on the server version the user saw. Taking the server version SHALL replace the local version. Deletion conflicts SHALL offer the same two choices.

#### Scenario: Keep mine
- **WHEN** the user keeps the local version of a conflict
- **THEN** the next sync uploads it, after checking that the server version is still the one the user saw

#### Scenario: Take the server's
- **WHEN** the user takes the server version of a conflict
- **THEN** the local version is replaced and nothing is uploaded

#### Scenario: Keep a recipe deleted on the server
- **WHEN** a recipe changed here was deleted on the server and the user keeps the local version
- **THEN** the next sync creates it on the server again as a new recipe

### Requirement: Rejected uploads keep the local change
When the server refuses an upload, for example because another recipe already has the name or the name is missing, Kombu SHALL keep the local change pending, record the reason for the recipe, and continue with the other recipes.

#### Scenario: Name already taken
- **WHEN** a recipe created offline has the name of an existing server recipe
- **THEN** the recipe stays local with the reason "name already exists", and the other pending changes are uploaded

### Requirement: Sync triggers
Kombu SHALL sync when the app starts, shortly after a local change, when the user asks for it, and periodically about every hour while a network is available. Only one sync SHALL run at a time; a trigger during a running sync SHALL lead to at most one further run.

#### Scenario: Manual sync
- **WHEN** the user asks for a sync and none is running
- **THEN** a sync starts

#### Scenario: Trigger while syncing
- **WHEN** a local change is made while a sync is running
- **THEN** the change is uploaded by a sync that starts after the running one

### Requirement: Interrupted and failed syncs
A sync that fails because of the network or the server SHALL NOT lose or half-apply any recipe. Every recipe SHALL either be fully updated or left as before. Kombu SHALL retry later with increasing delays.

#### Scenario: Network lost during sync
- **WHEN** the connection drops in the middle of a sync
- **THEN** recipes already processed stay updated, the others stay as before, pending changes stay pending, and a later sync continues

#### Scenario: Server error
- **WHEN** the server answers with a server error
- **THEN** the sync stops, the status shows the problem, and Kombu retries later

### Requirement: Rejected credentials stop the sync
When the server rejects the stored credentials, Kombu SHALL stop syncing, keep all local data including pending changes, and show that the user needs to sign in again. It SHALL NOT retry with the rejected credentials.

#### Scenario: App password revoked on the server
- **WHEN** the app password was removed in the Nextcloud security settings and a sync runs
- **THEN** the sync stops, no local data is deleted, and the status asks the user to sign in again

### Requirement: Sync status
Kombu SHALL show the number of local recipes, the time of the last successful sync, the number of pending changes and conflicts, whether a sync is running, and the current problem if there is one. It SHALL offer an action to sync now.

#### Scenario: Status after a sync
- **WHEN** a sync has completed
- **THEN** the status shows the recipe count and the time of that sync

#### Scenario: Status with a problem
- **WHEN** the last sync failed
- **THEN** the status shows the reason in the app language, and the time of the last successful sync stays visible

### Requirement: Local recipe data stays on the device
The local recipe copy, its images and the sync state SHALL NOT be included in backups or device-to-device transfers. They SHALL be stored where the system does not delete them to free space.

#### Scenario: Restored from a backup
- **WHEN** Kombu is restored from a backup or moved to a new device
- **THEN** no recipes are restored, and they are downloaded again after login
