package com.termux.app.baby.onboarding;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.termux.R;

/**
 * Windowed dot progress indicator for onboarding.
 *
 * Displays at most 5 visible dots at a time without showing numeric page counts
 * (e.g. "3 / 7"). As the user moves across pages, the window slides smoothly so
 * every page transition visibly advances the active dot, while edge dots shrink
 * when additional pages exist beyond the current window.
 *
 * Display-only (non-clickable) per onboarding design guidelines.
 */
public class WindowDotsIndicatorView extends View {

    private static final int MAX_VISIBLE_DOTS = 5;
    private static final long TRANSITION_DURATION_MS = 220L;

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mDotRect = new RectF();

    @ColorInt
    private int mActiveColor;
    @ColorInt
    private int mInactiveColor;

    private float mActiveWidthPx;
    private float mRegularDiameterPx;
    private float mEdgeDiameterPx;
    private float mSpacingPx;
    private float mSlotWidthPx;

    private int mPageCount = 7;
    private int mCurrentPage = 0;
    private float mAnimatedPosition = 0f;
    private float mAnimatedWindowStart = 0f;

    @Nullable
    private ValueAnimator mPositionAnimator;

    public WindowDotsIndicatorView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public WindowDotsIndicatorView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public WindowDotsIndicatorView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(@NonNull Context context) {
        mActiveColor = ContextCompat.getColor(context, R.color.baby_primary);
        mInactiveColor = ContextCompat.getColor(context, R.color.baby_dot_inactive);

        mActiveWidthPx = dpToPx(22f);
        mRegularDiameterPx = dpToPx(8f);
        mEdgeDiameterPx = dpToPx(5f);
        mSpacingPx = dpToPx(8f);
        mSlotWidthPx = mRegularDiameterPx + mSpacingPx;

        setClickable(false);
        setFocusable(false);
    }

    public void setPageCount(int pageCount) {
        int safeCount = Math.max(1, pageCount);
        if (mPageCount == safeCount) return;
        mPageCount = safeCount;
        mCurrentPage = Math.min(mCurrentPage, mPageCount - 1);
        mAnimatedPosition = mCurrentPage;
        mAnimatedWindowStart = computeTargetWindowStart(mCurrentPage);
        requestLayout();
        invalidate();
    }

    public int getPageCount() {
        return mPageCount;
    }

    public int getCurrentPage() {
        return mCurrentPage;
    }

    /**
     * Updates the active page index. Animates smoothly unless {@code animate} is false
     * or system animations are disabled.
     */
    public void setCurrentPage(int page, boolean animate) {
        int clamped = Math.max(0, Math.min(page, mPageCount - 1));
        mCurrentPage = clamped;

        float targetPosition = clamped;
        float targetWindowStart = computeTargetWindowStart(clamped);

        if (mPositionAnimator != null) {
            mPositionAnimator.cancel();
            mPositionAnimator = null;
        }

        if (!animate || isReducedMotionEnabled(getContext())) {
            mAnimatedPosition = targetPosition;
            mAnimatedWindowStart = targetWindowStart;
            invalidate();
            return;
        }

        final float startPos = mAnimatedPosition;
        final float startWin = mAnimatedWindowStart;
        mPositionAnimator = ValueAnimator.ofFloat(0f, 1f);
        mPositionAnimator.setDuration(TRANSITION_DURATION_MS);
        mPositionAnimator.setInterpolator(new DecelerateInterpolator());
        mPositionAnimator.addUpdateListener(animation -> {
            float fraction = (float) animation.getAnimatedValue();
            mAnimatedPosition = lerp(startPos, targetPosition, fraction);
            mAnimatedWindowStart = lerp(startWin, targetWindowStart, fraction);
            invalidate();
        });
        mPositionAnimator.start();
    }

    /**
     * Sets the accessibility label describing the current step (never a numeric total like "3/7").
     */
    public void setStepContentDescription(@Nullable CharSequence description) {
        setContentDescription(description);
    }

