package com.termux.app.baby;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Persistent preferences for Termux Baby mode, onboarding state, and beginner guidance.
 */
public final class BabyPreferences {

    private static final String PREFS_NAME = "termux_baby_prefs";

    private static final String KEY_BABY_MODE_ENABLED = "baby_mode_enabled";
    private static final String KEY_ONBOARDING_COMPLETED = "onboarding_completed";
    private static final String KEY_ONBOARDING_GOAL = "onboarding_goal";
    private static final String KEY_FIRST_ACTION_COMPLETED = "first_action_completed";

    /** Goal choice 1: Try tools or Python. */
    public static final String GOAL_TOOLS_PYTHON = "tools_python";
    /** Goal choice 2: Check files or device info easily. */
    public static final String GOAL_FILES_DEVICE = "files_device";
    /** Goal choice 3: Not sure yet / just exploring. */
    public static final String GOAL_EXPLORE = "explore";

    private BabyPreferences() {
    }

    @NonNull
    private static SharedPreferences getPrefs(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** Returns true if Baby Mode (Easy Home) is active. Defaults to true for beginners. */
    public static boolean isBabyMode(@NonNull Context context) {
        return getPrefs(context).getBoolean(KEY_BABY_MODE_ENABLED, true);
    }

    /** Sets whether Baby Mode (Easy Home) is active. */
    public static void setBabyMode(@NonNull Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_BABY_MODE_ENABLED, enabled).apply();
    }

    /** Returns true if the user has completed or skipped first-run onboarding. */
    public static boolean isOnboardingCompleted(@NonNull Context context) {
        return getPrefs(context).getBoolean(KEY_ONBOARDING_COMPLETED, false);
    }

    /** Marks first-run onboarding as completed (or not completed). */
    public static void setOnboardingCompleted(@NonNull Context context, boolean completed) {
        getPrefs(context).edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply();
    }

    /** Resets onboarding completion so it can be viewed again from Settings. */
    public static void resetOnboarding(@NonNull Context context) {
        getPrefs(context).edit()
            .putBoolean(KEY_ONBOARDING_COMPLETED, false)
            .apply();
    }

    /** Returns the user's selected onboarding goal, or {@link #GOAL_EXPLORE} if unset. */
    @NonNull
    public static String getOnboardingGoal(@NonNull Context context) {
        String goal = getPrefs(context).getString(KEY_ONBOARDING_GOAL, GOAL_EXPLORE);
        return goal != null ? goal : GOAL_EXPLORE;
    }

    /** Saves the user's selected onboarding goal (use GOAL_* constants). */
    public static void setOnboardingGoal(@NonNull Context context, @NonNull String goal) {
        getPrefs(context).edit().putString(KEY_ONBOARDING_GOAL, goal).apply();
    }

    /** Returns true once the user has successfully completed at least one Baby Mode action. */
    public static boolean hasCompletedFirstAction(@NonNull Context context) {
        return getPrefs(context).getBoolean(KEY_FIRST_ACTION_COMPLETED, false);
    }

    /** Marks that the user has completed their first Baby Mode action. */
    public static void setFirstActionCompleted(@NonNull Context context, boolean completed) {
        getPrefs(context).edit().putBoolean(KEY_FIRST_ACTION_COMPLETED, completed).apply();
    }

    /**
     * Returns the BabyAction ID to highlight on the Baby Home screen based on the user's
     * onboarding choice, or null once the user has already completed their first action.
     */
    @Nullable
    public static String getRecommendedActionId(@NonNull Context context) {
        if (hasCompletedFirstAction(context)) {
            return null;
        }
        String goal = getOnboardingGoal(context);
        if (GOAL_TOOLS_PYTHON.equals(goal)) {
            return BabyActionCatalog.ACTION_TRY_PYTHON;
        } else if (GOAL_FILES_DEVICE.equals(goal)) {
            return BabyActionCatalog.ACTION_SEE_FILES;
        } else {
            return BabyActionCatalog.ACTION_SEE_FILES;
        }
    }

    /**
     * Returns true if system animations are enabled (animator_duration_scale > 0).
     */
    public static boolean areAnimationsEnabled(@NonNull Context context) {
        try {
            float scale = Settings.Global.getFloat(
                context.getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            );
            return scale > 0f;
        } catch (Exception ignored) {
            return true;
        }
    }
}
