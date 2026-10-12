# Development checkpoints

1. Project, Compose, Material 3 and Room configuration.
2. Entities, DAO, repositories and asset-based seed.
3. Atomic consumption operations and group Undo.
4. Today and quick entry navigation.
5. Pure weekly progress engine and tests.
6. Weekly screen.
7. Meal shortcuts, transaction and source attribution.
8. Date-based history and deletion.
9. Food, category and meal editors; preferences.
10. Pixabay provider, local image storage, Coil and WorkManager retry.
11. Visual polish, feedback, light/dark theme and adaptive pregnancy icon.
12. Validated JSON export/import via Android document picker.

## Build checkpoints

Validated on Windows with the installed Android Studio JDK, Android SDK 34, Gradle 8.7, AGP 8.5.2 and Kotlin 1.9.24.

- Foundation, entities, seed, repository and atomic consumption operations: `assembleDebug` passed; Room generated schema v1.
- Today, quick food/meal entry, navigation and progress engine: `assembleDebug testDebugUnitTest` passed.
- Weekly screen: `assembleDebug` passed.
- History and settings/editors: `assembleDebug` passed.
- Images, theme, icon and backup: combined build, unit tests, instrumented tests and lint run on Android 15 / API 35 emulator.

## Version 1.5: historical entry and goal overview

History now offers daily and weekly lists with fulfillment summaries, a calendar for opening previous dates (including empty days), historical food/meal quick entry, and day-to-week navigation. Daily progress includes met and unmet checklist goals derived from weeklyTarget; weekly history reuses the existing progress engine and the configured first weekday. Date/view changes reset scroll to the heading. Historical calculations explicitly use current goals/category membership; previous goal configurations are not reconstructed.

Consumption repositories/use cases accept an optional date, preserve selected localDate and create a timestamp for that local day in the user's zone. Meals retain one transaction and shared group/timestamp; future dates are rejected before inserts. Existing entries, Room schema v2 and JSON backup fields are unchanged. Version 1.5.0 (versionCode 7).

Validation: `assembleDebug` and `lintDebug` passed. Twenty-six unit tests passed, including historical daily fulfillment and day counting/custom week boundaries. Nine Room instrumentation tests passed, including historical food/meal dating, isolated group Undo, future-date rejection and existing history/backup behavior. The end-to-end historical UI test passed after adding scroll reset: calendar cancellation, two food entries for yesterday, daily fulfillment, the matching historical week, meal entry for yesterday and Undo preserving the food entries. Its temporary entries are removed in finally; user data/preferences remain intact.

## Version 1.4: own photographs

Food, category and meal editors include `Odaberi svoju sliku` alongside Pixabay replacement. Android's image-only picker grants access to the selected picture. Photos are decoded, oriented, scaled to at most 1600 pixels, copied into private storage and assigned with guards against concurrent edits. Cancellation and invalid files keep the original photo. Renaming preserves custom images; the image worker skips them. JSON backups embed referenced custom photos by content hash, deduplicate shared pictures and restore them locally before restoring rows. Existing v1/v2 backups remain supported, and Room schema remains v2.

`assembleDebug`, `lintDebug`, all 24 unit tests and 21 focused Android tests passed with zero failures/skips on API 35. Tests cover all three item types without networking, deletion of the original photo, local reopening, corrupt/unreadable files, portable backup restoration, image provider regression checks, consumption/meal/history behavior, and editor picker cancellation/selection plus rename persistence. The picker UI test supplies ActivityResult fixtures and restores original rows afterwards; existing live Pixabay button tests also pass. The real system picker was additionally opened and cancelled on the emulator, confirming selected-photo access without broad gallery permissions. The editor screenshot was refreshed. APK version 1.4.0 (versionCode 6).

## Version 1.3: user-requested photo replacement

Saved food, category and meal editors show the current photo and a `Dohvati drugu sliku` button with loading feedback. Each request selects an alternative from twenty cached Pixabay candidates for the saved English query, downloads it into persistent local storage, then updates image metadata with a concurrent-edit guard. The previous picture remains intact when offline, when no alternative exists, or if the item changes during the download. New names must be saved before replacement. No Room migration is required.

