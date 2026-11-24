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
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.viewpager.widget.PagerAdapter;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.termux.R;
import com.termux.app.baby.BabyAction;
import com.termux.app.baby.BabyActionCatalog;
import com.termux.app.baby.BabyPreferences;

/**
 * 3-page onboarding for Termux Baby (redesign):
 *
 * 0 - Concept: "a terminal that teaches you commands as you tap" — real
 *     commands on cards, never hidden, never "no commands needed".
 * 1 - 3-Choice Goal Selector (tap triggers bounce + haptic + praise, then
 *     auto-advance). The goal seeds the home curriculum.
 * 2 - Try it now: the user runs ONE real command inside onboarding and
 *     sees the command, the meaning of its parts, and the result — the
 *     whole learning loop in miniature — before reaching Home.
 */
public class BabyOnboardingPagerAdapter extends PagerAdapter {

    public static final int PAGE_CONCEPT = 0;
    /** Kept for compatibility with existing references. */
    public static final int PAGE_HOOK = PAGE_CONCEPT;
    public static final int PAGE_CHOICE = 1;
    public static final int PAGE_TRY = 2;
    /** Kept for compatibility: the try page is the final CTA page. */
    public static final int PAGE_CTA = PAGE_TRY;
    public static final int PAGE_COUNT = 3;

    public interface OnboardingInteractionListener {
        /** Called when the user taps one of the 3 choices on the choice page. */
        void onGoalChoiceSelected(@NonNull String goal);

        /** Called if the user taps the praise banner during the linger delay to advance immediately. */
        void onPraiseTappedToAdvance();

        /** Called when the user taps "run it" on the try page. */
        void onTryRunRequested();
    }

    private final Context mContext;
    private final LayoutInflater mInflater;
    private final OnboardingInteractionListener mListener;
    private final SparseArray<View> mInstantiatedViews = new SparseArray<>();

    @NonNull
    private String mSelectedGoal = BabyPreferences.GOAL_EXPLORE;
    private boolean mHasUserSelectedGoal = false;
    private boolean mTryRunning = false;
    private boolean mTryDone = false;

    public BabyOnboardingPagerAdapter(@NonNull Context context,
                                      @NonNull OnboardingInteractionListener listener) {
        mContext = context;
        mInflater = LayoutInflater.from(context);
        mListener = listener;
    }

    public void setSelectedGoal(@NonNull String goal, boolean userSelected) {
        mSelectedGoal = goal;
        mHasUserSelectedGoal = userSelected;
        updateChoicePageSelectionState(false);
        bindTryPageContent();
    }

    @NonNull
    public String getSelectedGoal() {
        return mSelectedGoal;
    }

    /** The action the try page runs for the current goal. */
    @NonNull
    public BabyAction getTryAction() {
        if (BabyPreferences.GOAL_TOOLS_PYTHON.equals(mSelectedGoal)) {
            BabyAction action = BabyActionCatalog.findById(BabyActionCatalog.ACTION_TRY_PYTHON);
            if (action != null) return action;
        } else if (BabyPreferences.GOAL_FILES_DEVICE.equals(mSelectedGoal)) {
            BabyAction action = BabyActionCatalog.findById(BabyActionCatalog.ACTION_SEE_FILES);
            if (action != null) return action;
        }
        BabyAction pwd = BabyActionCatalog.findById("recipe_pwd");
        if (pwd != null) return pwd;
        return BabyActionCatalog.getHomeActions().get(0);
    }

    /** The literal command the try page runs (Hello-Python for the Python goal). */
    @NonNull
    public String getTryCommand() {
        if (BabyPreferences.GOAL_TOOLS_PYTHON.equals(mSelectedGoal)) {
            return BabyActionCatalog.PYTHON_HELLO_COMMAND;
        }
        BabyAction action = getTryAction();
        return action.commandTemplate != null ? action.commandTemplate : "";
    }

