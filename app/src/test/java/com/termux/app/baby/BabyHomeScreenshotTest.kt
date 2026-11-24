package com.termux.app.baby

import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
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
 * (`view_baby_home.xml` + [BabyModeController] dynamic grid binding).
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

    private fun collectCards(container: ViewGroup, into: MutableList<View>) {
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            if (child.id == R.id.baby_card_title || child is com.google.android.material.card.MaterialCardView) {
                if (child is com.google.android.material.card.MaterialCardView) into.add(child)
            }
            if (child is ViewGroup) collectCards(child, into)
        }
    }

    @Test
    fun babyHome_rendersPinnedCardsWithCommandsAndCta() {
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

        // The grid is built dynamically from the pinned ids: default pins
        // resolve to 6 cards, each showing its REAL command (the redesign's
        // core promise — commands are visible, not hidden).
        val grid = root.findViewById<LinearLayout>(R.id.baby_action_grid)
        val cards = mutableListOf<View>()
        collectCards(grid, cards)
        val pinnedIds = BabyPreferences.getPinnedIds(activity)
        assertEquals(pinnedIds.size, cards.size)
        assertTrue("expected at least 6 default pinned cards", cards.size >= 6)

        val firstCard = cards[0]
        val firstAction = BabyActionCatalog.findById(pinnedIds[0])!!
        val title = firstCard.findViewById<TextView>(R.id.baby_card_title)
        assertEquals(
            activity.getString(firstAction.titleResId),
            title.text.toString()
        )
        val command = firstCard.findViewById<TextView>(R.id.baby_card_command)
        assertEquals(View.VISIBLE, command.visibility)
        assertEquals(
            BabyActionCatalog.getDisplayCommandPreview(firstAction),
            command.text.toString()
        )

        // Primary CTA is present and labelled (the command catalog).
        val cta = root.findViewById<TextView>(R.id.baby_home_find_action_button)
        assertEquals(activity.getString(R.string.baby_home_find_action), cta.text.toString())

        val golden = File("src/test/screenshots/baby_home.png")
        golden.parentFile?.mkdirs()
        root.captureRoboImage(golden)
    }
}
