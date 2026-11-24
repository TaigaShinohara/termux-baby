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
}
