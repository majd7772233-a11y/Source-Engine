# Online features and server-browser status

## Current verified status

The launcher declares Android's INTERNET and ACCESS_NETWORK_STATE permissions. The Stage 9 launcher action reports only whether Android currently reports an active connected network.

**The in-app server browser is unavailable in this revision.** The Android launcher has no verified native interface that requests a server list, discovers compatible servers, refreshes results, filters entries, or joins a selected server. The current native build script builds the tierhook, engine, GL4ES/Regal, client wrapper, and server wrapper; the APK verifier requires those five native libraries. Neither a library filename nor a general network permission proves a usable browser API.

The app deliberately does not fabricate servers, contact an invented backend, present a fake loading spinner, or enable a join action that has no verified implementation. A connected network only indicates Android's connectivity state; it does not test DNS, reachability, server compatibility, game authentication, or a successful multiplayer session.

## Multiplayer is not a hosted backend

This project is a launcher/runtime integration, not a game-server hosting service or a project-wide multiplayer backend. Whether multiplayer works depends on the selected game/mod, its protocol and native implementation, and a known compatible server. No online-play guarantee is made for all Source games or mods.

## What must be verified before implementing a browser

1. Inspect the exact pinned Source SDK revision and packaged native libraries, including whether libServerBrowser.so is actually built or shipped.
2. Locate documented/exported native interfaces for server discovery, query responses, cancellation, joining, and lifecycle/thread ownership. A binary's presence alone is insufficient.
3. Identify a lawful, known-compatible server and the correct game/mod query protocol.
4. Implement only supported calls, with explicit timeouts, cancellation, bounded parsing, error/empty states, refresh/filter behavior, and no fabricated fallback results.
5. Verify end-to-end query and join behavior against that server on a real device. Until then, keep the feature marked unavailable.

## Automated coverage and limits

scripts/verify-stage9.sh checks the manifest permissions, advanced-settings entry point, Android connectivity-state reporting, English/Arabic disclosure strings, absence of fabricated server endpoints, and CI wiring. These are static checks; they do not demonstrate a successful server query or multiplayer session.


## App update status

There is no in-app update checker, APK download, or automatic installer in this launcher. The previously present deployment-marker checker had no user-facing result path and did not update the application; that dead code and its disabled service have been removed rather than implying a newer build will be detected. A future update flow requires an approved signed release, checksum/signature verification, and a safe Android installation handoff. Until that exists, install only a separately approved build distributed through the project's cleared release process.
