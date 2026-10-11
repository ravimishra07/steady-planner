# RevenueCat (Pro) — Play Store V1

No backend, no login. Everything is on the phone; RevenueCat is the only server, and only for purchases.

## How it works

- RevenueCat runs with an anonymous app user id, configured after the first frame (`ProRepository.start()`), never in `Application.onCreate`.
- Pro = the `neet_tracker_pro` entitlement is active (product `lifetime`, one-time, ₹49). Cached in the `pro` DataStore so it works offline; each CustomerInfo rewrites it (refund → Pro off).
- Paywall = RevenueCat's `PaywallDialog` (`purchases-ui`), showing the paywall designed in the dashboard for the current offering. No custom paywall UI in the app.
- Restore (Settings → Restore, or the paywall's own button) restores from the Google Play account: reinstall or new phone gets Pro back.
- Firebase Analytics + Crashlytics stay as before: only active with `google-services.json`, behind Settings → share usage data.

## Setup

1. `android/local.properties` (gitignored):
   ```
   revenuecat.testApiKey=test_...   # debug builds: RevenueCat Test Store, no Play needed
   revenuecat.apiKey=goog_...       # release builds: the Play Store app's key
   ```
   Release never uses the test key. Without a key the app builds and runs; the paywall stays closed.
2. Play Console: create the in-app product(s); enable the Google Play Developer API; create a service account with financial permissions and upload its JSON to RevenueCat.
3. RevenueCat dashboard (project neet-tracker): Play Store app → public SDK key (above); product `lifetime`; entitlement `neet_tracker_pro` with `lifetime` attached; offering `default` (current) with a Lifetime package; build and publish its paywall. Mirror `lifetime` in the Test Store for debug testing.
4. Test on device with a Play license tester: open paywall, buy, Restore, reinstall → Restore.

## Code map

| What | Where |
|---|---|
| Configure, entitlement cache, restore | `android/core/data/.../ProRepository.kt` |
| Paywall | `android/app/.../paywall/PaywallHost.kt` |
| API key → BuildConfig | `android/app/build.gradle.kts` (`REVENUECAT_API_KEY`) |

## Play Store paperwork

- Data Safety: declare purchase history and an app-scoped identifier shared with RevenueCat (and Firebase data if `google-services.json` ships). No account, so no account-deletion requirement.
- `docs/privacy-policy.md`: replace the Play Billing wording with RevenueCat as the purchase processor.
