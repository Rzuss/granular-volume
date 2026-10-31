package com.granularvolume.debugtools

import android.app.Activity
import android.os.Bundle
import com.granularvolume.util.ControlLive

/**
 * Debug builds only: says "the review card is up" or "it has gone", exactly as ReviewHelper does,
 * so the harness can read the dial's answer. Google shows the real card only to a Play install.
 */
class ReviewCardSimActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ControlLive.onReviewCard?.invoke(intent.getBooleanExtra("showing", false))
        finish()
    }
}
