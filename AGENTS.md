# Working on Kombu

Kombu is developed spec-first with OpenSpec. The project context (product, fixed decisions, design rules R1–R7, module layout) is in `openspec/config.yaml`; read it before proposing or implementing anything.

If a `private/` folder exists locally, it holds the maintainer's German planning documents (for example `private/SPEC.de.md`). Use them as background context, but never commit them, never quote personal details from them in code, specs or commit messages, and never move content from them into tracked files without translating and anonymizing it. The folder is git-ignored.

## Ground rules

- Plan every larger feature as an OpenSpec change (`/openspec-propose`, then apply, then archive). Do not start implementation without an approved change.
- Write code, comments, commit messages, docs and specs in English.
- Never put user-visible text in code. Use string resources: English in `values/`, German in `values-de/`.
- Never commit anything personal: no server URLs, account names, credentials, store names or private recipes.
- Do not add dependencies on Google Play Services. Every new dependency needs a license compatible with GPL-3.0-or-later.
- Never overwrite user data silently. Sync and bulk operations must detect conflicts and let the user decide.
- Keep parsers and API clients in pure Kotlin modules without Android dependencies, and cover them with tests.
