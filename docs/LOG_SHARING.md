# Sharing Logs Safely

Authentication logs may contain account names, UUIDs, local file paths, authorization codes, access tokens, refresh tokens, or other sensitive information. Do not upload a complete unredacted log to a public issue, chat, paste service, or repository.

Before sharing a log:

1. Reproduce the problem with the fewest unrelated mods possible.
2. Make a separate copy of the log.
3. Remove passwords, authorization codes, access tokens, refresh tokens, email addresses, account names, UUIDs, local user paths, server addresses, and session identifiers.
4. Share only the lines needed to understand the failure, together with the mod version, Minecraft version, Fabric Loader version, and Fabric API version.

Never share these files or directories:

```text
_IAS_ACCOUNTS_DO_NOT_SEND_TO_ANYONE/
_IAS_ACCOUNTS_DO_NOT_SEND_TO_ANYONE/.hidden/accounts_v1.do_not_send_to_anyone
_IAS_ACCOUNTS_DO_NOT_SEND_TO_ANYONE/.hidden/global_password_v2
```

Use the repository's GitHub Security tab for a private vulnerability report. Use a public issue only after all sensitive information has been removed.