    public boolean isTryDone() {
        return mTryDone;
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
        } else if (position == PAGE_TRY) {
            pageView = mInflater.inflate(R.layout.item_baby_onboarding_try, container, false);
            bindTryPage(pageView);
        } else {
            pageView = mInflater.inflate(R.layout.item_baby_onboarding_info, container, false);
            bindConceptPage(pageView);
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
            case PAGE_CHOICE:
                return mContext.getString(R.string.baby_onboarding_p2_title);
            case PAGE_TRY:
                return mContext.getString(R.string.baby_onboarding_try_title);
            case PAGE_CONCEPT:
            default:
                return mContext.getString(R.string.baby_onboarding_p1_title);
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

        if (position == PAGE_CONCEPT) {
            animateHookPage(pageView, animate);
        } else if (position == PAGE_CHOICE) {
            updateChoicePageSelectionState(false);
        } else if (position == PAGE_TRY) {
            bindTryPageContent();
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

    private void bindConceptPage(@NonNull View view) {
        TextView titleView = view.findViewById(R.id.onboarding_title);
        TextView bodyView = view.findViewById(R.id.onboarding_body);
        ImageView heroIcon = view.findViewById(R.id.onboarding_hero_icon);
        View heroBadge = view.findViewById(R.id.onboarding_hero_badge);
        View mirrorContainer = view.findViewById(R.id.onboarding_mirror_container);
        View proToggleDemo = view.findViewById(R.id.onboarding_pro_toggle_demo);

        heroBadge.setVisibility(View.GONE);
        mirrorContainer.setVisibility(View.GONE);
        proToggleDemo.setVisibility(View.GONE);

        titleView.setText(R.string.baby_onboarding_p1_title);
        bodyView.setText(R.string.baby_onboarding_p1_body);
        heroIcon.setImageResource(R.drawable.ic_baby_terminal);
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

        performConfirmHaptic(tappedCard);
        updateChoicePageSelectionState(true);
        animateChoiceBounce(tappedCard, BabyPreferences.areAnimationsEnabled(mContext));

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

        bindTryPageContent();
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

    // --- Try page ---

    private void bindTryPage(@NonNull View view) {
        MaterialButton runButton = view.findViewById(R.id.onboarding_try_run_button);
        runButton.setOnClickListener(v -> {
            if (mTryRunning || mTryDone) return;
            mTryRunning = true;
            runButton.setEnabled(false);
            runButton.setText(R.string.baby_onboarding_try_running);
            mListener.onTryRunRequested();
        });
        bindTryPageContent();
    }

    /** (Re)fills the try page's command + part meanings for the current goal. */
    public void bindTryPageContent() {
        View view = mInstantiatedViews.get(PAGE_TRY);
        if (view == null) return;
        TextView commandView = view.findViewById(R.id.onboarding_try_command);
        TextView tokensView = view.findViewById(R.id.onboarding_try_tokens);
        commandView.setText(BabyActionCatalog.truncateForDisplay(getTryCommand()));
        StringBuilder tokens = new StringBuilder();
        for (BabyAction.TokenNote note : BabyActionCatalog.getTokenNotes(getTryAction().id)) {
            if (tokens.length() > 0) tokens.append('\n');
            tokens.append(note.token).append(" — ").append(mContext.getString(note.meaningResId));
        }
        tokensView.setText(tokens.toString());
        tokensView.setVisibility(tokens.length() > 0 ? View.VISIBLE : View.GONE);
    }

    /** Shows the try run's outcome: friendly summary first, raw output under it. */
    public void showTryResult(@NonNull String summary, @Nullable String rawOutput) {
        mTryRunning = false;
        mTryDone = true;
        View view = mInstantiatedViews.get(PAGE_TRY);
        if (view == null) return;
        MaterialButton runButton = view.findViewById(R.id.onboarding_try_run_button);
        runButton.setVisibility(View.GONE);
        TextView resultView = view.findViewById(R.id.onboarding_try_result);
        resultView.setText(summary + "\n\n" + mContext.getString(R.string.baby_onboarding_try_done));
        resultView.setVisibility(View.VISIBLE);
        TextView rawView = view.findViewById(R.id.onboarding_try_raw);
        if (rawOutput != null && !rawOutput.trim().isEmpty()) {
            String raw = rawOutput.trim();
            if (raw.length() > 300) raw = raw.substring(0, 300) + "…";
            rawView.setText(raw);
            rawView.setVisibility(View.VISIBLE);
        } else {
            rawView.setVisibility(View.GONE);
        }
    }

    // ---- Meaningful Page Animations (only when motion is enabled) ----

    private void animateHookPage(@NonNull View view, boolean animate) {
        View visual = view.findViewById(R.id.onboarding_visual_container);
        if (visual == null) return;
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
        View praise = view.findViewById(R.id.onboarding_praise_container);
        if (praise != null) praise.animate().cancel();
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
