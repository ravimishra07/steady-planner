# Play Console — Data safety answers

Answers for Play Console → App content → Data safety, matched to the code at the
commit that added this file. If the code changes (a new SDK, a new event with
new kinds of data, a server), update this file, `docs/privacy-policy.md` and the
in-app Privacy page together.

Sources: Firebase "Prepare for Google Play's Data safety" page
(firebase.google.com/docs/android/play-data-disclosure) and Google Analytics
"Data disclosure for Google Play" (support.google.com/analytics/answer/11582702),
read 2026-10-07.

## What the code actually does

| Area | Behaviour | Where |
|---|---|---|
| Study data (plan, syllabus, sessions, revision, mocks, mistakes, settings) | Room + DataStore in app-private storage. Never sent anywhere. Excluded from cloud backup and device transfer (`allowBackup=false`, `data_extraction_rules.xml`). | `core/data` |
| Export / import | JSON file written to / read from a location the user picks via the system file picker. No upload. | `BackupRepository` |
| Analytics | Firebase Analytics, only when `google-services.json` was present at build time. Custom events carry enum names, counts and 0/1 flags only. | `core/common/Analytics.kt`, `app/analytics/FirebaseAppAnalytics.kt` |
| Crash reports | Firebase Crashlytics, same condition. | same |
| Advertising ID | Not collected: `google_analytics_adid_collection_enabled=false`, and the merged `AD_ID`, `ACCESS_ADSERVICES_AD_ID`, `ACCESS_ADSERVICES_ATTRIBUTION` permissions are removed. | `AndroidManifest.xml` |
| SSAID / ad personalisation | Off (`google_analytics_ssaid_collection_enabled=false`, `google_analytics_default_allow_ad_personalization_signals=false`). | `AndroidManifest.xml` |
| Opt-out | Settings → Privacy → "Share anonymous usage data" (default on) calls `setAnalyticsCollectionEnabled` and `setCrashlyticsCollectionEnabled`. | `AppContainer`, `SettingsStore` |
| Purchases | Google Play Billing. The app queries ownership of `pro_lifetime` and caches a yes/no locally. No server; purchase tokens are never sent anywhere by the app. | `ProRepository` |
| Notifications | Local alarms only. | `reminder/`, `focus/` |
| Account / sign-in | None. | — |

Custom events sent (§11.3): `onboarding_started`, `onboarding_completed`
(`mode`, `starting_point`), `plan_generated` (`mode`), `block_started` /
`block_completed` (`planned`, `is_revision`), `block_missed` (`count`),
`block_moved` / `block_dropped` (`activity`), `chapter_learned`,
`revision_completed` (`source`), `mock_logged` (`mock_type`, `is_edit`,
`count`), `paywall_viewed`, `purchase_started`, `purchase_completed`,
`restore_completed` (`found`), `widget_added` (`widget`), `export_done`.
No chapter ids, titles, scores, dates or free text are sent.

## Form answers

**Does your app collect or share any of the required user data types?** Yes.

**Is all of the user data collected by your app encrypted in transit?** Yes
(Firebase uses HTTPS).

**Do you provide a way for users to request that their data is deleted?**
Answer **No** unless Ravi sets up a request path. There is no account, so the
on-device data is deleted by uninstalling; Firebase data is keyed to a random
installation id we cannot look up from a request. If a deletion request path is
added (email + Firebase user-deletion API), switch this to Yes.
<!-- TODO(Ravi): decide. -->

### Data types

Declare each as **Collected**, **not Shared**, **processed ephemerally: No**,
**collection optional: Yes** (the user can turn it off in Settings → Privacy).

| Category → type | Purpose(s) to tick | Why |
|---|---|---|
| App activity → **App interactions** | Analytics | Custom events above; automatic sessions and screen views. |
| App info and performance → **Crash logs** | App functionality, Analytics | Crashlytics stack traces. |
| App info and performance → **Diagnostics** | App functionality, Analytics | Crashlytics device state; Analytics device/app version info. |
| Device or other IDs → **Device or other IDs** | Analytics | Firebase app-instance ID / installation ID and the Crashlytics installation UUID. Not the Advertising ID. |
| Location → **Approximate location** | Analytics | Google Analytics derives coarse location from the IP address. Google's disclosure page lists it. |
| Financial info → **Purchase history** | Analytics | Google Analytics automatically logs `in_app_purchase` events for Play purchases. |

Do **not** declare: personal info (name, email, phone), precise location,
messages, photos, files and docs, contacts, calendar, health, web browsing,
or "Financial info → payment info" (Google Play handles payment, not the app).

**Shared:** No. Firebase is a service provider processing data on the
developer's behalf, which Play does not count as sharing. Nothing is sold.

### Things not collected that the form may ask about

- Study data: stays on device → not "collected" in Play's sense.
- Exported backup file: user-initiated, written where the user chooses, never
  transmitted by the app → not collected.
- Purchases: payment handled by Google Play; the app holds only an ownership flag.

## Notes for Ravi

- These answers assume a build **with** `android/app/google-services.json`.
  A build without it sends nothing at all (Firebase never initialises).
- Target audience: declare 16+ / not designed for children; no ads.
- Privacy policy URL: host `docs/privacy-policy.md` and enter its URL here and
  in the store listing. Fill the `privacy@<domain>` TODO first.
