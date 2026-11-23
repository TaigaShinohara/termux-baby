package com.termux.app.baby

import android.os.Looper
import android.view.View
import androidx.viewpager.widget.ViewPager
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.android.material.button.MaterialButton
import com.termux.R
import com.termux.app.baby.onboarding.BabyOnboardingActivity
import com.termux.app.baby.onboarding.BabyOnboardingPagerAdapter
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * JVM screenshot + transition test for the fork's onboarding entry screen
 * (`activity_baby_onboarding.xml`, 7 pages).
 *
 * Part of the pre-delivery UI verification standard (no emulator).
 * Golden images live in `src/test/screenshots/`; record with
 * ROBORAZZI_RECORD=true, verify with ROBORAZZI_VERIFY=true.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class BabyOnboardingScreenshotTest {

    @Test
    fun onboarding_firstPageRenders_nextAdvances_skipCompletes() {
        val controller = Robolectric.buildActivity(BabyOnboardingActivity::class.java)
        val activity = controller.setup().get()
        shadowOf(Looper.getMainLooper()).idle()

        val root = activity.findViewById<View>(R.id.baby_onboarding_root)
        assertTrue("onboarding root has no height", root.height > 0)

        val pager = activity.findViewById<ViewPager>(R.id.baby_onboarding_pager)
        assertEquals(BabyOnboardingPagerAdapter.PAGE_COUNT, pager.adapter?.count)
        assertEquals(BabyOnboardingPagerAdapter.PAGE_HOOK, pager.currentItem)
        assertFalse(BabyPreferences.isOnboardingCompleted(activity))

        val golden = File("src/test/screenshots/baby_onboarding_page1.png")
        golden.parentFile?.mkdirs()
        root.captureRoboImage(golden)

        // Transition: Next moves to page 2.
        activity.findViewById<MaterialButton>(R.id.baby_onboarding_next_button).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(BabyOnboardingPagerAdapter.PAGE_CHOICE, pager.currentItem)

        // Transition: Skip completes onboarding and finishes the activity.
        activity.findViewById<MaterialButton>(R.id.baby_onboarding_skip_button).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(BabyPreferences.isOnboardingCompleted(activity))
        assertTrue(activity.isFinishing)
    }
}
