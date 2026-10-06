# Android app

The native Android version of Budget: `android/`. Primary device: **Samsung Galaxy Z Fold 8 Ultra**
(cover and inner screens). It follows the same spec as the web app (`PRODUCT_SPEC.md`), the same math
(`docs/DOMAIN_RULES.md`), the same sync contract (`docs/SYNC.md`) and the same look
(`docs/UI_ANATOMY.md`, `docs/FLUID_GLASS_UI.md`, `design/tokens.json` v2, `design/brand/`).

| | |
|---|---|
| applicationId / namespace | `com.personal.budget` (**permanent**: changing it makes a different app) |
| App name | Budget |
| minSdk / targetSdk / compileSdk | 29 / 37 / 37 |
| versionName | `android/version.properties` (starts at 1.0.0) |
| versionCode | `git rev-list --count HEAD` (fallback 1 without git) |

## Toolchain

| | Version |
|---|---|
| Android Gradle Plugin | 9.4.1 (built-in Kotlin; no `kotlin-android` plugin applied) |
| Gradle (wrapper, committed) | 9.7.1 |
| Kotlin (+ Compose compiler, serialization plugins) | 2.4.20 |
| KSP | 2.3.12 |
| Compose BOM | 2026.09.00 (UI 1.12.1, Material 3 1.4.0) |
| material3-adaptive | 1.3.0 · navigation-suite (BOM) |
| Navigation Compose | 2.10.2 |
| Room | 2.8.5 (KSP, schema exported) |
| DataStore | 1.2.1 · WorkManager 2.12.0 · Glance 1.2.0 · core-splashscreen 1.2.0 |
| kotlinx.serialization / coroutines | 1.11.0 / 1.11.0 |
| OkHttp | 5.5.0 |
| Haze (`dev.chrisbanes.haze`, backdrop blur for the glass chrome) | 2.0.1 |
| Tests | JUnit 4.13.2, Robolectric 4.17, MockWebServer 5.5.0, Room testing |

All versions live in `android/gradle/libs.versions.toml`. Bytecode targets Java 17 (CI uses JDK 17;
JDK 21 also works locally).

## Architecture

```
android/app/src/main/java/com/personal/budget/
  BudgetApp.kt, AppContainer.kt   manual DI: one instance of each service per process
  MainActivity.kt                 splash, edge-to-edge, theme, auth gate, budget://add deep link
  domain/
    model/                        pure Kotlin models + the backup JSON format (kotlinx.serialization)
    usecase/                      DOMAIN_RULES: BudgetMath (§1–§5), NetWorthHistoryMath (§5b),
                                  NewMonthPlanner (§6), Formatters (§8), BackupMerge (import rules)
  data/
    local/                        Room entities + DAOs + BudgetDatabase (v1); DataStore stores
    remote/                       GoTrue + PostgREST over OkHttp; entity <-> JSON mappers
    repository/                   BudgetRepository (all writes), SyncEngine, SyncScheduler,
                                  AuthRepository (tokens + refresh), AccountManager (sign-in/out)
  ui/
    theme/                        tokens v2 → Material ColorScheme + BudgetColors, type, motion
    components/                   Fluid glass v2 kit: Glass.kt (ambient backdrop, glass / blur
                                  modifiers, soft shadows), Chrome.kt (collapsing top bar, large
                                  title, floating pill nav, glass rail, toasts), GlassSheet.kt
                                  (drag-to-dismiss sheet), Swipe.kt (swipe to delete), segmented
                                  control, switch, chips, rolling numbers, FLIP list, charts
    navigation/AppShell.kt        window-size-driven shell (bottom bar + FAB / rail), NavHost
    screens/                      home, month (+ category detail), year, networth, settings, auth,
                                  add (transaction sheet)
  widgets/                        Glance widget + updater
  workers/                        SyncWorker (periodic)
  utilities/                      haptics
```

Data flows one way: Room → `BudgetRepository.snapshot` (one combined Flow of every table, as domain
objects) → shared `StateFlow` in `AppContainer` → screen ViewModels / composables → Compose. The
domain math (`BudgetBook`, `NetWorthMath`, …) is pure Kotlin and runs on that snapshot. UI actions go
back through ViewModels to `BudgetRepository`, the only writer.

**Room is the UI's source of truth.** Every entity mirrors its Supabase table plus:

