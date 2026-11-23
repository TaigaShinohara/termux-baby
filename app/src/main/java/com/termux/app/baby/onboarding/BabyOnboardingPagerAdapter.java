package com.termux.app.baby.onboarding;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.os.Build;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.viewpager.widget.PagerAdapter;

import com.google.android.material.card.MaterialCardView;
import com.termux.R;
import com.termux.app.baby.BabyPreferences;

/**
 * 7-page 1-page-1-claim onboarding adapter for Termux Baby.
 *
 * Pages:
 * 0 - Hook / Identity
 * 1 - 3-Choice Goal Selector (no Next button; tap triggers bounce + haptic + praise -> auto-advance)
 * 2 - Match Declaration (dedicated result page echoing the chosen goal + 1 core strength)
 * 3 - Step 1/3: Pick a card
 * 4 - Step 2/3: See clear results
 * 5 - Step 3/3: Switch to Pro anytime
 * 6 - Connect / CTA: Let's try your first tap
 */
public class BabyOnboardingPagerAdapter extends PagerAdapter {

    public static final int PAGE_HOOK = 0;
    public static final int PAGE_CHOICE = 1;
    public static final int PAGE_MATCH = 2;
    public static final int PAGE_STEP_1 = 3;
    public static final int PAGE_STEP_2 = 4;
    public static final int PAGE_STEP_3 = 5;
    public static final int PAGE_CTA = 6;
    public static final int PAGE_COUNT = 7;

    public interface OnboardingInteractionListener {
        /** Called when the user taps one of the 3 choices on Page 2. */
        void onGoalChoiceSelected(@NonNull String goal);

        /** Called if the user taps the praise banner during the linger delay to advance immediately. */
        void onPraiseTappedToAdvance();
    }

    private final Context mContext;
    private final LayoutInflater mInflater;
    private final OnboardingInteractionListener mListener;
    private final SparseArray<View> mInstantiatedViews = new SparseArray<>();

    @NonNull
    private String mSelectedGoal = BabyPreferences.GOAL_EXPLORE;
    private boolean mHasUserSelectedGoal = false;

    public BabyOnboardingPagerAdapter(@NonNull Context context,
                                      @NonNull OnboardingInteractionListener listener) {
        mContext = context;
        mInflater = LayoutInflater.from(context);
        mListener = listener;
    }

    public void setSelectedGoal(@NonNull String goal, boolean userSelected) {
        mSelectedGoal = goal;
        mHasUserSelectedGoal = userSelected;
        updateMatchPageContent();
        updateChoicePageSelectionState(false);
    }

    @NonNull
    public String getSelectedGoal() {
        return mSelectedGoal;
    }

    @Override
    public int getCount() {
        return PAGE_COUNT;
    }

