// Port of app/analytics/FirebaseAppAnalytics.kt.
// Stubbed: Firebase is never configured in the web clone, so createOrNoOp always returns Analytics.NoOp
// (exactly what Android does without google-services.json).
import { Analytics } from '../common.js';

export class FirebaseAppAnalytics {
  log(_event, _params) {}
  setCollectionEnabled(_enabled) {}
  /** Firebase when it is configured, otherwise a no-op. Never throws. */
  static createOrNoOp(_context) { return Analytics.NoOp; }
}