- `dirty`: the row has local changes not yet on the server;
- `local_version`: bumped on every local edit (see "Sync");
- `updated_at`: the server's timestamp from the last push/pull.

Deletes are tombstones (`deleted = 1`), pushed like any edit and hidden by every UI query.

The **Meals** feature was removed from the product; there are no meal tables. Backup files from older
versions that still contain `meal_plans` / `meal_items` import fine: those keys are ignored.

## Sync and auth

### Auth (GoTrue, `/auth/v1/…`)

- Sign in (`token?grant_type=password`), sign up (`signup`: when email confirmation is on the
  response has no session and the app says "Check your email"), forgot password (`recover`), sign out
  (`logout`, best effort). Unauthenticated calls send only `apikey: <publishable key>`; the
  publishable key (`sb_publishable_…`) is not a JWT and never goes in `Authorization`.
- Tokens live in DataStore `auth.preferences_pb` (excluded from Android backup).
- **Refresh is single-flight.** `AuthRepository` holds a mutex: a request whose token expires within
  60 s refreshes first; a 401 calls `refreshAfterUnauthorized(rejectedToken)`, which refreshes only if
  the rejected token is still the current one (otherwise another caller already refreshed). Supabase
  rotates refresh tokens, so two parallel refreshes would invalidate each other; the mutex prevents it.
- A rejected refresh token (400–403) ends the session but **keeps local data**: the app keeps working
  offline and Settings shows "Sign in again". A network error during refresh is just "offline".
- The first launch needs the network to sign in; after that everything works offline.
- Signing in as a *different* account than the one whose data is on the device clears local data
  (with a confirmation if that data has unsynced changes).

### SyncEngine (docs/SYNC.md, exactly)

1. **Push** (first), tables in the documented order. Dirty rows (tombstones included) go out as a bulk
   upsert: `POST /rest/v1/{table}?on_conflict=…` with
   `Prefer: resolution=merge-duplicates,return=representation`, always with `user_id`, never with
   `updated_at`. Before the request the engine snapshots each row's `local_version`; for each row the
   server returns it runs `UPDATE … SET dirty = 0, updated_at = :server WHERE key AND local_version =
   :sent`. **An edit made while the request was in flight bumped the version, so the row stays dirty**
   and goes out next time. If a batch is rejected (4xx), rows are retried one by one so a single bad
   row can't block the rest; rejected rows stay dirty and the status shows the error.
2. **Net worth snapshot** (DOMAIN_RULES §5b): if the push included accounts, ledger entries or budgets
   of an account-linked category (or an import asked for it), the engine calls
   `POST /rest/v1/rpc/take_net_worth_snapshot` (body `{}`, user JWT) and stores the returned row. A
   failed call is retried on the next sync.
3. **Pull** per table: `updated_at=gt.<cursor − 10 s>&order=updated_at.asc&limit=1000`. Pages continue
   with `updated_at=gte.<max of previous page>` until a page has fewer than 1000 rows (if a whole page
   shares one timestamp, `offset` steps past it). Re-reading overlap rows is harmless. The cursor (max
   `updated_at` seen, stored per table in Room) is saved after every page. Each page is applied in one
   Room transaction that re-reads the local rows and **skips any that are dirty**, so a remote row
   never overwrites an unpushed local edit, even one made after the page was fetched. Tombstones are
   stored. `net_worth_snapshots` is pull-only (rows are dirty only after a backup import, which is the
   one time the client writes that table).
4. One sync at a time (mutex); requests that arrive meanwhile coalesce into one re-run.

**Triggers** (`SyncScheduler`): app start and every return to the foreground (ProcessLifecycleOwner),
1.5 s after the last local edit (debounced), when the network comes back (ConnectivityManager
callback), and every 6 h via WorkManager (`SyncWorker`, network required).

**Status** (top-bar dot): green synced · pulsing accent syncing · `warn` amber offline or changes
pending · `bad` error; tap it to sync now. Settings → Account shows the status text, pending count,
last sync time and **Sync now**.

**Sign out** pushes first. Local data is deleted only after a clean push, or after the user confirms
discarding the listed number of unsynced changes.

