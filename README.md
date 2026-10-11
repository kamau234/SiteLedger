# SiteLedger

Offline-first construction expense tracker for Android (V1, single user). Built for Kenyan
site work: every amount is whole Kenyan shillings, and the app is fully usable with no network.

## Highlights

- **Local-first.** Room is the single source of truth for the UI. Every entry is saved on the
  phone immediately and works offline indefinitely.
- **Durable sync.** Business changes and their sync-outbox entries are written in one database
  transaction, so nothing is ever lost between "saved" and "uploaded". WorkManager retries
  network-constrained, parent rows upload before children, uploads are idempotent, deletes are
  tombstones that never resurrect, and conflicted records are never silently overwritten —
  the user chooses "Retry" or "Keep my local version".
- **Money is exact.** All amounts are `Long` whole shillings (never `Float`/`Double`);
  quantities are stored as thousandths (`quantity_milli`).
- **Reports.** Project financial summary, expense ledger, category breakdown, period report and
  project comparison — exported offline as PDF or Excel (XLSX) to any location you pick.
  Budget ≠ revenue and remaining ≠ profit; projects crossing 80% of budget (configurable) are
  flagged, and the zero-budget state is handled explicitly.
- **Receipts** attach to expenses, stay on the phone until sync, and upload to a private
  Storage bucket at `{user_uuid}/{project_uuid}/{expense_uuid}.jpg`.

## Requirements

- JDK 17 (Android Studio's bundled JBR works: `JAVA_HOME` → Android Studio `jbr`)
- Android SDK with API 37 build tools; minSdk 26
- Gradle wrapper is included (Gradle 9.8.1, AGP 9.4.1, Kotlin 2.3.21)

## Build & run (local-only mode)

Out of the box the app builds in **local-only mode** — no cloud account, everything on the
phone. Just build and install:

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Default expense categories (Equipment, Labour, Materials, Other, Permits, Transport) are
created on first use.

## Connecting Supabase (cloud sync + sign-in)

1. Create a Supabase project, then set the **project URL** and **publishable (anon) key** in
   `local.properties` (or as environment variables):

   ```properties
   supabase.url=https://YOUR-PROJECT-REF.supabase.co
   supabase.key=YOUR-PUBLISHABLE-KEY
   ```

   The app never embeds or requires a service-role key — the publishable key only ever grants
   what RLS allows.

2. Apply the schema. With the Supabase CLI:

   ```bash
   supabase link --project-ref YOUR-PROJECT-REF
   supabase db push        # applies supabase/migrations/001..005
   ```

   Migrations: `001_helpers` (triggers), `002_projects`, `003_categories_and_suppliers`,
   `004_expenses`, `005_storage_and_sync` (private `receipts` bucket + policies). Optional
   demo data: `supabase/seed.sql`.

3. Rebuild. The login screen now appears; create an account with the in-app sign-up
   (or sign in with an existing email + password). Existing local records
   are kept and uploaded after sign-in; a fresh cloud account pulls down before seeding
   defaults so devices never duplicate categories.

Security model: Row Level Security scopes every table to `auth.uid()`, the `receipts` bucket is
private with per-user path policies, and `updated_at` is server-stamped. Cross-user access is
denied server-side, not just hidden in the UI.

## Tests

```bash
./gradlew :app:testDebugUnitTest          # 44 unit tests: money math, validation,
                                          # report periods, XLSX/PDF content, comparison
./gradlew :app:connectedDebugAndroidTest  # 8 Room tests: outbox ordering, retry semantics,
                                          # tombstones, breakdowns (needs a device/emulator)
```

## Project structure

```
app/src/main/java/com/siteledger/app/
  core/         design system, database, common utils (money, ids, clock, connectivity)
  domain/       models, repository interfaces, use cases, validation
  data/         Room entities/DAOs, repository implementations, Supabase remote, sessions
  sync/         outbox writer, sync engine, pull merger, WorkManager scheduling
  reports/      report composer + PDF/XLSX writers
  feature/      login, dashboard, projects, expenses, suppliers, categories, reports,
                sync status, settings
  navigation/   routes and the main scaffold
supabase/       config.toml, versioned SQL migrations, seed
```
