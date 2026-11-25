package com.termux.app;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

import com.termux.R;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/**
 * Robolectric tests for the session bar's mode-dependent placement: a strip
 * under the top bar in Easy Mode, a floating overlay at the terminal's top
 * start (same line as the mode pill) in Pro Mode. Part of the pre-delivery
 * UI verification standard (no emulator).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class BabySessionBarPlacementTest {

    private AppCompatActivity activity;
    private LinearLayout root;
    private View topBar;
    private HorizontalScrollView sessionBar;
    private FrameLayout proContainer;

    @Before
    public void setUp() {
        ActivityController<AppCompatActivity> controller =
            Robolectric.buildActivity(AppCompatActivity.class);
        activity = controller.get();
        activity.setTheme(R.style.Theme_TermuxBaby_Onboarding);
        controller.setup();

        root = new LinearLayout(activity);
        topBar = new View(activity);
        root.addView(topBar);
        sessionBar = new HorizontalScrollView(activity);
        root.addView(sessionBar); // starts as the Easy Mode strip under the top bar
        proContainer = new FrameLayout(activity);
        root.addView(proContainer);
        activity.setContentView(root);
    }

    @Test
    public void proMode_movesBarIntoContentContainerAsOverlay() {
        TermuxActivity.placeSessionBarForMode(sessionBar, topBar, proContainer, true);

        Assert.assertSame(proContainer, sessionBar.getParent());
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) sessionBar.getLayoutParams();
        int verticalGravity = lp.gravity & Gravity.VERTICAL_GRAVITY_MASK;
        Assert.assertEquals(Gravity.TOP, verticalGravity);
        Assert.assertTrue("overlay keeps room for the mode pill at the top end",
            lp.getMarginEnd() > 0);
    }

    @Test
    public void easyMode_returnsBarToStripUnderTopBar() {
        TermuxActivity.placeSessionBarForMode(sessionBar, topBar, proContainer, true);
        TermuxActivity.placeSessionBarForMode(sessionBar, topBar, proContainer, false);

        Assert.assertSame(root, sessionBar.getParent());
        Assert.assertEquals(root.indexOfChild(topBar) + 1, root.indexOfChild(sessionBar));
    }

    @Test
    public void sameModeTwice_keepsSingleInstance() {
        TermuxActivity.placeSessionBarForMode(sessionBar, topBar, proContainer, true);
        TermuxActivity.placeSessionBarForMode(sessionBar, topBar, proContainer, true);

        Assert.assertEquals(1, proContainer.getChildCount());
    }

    @Test
    public void sessionChip_hasLiveDotHiddenByDefault() {
        View chip = LayoutInflater.from(activity)
            .inflate(R.layout.item_baby_session_chip, null, false);
        View dot = chip.findViewById(R.id.chip_dot);
        Assert.assertNotNull(dot);
        Assert.assertEquals(View.GONE, dot.getVisibility());
    }
}