    private float computeTargetWindowStart(int page) {
        if (mPageCount <= MAX_VISIBLE_DOTS) {
            return 0f;
        }
        int centeredStart = page - (MAX_VISIBLE_DOTS / 2);
        int maxStart = mPageCount - MAX_VISIBLE_DOTS;
        return Math.max(0, Math.min(centeredStart, maxStart));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int visibleSlots = Math.min(mPageCount, MAX_VISIBLE_DOTS);
        float activeExtra = mActiveWidthPx - mRegularDiameterPx;
        int desiredWidth = Math.round(
            getPaddingLeft() + getPaddingRight()
                + (visibleSlots * mSlotWidthPx)
                + activeExtra
                + dpToPx(4f)
        );
        int desiredHeight = Math.round(
            getPaddingTop() + getPaddingBottom()
                + Math.max(mRegularDiameterPx, dpToPx(12f))
        );

        int resolvedWidth = resolveSize(desiredWidth, widthMeasureSpec);
        int resolvedHeight = resolveSize(desiredHeight, heightMeasureSpec);
        setMeasuredDimension(resolvedWidth, resolvedHeight);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (mPageCount <= 0) return;

        int visibleSlots = Math.min(mPageCount, MAX_VISIBLE_DOTS);
        float activeExtra = mActiveWidthPx - mRegularDiameterPx;
        float totalWindowWidth = (visibleSlots - 1) * mSlotWidthPx + mActiveWidthPx;
        float startX = (getWidth() - totalWindowWidth) / 2f + (mRegularDiameterPx / 2f);
        float centerY = getHeight() / 2f;

        boolean hasMoreLeft = mAnimatedWindowStart > 0.05f;
        boolean hasMoreRight = (mAnimatedWindowStart + MAX_VISIBLE_DOTS) < (mPageCount - 0.05f);

        // Compute visual x centers sequentially for dots in/near the visible window
        int firstDrawIndex = Math.max(0, (int) Math.floor(mAnimatedWindowStart) - 1);
        int lastDrawIndex = Math.min(mPageCount - 1, (int) Math.ceil(mAnimatedWindowStart + visibleSlots));

        for (int i = firstDrawIndex; i <= lastDrawIndex; i++) {
            float relIndex = i - mAnimatedWindowStart;
            if (relIndex < -0.6f || relIndex > visibleSlots - 0.4f) {
                continue;
            }

            float activeWeight = clamp(1f - Math.abs(i - mAnimatedPosition), 0f, 1f);

            // Fade smoothly if entering/leaving the window boundary
            float visibilityAlpha = 1f;
            if (relIndex < 0f) {
                visibilityAlpha = clamp(1f + relIndex, 0f, 1f);
            } else if (relIndex > visibleSlots - 1) {
                visibilityAlpha = clamp(visibleSlots - relIndex, 0f, 1f);
            }

            // Shrink edge dots when more pages exist beyond that edge
            float edgeFactor = 0f;
            if (hasMoreLeft && relIndex < 0.75f) {
                edgeFactor = Math.max(edgeFactor, clamp(1f - (relIndex / 0.75f), 0f, 1f));
            }
            if (hasMoreRight && relIndex > (visibleSlots - 1.75f)) {
                float distFromRightEdge = (visibleSlots - 1f) - relIndex;
                edgeFactor = Math.max(edgeFactor, clamp(1f - (distFromRightEdge / 0.75f), 0f, 1f));
            }
            // Active dot is never shrunk as an edge dot
            edgeFactor *= (1f - activeWeight);

            float dotHeight = lerp(mRegularDiameterPx, mEdgeDiameterPx, edgeFactor);
            float dotWidth = lerp(dotHeight, mActiveWidthPx, activeWeight);

            // Shift dots to the right of the active pill so spacing remains balanced
            float shiftRight = 0f;
            float deltaFromActive = i - mAnimatedPosition;
            if (deltaFromActive > 0f) {
                shiftRight = activeExtra * clamp(deltaFromActive, 0f, 1f);
            } else if (deltaFromActive < 0f) {
                shiftRight = 0f;
            } else {
                shiftRight = activeExtra * 0.5f;
            }
            // Smoothly center the active pill at its slot
            float centerX = startX + (relIndex * mSlotWidthPx) + shiftRight;
            if (activeWeight > 0f && deltaFromActive != 0f) {
                centerX = startX + (relIndex * mSlotWidthPx) + (activeExtra * 0.5f * activeWeight)
                    + (deltaFromActive > 0f ? activeExtra * clamp(deltaFromActive, 0f, 1f) * (1f - activeWeight) : 0f);
            }

            int blendedColor = blendColors(mInactiveColor, mActiveColor, activeWeight);
            int baseAlpha = Math.round(lerp(255f, 165f, edgeFactor));
            int finalAlpha = Math.round(baseAlpha * visibilityAlpha);

            mPaint.setColor(blendedColor);
            mPaint.setAlpha(finalAlpha);

            float halfW = dotWidth / 2f;
            float halfH = dotHeight / 2f;
            mDotRect.set(centerX - halfW, centerY - halfH, centerX + halfW, centerY + halfH);
            float radius = halfH;
            canvas.drawRoundRect(mDotRect, radius, radius, mPaint);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mPositionAnimator != null) {
            mPositionAnimator.cancel();
            mPositionAnimator = null;
        }
    }

    private float dpToPx(float dp) {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics()
        );
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    @ColorInt
    private static int blendColors(@ColorInt int from, @ColorInt int to, float ratio) {
        float inverse = 1f - ratio;
        int a = Math.round(((from >>> 24) & 0xFF) * inverse + ((to >>> 24) & 0xFF) * ratio);
        int r = Math.round(((from >>> 16) & 0xFF) * inverse + ((to >>> 16) & 0xFF) * ratio);
        int g = Math.round(((from >>> 8) & 0xFF) * inverse + ((to >>> 8) & 0xFF) * ratio);
        int b = Math.round((from & 0xFF) * inverse + (to & 0xFF) * ratio);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static boolean isReducedMotionEnabled(@NonNull Context context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                float scale = Settings.Global.getFloat(
                    context.getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1.0f
                );
                return scale == 0f;
            }
        } catch (Exception ignored) {
            // Fall through to default motion enabled.
        }
        return false;
    }
}