`assembleDebug`, all 20 unit tests and `lintDebug` passed. Thirteen focused Android tests passed on API 35 with zero failures/skips, covering candidate cycling/cache, a single-result search, all three entity types, failure preservation, translation and rename races, and actual editor buttons with live Pixabay downloads and bitmap decoding. The UI test restores original pictures afterwards. APK version 1.3.0 (versionCode 5).

## Version 1.2: complete initial catalog

The default seed and matching import now contain exactly the user's 84 food names across twelve diet categories, in the requested order. Poultry (Piletina / puretina) contains only chicken and turkey; Crveno meso contains Govedina, Junetina, Teletina, Svinjetina and Janjetina. Meals remain one additional navigation destination. All foods and categories have English image queries; category targets retain the previously approved lower bounds and day-counting rules.

`assembleDebug`, all **20 unit tests**, `lintDebug` and all **26 Android instrumented tests** passed on API 35: **46 tests, zero failures or skips**. The live image test downloaded/decoded photographs for the expanded active catalog. The APK is version 1.2.0 (versionCode 4); Room schema remains v2.

Fresh-install Room tests now verify 84 seeded foods. Unit tests compare seed names/order with the import, validate all category references/queries, and verify the exact poultry/red-meat split and eleven grains. A new instrumentation test initializes an old catalog and verifies automatic expansion to 84, alias-based renaming while preserving IDs and photographs, preserved goals and inactive choices, unchanged meal links/history, and no repeat seeding or goal reset on reopening.

Existing catalogs are upgraded once using seed-state marker 2. Known aliases include Orah → Orasi, Badem → Bademi, Lješnjak → Lješnjaci, Zob → Zobene pahuljice, Chia sjemenke → Chia and Lanene sjemenke → Mljeveni lan. Category slash spacing is normalized without creating new duplicate categories. User-added foods outside the supplied list remain present. Restoring a backup marks catalog initialization complete, preserving intentional empty/custom restores. The emulator's pre-test data was restored and automatically upgraded after the suite, removing test fixtures while retaining user data and the image cache.

## Version 1.1.1: translated image queries and one Meals destination

`assembleDebug`, all **20 unit tests**, `lintDebug` and all **25 Android instrumented tests** passed on API 35: **45 tests, zero failures or skips**. The APK is version 1.1.1 (versionCode 3); Room schema stays at v2.

Missing imageQuery values now use Croatian → English translation on the device through ML Kit. The translated query is saved before Pixabay search with guards against concurrent renames. Explicit English queries and existing photographs bypass translation. A translation failure leaves the row ready for background retry and never sends the original Croatian name as the fallback search. The translation model downloads automatically on its first use; recording food remains immediate.

Three new image tests verify query persistence before search, reuse for food/meal, failure isolation, concurrent renames, and live translation of Kruška and Juha od rajčice followed by real Pixabay downloads decoded as bitmaps. A new UI regression verifies one Meals card despite a real Jela category containing ordinary Food rows, newly saved Meal visibility, ordinary Food visibility in that same destination, and that saving a meal does not create a category. Case/whitespace variants of Jela share this destination and grid item keys separate meals from foods.

The emulator's database/files were backed up before this test run and restored afterward, preserving original food, meal, history and preference data. Downloaded photographs and the translation model remain available. The corrected APK remains installed with the app open.

## Version 1.1: imported category checklist

The user approved shared category goals, the lower bound of each supplied range, and counting every meal ingredient separately. The supplied plan has 12 categories and 39 food choices. Nuts count four distinct days, grains seven distinct days, and fruit requires two entries per day derived from its weekly target of fourteen.

Final validation: **20 unit tests and 21 Android instrumented tests passed**, with zero failures or skips. `assembleDebug`, `installDebug`, `testDebugUnitTest` and `lintDebug` passed; the final `connectedDebugAndroidTest` run passed on API 35 after installing the corrected APK.

New coverage includes mixed foods sharing one goal, meal ingredients counting independently, target-14 daily filtering, distinct-day counting, nonnegative remaining counts, twelve exact plan targets, duplicate/invalid plan rejection, v1 backup conversion, Room 1 → 2 schema validation with preserved meal sources/history, repeatable catalog merging that preserves IDs/images/entries/meal links, the category goal editor, bundled preview and external JSON import through the actual ViewModel/content resolver, closing the import dialog, and category-shortcut food entry with Undo. Live authenticated image tests ran with the expanded catalog and verified downloaded bitmaps and availability through an offline store.