    @Override
    public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
        return view == object;
    }

    @NonNull
    @Override
    public Object instantiateItem(@NonNull ViewGroup container, int position) {
        View pageView;
        if (position == PAGE_CHOICE) {
            pageView = mInflater.inflate(R.layout.item_baby_onboarding_choice, container, false);
            bindChoicePage(pageView);
        } else {
            pageView = mInflater.inflate(R.layout.item_baby_onboarding_info, container, false);
            bindInfoPage(pageView, position);
        }
        container.addView(pageView);
        mInstantiatedViews.put(position, pageView);
        return pageView;
    }

    @Override
    public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
        View view = (View) object;
        clearPageAnimations(view);
        container.removeView(view);
        mInstantiatedViews.remove(position);
    }

    /**
     * Returns the accessibility headline text for the given page so TalkBack can announce it.
     */
    @NonNull
    public CharSequence getPageHeadline(int position) {
        switch (position) {
            case PAGE_HOOK:
                return mContext.getString(R.string.baby_onboarding_p1_title);
            case PAGE_CHOICE:
                return mContext.getString(R.string.baby_onboarding_p2_title);
            case PAGE_MATCH:
                return mContext.getString(R.string.baby_onboarding_p3_title);
            case PAGE_STEP_1:
                return mContext.getString(R.string.baby_onboarding_p4_title);
            case PAGE_STEP_2:
                return mContext.getString(R.string.baby_onboarding_p5_title);
            case PAGE_STEP_3:
                return mContext.getString(R.string.baby_onboarding_p6_title);
            case PAGE_CTA:
            default:
                return mContext.getString(R.string.baby_onboarding_p7_title);
        }
    }

    /**
     * Triggers the single meaningful entrance animation for the newly visible page.
     * Respects OS reduced-motion settings.
     */
    public void onPageBecameVisible(int position) {
        View pageView = mInstantiatedViews.get(position);
        if (pageView == null) return;

        boolean animate = BabyPreferences.areAnimationsEnabled(mContext);

        switch (position) {
            case PAGE_HOOK:
                animateHookPage(pageView, animate);
                break;
            case PAGE_CHOICE:
                updateChoicePageSelectionState(false);
                break;
            case PAGE_MATCH:
                updateMatchPageContent();
                animateMatchPageStaged(pageView, animate);
                break;
            case PAGE_STEP_1:
                animateStep1CardLift(pageView, animate);
                break;
            case PAGE_STEP_2:
                animateStep2CheckReveal(pageView, animate);
                break;
            case PAGE_STEP_3:
                animateStep3ToggleDemo(pageView, animate);
                break;
            case PAGE_CTA:
                animateCtaPage(pageView, animate);
                break;
            default:
                break;
        }
    }

    /**
     * Stops any in-flight animations on pages that scrolled offscreen.
     */
    public void onPageLeft(int position) {
        View pageView = mInstantiatedViews.get(position);
        if (pageView != null) {
            clearPageAnimations(pageView);
        }
    }

    private void bindInfoPage(@NonNull View view, int position) {
        TextView titleView = view.findViewById(R.id.onboarding_title);
        TextView bodyView = view.findViewById(R.id.onboarding_body);
        ImageView heroIcon = view.findViewById(R.id.onboarding_hero_icon);
        View heroBadge = view.findViewById(R.id.onboarding_hero_badge);
        ImageView heroBadgeIcon = view.findViewById(R.id.onboarding_hero_badge_icon);
        View mirrorContainer = view.findViewById(R.id.onboarding_mirror_container);
        View proToggleDemo = view.findViewById(R.id.onboarding_pro_toggle_demo);

        heroBadge.setVisibility(View.GONE);
        mirrorContainer.setVisibility(View.GONE);
        proToggleDemo.setVisibility(View.GONE);

        switch (position) {
            case PAGE_HOOK:
                titleView.setText(R.string.baby_onboarding_p1_title);
                bodyView.setText(R.string.baby_onboarding_p1_body);
                heroIcon.setImageResource(R.drawable.ic_baby_sparkle);
                break;

            case PAGE_MATCH:
                titleView.setText(R.string.baby_onboarding_p3_title);
                heroBadge.setVisibility(View.VISIBLE);
                heroBadgeIcon.setImageResource(R.drawable.ic_baby_check);
                mirrorContainer.setVisibility(View.VISIBLE);
                bindMatchContentIntoView(view);
                break;

            case PAGE_STEP_1:
                titleView.setText(R.string.baby_onboarding_p4_title);
                bodyView.setText(R.string.baby_onboarding_p4_body);
                heroIcon.setImageResource(R.drawable.ic_baby_cards);
                break;

            case PAGE_STEP_2:
                titleView.setText(R.string.baby_onboarding_p5_title);
                bodyView.setText(R.string.baby_onboarding_p5_body);
                heroIcon.setImageResource(R.drawable.ic_baby_check);
                break;

            case PAGE_STEP_3:
                titleView.setText(R.string.baby_onboarding_p6_title);
                bodyView.setText(R.string.baby_onboarding_p6_body);
                heroIcon.setImageResource(R.drawable.ic_baby_switch);
                proToggleDemo.setVisibility(View.VISIBLE);
                applyProToggleDemoState(view, false);
                break;

            case PAGE_CTA:
            default:
                titleView.setText(R.string.baby_onboarding_p7_title);
                bodyView.setText(R.string.baby_onboarding_p7_body);
                heroIcon.setImageResource(R.drawable.ic_baby_sparkle);
                heroBadge.setVisibility(View.VISIBLE);
                heroBadgeIcon.setImageResource(R.drawable.ic_baby_check);
                break;
        }
    }

    private void bindChoicePage(@NonNull View view) {
        MaterialCardView card1 = view.findViewById(R.id.onboarding_choice_card_1);
        MaterialCardView card2 = view.findViewById(R.id.onboarding_choice_card_2);
        MaterialCardView card3 = view.findViewById(R.id.onboarding_choice_card_3);

        ImageView icon1 = view.findViewById(R.id.onboarding_choice_icon_1);
        ImageView icon2 = view.findViewById(R.id.onboarding_choice_icon_2);
        ImageView icon3 = view.findViewById(R.id.onboarding_choice_icon_3);
        icon1.setImageResource(R.drawable.ic_baby_python);
        icon2.setImageResource(R.drawable.ic_baby_files);
        icon3.setImageResource(R.drawable.ic_baby_explore);

        ImageView check1 = view.findViewById(R.id.onboarding_choice_check_1);
        ImageView check2 = view.findViewById(R.id.onboarding_choice_check_2);
        ImageView check3 = view.findViewById(R.id.onboarding_choice_check_3);
        check1.setImageResource(R.drawable.ic_baby_check);
        check2.setImageResource(R.drawable.ic_baby_check);
        check3.setImageResource(R.drawable.ic_baby_check);

        View praiseContainer = view.findViewById(R.id.onboarding_praise_container);
        praiseContainer.setOnClickListener(v -> mListener.onPraiseTappedToAdvance());

        card1.setOnClickListener(v -> handleChoiceTapped(view, card1, BabyPreferences.GOAL_TOOLS_PYTHON));
        card2.setOnClickListener(v -> handleChoiceTapped(view, card2, BabyPreferences.GOAL_FILES_DEVICE));
        card3.setOnClickListener(v -> handleChoiceTapped(view, card3, BabyPreferences.GOAL_EXPLORE));

        updateChoicePageSelectionState(false);
    }

    private void handleChoiceTapped(@NonNull View pageView,
                                    @NonNull MaterialCardView tappedCard,
                                    @NonNull String goal) {
        mSelectedGoal = goal;
        mHasUserSelectedGoal = true;

        // Medium haptic feedback (respecting OS settings)
        performConfirmHaptic(tappedCard);

        // Visual selection state + bounce animation
        updateChoicePageSelectionState(true);
        animateChoiceBounce(tappedCard, BabyPreferences.areAnimationsEnabled(mContext));

        // Show specific encouraging praise sentence immediately on the current page
        View praiseContainer = pageView.findViewById(R.id.onboarding_praise_container);
        TextView praiseText = pageView.findViewById(R.id.onboarding_praise_text);
        praiseText.setText(getPraiseStringResForGoal(goal));
        praiseContainer.setVisibility(View.VISIBLE);

        if (BabyPreferences.areAnimationsEnabled(mContext)) {
            praiseContainer.animate().cancel();
            praiseContainer.setAlpha(0f);
            praiseContainer.setTranslationY(dpToPx(8f));
            praiseContainer.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(240L)
                .setInterpolator(new DecelerateInterpolator())
                .start();
        } else {
            praiseContainer.setAlpha(1f);
            praiseContainer.setTranslationY(0f);
        }

        // Update Match Declaration page in advance so it's ready after the linger
        updateMatchPageContent();

        mListener.onGoalChoiceSelected(goal);
    }

    private void updateChoicePageSelectionState(boolean keepPraiseVisible) {
        View pageView = mInstantiatedViews.get(PAGE_CHOICE);
        if (pageView == null) return;

        MaterialCardView card1 = pageView.findViewById(R.id.onboarding_choice_card_1);
        MaterialCardView card2 = pageView.findViewById(R.id.onboarding_choice_card_2);
        MaterialCardView card3 = pageView.findViewById(R.id.onboarding_choice_card_3);

        ImageView check1 = pageView.findViewById(R.id.onboarding_choice_check_1);
        ImageView check2 = pageView.findViewById(R.id.onboarding_choice_check_2);
        ImageView check3 = pageView.findViewById(R.id.onboarding_choice_check_3);

        styleChoiceCard(card1, check1, mHasUserSelectedGoal && BabyPreferences.GOAL_TOOLS_PYTHON.equals(mSelectedGoal));
        styleChoiceCard(card2, check2, mHasUserSelectedGoal && BabyPreferences.GOAL_FILES_DEVICE.equals(mSelectedGoal));
        styleChoiceCard(card3, check3, mHasUserSelectedGoal && BabyPreferences.GOAL_EXPLORE.equals(mSelectedGoal));

        if (!keepPraiseVisible) {
            View praiseContainer = pageView.findViewById(R.id.onboarding_praise_container);
            if (!mHasUserSelectedGoal) {
                praiseContainer.setVisibility(View.INVISIBLE);
            }
        }
    }

    private void styleChoiceCard(@NonNull MaterialCardView card,
                                 @NonNull ImageView checkIcon,
                                 boolean selected) {
        card.setSelected(selected);
        int bgColor = ContextCompat.getColor(
            mContext, selected ? R.color.baby_primary_container : R.color.baby_surface
        );
        int strokeColor = ContextCompat.getColor(
            mContext, selected ? R.color.baby_primary : R.color.baby_border
        );
        int strokeWidthPx = Math.round(dpToPx(selected ? 2f : 1.5f));
        card.setCardBackgroundColor(bgColor);
        card.setStrokeColor(strokeColor);
        card.setStrokeWidth(strokeWidthPx);
        checkIcon.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
    }

    private void updateMatchPageContent() {
        View matchView = mInstantiatedViews.get(PAGE_MATCH);
        if (matchView != null) {
            bindMatchContentIntoView(matchView);
        }
    }

    private void bindMatchContentIntoView(@NonNull View view) {
        TextView mirrorText = view.findViewById(R.id.onboarding_mirror_text);
        TextView bodyView = view.findViewById(R.id.onboarding_body);
        ImageView heroIcon = view.findViewById(R.id.onboarding_hero_icon);

        mirrorText.setText(getChoiceStringResForGoal(mSelectedGoal));
        bodyView.setText(getMatchStringResForGoal(mSelectedGoal));
        heroIcon.setImageResource(getIconResForGoal(mSelectedGoal));
    }

    // ---- Meaningful Page Animations (only when motion is enabled) ----

    private void animateHookPage(@NonNull View view, boolean animate) {
        View visual = view.findViewById(R.id.onboarding_visual_container);
        if (!animate) {
            visual.setAlpha(1f);
            visual.setScaleX(1f);
            visual.setScaleY(1f);
            return;
        }
        visual.animate().cancel();
        visual.setAlpha(0f);
        visual.setScaleX(0.94f);
        visual.setScaleY(0.94f);
        visual.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(300L)
            .setInterpolator(new DecelerateInterpolator())
            .start();
    }

    /**
     * Staged sequential entrance on Page 3 (Match Declaration):
     * 1) Echoed user choice -> 2) Hero visual -> 3) Headline -> 4) Match strength (150ms apart).
     */
    private void animateMatchPageStaged(@NonNull View view, boolean animate) {
        View mirror = view.findViewById(R.id.onboarding_mirror_container);
        View visual = view.findViewById(R.id.onboarding_visual_container);
        View title = view.findViewById(R.id.onboarding_title);
        View body = view.findViewById(R.id.onboarding_body);

        View[] sequence = new View[]{mirror, visual, title, body};
        for (int i = 0; i < sequence.length; i++) {
            View target = sequence[i];
            target.animate().cancel();
            if (!animate) {
                target.setAlpha(1f);
                target.setTranslationY(0f);
            } else {
                target.setAlpha(0f);
                target.setTranslationY(dpToPx(10f));
                target.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(i * 150L)
                    .setDuration(260L)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
            }
        }
    }

    private void animateStep1CardLift(@NonNull View view, boolean animate) {
        View visualCard = view.findViewById(R.id.onboarding_visual_card);
        visualCard.animate().cancel();
        if (!animate) {
            visualCard.setTranslationY(0f);
            visualCard.setScaleX(1f);
            visualCard.setScaleY(1f);
            return;
        }
        visualCard.setTranslationY(dpToPx(8f));
        visualCard.setScaleX(0.96f);
        visualCard.setScaleY(0.96f);
        visualCard.animate()
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(250L)
            .setInterpolator(new OvershootInterpolator(1.2f))
            .start();
    }

    private void animateStep2CheckReveal(@NonNull View view, boolean animate) {
        View heroIcon = view.findViewById(R.id.onboarding_hero_icon);
        heroIcon.animate().cancel();
        if (!animate) {
            heroIcon.setScaleX(1f);
            heroIcon.setScaleY(1f);
            heroIcon.setAlpha(1f);
            return;
        }
        heroIcon.setScaleX(0.55f);
        heroIcon.setScaleY(0.55f);
        heroIcon.setAlpha(0f);
        heroIcon.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(280L)
            .setInterpolator(new OvershootInterpolator(1.6f))
            .start();
    }

    private void animateStep3ToggleDemo(@NonNull View view, boolean animate) {
        applyProToggleDemoState(view, false);
        if (!animate) return;

        // One clear demonstration: Easy -> Terminal -> Easy (not an endless distracting loop)
        view.postDelayed(() -> {
            if (view.isAttachedToWindow()) {
                applyProToggleDemoState(view, true);
            }
        }, 420L);
        view.postDelayed(() -> {
            if (view.isAttachedToWindow()) {
                applyProToggleDemoState(view, false);
            }
        }, 1250L);
    }

    private void applyProToggleDemoState(@NonNull View view, boolean proActive) {
        TextView easyLabel = view.findViewById(R.id.onboarding_demo_easy);
        TextView proLabel = view.findViewById(R.id.onboarding_demo_pro);
        if (easyLabel == null || proLabel == null) return;

        int activeText = ContextCompat.getColor(mContext, R.color.baby_on_primary);
        int inactiveText = ContextCompat.getColor(mContext, R.color.baby_text_secondary);

        if (proActive) {
            easyLabel.setBackground(null);
            easyLabel.setTextColor(inactiveText);
            proLabel.setBackgroundResource(R.drawable.baby_pill_selected);
            proLabel.setTextColor(activeText);
        } else {
            easyLabel.setBackgroundResource(R.drawable.baby_pill_selected);
            easyLabel.setTextColor(activeText);
            proLabel.setBackground(null);
            proLabel.setTextColor(inactiveText);
        }
    }

    private void animateCtaPage(@NonNull View view, boolean animate) {
        View badge = view.findViewById(R.id.onboarding_hero_badge);
        badge.animate().cancel();
        if (!animate) {
            badge.setScaleX(1f);
            badge.setScaleY(1f);
            badge.setAlpha(1f);
            return;
        }
        badge.setScaleX(0.4f);
        badge.setScaleY(0.4f);
        badge.setAlpha(0f);
        badge.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setStartDelay(140L)
            .setDuration(300L)
            .setInterpolator(new OvershootInterpolator(1.8f))
            .start();
    }

    private void animateChoiceBounce(@NonNull View card, boolean animate) {
        if (!animate) return;
        ObjectAnimator scaleDownX = ObjectAnimator.ofFloat(card, View.SCALE_X, 1f, 0.96f, 1.02f, 1f);
        ObjectAnimator scaleDownY = ObjectAnimator.ofFloat(card, View.SCALE_Y, 1f, 0.96f, 1.02f, 1f);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(scaleDownX, scaleDownY);
        set.setDuration(250L);
        set.setInterpolator(new DecelerateInterpolator());
        set.start();
    }

    private void clearPageAnimations(@NonNull View view) {
        view.animate().cancel();
        View visual = view.findViewById(R.id.onboarding_visual_container);
        if (visual != null) visual.animate().cancel();
        View visualCard = view.findViewById(R.id.onboarding_visual_card);
        if (visualCard != null) visualCard.animate().cancel();
        View heroIcon = view.findViewById(R.id.onboarding_hero_icon);
        if (heroIcon != null) heroIcon.animate().cancel();
        View badge = view.findViewById(R.id.onboarding_hero_badge);
        if (badge != null) badge.animate().cancel();
        View mirror = view.findViewById(R.id.onboarding_mirror_container);
        if (mirror != null) mirror.animate().cancel();
        View title = view.findViewById(R.id.onboarding_title);
        if (title != null) title.animate().cancel();
        View body = view.findViewById(R.id.onboarding_body);
        if (body != null) body.animate().cancel();
        View praise = view.findViewById(R.id.onboarding_praise_container);
        if (praise != null) praise.animate().cancel();
    }

    @StringRes
    private static int getChoiceStringResForGoal(@NonNull String goal) {
        if (BabyPreferences.GOAL_TOOLS_PYTHON.equals(goal)) {
            return R.string.baby_onboarding_choice_1;
        } else if (BabyPreferences.GOAL_FILES_DEVICE.equals(goal)) {
            return R.string.baby_onboarding_choice_2;
        }
        return R.string.baby_onboarding_choice_3;
    }

    @StringRes
    private static int getPraiseStringResForGoal(@NonNull String goal) {
        if (BabyPreferences.GOAL_TOOLS_PYTHON.equals(goal)) {
            return R.string.baby_onboarding_praise_1;
        } else if (BabyPreferences.GOAL_FILES_DEVICE.equals(goal)) {
            return R.string.baby_onboarding_praise_2;
        }
        return R.string.baby_onboarding_praise_3;
    }

    @StringRes
    private static int getMatchStringResForGoal(@NonNull String goal) {
        if (BabyPreferences.GOAL_TOOLS_PYTHON.equals(goal)) {
            return R.string.baby_onboarding_match_1;
        } else if (BabyPreferences.GOAL_FILES_DEVICE.equals(goal)) {
            return R.string.baby_onboarding_match_2;
        }
        return R.string.baby_onboarding_match_3;
    }

    @DrawableRes
    private static int getIconResForGoal(@NonNull String goal) {
        if (BabyPreferences.GOAL_TOOLS_PYTHON.equals(goal)) {
            return R.drawable.ic_baby_python;
        } else if (BabyPreferences.GOAL_FILES_DEVICE.equals(goal)) {
            return R.drawable.ic_baby_files;
        }
        return R.drawable.ic_baby_explore;
    }

    public static void performConfirmHaptic(@NonNull View view) {
        int feedbackConstant = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
            ? HapticFeedbackConstants.CONFIRM
            : HapticFeedbackConstants.VIRTUAL_KEY;
        view.performHapticFeedback(feedbackConstant);
    }

    public static void performGentleHaptic(@NonNull View view) {
        int feedbackConstant = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
            ? HapticFeedbackConstants.CLOCK_TICK
            : HapticFeedbackConstants.VIRTUAL_KEY;
        view.performHapticFeedback(feedbackConstant);
    }

    private float dpToPx(float dp) {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp, mContext.getResources().getDisplayMetrics()
        );
    }
}
