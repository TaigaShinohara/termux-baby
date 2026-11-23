package com.termux.app.baby

import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.github.takahirom.roborazzi.captureRoboImage
import com.termux.R
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * JVM screenshot test for the fork's Easy Mode home screen
 * (`view_baby_home.xml` + [BabyModeController] card binding).
 *
 * Part of the pre-delivery UI verification standard (no emulator):
 * catches layout collapse / blank screens on the JVM before delivery.
 * Golden images live in `src/test/screenshots/`; record with
 * ROBORAZZI_RECORD=true, verify with ROBORAZZI_VERIFY=true.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class BabyHomeScreenshotTest {

    private var controller: BabyModeController? = null

    @After
    fun tearDown() {
        controller?.destroy()
        controller = null
    }

    @Test
    fun babyHome_rendersSixActionCardsAndCta() {
        val activityController = Robolectric.buildActivity(AppCompatActivity::class.java)
        val activity = activityController.get()
        activity.setTheme(R.style.Theme_TermuxBaby_Onboarding)
        activityController.setup()

        val root = LayoutInflater.from(activity)
            .inflate(R.layout.view_baby_home, null, false)
        activity.setContentView(root)

        var proModeCommand: String? = "unset"
        val babyController = BabyModeController(activity, root, object : BabyModeController.HostCallbacks {
            override fun onSwitchToProMode(optionalCommandToRun: String?) {
                proModeCommand = optionalCommandToRun
            }

            override fun onOpenSettings() {}
        })
        controller = babyController
        babyController.showBabyHome()
        shadowOf(Looper.getMainLooper()).idle()

        // Layout must not collapse (the failure mode this standard exists to catch).
        assertTrue("baby home root has no height", root.height > 0)
        assertEquals(View.VISIBLE, root.visibility)

        // All six home action cards are bound with real titles from the catalog.
        val actions = BabyActionCatalog.getHomeActions()
        assertEquals(6, actions.size)
        val cardIds = intArrayOf(
            R.id.baby_card_0, R.id.baby_card_1, R.id.baby_card_2,
            R.id.baby_card_3, R.id.baby_card_4, R.id.baby_card_5
        )
        for ((index, cardId) in cardIds.withIndex()) {
            val card = root.findViewById<View>(cardId)
            val title = card.findViewById<TextView>(R.id.baby_card_title)
            assertEquals(
                activity.getString(actions[index].titleResId),
                title.text.toString()
            )
        }

        // Primary CTA is present and labelled.
        val cta = root.findViewById<TextView>(R.id.baby_home_find_action_button)
        assertEquals(activity.getString(R.string.baby_home_find_action), cta.text.toString())

        val golden = File("src/test/screenshots/baby_home.png")
        golden.parentFile?.mkdirs()
        root.captureRoboImage(golden)
    }
}