Manual checks installed the APK over the previous version, migrated the user's original database, previewed and applied the built-in plan, and checked Today's four remaining categories (fruit 2, vegetables 1, dairy 1, grains 1). A null-state crash in the disappearing import preview was fixed by capturing its immutable plan value; the import UI test now covers that transition. Quick-entry UI automation now scrolls through the expanded category grid to Meals.

Final screenshots were refreshed for both themes and weekly category progress. With Wi-Fi and mobile data disabled, the app was force-stopped and relaunched; all four daily category photographs remained visible. Both network connections and the original dark preference were restored afterward.

The emulator's original data was backed up before migration. After testing, the imported catalog was restored without test fixtures or consumption entries, retaining original Monday/dark preferences and cached photographs. The app remains installed and the emulator remains running. The reusable plan is in `imports/lucijin-prehrambeni-popis.json`; it merges metadata rather than replacing history. The shipped debug APK is version 1.1.0 (versionCode 2).

## Version 1.0 verification (before the category-goal change)

Final results, 5 October 2026: **13 unit tests + 18 Android instrumented tests passed**, with no failures or skipped tests. The combined `assembleDebug testDebugUnitTest connectedDebugAndroidTest lintDebug` run passed on the user's Android 15 / API 35 emulator. Lint has zero errors; toolchain/library update recommendations and minor informational/resource warnings remain.

The supplied API key is configured in ignored local `secrets.properties`, and the final debug APK contains that configuration. The APK was installed and visually inspected on API 35. Croatian dates/text, Today, Weekly, navigation and explicit light/dark preferences were checked; screenshots with actual photographs are in `docs/screenshots/`. A startup navigation crash discovered during verification was fixed by showing navigation and the FAB only after initial data loading succeeds.

Manual Android document-picker export produced a valid JSON file with the original 5 categories, 9 foods, 3 meals, 5 meal-food links and preferences. Importing that same file completed successfully. With both Wi-Fi and mobile data disabled, the app was force-stopped and restarted: the stored apple photograph remained visible, adding an apple updated today's checklist immediately, and Undo restored the original empty history. Both network connections were then restored. Test fixtures were removed and the original Monday-start/dark-theme preferences were restored; the emulator remains running with the app open.

Unit tests cover exact target-7 daily filtering, repeated same-day weekly counting, meal contributions, zero-target exclusion, cycle boundaries/year transition, custom first weekday, backup round trips, broken references, group consistency, invalid/null dates, decimal targets and integer overflow.

Room instrumentation covers seed initialization, local timezone date capture, atomic meal groups, Undo isolation from direct entries and other meal taps, orange juice source attribution, FK transaction rollback, individual deletion, archiving and database/preference backup restore.

Image instrumentation uses intercepted HTTP responses to verify encoded/safe queries, 24-hour response reuse, no requests without a key, persistent image files across new store instances, no duplicate downloads and recovery from an expired restored URL.

Two authenticated Pixabay tests verify that every active seed category, food and meal receives a decodable photograph, that saved URLs stay unchanged and local image files survive a new offline image-store instance. They also verify automatic background image resolution after saving a new food and a new meal.

Compose instrumentation covers all four main tabs, category navigation and food editor, actual quick food/meal entry and snackbar Undo while preserving a separate manually recorded food, category/food/meal creation, meal membership, target changes to zero, activation toggles, individual and grouped history deletion, a Wednesday cycle start and light/dark preferences. Instrumentation runs leave the application installed so its data and image cache survive test cleanup.

## Limits

- Automated and manual checks cover the listed workflows on API 35; other physical devices and Android versions were not exercised. Pixabay response/download failure behavior is covered with intercepted HTTP responses; a prolonged real service outage was not simulated.
- Version 1.1 uses schema v2 with explicit, tested migration 1 → 2. Both schemas are included, and destructive fallback is absent.
- Dependencies are explicitly pinned to a compatible, tested toolchain. Lint may report newer SDK/library versions; these are update recommendations, not compilation errors.
- JSON copies include image URLs, not image bytes. Device-local persistent photographs and Coil caches are separate from the backup. An unavailable URL is cleared only when its local image is missing and the provider responds 403/404/410; background retry can then resolve it again. Existing local photographs stay unchanged.
- Room and DataStore are separate stores. Normal import failures roll back Room and restore preferences; a forced process termination during import cannot guarantee an atomic cross-store commit.
