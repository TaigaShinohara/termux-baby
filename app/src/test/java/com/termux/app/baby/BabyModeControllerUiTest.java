package com.termux.app.baby;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.termux.R;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/**
 * Robolectric UI-logic tests for the fork's Easy Mode home controller:
 * status badge states and Baby/Pro visibility switching.
 * Part of the pre-delivery UI verification standard (no emulator).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class BabyModeControllerUiTest {

    private ActivityController<AppCompatActivity> activityController;
    private AppCompatActivity activity;
    private View root;
    private BabyModeController controller;
    private String lastProModeCommand;

    @Before
    public void setUp() {
        activityController = Robolectric.buildActivity(AppCompatActivity.class);
        activity = activityController.get();
        activity.setTheme(R.style.Theme_TermuxBaby_Onboarding);
        activityController.setup();

        root = LayoutInflater.from(activity).inflate(R.layout.view_baby_home, null, false);
        activity.setContentView(root);

        controller = new BabyModeController(activity, root, new BabyModeController.HostCallbacks() {
            @Override
            public void onSwitchToProMode(String optionalCommandToRun) {
                lastProModeCommand = optionalCommandToRun;
            }

            @Override
            public void onOpenSettings() {
            }
        });
    }

    @After
    public void tearDown() {
        if (controller != null) controller.destroy();
        if (activityController != null) activityController.close();
    }

    @Test
    public void statusBadge_showsPreparingUntilEnvironmentReady() {
        View headerRow = root.findViewById(R.id.baby_home_header_row);
        TextView status = root.findViewById(R.id.baby_home_status_text);
        // No bootstrap exists in the Robolectric sandbox, so it starts as
        // "preparing" with the banner visible.
        Assert.assertEquals(View.VISIBLE, headerRow.getVisibility());
        Assert.assertEquals(
            activity.getString(R.string.baby_home_status_preparing),
            status.getText().toString());

        // Once ready, the banner leaves the home entirely (redesign: no
        // permanent greeting/status noise on a shortcut surface).
        controller.setEnvironmentReady(true);
        Assert.assertEquals(View.GONE, headerRow.getVisibility());
    }

    @Test
    public void showAndHideBabyHome_togglesVisibility() {
        controller.showBabyHome();
        Assert.assertEquals(View.VISIBLE, root.getVisibility());
        Assert.assertTrue(controller.isBabyHomeVisible());

        controller.hideBabyHome();
        Assert.assertEquals(View.GONE, root.getVisibility());
        Assert.assertFalse(controller.isBabyHomeVisible());
    }

    // --- Learning-state recording (review fix F1) ---

    private BabyCommandExecutor testExecutor;
    private java.io.File testPrefix;
    private java.io.File testHome;

    private BabyRunSession runWithTestExecutor(String command, String sourceId) throws Exception {
        if (testExecutor == null) {
            java.nio.file.Path base = java.nio.file.Files.createTempDirectory("baby-ui-test");
            testPrefix = new java.io.File(base.toFile(), "prefix");
            testHome = new java.io.File(base.toFile(), "home");
            testPrefix.mkdirs();
            testHome.mkdirs();
            // Null main handler: callbacks run inline on the executor thread.
            testExecutor = new BabyCommandExecutor(
                java.util.concurrent.Executors.newCachedThreadPool(), null, testPrefix, testHome);
        }
        BabyRunSessionRegistry registry = new BabyRunSessionRegistry();
        BabyRunSession session = registry.create(
            "Test", R.drawable.ic_baby_sparkle, command,
            BabyAction.OutputFormat.GENERIC, 30, "Test", sourceId);
        session.start(testExecutor);
        return session;
    }

    private static void waitForState(BabyRunSession session, BabyRunSession.State expected)
        throws Exception {
        long deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline) {
            if (session.getState() == expected) return;
            Thread.sleep(50);
        }
        throw new AssertionError("session did not reach " + expected);
    }

    private void clearBabyPrefs() {
        activity.getSharedPreferences("termux_baby_prefs", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit();
    }

    @Test
    public void completion_success_countsTowardMastery() throws Exception {
        clearBabyPrefs();
        BabyRunSession session = runWithTestExecutor("true", "see_files");
        waitForState(session, BabyRunSession.State.SUCCESS);
        Assert.assertTrue(controller.recordCompletion(session));
        Assert.assertEquals(1, BabyPreferences.getRunCount(activity, "see_files"));
        Assert.assertTrue(BabyPreferences.getLastResultOk(activity, "see_files"));
        Assert.assertNotNull(BabyPreferences.getLastResultSummary(activity, "see_files"));
        if (testExecutor != null) testExecutor.shutdown();
    }

    @Test
    public void completion_failure_recordsResultButDoesNotCount() throws Exception {
        clearBabyPrefs();
        BabyRunSession session = runWithTestExecutor("exit 3", "see_files");
        waitForState(session, BabyRunSession.State.FAILED);
        Assert.assertTrue(controller.recordCompletion(session));
        Assert.assertEquals(0, BabyPreferences.getRunCount(activity, "see_files"));
        Assert.assertFalse(BabyPreferences.getLastResultOk(activity, "see_files"));
        Assert.assertNotNull(BabyPreferences.getLastResultSummary(activity, "see_files"));
        if (testExecutor != null) testExecutor.shutdown();
    }

    @Test
    public void completion_cancelled_recordsNothing() throws Exception {
        clearBabyPrefs();
        BabyRunSession session = runWithTestExecutor("while :; do :; done", "see_files");
        Thread.sleep(300);
        session.cancel();
        waitForState(session, BabyRunSession.State.CANCELLED);
        Assert.assertFalse(controller.recordCompletion(session));
        Assert.assertEquals(0, BabyPreferences.getRunCount(activity, "see_files"));
        Assert.assertNull(BabyPreferences.getLastResultSummary(activity, "see_files"));
        if (testExecutor != null) testExecutor.shutdown();
    }

    // --- Shortcut path (review fix F2) ---

    private View findCardByCommand(String commandPrefix) {
        android.widget.LinearLayout grid = root.findViewById(R.id.baby_action_grid);
        java.util.List<View> cards = new java.util.ArrayList<>();
        collectCards(grid, cards);
        for (View card : cards) {
            TextView command = card.findViewById(R.id.baby_card_command);
            if (command != null && command.getText().toString().startsWith(commandPrefix)) {
                return card;
            }
        }
        return null;
    }

    private static void collectCards(android.view.ViewGroup group, java.util.List<View> into) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof com.google.android.material.card.MaterialCardView) {
                into.add(child);
            }
            if (child instanceof android.view.ViewGroup) {
                collectCards((android.view.ViewGroup) child, into);
            }
        }
    }

    @Test
    public void masteredCard_tapRunsDirectlyWithoutSheet() {
        clearBabyPrefs();
        // see_files mastered (3 successful runs) and pinned.
        for (int i = 0; i < 3; i++) {
            BabyPreferences.incrementRunCount(activity, "see_files");
        }
        BabyPreferences.setPinnedIds(activity,
            java.util.Collections.singletonList("see_files"));
        controller.refreshState();

        View card = findCardByCommand("ls");
        Assert.assertNotNull("see_files card not found", card);
        card.performClick();

        // A run session started right away, with no sheet/dialog involved.
        boolean started = false;
        for (BabyRunSession s : controller.getRunSessionRegistry().getSessions()) {
            if ("see_files".equals(s.sourceId)) started = true;
        }
        Assert.assertTrue("mastered card tap should start a run session", started);
        android.app.Dialog latest = org.robolectric.shadows.ShadowDialog.getLatestDialog();
        Assert.assertTrue("no sheet should be showing", latest == null || !latest.isShowing());
    }

    @Test
    public void unmasteredCard_tapOpensTheRunnerSheet() {
        clearBabyPrefs();
        BabyPreferences.setPinnedIds(activity,
            java.util.Collections.singletonList("see_files"));
        controller.refreshState();

        View card = findCardByCommand("ls");
        Assert.assertNotNull("see_files card not found", card);
        card.performClick();

        android.app.Dialog latest = org.robolectric.shadows.ShadowDialog.getLatestDialog();
        Assert.assertNotNull("unmastered tap should open the runner sheet", latest);
        Assert.assertTrue(latest.isShowing());
        latest.dismiss();
    }
}
