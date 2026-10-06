# Zdrava Lucija

Native Android, offline-first. UI → ViewModel/StateFlow → use cases → repositories → Room.
Preferences use DataStore. Only image resolution and user-requested photo replacement use the network.

## Invariants
- Category has one integer `weeklyTarget >= 0` and `countingMode` ENTRIES or DAYS. Food goals have been retired following the user's revised specification.
- Every consumption row contributes one to its category in ENTRIES mode, including repeated same-day rows and each meal ingredient. DAYS mode counts distinct local dates, with a maximum target of seven.
- Today remaining: active categories whose positive weekly target is divisible by seven. Required count is derived as target / 7 for ENTRIES or one for DAYS, minus today's count, clamped to zero.
- Week remaining: active categories with positive target and cycle count below target.
- Cycle is the most recent selected weekday through the following six days; Monday by default.
- Direct tap inserts one row with a unique group UUID and no meal source.
- Meal tap inserts its unique food membership in one Room transaction, sharing a UUID, timestamp and local date.
- Undo deletes by UUID only. History supports deletion of one row or a whole group.
- Date is stored at entry time in the device's timezone; midnight/resume/timezone changes refresh the viewed date.
- Archive foods/categories/meals rather than deleting referenced metadata, preserving history.
- JSON backup restore validates all relationships before replacing local data atomically; preferences are included. Format v2 includes category goals; v1 imports sum active food goals into categories.
- Diet plan import has a distinct `kind: dietPlan`, validates the complete catalog before a Room transaction, merges by trimmed case-insensitive names, preserves IDs/images/history/meals/preferences, and is repeatable. It never creates consumption rows.

## Structure
`data/`: Room, preferences, backup, JSON seed and image provider.
`domain/`: pure cycle/progress rules and small use cases.
`ui/`: navigation, screens, editors, reusable visual cards and theme.

No DI framework: a small application container owns database/repositories. WorkManager resolves missing images with network constraints and exponential backoff. Saved URLs never change during rendering. Images are downloaded into private persistent storage to avoid permanent Pixabay hotlinking; Coil adds memory and disk caches. JSON backups retain image provenance, and downloads can be rebuilt after restore.

Photo replacement is an explicit editor action for saved foods, categories and meals. The provider caches twenty candidate URLs per English query for 24 hours and advances past the current URL on each request, wrapping through alternatives. `ReplaceImageUseCase` downloads first, then updates only the image fields with a name, query and previous-URL guard. Failures, missing alternatives and concurrent edits keep the previous picture; targets, history and meal membership are untouched. Editor loading state prevents duplicate requests and saving over a pending replacement. Room schema remains v2.

User-selected photos use the Activity `PickVisualMedia` contract (system picker, with document picker fallback). The chosen URI is immediately read, decoded with orientation handling, reduced to at most 1600 pixels per side, and stored as a private JPEG keyed by its content hash. `local-image://<sha256>.jpg` identifies the private image through the existing nullable imageUrl field; arbitrary file/content paths are never accepted in backup data. `UploadImageUseCase` uses the same compare-and-update guards as provider replacement. Rename preserves custom images; background image search skips them. No network or broad media/storage permission is required.

Backup v2 has an optional `customImages` map of referenced local identifiers to Base64 JPEG data. Export deduplicates shared images; validation checks bounded data and hashes; restore validates/installs pictures before changing database rows. Older v1/v2 backups without the map remain compatible. Import limit is 100 MB to accommodate embedded photos. Room schema remains v2.

Missing image queries are translated from Croatian to English by `NameTranslator` using on-device ML Kit Translation. The first translation downloads its language model; the original name is not sent to a translation backend. The English `imageQuery` is saved with a name/query guard before Pixabay search, so rename races cannot attach stale translations. Explicit English seed/import queries bypass translation. Failed translation retries through the image worker without falling back to Croatian search. Existing photos continue using their saved URL and do not require translation.

The Add Food picker has one Meals destination. Real categories named Jela (case/whitespace insensitive) share that destination with Meal records. Their ordinary Food records are also included, with distinct grid keys; no category or history is deleted to repair the duplicate navigation card.

The complete default catalog is defined in seed assets and the matching diet-plan JSON: 84 foods in twelve diet categories, with poultry separate from red meat, plus the Meals navigation. Seed-state row 1 marks initialization; row 2 marks the complete catalog version. New installs set both after seeding. Older installs merge the bundled catalog once before exposing the snapshot, preserving existing goals, activation choices, images, meal links and entries. Food aliases resolve old singular/alternate names to canonical names while retaining their IDs. Category matching normalizes slash spacing. Backup restore sets both markers so intentionally restored empty/custom datasets are respected. No schema change is needed for these metadata rows.

## Phase validation
See DEVELOPMENT.md for build checkpoints and final checks. Database versions 1 and 2 export schemas; migration 1 → 2 has an instrumented schema/data preservation test. The Food weeklyTarget column remains solely for compatibility and is cleared during migration. There is no destructive migration fallback.
