package com.termux.app.baby.onboarding;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.button.MaterialButton;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.baby.BabyAction;
import com.termux.app.baby.BabyPreferences;
import com.termux.shared.activity.media.AppCompatActivityUtils;
import com.termux.shared.theme.NightMode;

/**
 * 3-page first-run onboarding for Termux Baby: the concept ("commands are
 * shown, not hidden"), a 3-choice goal selector with immediate tactile +
 * praise response and a 950ms linger, then a try page where the user runs
 * one real command before reaching Home. Neutral skip/back navigation,
 * clean finish into Baby Mode.
 */
public class BabyOnboardingActivity extends AppCompatActivity
    implements BabyOnboardingPagerAdapter.OnboardingInteractionListener {

    /** Recommended request code when launching from TermuxActivity via startActivityForResult. */
    public static final int REQUEST_CODE_ONBOARDING = 4101;

    /** Optional boolean extra: when true, explicitly starts TermuxActivity upon completion. */
    public static final String EXTRA_LAUNCH_TERMUX_ON_FINISH = "com.termux.baby.LAUNCH_TERMUX_ON_FINISH";

    private static final String STATE_CURRENT_PAGE = "state_current_page";
    private static final String STATE_SELECTED_GOAL = "state_selected_goal";
    private static final String STATE_HAS_SELECTED_GOAL = "state_has_selected_goal";
    private static final String STATE_TRY_DONE = "state_try_done";
    private static final String STATE_TRY_FAILED = "state_try_failed";
    private static final String STATE_TRY_SUMMARY = "state_try_summary";
    private static final String STATE_TRY_RAW = "state_try_raw";

    private static final long CHOICE_LINGER_BEFORE_ADVANCE_MS = 950L;
    private static final long COMPLETION_PULSE_DELAY_MS = 180L;

    private ViewPager mPager;
    private BabyOnboardingPagerAdapter mAdapter;
    private WindowDotsIndicatorView mDotsIndicator;
    private MaterialButton mBackButton;
    private MaterialButton mSkipButton;
    private MaterialButton mNextButton;

    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    @Nullable
    private Runnable mPendingAutoAdvanceRunnable;

    @Nullable
    private com.termux.app.baby.BabyCommandExecutor mTryExecutor;

    /**
     * Try-run state, held here (not in the adapter) so it survives
     * adapter/activity recreation; saved/restored with the instance state.
     */
    private final BabyOnboardingTryState mTryState = new BabyOnboardingTryState();

    private int mCurrentPage = BabyOnboardingPagerAdapter.PAGE_HOOK;
    @NonNull
    private String mSelectedGoal = BabyPreferences.GOAL_EXPLORE;
    private boolean mHasUserSelectedGoal = false;
    private boolean mWasAlreadyCompleted = false;
    private boolean mIsFinishingOnboarding = false;
    private boolean mLaunchTermuxOnFinish = false;

    /** Returns true if first-run onboarding has not yet been completed or skipped. */
    public static boolean shouldShowOnboarding(@NonNull Context context) {
        return !BabyPreferences.isOnboardingCompleted(context);
    }

    /** Creates an intent to show onboarding (returns to caller via finish() + RESULT_OK). */
    @NonNull
    public static Intent createIntent(@NonNull Context context) {
        return createIntent(context, false);
    }

    /**
     * Creates an intent to show onboarding.
     *
     * @param launchTermuxOnFinish If true (e.g. when opened from Settings), brings TermuxActivity
     *                             to front in Baby Mode when onboarding completes.
     */
    @NonNull
    public static Intent createIntent(@NonNull Context context, boolean launchTermuxOnFinish) {
        Intent intent = new Intent(context, BabyOnboardingActivity.class);
        intent.putExtra(EXTRA_LAUNCH_TERMUX_ON_FINISH, launchTermuxOnFinish);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AppCompatActivityUtils.setNightMode(this, NightMode.getAppNightMode().getName(), true);
        setContentView(R.layout.activity_baby_onboarding);
        configureSystemBarsAppearance();

        mLaunchTermuxOnFinish = getIntent().getBooleanExtra(EXTRA_LAUNCH_TERMUX_ON_FINISH, false);
        mWasAlreadyCompleted = BabyPreferences.isOnboardingCompleted(this);

        if (savedInstanceState != null) {
            mCurrentPage = savedInstanceState.getInt(STATE_CURRENT_PAGE, BabyOnboardingPagerAdapter.PAGE_HOOK);
            String savedGoal = savedInstanceState.getString(STATE_SELECTED_GOAL, BabyPreferences.GOAL_EXPLORE);
            mSelectedGoal = savedGoal != null ? savedGoal : BabyPreferences.GOAL_EXPLORE;
            mHasUserSelectedGoal = savedInstanceState.getBoolean(STATE_HAS_SELECTED_GOAL, false);
            // A run that was in flight when the activity died never
            // completes (its executor was shut down) and was never
            // counted, so it comes back as retryable, not as "running".
            mTryState.done = savedInstanceState.getBoolean(STATE_TRY_DONE, false);
            mTryState.failed = savedInstanceState.getBoolean(STATE_TRY_FAILED, false);
            mTryState.summary = savedInstanceState.getString(STATE_TRY_SUMMARY, null);
            mTryState.rawOutput = savedInstanceState.getString(STATE_TRY_RAW, null);
        }

        mPager = findViewById(R.id.baby_onboarding_pager);
        mDotsIndicator = findViewById(R.id.baby_onboarding_dots);
        mBackButton = findViewById(R.id.baby_onboarding_back_button);
        mSkipButton = findViewById(R.id.baby_onboarding_skip_button);
        mNextButton = findViewById(R.id.baby_onboarding_next_button);

        mAdapter = new BabyOnboardingPagerAdapter(this, this, mTryState);
        mAdapter.setSelectedGoal(mSelectedGoal, mHasUserSelectedGoal);

        mPager.setAdapter(mAdapter);
        mPager.setOffscreenPageLimit(2);
        mPager.setCurrentItem(mCurrentPage, false);

        mDotsIndicator.setPageCount(BabyOnboardingPagerAdapter.PAGE_COUNT);
        mDotsIndicator.setCurrentPage(mCurrentPage, false);

        mPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                cancelPendingAutoAdvance();
                int previous = mCurrentPage;
                mCurrentPage = position;
                mAdapter.onPageLeft(previous);
                mDotsIndicator.setCurrentPage(position, true);
                updateChromeForPage(position);
                mAdapter.onPageBecameVisible(position);
                announceCurrentPageHeadline(position);
            }

            @Override
            public void onPageScrollStateChanged(int state) {
                if (state == ViewPager.SCROLL_STATE_DRAGGING) {
                    // Respect user gesture immediately during choice linger
                    cancelPendingAutoAdvance();
                }
            }
        });

        // Neutral Back button (no praise, no confirm haptic)
        mBackButton.setOnClickListener(v -> {
            cancelPendingAutoAdvance();
            if (mCurrentPage > 0) {
                mPager.setCurrentItem(mCurrentPage - 1, BabyPreferences.areAnimationsEnabled(this));
            }
        });

        // Neutral Skip button (no praise, no guilt, marks onboarding completed)
        mSkipButton.setOnClickListener(v -> {
            cancelPendingAutoAdvance();
            completeOnboarding(false);
        });

        // Primary Next / Start button
        mNextButton.setOnClickListener(v -> {
            if (mIsFinishingOnboarding) return;
            cancelPendingAutoAdvance();
            if (mCurrentPage < BabyOnboardingPagerAdapter.PAGE_COUNT - 1) {
                BabyOnboardingPagerAdapter.performGentleHaptic(v);
                pulseButtonLightly(v);
                mPager.setCurrentItem(mCurrentPage + 1, BabyPreferences.areAnimationsEnabled(this));
            } else {
                // Final CTA: celebratory confirm haptic + gentle pulse + launch Baby Home
                BabyOnboardingPagerAdapter.performConfirmHaptic(v);
                completeOnboarding(true);
            }
        });

        updateChromeForPage(mCurrentPage);
        mPager.post(() -> mAdapter.onPageBecameVisible(mCurrentPage));
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_CURRENT_PAGE, mCurrentPage);
        outState.putString(STATE_SELECTED_GOAL, mSelectedGoal);
        outState.putBoolean(STATE_HAS_SELECTED_GOAL, mHasUserSelectedGoal);
        outState.putBoolean(STATE_TRY_DONE, mTryState.done);
        outState.putBoolean(STATE_TRY_FAILED, mTryState.failed);
        outState.putString(STATE_TRY_SUMMARY, mTryState.summary);
        outState.putString(STATE_TRY_RAW, mTryState.rawOutput);
    }

    @Override
    protected void onDestroy() {
        cancelPendingAutoAdvance();
        mMainHandler.removeCallbacksAndMessages(null);
        if (mTryExecutor != null) {
            mTryExecutor.shutdown();
            mTryExecutor = null;
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        cancelPendingAutoAdvance();
        if (mCurrentPage > 0) {
            mPager.setCurrentItem(mCurrentPage - 1, BabyPreferences.areAnimationsEnabled(this));
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onGoalChoiceSelected(@NonNull String goal) {
        mSelectedGoal = goal;
        mHasUserSelectedGoal = true;
        BabyPreferences.setOnboardingGoal(this, goal);

        // Cancel any previous timer to prevent double-advance on rapid taps
        cancelPendingAutoAdvance();

        // Linger 950ms so the user reads the specific praise before advancing
        // to the try-it page, where they run their first real command.
        mPendingAutoAdvanceRunnable = () -> {
            mPendingAutoAdvanceRunnable = null;
            if (!isFinishing() && mCurrentPage == BabyOnboardingPagerAdapter.PAGE_CHOICE) {
                mPager.setCurrentItem(
                    BabyOnboardingPagerAdapter.PAGE_TRY,
                    BabyPreferences.areAnimationsEnabled(BabyOnboardingActivity.this)
                );
            }
        };
        mMainHandler.postDelayed(mPendingAutoAdvanceRunnable, CHOICE_LINGER_BEFORE_ADVANCE_MS);
    }

    @Override
    public void onPraiseTappedToAdvance() {
        if (mPendingAutoAdvanceRunnable != null && mCurrentPage == BabyOnboardingPagerAdapter.PAGE_CHOICE) {
            cancelPendingAutoAdvance();
            mPager.setCurrentItem(
                BabyOnboardingPagerAdapter.PAGE_TRY,
                BabyPreferences.areAnimationsEnabled(this)
            );
        }
    }

    /**
     * Runs the try page's real command on a background thread and shows the
     * outcome: friendly summary + the raw output, exactly like the runner
     * sheet on Home. A success is recorded as the item's first run, so the
     * home card already carries its first result when the user arrives.
     */
    @Override
    public void onTryRunRequested() {
        if (mTryState.done) return;
        final BabyAction action = mAdapter.getTryAction();
        final String command = mAdapter.getTryCommand();
        if (mTryExecutor == null) {
            mTryExecutor = new com.termux.app.baby.BabyCommandExecutor();
        }
        final com.termux.app.baby.BabyCommandExecutor executor = mTryExecutor;
        // Gate on the environment: while the bootstrap is still being
        // extracted there is no shell to run, and the attempt would only
        // fail. Say it is still preparing instead and keep the retry.
        if (!executor.isBootstrapReady()) {
            mAdapter.showTryNotReady();
            return;
        }
        executor.executeAsync(command, action.timeoutSeconds,
            new com.termux.app.baby.BabyCommandExecutor.Callback() {
                @Override
                public void onOutputLine(@NonNull String line) {
                }

                @Override
                public void onComplete(
                    @NonNull com.termux.app.baby.BabyCommandExecutor.ExecutionResult result) {
                    if (isFinishing() || isDestroyed()) return;
                    String summary;
                    if (result.isSuccess()) {
                        com.termux.app.baby.BabyOutputFormatter.ParsedSummary parsed =
                            com.termux.app.baby.BabyOutputFormatter.parse(
                                action.outputFormat, result.stdout, result.stderr, result.exitCode);
                        summary = com.termux.app.baby.BabyOutputFormatter.formatForDisplay(
                            BabyOnboardingActivity.this, parsed, getString(action.titleResId));
                        BabyPreferences.incrementRunCount(
                            BabyOnboardingActivity.this, action.id);
                        BabyPreferences.setFirstActionCompleted(
                            BabyOnboardingActivity.this, true);
                        // The home card carries this first result on arrival.
                        String oneLine = summary;
                        int newline = oneLine.indexOf('\n');
                        if (newline >= 0) oneLine = oneLine.substring(0, newline);
                        BabyPreferences.setLastResult(
                            BabyOnboardingActivity.this, action.id, true, oneLine.trim());
                    } else {
                        summary = com.termux.app.baby.BabyErrorTranslator.getFriendlyMessage(
                            BabyOnboardingActivity.this,
                            com.termux.app.baby.BabyErrorTranslator.classify(result));
                    }
                    mAdapter.showTryResult(summary, result.getCombinedOutput(), result.isSuccess());
                }
            });
    }

    private void cancelPendingAutoAdvance() {
        if (mPendingAutoAdvanceRunnable != null) {
            mMainHandler.removeCallbacks(mPendingAutoAdvanceRunnable);
            mPendingAutoAdvanceRunnable = null;
        }
    }

    private void updateChromeForPage(int position) {
        // Back button: hidden on Page 1, visible on Pages 2..7
        mBackButton.setVisibility(position == BabyOnboardingPagerAdapter.PAGE_HOOK ? View.INVISIBLE : View.VISIBLE);

        // Skip button: always visible on Pages 1..6, hidden on final CTA page
        mSkipButton.setVisibility(position == BabyOnboardingPagerAdapter.PAGE_CTA ? View.INVISIBLE : View.VISIBLE);

        // Next / Start button: strictly hidden on the 3-choice page so tapping a choice IS the forward action
        if (position == BabyOnboardingPagerAdapter.PAGE_CHOICE) {
            mNextButton.setVisibility(View.INVISIBLE);
            mNextButton.setEnabled(false);
        } else {
            mNextButton.setVisibility(View.VISIBLE);
            mNextButton.setEnabled(true);
            if (position == BabyOnboardingPagerAdapter.PAGE_CTA) {
                mNextButton.setText(R.string.baby_onboarding_start);
            } else {
                mNextButton.setText(R.string.baby_onboarding_next);
            }
        }
    }

    private void completeOnboarding(boolean celebratedFinish) {
        if (mIsFinishingOnboarding) return;
        mIsFinishingOnboarding = true;

        // Only write the goal when the user actually picked one in this
        // run — it is already persisted at pick time, and writing the
        // in-memory default here would clobber a previously saved goal
        // when onboarding is skipped or replayed.
        if (mHasUserSelectedGoal) {
            BabyPreferences.setOnboardingGoal(this, mSelectedGoal);
        }
        BabyPreferences.setOnboardingCompleted(this, true);
        // First run lands in Baby Mode; a replay from Settings must not
        // force a Pro-mode user back into Baby Mode.
        if (!mWasAlreadyCompleted) {
            BabyPreferences.setBabyMode(this, true);
        }

        setResult(RESULT_OK);

        if (celebratedFinish && BabyPreferences.areAnimationsEnabled(this)) {
            pulseButtonLightly(mNextButton);
            mMainHandler.postDelayed(this::finishIntoBabyHome, COMPLETION_PULSE_DELAY_MS);
        } else {
            finishIntoBabyHome();
        }
    }

    private void finishIntoBabyHome() {
        if (mLaunchTermuxOnFinish || isTaskRoot()) {
            Intent intent = new Intent(this, TermuxActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        }
        finish();
    }

    private void pulseButtonLightly(@NonNull View view) {
        if (!BabyPreferences.areAnimationsEnabled(this)) return;
        view.animate().cancel();
        view.animate()
            .scaleX(0.97f)
            .scaleY(0.97f)
            .setDuration(90L)
            .setInterpolator(new DecelerateInterpolator())
            .withEndAction(() -> view.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(120L)
                .setInterpolator(new DecelerateInterpolator())
                .start())
            .start();
    }

    private void announceCurrentPageHeadline(int position) {
        CharSequence headline = mAdapter.getPageHeadline(position);
        mPager.announceForAccessibility(headline);
    }

    private void configureSystemBarsAppearance() {
        Window window = getWindow();
        if (window == null) return;
        int nightMask = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        boolean isNight = (nightMask == Configuration.UI_MODE_NIGHT_YES);
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(window, window.getDecorView());
        controller.setAppearanceLightStatusBars(!isNight);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            controller.setAppearanceLightNavigationBars(!isNight);
        }
    }
}