Tests: `app/src/test/.../data/SyncEngineTest.kt` runs the engine against MockWebServer: edit during an
in-flight push stays dirty, pull skips dirty rows, keeps tombstones, pages past 1000 rows, stores the
cursor and re-reads with a 10 s overlap, and a 401 triggers exactly one refresh.

## Building locally

### SDK setup (once)

```sh
# Command-line tools from https://developer.android.com/studio#command-line-tools-only
mkdir -p /opt/android-sdk/cmdline-tools
unzip commandlinetools-linux-*_latest.zip -d /opt/android-sdk/cmdline-tools
mv /opt/android-sdk/cmdline-tools/cmdline-tools /opt/android-sdk/cmdline-tools/latest
yes | /opt/android-sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=/opt/android-sdk --licenses
/opt/android-sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=/opt/android-sdk \
  "platform-tools" "platforms;android-37.0" "build-tools;37.0.0"
echo "sdk.dir=/opt/android-sdk" > android/local.properties   # git-ignored
```

Android Studio users can simply open `android/`.

### Build, test, APK

```sh
cd android
./gradlew testDebugUnitTest            # domain fixtures, formatting, sync engine, migrations
./gradlew lintDebug                    # fails on errors only
./gradlew assembleDebug                # app/build/outputs/apk/debug/budget-<name>-<code>-debug.apk
./gradlew assembleRelease              # app/build/outputs/apk/release/budget-<name>-<code>.apk
./gradlew printVersion                 # versionName / versionCode / whether release signing is set up
```

Without signing configured, `assembleRelease` signs with the debug key and names the file
`budget-<name>-<code>-UNSIGNED-DEBUGKEY.apk` so it is obviously not upgrade-compatible.

### Backend config

A Gradle step reads `../config/supabase.json` (`url`, `publishableKey`) into `BuildConfig.SUPABASE_URL`
/ `BuildConfig.SUPABASE_KEY`; env vars `SUPABASE_URL` / `SUPABASE_PUBLISHABLE_KEY` override it. Empty
values build an app whose sign-in screen says "Backend not configured".

### Screenshots (headless)

```sh
./gradlew testDebugUnitTest --tests '*ScreenshotTest*' -Pscreenshots=/abs/output/dir
```

Robolectric (native graphics) renders Home, Month (+ category detail, + scrolled with the collapsed top
bar), Year, Net worth, Sheet (prompt, summary, month), Settings, the Add sheet, the sign-in screen and the widget (small/medium/large) at 412dp (cover) and 900dp (inner)
in light and dark, from synthetic data (`docs/fixtures/sample-backup.json` plus generated months and
net-worth history). Without `-Pscreenshots` those tests are skipped. Component previews for Android
Studio live in `ui/Previews.kt`. A selection is kept in `docs/screenshots/android-*.png`.

## Signing

Release signing reads, in order: env vars `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`,
`ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`, else the git-ignored `android/keystore.properties`:

```properties
storeFile=/absolute/path/to/budget-release.jks
storePassword=…
keyAlias=budget
keyPassword=…
```

**The release keystore is the app's permanent identity.** Every APK that should install over the
installed app must be signed with it. Never generate a new one for a build, never commit it (the root
`.gitignore` ignores `*.jks`, `*.keystore`, `keystore.properties`), and keep at least two backups
(e.g. a password manager attachment plus an offline copy) together with its passwords and alias.

### GitHub secrets

| Secret | Value |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | `base64 -w0 budget-release.jks` |
| `ANDROID_KEYSTORE_PASSWORD` | keystore password |
| `ANDROID_KEY_ALIAS` | key alias |
| `ANDROID_KEY_PASSWORD` | key password |

The workflows decode the keystore to `$RUNNER_TEMP`, never print secrets, and delete the file at the
end.

### Restoring signing on a new machine

1. Copy the keystore from your backup to a safe path outside the repo.
2. Create `android/keystore.properties` (above) or export the four env vars.
3. `./gradlew printVersion` must say `releaseSigned=true`.
4. Check the certificate matches earlier releases:
   `apksigner verify --print-certs app/build/outputs/apk/release/budget-*.apk` and compare the SHA-256
   digest with the one from a previous release.

## GitHub Actions

- `.github/workflows/android.yml`: on pushes/PRs touching `android/**`, `docs/fixtures/**` or
  `config/**`. Checks out with `fetch-depth: 0`, JDK 17, Gradle setup with caching, unit tests, lint
  (errors fail), `assembleRelease` (signed if the secrets exist, else the debug-key fallback), and
  uploads the APK as artifact `budget-<versionName>-<versionCode>.apk`.
