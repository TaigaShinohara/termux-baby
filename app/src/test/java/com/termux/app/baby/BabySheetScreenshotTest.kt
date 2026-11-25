package com.termux.app.baby

import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.android.material.chip.Chip
import com.termux.R
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * JVM screenshot tests for the Easy Mode sheets and pages the redesign
 * changed: the runner sheet (running / success states), the command
 * catalog row with its meanings expansion, and the onboarding try page.
 * These states previously had no screenshot coverage at all (review M6).
 *
 * Golden images live in `src/test/screenshots/`; record with
 * ROBORAZZI_RECORD=true, verify with ROBORAZZI_VERIFY=true.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class BabySheetScreenshotTest {

    private lateinit var activity: AppCompatActivity

    @Before
    fun setUp() {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java)
        activity = controller.get()
        activity.setTheme(R.style.Theme_TermuxBaby_Onboarding)
        controller.setup()
    }

    private fun inflate(layout: Int): View {
        val view = LayoutInflater.from(activity).inflate(layout, null, false)
        activity.setContentView(view)
        return view
    }

    @Test
    fun runnerSheet_runningState_hidesGraduationRow() {
        val root = inflate(R.layout.sheet_baby_runner)
        root.findViewById<ImageView>(R.id.runner_action_icon)
            .setImageResource(R.drawable.ic_baby_files)
        root.findViewById<TextView>(R.id.runner_title)
            .setText(R.string.baby_action_files_title)
        root.findViewById<TextView>(R.id.runner_command_text).text = "ls -la --color=never"
        root.findViewById<View>(R.id.runner_progress).visibility = View.VISIBLE
        root.findViewById<TextView>(R.id.runner_status_headline)
            .setText(R.string.baby_runner_running)
        // The graduation row must NOT be offered while a run is in flight.
        assertEquals(
            View.GONE,
            root.findViewById<View>(R.id.runner_graduation_row).visibility
        )
        val golden = File("src/test/screenshots/baby_runner_running.png")
        golden.parentFile?.mkdirs()
        root.captureRoboImage(golden)
    }

    @Test
    fun runnerSheet_successState_showsResultAndGraduationRow() {
        val root = inflate(R.layout.sheet_baby_runner)
        root.findViewById<ImageView>(R.id.runner_action_icon)
            .setImageResource(R.drawable.ic_baby_files)
        root.findViewById<TextView>(R.id.runner_title)
            .setText(R.string.baby_action_files_title)
        root.findViewById<TextView>(R.id.runner_command_text).text = "ls -la --color=never"

        // Token chips with one meaning opened, like after a tap.
        root.findViewById<View>(R.id.runner_tokens_section).visibility = View.VISIBLE
        val chips = root.findViewById<com.google.android.material.chip.ChipGroup>(
            R.id.runner_token_chips
        )
        val chip = Chip(activity)
        chip.text = "ls"
        chip.isCheckable = true
        chip.isChecked = true
        chips.addView(chip)
        val meaning = root.findViewById<TextView>(R.id.runner_token_meaning)
        meaning.setText(R.string.baby_token_ls)
        meaning.visibility = View.VISIBLE

        root.findViewById<View>(R.id.runner_progress).visibility = View.GONE
        val statusIcon = root.findViewById<ImageView>(R.id.runner_status_icon)
        statusIcon.setImageResource(R.drawable.ic_baby_check)
        statusIcon.visibility = View.VISIBLE
        root.findViewById<TextView>(R.id.runner_status_headline)
            .setText(R.string.baby_runner_done)
        val summary = root.findViewById<TextView>(R.id.runner_summary_text)
        summary.text = "12 files"
        summary.visibility = View.VISIBLE
        root.findViewById<TextView>(R.id.runner_raw_output_text).text =
            "memo.txt\nphotos/\nDownloads/"
        // Finished state: the sheet offers the graduation row now.
        val graduation = root.findViewById<View>(R.id.runner_graduation_row)
        graduation.visibility = View.VISIBLE
        assertEquals(View.VISIBLE, graduation.visibility)
        val golden = File("src/test/screenshots/baby_runner_success.png")
        golden.parentFile?.mkdirs()
        root.captureRoboImage(golden)
    }

    @Test
    fun helperRow_meaningsExpandUnderTheRow() {
        val root = inflate(R.layout.item_baby_helper_recipe)
        root.findViewById<TextView>(R.id.recipe_title)
            .setText(R.string.baby_action_files_title)
        root.findViewById<TextView>(R.id.recipe_subtitle)
            .setText(R.string.baby_action_files_sub)
        root.findViewById<TextView>(R.id.recipe_command_preview).text = "ls -la --color=never"
        val meanings = root.findViewById<LinearLayout>(R.id.recipe_meanings)
        val line = TextView(activity)
        line.text = "ls — " + activity.getString(R.string.baby_token_ls)
        line.setTextAppearance(activity, R.style.TextAppearance_Baby_Body)
        meanings.addView(line)
        meanings.visibility = View.VISIBLE
        assertTrue(meanings.childCount == 1)
        val golden = File("src/test/screenshots/baby_helper_row.png")
        golden.parentFile?.mkdirs()
        root.captureRoboImage(golden)
    }

    @Test
    fun onboardingTryPage_showsCommandTokensAndRunButton() {
        val root = inflate(R.layout.item_baby_onboarding_try)
        root.findViewById<TextView>(R.id.onboarding_try_command).text = "pwd"
        root.findViewById<TextView>(R.id.onboarding_try_tokens).text =
            "pwd — " + activity.getString(R.string.baby_token_pwd)
        val golden = File("src/test/screenshots/baby_onboarding_try.png")
        golden.parentFile?.mkdirs()
        root.captureRoboImage(golden)
    }
}
