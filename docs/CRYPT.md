# Global Password Vault and Account Data Encryption

In-Game Account Switcher: JohnMuyuan Edited Version uses a global password to protect Microsoft accounts saved in the in-game account switcher. This document describes the current version's storage format, encryption parameters, and security boundaries.

## Global Password Model

- Users must create a global password before adding their first Microsoft account.
- The same global password is used when adding accounts, unlocking the vault, switching accounts, and changing the global password.
- The global password is kept only in memory for the current game process and is never written to disk in plaintext.
- Account data cannot be recovered if the global password is forgotten. There is no backdoor or recovery key; the vault must be deleted and the accounts added again.

## Password Requirements

When creating or changing the global password, it must meet all of the following requirements:

- More than 16 characters long
- At least one uppercase letter
- At least one lowercase letter
- At least one digit
- At least one special character

Do not use your Microsoft account password as the global password, and do not send the global password to anyone.

## File Locations

The password vault is stored in the Minecraft game directory:

```text
_IAS_ACCOUNTS_DO_NOT_SEND_TO_ANYONE/
└── .hidden/
    ├── accounts_v1.do_not_send_to_anyone
    └── global_password_v2
```

- `accounts_v1.do_not_send_to_anyone` stores account records and encrypted Microsoft credentials.
- `global_password_v2` stores a fixed verification marker encrypted with the global password. It is used to determine whether an entered password is correct.
- These files contain sensitive account data and must not be uploaded or shared.

## Encryption Parameters

The account encryption key is derived from the global password using the following parameters:

- Key derivation algorithm: `PBKDF2WithHmacSHA512`
- Iterations: `1,000,000`
- Derived key length: 256 bits
- Symmetric encryption algorithm: `AES/GCM/NoPadding`
- GCM authentication tag: 128 bits
- 128-byte random salt for each encryption operation
- 16-byte random IV for each encryption operation
- Random number source: `SecureRandom.getInstanceStrong()`

The salt and IV do not need to be kept secret and are stored alongside the ciphertext. The AES-GCM authentication tag detects ciphertext tampering and incorrect passwords.

## Protected Data

Microsoft account access and refresh tokens are encrypted with the key derived from the global password. Account names, UUIDs, account types, and other metadata used to display and identify records are not encrypted at the file-container level. The account file should therefore not be considered fully anonymous.

Offline accounts do not contain Microsoft access or refresh tokens and therefore do not have the same credential-protection implications as online Microsoft accounts.

## Changing the Global Password

When the password is changed, the mod re-encrypts account data transactionally:

1. Create `.password_change_backup` backups of the account file and password verifier.
2. Write the `.hidden/password_change_pending` marker.
3. Re-encrypt all protected account data with the new password.
4. Save the new password verifier.
5. Delete the pending marker and backup files after the operation succeeds.

If the game exits unexpectedly while the password is being changed, the next startup detects the pending marker and restores the backups. Stale password-change backups without a pending marker are removed.

## Security Boundaries

This design primarily prevents an attacker from directly reading Microsoft tokens after obtaining a copied, leaked, or backed-up vault file. It cannot defend against malicious software that already controls the game process or system, including keyloggers, debuggers, process-memory readers, malicious mods, or attackers with access to the current Windows user account.

Use mods from trusted sources, protect the Minecraft game directory and system account, and use a unique, sufficiently complex global password.