- `.github/workflows/android-release.yml`: on tags `v*`. Fails if any signing secret is missing or the
  tag doesn't match `version.properties`, runs tests + lint, builds the signed release, verifies it with
  `apksigner verify --print-certs`, and attaches `budget-v<version>.apk` to a GitHub Release.

## Versioning

- `versionName`: human version in `android/version.properties`; bump it per release and tag `v<it>`.
- `versionCode`: `git rev-list --count HEAD`. It grows with every commit on `main`, so a build from a
  newer commit always has a higher code. CI must use `fetch-depth: 0` (a shallow clone would count 1).
  Builds from a side branch can have a lower count than `main`: install only `main`/tag builds over the
  primary app. If history is ever rewritten (squash), make sure the count still exceeds the last
  released code (add commits, or temporarily add an offset in `app/build.gradle.kts`).

## Upgrade in place

A new APK installs as an update only with: the same applicationId (`com.personal.budget`), the same
signing certificate, and a higher versionCode. Debug builds (`-debug.apk`, debug key) and
`-UNSIGNED-DEBUGKEY` releases **cannot** update a release-signed install. If an update is refused, check
those three things first; **do not uninstall** (that deletes local data, including unsynced changes).

Before relying on the app: install a signed build 1, add dummy data, install signed build 2 over it,
and confirm data, settings and the widget survive.

## Data migration strategy

- Room DB `budget.db`, version 1. Schema JSONs are committed in `android/app/schemas/`.
- Any schema change: bump `BudgetDatabase.VERSION`, add a `Migration` to `BudgetDatabase.MIGRATIONS`
  (all migrations ever shipped stay there, so 1→2→3 chains work), commit the new schema JSON, and extend
  `app/src/test/.../data/MigrationTest.kt` (MigrationTestHelper scaffold, currently opening v1 with all
  migrations). Destructive migration is never enabled.
- DataStore keys are never renamed; new keys get defaults.
- The widget receiver class name and provider XML stay stable so placed widgets survive updates.

## Backup and export

- **Settings → Data → Export backup**: the shared JSON format (docs/SYNC.md) through the Storage Access
  Framework (`CreateDocument`), no storage permission. Includes `net_worth_snapshots`.
- **Import backup** (`OpenDocument`): parse, plan the merge (categories matched by name and remapped
  everywhere, months/budgets by natural key, the rest by id, settings replaced, tombstones skipped,
  old `meal_*` keys ignored), show a preview ("This will add 6 months, 158 transactions …"), then apply.
  Imported rows are dirty and sync up; snapshots are written with source `import`, and a fresh net-worth
  snapshot is requested on the next sync.
- **Android backup**: `allowBackup` with rules that include the Room DB and app preferences and leave out
  auth tokens (`res/xml/data_extraction_rules.xml`, `backup_rules.xml`). A restored device keeps its data
  and needs one sign-in to resume syncing.

## Permissions

`INTERNET` and `ACCESS_NETWORK_STATE` are the only permissions the app requests. WorkManager (the 6 h background sync) merges in `WAKE_LOCK` and `RECEIVE_BOOT_COMPLETED` (install-time, no prompt); its `FOREGROUND_SERVICE` is removed. No runtime permission prompts. Haptics use View haptic
constants (no `VIBRATE`).

## Widget

Glance widget "Budget" (`widgets/BudgetWidget.kt`), responsive:

- small: leftover this month + **Add**;
- medium: + top 3 expense categories (by spend) with "left"/"over";
- large: + a 30-day net-worth sparkline and progress bars.

**Add** deep-links to `budget://add`, which opens the add-transaction sheet. Data comes from the same
Room database via `BudgetRepository`. The widget refreshes ~0.8 s after any local change and after a
sync that changed data, plus every 6 h (month rollover). Brand colours follow the launcher's light/dark.

Other integrations: static app shortcut **Add expense** (`budget://add`), deep link `budget://add`,
native splash (brand mark on the themed background), adaptive launcher icon (background, foreground,
monochrome on Android 13+), subtle haptics on save, closing a month and settling an IOU.

## Fold behaviour

Layout is chosen from the **window width**, never the device model:

