# Spec Delta

## Purpose

Connects Kombu to the user's own Nextcloud account: entering the server address, signing in through Nextcloud's browser login, checking that the Cookbook app is available, keeping the credentials safe on the device, and signing out again.

## ADDED Requirements

### Requirement: Any server address
The user SHALL be able to enter the address of any Nextcloud server. Kombu SHALL NOT contain or suggest a fixed server address. Kombu SHALL accept the address with or without `https://`, with or without trailing slashes, and with a sub-path when Nextcloud is installed in a sub-directory.

#### Scenario: Address without scheme
- **WHEN** the user enters `cloud.example.com`
- **THEN** Kombu uses `https://cloud.example.com` as the server address

#### Scenario: Sub-path install
- **WHEN** the user enters `https://example.com/nextcloud/`
- **THEN** Kombu uses `https://example.com/nextcloud` as the server address

#### Scenario: Copied login URL
- **WHEN** the user enters an address that ends in `/index.php` or `/index.php/login`
- **THEN** Kombu uses the address without that suffix

#### Scenario: Not a web address
- **WHEN** the user enters text that is not a valid host name or web address
- **THEN** Kombu shows that the address is invalid and does not contact any server

### Requirement: HTTPS only
Kombu SHALL connect to the server only over HTTPS with a certificate the device trusts by default. It SHALL refuse plain HTTP addresses and SHALL NOT offer to ignore certificate errors.

#### Scenario: HTTP address
- **WHEN** the user enters an address starting with `http://`
- **THEN** Kombu explains that only HTTPS is supported and does not contact the server

#### Scenario: Untrusted certificate
- **WHEN** the server presents a certificate the device does not trust
- **THEN** Kombu shows a certificate error and the login does not start

### Requirement: Server check before login
Before it starts the login, Kombu SHALL check that the address belongs to an installed Nextcloud server. It SHALL tell the user which of these failed: the server could not be reached, or it is not a Nextcloud server, or it is in maintenance mode.

#### Scenario: Unreachable server
- **WHEN** the device is offline or the host cannot be resolved or reached
- **THEN** Kombu shows that the server cannot be reached and keeps the entered address

#### Scenario: Not a Nextcloud server
- **WHEN** the address answers but does not identify itself as an installed Nextcloud
- **THEN** Kombu shows that no Nextcloud was found at this address

#### Scenario: Maintenance mode
- **WHEN** the Nextcloud server reports maintenance mode
- **THEN** Kombu shows that the server is in maintenance and asks the user to try again later

### Requirement: Browser login with app password
Kombu SHALL sign in through Nextcloud Login Flow v2: it opens the server's login page in a browser and waits for the result. Kombu SHALL NOT ask for, receive or store the account password. On success it SHALL receive an app password that is shown in Nextcloud under the name "Kombu".

#### Scenario: Successful login
- **WHEN** the user approves access on the Nextcloud login page
- **THEN** Kombu receives the server address, login name and app password, and continues without further input

#### Scenario: Two-factor authentication
- **WHEN** the account uses two-factor authentication or single sign-on
- **THEN** the user completes it on the Nextcloud page and the login succeeds like any other

#### Scenario: Returning without approving
- **WHEN** the user returns to Kombu before approving access
- **THEN** Kombu keeps waiting and offers to reopen the login page or to cancel

#### Scenario: Login expires
- **WHEN** access is not approved before the login token expires
- **THEN** Kombu stops waiting, says that the login timed out and lets the user start again

#### Scenario: Cancel
- **WHEN** the user cancels while Kombu waits
- **THEN** Kombu stops waiting, stores nothing and returns to the address input

### Requirement: Account check after login
After receiving the app password, Kombu SHALL verify it with an authenticated request before storing it. It SHALL also check that the Nextcloud Cookbook app answers for this account. If either check fails, Kombu SHALL store nothing and explain the reason.

#### Scenario: Cookbook available
- **WHEN** the credentials work and the Cookbook app answers
- **THEN** Kombu stores the credentials and opens the start screen

#### Scenario: Cookbook missing
- **WHEN** the credentials work but the Cookbook app is not installed or not enabled for the account
- **THEN** Kombu explains that the Cookbook app is needed, stores nothing and tries to revoke the new app password on the server

#### Scenario: Credentials rejected
- **WHEN** the server rejects the received credentials
- **THEN** Kombu shows that the login failed and stores nothing

### Requirement: Credentials stored securely
Kombu SHALL store the app password only in encrypted form, with a key that is held by the device's keystore and cannot be exported. The stored credentials SHALL NOT be included in backups or device-to-device transfers. Kombu SHALL NOT write the app password to logs.

#### Scenario: App restarted
- **WHEN** the user signed in earlier and Kombu is started again, also without network
- **THEN** Kombu opens on the start screen without asking for login again

#### Scenario: Restored from a backup
- **WHEN** Kombu is restored from a backup or moved to a new device
- **THEN** no credentials are restored and Kombu opens on the login screen

#### Scenario: Stored data unreadable
- **WHEN** the stored credentials cannot be decrypted, for example because the keystore key is gone
- **THEN** Kombu discards them and opens on the login screen instead of failing

### Requirement: Entry point depends on login state
Kombu SHALL open on the login screen when no credentials are stored and on the start screen when they are.

#### Scenario: First start
- **WHEN** Kombu is started for the first time
- **THEN** it shows the login screen

### Requirement: Account information
Kombu SHALL show the signed-in server address and login name to the user.

#### Scenario: Signed in
- **WHEN** the user opens the account section
- **THEN** Kombu shows the server address and the login name

### Requirement: Logout revokes the app password
On logout Kombu SHALL ask the user to confirm. It SHALL then delete the stored credentials and ask the server to revoke the app password. If the server cannot be reached, Kombu SHALL still sign out locally and tell the user that the app password is still listed in Nextcloud and can be removed there.

#### Scenario: Logout online
- **WHEN** the user confirms logout and the server is reachable
- **THEN** the app password is revoked on the server, the local credentials are deleted and Kombu shows the login screen

#### Scenario: Logout offline
- **WHEN** the user confirms logout and the server cannot be reached
- **THEN** the local credentials are deleted, Kombu shows the login screen and a notice that the app password remains in the Nextcloud security settings

#### Scenario: Logout cancelled
- **WHEN** the user does not confirm the logout
- **THEN** Kombu stays signed in and nothing is deleted
