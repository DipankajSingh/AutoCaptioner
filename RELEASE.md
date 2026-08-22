# Release & Beta Workflow

How this app is versioned, branched, and shipped to Google Play.
Current production: `versionName 2.10.1`, `versionCode 21`.

## Branch model

```
master  ──► always mirrors what is live (or about to go live) in Production
beta    ──► integration branch for upcoming features; builds go to the Open testing track
feature/* ─► short-lived branches, merged into beta (not directly into master)
```

Rules:

- Feature work happens on short-lived `feature/*` branches (created when needed) merged
  into `beta`. The only exception is urgent hotfixes, which land on `master` first.
- When a feature set is stable, merge `beta` -> `master`.
- **Every production hotfix must be merged back into `beta` immediately** after shipping,
  so the branches never drift apart.
- Delete merged feature branches. Only `master` and `beta` are permanent.

## Versioning rules (critical)

Google Play enforces these across ALL tracks:

1. `versionCode` must strictly increase forever. A number can never be reused, even if a
   release is discarded. Hard ceiling is 2,100,000,000 (we increment by 1, so no risk).
2. Users eligible for multiple tracks receive the build with the **highest versionCode**
   across those tracks. If Production ever gets a higher code than the Open testing track,
   beta testers silently stop receiving betas ("Superseded" status in Console).
3. You cannot create a new release while another release has an outstanding staged rollout.
   Finish it (100%) or discard it first via Publishing overview.

Therefore:

- Keep **one global counter**: next upload is `22`, then `23`, ... regardless of track.
  Before any upload: `versionCode = max(ever used anywhere) + 1`.
- Bump `versionCode`/`versionName` in `app/build.gradle.kts` (`defaultConfig`) as part of
  the release commit on the branch being released.

### Naming convention

| Build type        | versionName        | example      |
|-------------------|--------------------|--------------|
| Beta (Open track) | semver + `-betaNN` | `2.11.0-beta01` |
| Final production  | plain semver       | `2.11.0`     |

The final production build is rebuilt from the `master` merge commit with the suffix
stripped and a fresh versionCode — do not promote the `-betaNN` binary to Production.

## Release flows

### Beta release

1. Merge feature branch(es) into `beta`.
2. Bump versions: e.g. `versionName = "2.11.0-beta01"`, `versionCode = 22`.
3. Commit: `chore: bump version to 2.11.0-beta01`.
4. Build: `./gradlew bundleRelease`.
5. Play Console -> Testing -> Open testing -> Create/Edit release -> upload AAB ->
   add release notes -> roll out (staged % optional).
6. Check the automated **Pre-launch report** after processing.

### Promoting a beta cycle to production

1. Verify beta feedback/Crashlytics look good.
2. On `master`: merge `beta` into `master`.
3. Strip suffix: `versionName = "2.11.0"`, bump versionCode again.
4. Build and upload to **Production**, ideally with a staged rollout (start 5-20%,
   increase gradually; halt if crash rate spikes).

### Hotfix (production bug)

1. Fix on `master` (or branch off `master`, fix, merge).
2. Patch bump: e.g. `2.10.2`, next free versionCode.
3. Ship to Production (staged rollout for anything non-trivial).
4. Immediately merge `master` -> `beta` so the fix reaches the beta line too.

## Play Console specifics

- **Tracks used:** Internal (rough WIP smoke tests, instant, invisible) -> Open testing
  (public beta program) -> Production.
- Internal track is exclusive: anyone opted into it receives ONLY internal-track builds;
  they must opt out before joining open/closed tests. Don't put real beta testers there.
- Open testing feedback is private and does NOT affect the public store rating. Testers
  join via the opt-in link on our store listing page (app already has production access).
- **In-app purchases (RevenueCat):** the SDK is integrated, but there are **no paid
  features or products yet** - billing is a non-issue for current releases. When paid
  features launch, remember: test-track users are charged REAL money unless they are
  license testers (Settings -> License testing). License-tester purchases become sandbox
  "Test order"s with accelerated renewals; RevenueCat shows them under View Sandbox Data.
- Pricing and country-availability changes apply across all tracks at once.
- Once published, builds on the Open track have a fixed signing key (Play App Signing).
- New uploads must target the current required API level (Android 16 / API 36 from
  Aug 31, 2026); project currently targets SDK 37 - keep raising targetSdk each year.