| Width | Layout |
|---|---|
| < 600dp (cover screen, split screen) | floating glass pill nav (Home · Month · Year · Net worth · Sheet, items sized to the window) + round Add beside it; one pane; two-line table rows instead of narrow columns (text never shrinks); Sheet shows the unfold prompt |
| 600–1023dp (inner screen) | floating glass rail (76dp) with the brand mark and Add at the top; Month = list + category detail side by side; Year = tables + chart; Home = 2 columns; Net worth = history chart beside the accounts list |
| ≥ 1024dp | wide rail (220dp, lockup + Add button); Home = 3 columns; wider panes |

Window size changes (fold/unfold, rotation, split screen, freeform resize) are handled in place
(`configChanges`), orientation is never locked, and `resizeableActivity` is on. State that must survive:
the selected month, Month tab, selected category, filters, Year, Net-worth range, open editors and the
whole add-transaction draft live in activity-scoped ViewModels backed by `SavedStateHandle`, so
unfolding while a category is open simply reveals the list beside it, folding collapses back to the
detail, and process death restores it too.

## Fluid glass v2 (1.3.0)

Values from `docs/FLUID_GLASS_UI.md` (v2, shared with Bets) live in `ui/theme/Tokens.kt` (glass, fill,
thumb, blob tokens, radii 20/28/18/12/pill, spring-soft curve and durations).

- **Backdrop:** `AmbientBackdrop` behind the shell (and sign-in): page tone plus three radial blobs that
  drift over 36/44/52 s, sampled at ~12 fps (draw-only); static with "Remove animations". The drift
  holds still while a sheet, dialog or menu is open (`PauseAmbientDrift`), so nothing re-renders the
  blur under an overlay while it animates in.
- **Glass:** cards are translucent glass (hairline highlight + two-layer soft shadow). The floating nav,
  rail, Sheet tab bar and Add sheet blur what is behind them with Haze (RenderEffect) on Android 12+;
  below 12 (and in Robolectric renders) they use the opaque strong-glass tone. The Add sheet's blur is
  fixed (24dp); only its position and the scrim's own alpha animate, the scrim following the drag. Dialogs, menus and chart tooltips use that opaque tone too (their own window / busy content).
- **Chrome:** each screen starts with a large title (micro label + Barlow 40); the 52dp top bar is
  transparent until the title scrolls under, then a compact Inter 17 title fades in over a progressive
  fade, not a panel (`TopBarBackdrop`): page tint 92 → 84 % and a 24dp blur run solid behind the bar
  (status bar included) and ease out to nothing over 28dp below it, with no shadow or hairline. On
  Android 12+ the blur is masked with that fade; below 12 the page-tone gradient alone.
- **Gestures:** swipe a transaction row left to delete (arms at 96dp / 30 %, haptic tick, Undo toast;
  accessibility action "Delete"); drag the Add sheet's grabber/header down to dismiss (120dp or
  0.6 dp/ms); charts scrub with a glass tooltip and a haptic tick per step.
- **Sheet view:** the grid is one opaque, dense card; only the title and the tab bar are glass.
- The Glance widget is unchanged.

## Known limitations

- Backdrop blur needs Android 12+; older versions get the opaque glass tone (the spec's fallback).
  Robolectric screenshots can't render RenderEffect, so they show that fallback too.
- Live end-to-end sync is covered by `app/src/test/.../data/LiveSyncTest.kt`, which is skipped unless
  `BUDGET_LIVE_EMAIL` / `BUDGET_LIVE_PASSWORD` are set (never commit them). It signs in, pulls, pushes a
  transaction, edits a balance (checks the snapshot RPC moved net worth), pulls a row written over REST,
  tombstones, and restores everything:
  `BUDGET_LIVE_EMAIL=… BUDGET_LIVE_PASSWORD=… ./gradlew testDebugUnitTest --tests '*LiveSyncTest*' --rerun`.
- No realtime subscription on Android (sync is event + schedule driven, per SYNC.md).
- Widget: Glance can't use the bundled fonts, so it uses the system sans-serif.
- No emulator screenshots: visuals were reviewed through Robolectric renders.

## Planned features

- Biometric lock (optional), quick-add directly from the widget without opening the app.
- Notification when a category goes over budget.
- Realtime (websocket) updates while the app is open.
