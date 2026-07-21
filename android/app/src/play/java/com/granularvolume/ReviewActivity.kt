package com.granularvolume

import android.app.Activity
import android.os.Bundle
import com.granularvolume.util.ReviewHelper

/**
 * A no-UI, translucent activity whose only job is to host Google Play's in-app
 * review dialog for tile-driven users who rarely reopen [MainActivity].
 *
 * The Play review API requires a live, resumed Activity to attach its dialog to,
 * and a Quick Settings [com.granularvolume.service.GranularVolumeTileService]
 * cannot host one directly. This activity is launched (via startActivityAndCollapse)
 * only once the tile-usage threshold has already been checked in
 * [ReviewHelper.reviewIntentIfDue], so it does no gating of its own — it simply
 * runs the flow and finishes, whether or not Google actually chooses to show a card.
 *
 * Play-flavor only: it references the proprietary review library and therefore has
 * no counterpart in the F-Droid build (see repo issue #1).
 */
class ReviewActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Fully transparent, no content view. Kick off the flow and get out of the
        // way; finish() the moment Google reports it is done (shown or not).
        ReviewHelper.launchFlow(this) { finish() }
    }
}
