package com.termux.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import com.termux.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * The session bar has ONE home in both modes: a strip in the root column
 * directly under the top bar (the top of the screen is identical in Easy
 * and Pro, user decision 2026-11-25). Also covers the mode-swipe fling
 * decision: only a fast, long, mostly-horizontal fling switches modes.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class BabySessionBarPlacementTest {

    private Context mContext;
    private LinearLayout mRoot;
    private View mTopBar;
    private HorizontalScrollView mSessionBar;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        mRoot = new LinearLayout(mContext);
        mRoot.setOrientation(LinearLayout.VERTICAL);

        mTopBar = new View(mContext);
        mRoot.addView(mTopBar, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 56));

        mSessionBar = new HorizontalScrollView(mContext);
        mSessionBar.addView(new LinearLayout(mContext));
        mRoot.addView(mSessionBar, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    @Test
    public void sessionBar_sitsDirectlyUnderTopBar() {
        TermuxActivity.placeSessionBarForMode(mSessionBar, mTopBar);

        assertSame(mRoot, mSessionBar.getParent());
        assertEquals(mRoot.indexOfChild(mTopBar) + 1, mRoot.indexOfChild(mSessionBar));
    }

    @Test
    public void placingTwice_keepsExactlyOneInstance() {
        // Simulate a bar that ended up somewhere else (older placements
        // moved it around): it must come back, exactly once.
        LinearLayout elsewhere = new LinearLayout(mContext);
        mRoot.removeView(mSessionBar);
        elsewhere.addView(mSessionBar);

        TermuxActivity.placeSessionBarForMode(mSessionBar, mTopBar);
        TermuxActivity.placeSessionBarForMode(mSessionBar, mTopBar);

        assertSame(mRoot, mSessionBar.getParent());
        assertEquals(0, elsewhere.getChildCount());
        assertEquals(mRoot.indexOfChild(mTopBar) + 1, mRoot.indexOfChild(mSessionBar));
    }

    @Test
    public void chipLayout_carriesThePulseDot() {
        // The chip hosts a Material progress indicator, which needs an
        // AppCompat-descendant theme to inflate.
        Context themed = new android.view.ContextThemeWrapper(
            mContext, R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        View chip = View.inflate(themed, R.layout.item_baby_session_chip, null);
        assertNotNull(chip.findViewById(R.id.chip_dot));
        TextView title = chip.findViewById(R.id.chip_title);
        assertNotNull(title);
    }

    // --- Mode swipe ---------------------------------------------------

    private static final float MIN_DISTANCE = 288f; // 96dp at density 3
    private static final float MIN_VELOCITY = 1650f; // 550dp/s at density 3

    @Test
    public void swipeLeft_inEasy_goesToPro() {
        assertEquals(Boolean.FALSE, TermuxActivity.swipeTargetMode(
            true, -400f, 40f, -2400f, MIN_DISTANCE, MIN_VELOCITY));
    }

    @Test
    public void swipeRight_inPro_returnsToEasy() {
        assertEquals(Boolean.TRUE, TermuxActivity.swipeTargetMode(
            false, 400f, -30f, 2400f, MIN_DISTANCE, MIN_VELOCITY));
    }

    @Test
    public void swipeTowardsCurrentMode_doesNothing() {
        assertNull(TermuxActivity.swipeTargetMode(
            false, -400f, 0f, -2400f, MIN_DISTANCE, MIN_VELOCITY));
        assertNull(TermuxActivity.swipeTargetMode(
            true, 400f, 0f, 2400f, MIN_DISTANCE, MIN_VELOCITY));
    }

    @Test
    public void slowShortOrDiagonalFlings_doNotSwitch() {
        // Too slow.
        assertNull(TermuxActivity.swipeTargetMode(
            true, -400f, 0f, -300f, MIN_DISTANCE, MIN_VELOCITY));
        // Too short.
        assertNull(TermuxActivity.swipeTargetMode(
            true, -120f, 0f, -2400f, MIN_DISTANCE, MIN_VELOCITY));
        // Mostly vertical (scrolling / text selection drag).
        assertNull(TermuxActivity.swipeTargetMode(
            true, -400f, 350f, -2400f, MIN_DISTANCE, MIN_VELOCITY));
    }
}
