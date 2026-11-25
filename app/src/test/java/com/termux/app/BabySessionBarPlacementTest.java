package com.termux.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
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
 * The session bar and the Pro mode pill share ONE row in the root column
 * in Pro mode, so they can never overlap (an earlier overlay placement
 * guessed the pill's width and the chips slid under it on device). In
 * Easy mode the bar returns to its strip under the top bar and the pill
 * returns to its overlay slot in the content container.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class BabySessionBarPlacementTest {

    private Context mContext;
    private LinearLayout mRoot;
    private View mTopBar;
    private HorizontalScrollView mSessionBar;
    private LinearLayout mProRow;
    private FrameLayout mContentContainer;
    private LinearLayout mPill;

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

        mProRow = new LinearLayout(mContext);
        mProRow.setOrientation(LinearLayout.HORIZONTAL);
        mProRow.setVisibility(View.GONE);
        mRoot.addView(mProRow, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        mContentContainer = new FrameLayout(mContext);
        mRoot.addView(mContentContainer, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        mPill = new LinearLayout(mContext);
        FrameLayout.LayoutParams pillLp = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, 34);
        pillLp.gravity = Gravity.TOP | Gravity.END;
        mContentContainer.addView(mPill, pillLp);
    }

    private void place(boolean proMode) {
        TermuxActivity.placeSessionBarForMode(
            mSessionBar, mTopBar, mPill, mProRow, mContentContainer, proMode);
    }

    @Test
    public void proMode_barAndPillShareOneRow_barFirstWithWeight() {
        place(true);

        assertSame(mProRow, mSessionBar.getParent());
        assertSame(mProRow, mPill.getParent());
        assertTrue(mProRow.indexOfChild(mSessionBar) < mProRow.indexOfChild(mPill));
        LinearLayout.LayoutParams lp =
            (LinearLayout.LayoutParams) mSessionBar.getLayoutParams();
        assertEquals(0, lp.width);
        assertEquals(1f, lp.weight, 0.001f);
        assertEquals(View.VISIBLE, mProRow.getVisibility());
    }

    @Test
    public void easyMode_barReturnsUnderTopBar_pillReturnsToOverlay() {
        place(true);
        place(false);

        assertSame(mRoot, mSessionBar.getParent());
        assertEquals(mRoot.indexOfChild(mTopBar) + 1, mRoot.indexOfChild(mSessionBar));
        assertSame(mContentContainer, mPill.getParent());
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mPill.getLayoutParams();
        assertEquals(Gravity.TOP | Gravity.END, lp.gravity);
        assertEquals(View.GONE, mProRow.getVisibility());
    }

    @Test
    public void switchingModesTwice_keepsExactlyOneInstanceOfEach() {
        place(true);
        place(false);
        place(true);

        assertSame(mProRow, mSessionBar.getParent());
        assertSame(mProRow, mPill.getParent());
        assertEquals(2, mProRow.getChildCount());
        assertEquals(0, mContentContainer.getChildCount());
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
}
