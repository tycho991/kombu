# Spec Delta

## MODIFIED Requirements

### Requirement: Account check after login
After receiving the app password, Kombu SHALL verify it with an authenticated request before storing it. It SHALL also check that the Nextcloud Cookbook app answers for this account in version 0.10.3 or newer. If either check fails, Kombu SHALL store nothing and explain the reason.

#### Scenario: Cookbook available
- **WHEN** the credentials work and the Cookbook app answers
- **THEN** Kombu stores the credentials and opens the start screen

#### Scenario: Cookbook missing
- **WHEN** the credentials work but the Cookbook app is not installed or not enabled for the account
- **THEN** Kombu explains that the Cookbook app is needed, stores nothing and tries to revoke the new app password on the server

#### Scenario: Cookbook too old
- **WHEN** the credentials work but the Cookbook app reports a version older than 0.10.3
- **THEN** Kombu explains that Cookbook 0.10.3 or newer is needed, names the installed version, stores nothing and tries to revoke the new app password on the server

#### Scenario: Credentials rejected
- **WHEN** the server rejects the received credentials
- **THEN** Kombu shows that the login failed and stores nothing

### Requirement: Logout revokes the app password
On logout Kombu SHALL ask the user to confirm and SHALL state how many local changes are not uploaded yet. It SHALL then delete the stored credentials and the local recipe copy with its images, and ask the server to revoke the app password. If the server cannot be reached, Kombu SHALL still sign out locally and tell the user that the app password is still listed in Nextcloud and can be removed there.

#### Scenario: Logout online
- **WHEN** the user confirms logout and the server is reachable
- **THEN** the app password is revoked on the server, the local credentials and recipes are deleted and Kombu shows the login screen

#### Scenario: Logout offline
- **WHEN** the user confirms logout and the server cannot be reached
- **THEN** the local credentials and recipes are deleted, Kombu shows the login screen and a notice that the app password remains in the Nextcloud security settings

#### Scenario: Logout with pending changes
- **WHEN** the user starts logout while three local changes are not uploaded
- **THEN** the confirmation says that three changes will be lost, and nothing is deleted unless the user confirms

#### Scenario: Logout cancelled
- **WHEN** the user does not confirm the logout
- **THEN** Kombu stays signed in and nothing is deleted

## ADDED Requirements

### Requirement: Sign in again to the same account
When the server has rejected the stored credentials, Kombu SHALL offer to sign in again. If the new login is for the same server and login name, Kombu SHALL replace the credentials and keep all local data. If it is for another account, Kombu SHALL store nothing, revoke the new app password and explain that the current account must be signed out first.

#### Scenario: Same account
- **WHEN** the user signs in again after the credentials were rejected, with the same login name on the same server
- **THEN** the new credentials are stored, local recipes and pending changes are kept, and a sync starts

#### Scenario: Another account
- **WHEN** the user signs in again with a different login name
- **THEN** Kombu keeps the old state, revokes the new app password and explains that a different account needs a logout first
