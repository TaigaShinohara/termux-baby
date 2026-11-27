package com.termux.app.baby;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Persistent preferences for Termux Baby mode, onboarding state, and beginner guidance.
 *
 * <p>All setters use {@code commit()}, not {@code apply()}: these flags are
 * one-way transitions (mode switch, onboarding done, first action done) and
 * losing one to a process kill right after the write would re-onboard the
 * user or resurrect the previous mode. The file is tiny, so the synchronous
 * write is cheap. This class is also the ONLY access point for the
 * {@code termux_baby_prefs} file — never open it by name elsewhere.
 */
public final class BabyPreferences {

    private static final String PREFS_NAME = "termux_baby_prefs";

    private static final String KEY_BABY_MODE_ENABLED = "baby_mode_enabled";
    private static final String KEY_ONBOARDING_COMPLETED = "onboarding_completed";
    private static final String KEY_ONBOARDING_GOAL = "onboarding_goal";
    private static final String KEY_FIRST_ACTION_COMPLETED = "first_action_completed";
    private static final String KEY_EXTRA_KEYS_PRESET = "extra_keys_preset";
    private static final String KEY_HUSHLOGIN_INITIALIZED = "hushlogin_initialized";
    private static final String KEY_PINNED_IDS = "pinned_ids";
    private static final String KEY_CUSTOM_COMMANDS = "custom_commands";
    private static final String KEY_CUSTOM_COMMANDS_BACKUP = "custom_commands_corrupt_backup";
    private static final String KEY_DANGER_CONFIRM_ENABLED = "danger_confirm_enabled";
    private static final String KEY_DANGER_PATTERNS = "danger_patterns";
    private static final String KEY_RUN_COUNT_PREFIX = "run_count_";
    private static final String KEY_LAST_RESULT_PREFIX = "last_result_";
    private static final String KEY_LAST_RESULT_OK_PREFIX = "last_result_ok_";

    /** Home grid contents on first run (after the redesign): the learnable core. */
    public static final String DEFAULT_PINNED_IDS =
        "see_files,recipe_pwd,check_network,device_info,try_python,install_tools";

    /** Extra keys preset: plain 2-row × 7 control panel — control keys and arrows (default). */
    public static final String EXTRA_KEYS_PRESET_SIMPLE = "simple";
    /** Extra keys preset: 2-row × 6 spacious keys with inverted-T arrows. */
    public static final String EXTRA_KEYS_PRESET_BALANCED = "balanced";
    /** Extra keys preset: original Termux 2-row × 7 keys (14 keys). */
    public static final String EXTRA_KEYS_PRESET_CLASSIC = "classic";

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
        getPrefs(context).edit().putBoolean(KEY_BABY_MODE_ENABLED, enabled).commit();
    }

    /** Returns true if the user has completed or skipped first-run onboarding. */
    public static boolean isOnboardingCompleted(@NonNull Context context) {
        return getPrefs(context).getBoolean(KEY_ONBOARDING_COMPLETED, false);
    }

    /** Marks first-run onboarding as completed (or not completed). */
    public static void setOnboardingCompleted(@NonNull Context context, boolean completed) {
        getPrefs(context).edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).commit();
    }

    /** Resets onboarding completion so it can be viewed again from Settings. */
    public static void resetOnboarding(@NonNull Context context) {
        getPrefs(context).edit()
            .putBoolean(KEY_ONBOARDING_COMPLETED, false)
            .commit();
    }

    /** Returns the user's selected onboarding goal, or {@link #GOAL_EXPLORE} if unset. */
    @NonNull
    public static String getOnboardingGoal(@NonNull Context context) {
        String goal = getPrefs(context).getString(KEY_ONBOARDING_GOAL, GOAL_EXPLORE);
        return goal != null ? goal : GOAL_EXPLORE;
    }

    /** Saves the user's selected onboarding goal (use GOAL_* constants). */
    public static void setOnboardingGoal(@NonNull Context context, @NonNull String goal) {
        // While the first onboarding is still running, the chosen goal
        // leads the default pin set (KEY_PINNED_IDS stays unset and the
        // pins are derived from the goal). Once onboarding is complete the
        // home grid is the user's own, so snapshot the pins they currently
        // see before a goal re-pick: re-picking a goal must never silently
        // rearrange the home of someone who never edited pins.
        if (isOnboardingCompleted(context)
            && getPrefs(context).getString(KEY_PINNED_IDS, null) == null) {
            setPinnedIds(context, getPinnedIds(context));
        }
        getPrefs(context).edit().putString(KEY_ONBOARDING_GOAL, goal).commit();
    }

    /** Returns true once the user has successfully completed at least one Baby Mode action. */
    public static boolean hasCompletedFirstAction(@NonNull Context context) {
        return getPrefs(context).getBoolean(KEY_FIRST_ACTION_COMPLETED, false);
    }

    /** Marks that the user has completed their first Baby Mode action. */
    public static void setFirstActionCompleted(@NonNull Context context, boolean completed) {
        getPrefs(context).edit().putBoolean(KEY_FIRST_ACTION_COMPLETED, completed).commit();
    }

    /**
     * Returns the selected Extra Keys layout preset ({@link #EXTRA_KEYS_PRESET_SIMPLE},
     * {@link #EXTRA_KEYS_PRESET_BALANCED}, or {@link #EXTRA_KEYS_PRESET_CLASSIC}).
     * Defaults to {@link #EXTRA_KEYS_PRESET_SIMPLE}.
     */
    @NonNull
    public static String getExtraKeysPreset(@NonNull Context context) {
        return normalizeExtraKeysPreset(getPrefs(context).getString(KEY_EXTRA_KEYS_PRESET, EXTRA_KEYS_PRESET_SIMPLE));
    }

    /** Saves the selected Extra Keys layout preset. Unknown values fall back to {@link #EXTRA_KEYS_PRESET_SIMPLE}. */
    public static void setExtraKeysPreset(@NonNull Context context, @Nullable String preset) {
        getPrefs(context).edit().putString(KEY_EXTRA_KEYS_PRESET, normalizeExtraKeysPreset(preset)).commit();
    }

    /** Normalizes any preset string to a valid preset constant, defaulting to {@link #EXTRA_KEYS_PRESET_SIMPLE}. */
    @NonNull
    public static String normalizeExtraKeysPreset(@Nullable String preset) {
        if (EXTRA_KEYS_PRESET_BALANCED.equals(preset)) {
            return EXTRA_KEYS_PRESET_BALANCED;
        }
        if (EXTRA_KEYS_PRESET_CLASSIC.equals(preset)) {
            return EXTRA_KEYS_PRESET_CLASSIC;
        }
        return EXTRA_KEYS_PRESET_SIMPLE;
    }

    /**
     * Returns the BabyAction ID to highlight on the Baby Home screen based on the user's
     * onboarding choice, or null once the user has already completed their first action.
     * Each goal recommends a DIFFERENT first item.
     */
    @Nullable
    public static String getRecommendedActionId(@NonNull Context context) {
        if (hasCompletedFirstAction(context)) {
            return null;
        }
        return getGoalLeadActionId(context);
    }

    /**
     * The item the user's onboarding goal leads with: it heads the default
     * home pins and the "next to learn" path (until mastered), so the goal
     * choice keeps shaping the learning path beyond the first run.
     */
    @Nullable
    public static String getGoalLeadActionId(@NonNull Context context) {
        String goal = getOnboardingGoal(context);
        if (GOAL_TOOLS_PYTHON.equals(goal)) {
            return BabyActionCatalog.ACTION_TRY_PYTHON;
        } else if (GOAL_FILES_DEVICE.equals(goal)) {
            return BabyActionCatalog.ACTION_SEE_FILES;
        } else {
            return "recipe_find";
        }
    }

    /** Default home pins per onboarding goal: the goal's lead comes first. */
    @NonNull
    public static String defaultPinnedIdsForGoal(@NonNull String goal) {
        if (GOAL_TOOLS_PYTHON.equals(goal)) {
            return "try_python,see_files,recipe_pwd,check_network,device_info,install_tools";
        } else if (GOAL_FILES_DEVICE.equals(goal)) {
            return "see_files,recipe_pwd,device_info,recipe_find,check_network,install_tools";
        }
        return "recipe_pwd,see_files,recipe_find,recipe_date,check_network,install_tools";
    }

    // --- Learning progress & home grid state (Easy Mode redesign) ---

    /** How many times this action/recipe/custom command has been run. */
    public static int getRunCount(@NonNull Context context, @NonNull String itemId) {
        return getPrefs(context).getInt(KEY_RUN_COUNT_PREFIX + itemId, 0);
    }

    /** Records one more run of the item and returns the new count. */
    public static int incrementRunCount(@NonNull Context context, @NonNull String itemId) {
        int next = getRunCount(context, itemId) + 1;
        getPrefs(context).edit().putInt(KEY_RUN_COUNT_PREFIX + itemId, next).commit();
        return next;
    }

    /** One-line summary of the item's last run ("12 files", "つながっています"), or null. */
    @Nullable
    public static String getLastResultSummary(@NonNull Context context, @NonNull String itemId) {
        return getPrefs(context).getString(KEY_LAST_RESULT_PREFIX + itemId, null);
    }

    public static boolean getLastResultOk(@NonNull Context context, @NonNull String itemId) {
        return getPrefs(context).getBoolean(KEY_LAST_RESULT_OK_PREFIX + itemId, false);
    }

    public static void setLastResult(
        @NonNull Context context,
        @NonNull String itemId,
        boolean ok,
        @Nullable String summary
    ) {
        SharedPreferences.Editor editor = getPrefs(context).edit()
            .putBoolean(KEY_LAST_RESULT_OK_PREFIX + itemId, ok);
        if (summary != null) {
            editor.putString(KEY_LAST_RESULT_PREFIX + itemId, summary);
        } else {
            // A null summary must clear the old text: leaving it would pair a
            // stale success sentence with the fresh ok/fail mark.
            editor.remove(KEY_LAST_RESULT_PREFIX + itemId);
        }
        editor.commit();
    }

    /** Ordered ids shown on the Easy Home grid (catalog ids and custom ids). */
    @NonNull
    public static java.util.List<String> getPinnedIds(@NonNull Context context) {
        String raw = getPrefs(context).getString(KEY_PINNED_IDS, null);
        if (raw == null) {
            raw = defaultPinnedIdsForGoal(getOnboardingGoal(context));
        }
        java.util.List<String> ids = new java.util.ArrayList<>();
        if (raw != null) {
            for (String part : raw.split(",")) {
                String id = part.trim();
                if (!id.isEmpty() && !ids.contains(id)) ids.add(id);
            }
        }
        return ids;
    }

    public static void setPinnedIds(@NonNull Context context, @NonNull java.util.List<String> ids) {
        getPrefs(context).edit()
            .putString(KEY_PINNED_IDS, android.text.TextUtils.join(",", ids))
            .commit();
    }

    /** User-registered shortcut commands, in registration order. */
    @NonNull
    public static java.util.List<BabyCustomCommand> getCustomCommands(@NonNull Context context) {
        java.util.List<BabyCustomCommand> result = new java.util.ArrayList<>();
        String raw = getPrefs(context).getString(KEY_CUSTOM_COMMANDS, null);
        if (raw == null || raw.isEmpty()) return result;
        try {
            org.json.JSONArray array = new org.json.JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                org.json.JSONObject obj = array.optJSONObject(i);
                if (obj == null) continue;
                String id = obj.optString("id", "");
                String label = obj.optString("label", "");
                String command = obj.optString("command", "");
                if (!id.isEmpty() && !command.isEmpty()) {
                    result.add(new BabyCustomCommand(id, label, command));
                }
            }
        } catch (org.json.JSONException ignored) {
            // Corrupt storage must never crash the home screen; start clean —
            // but keep one copy of the corrupt payload so the data is not
            // silently destroyed when the next save overwrites the key.
            SharedPreferences prefs = getPrefs(context);
            if (prefs.getString(KEY_CUSTOM_COMMANDS_BACKUP, null) == null) {
                prefs.edit().putString(KEY_CUSTOM_COMMANDS_BACKUP, raw).commit();
            }
        }
        return result;
    }

    public static void setCustomCommands(
        @NonNull Context context,
        @NonNull java.util.List<BabyCustomCommand> commands
    ) {
        org.json.JSONArray array = new org.json.JSONArray();
        for (BabyCustomCommand command : commands) {
            org.json.JSONObject obj = new org.json.JSONObject();
            try {
                obj.put("id", command.id);
                obj.put("label", command.label);
                obj.put("command", command.command);
            } catch (org.json.JSONException ignored) {
            }
            array.put(obj);
        }
        getPrefs(context).edit().putString(KEY_CUSTOM_COMMANDS, array.toString()).commit();
    }

    @Nullable
    public static BabyCustomCommand findCustomCommand(@NonNull Context context, @NonNull String id) {
        for (BabyCustomCommand command : getCustomCommands(context)) {
            if (command.id.equals(id)) return command;
        }
        return null;
    }

    /** Returns true once ~/.hushlogin has been created successfully. */
    public static boolean isHushLoginInitialized(@NonNull Context context) {
        return getPrefs(context).getBoolean(KEY_HUSHLOGIN_INITIALIZED, false);
    }

    /** Records that ~/.hushlogin creation succeeded (call only on success). */
    public static void setHushLoginInitialized(@NonNull Context context, boolean initialized) {
        getPrefs(context).edit().putBoolean(KEY_HUSHLOGIN_INITIALIZED, initialized).commit();
    }

    /** Whether dangerous commands ask for confirmation before running. Defaults to on. */
    public static boolean isDangerConfirmEnabled(@NonNull Context context) {
        return getPrefs(context).getBoolean(KEY_DANGER_CONFIRM_ENABLED, true);
    }

    public static void setDangerConfirmEnabled(@NonNull Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_DANGER_CONFIRM_ENABLED, enabled).commit();
    }

    /**
     * The user's dangerous-command patterns (one per line in storage), or the
     * built-in defaults from {@link BabyActionCatalog} while unset. Editable
     * from Settings; matching rules live in BabyActionCatalog.assessDanger.
     */
    @NonNull
    public static java.util.List<String> getDangerPatterns(@NonNull Context context) {
        String raw = getPrefs(context).getString(KEY_DANGER_PATTERNS, null);
        if (raw == null) return BabyActionCatalog.defaultDangerPatterns();
        java.util.List<String> patterns = new java.util.ArrayList<>();
        for (String line : raw.split("\n")) {
            String pattern = line.trim();
            if (!pattern.isEmpty() && !patterns.contains(pattern)) patterns.add(pattern);
        }
        return patterns;
    }

    public static void setDangerPatterns(
        @NonNull Context context,
        @NonNull java.util.List<String> patterns
    ) {
        getPrefs(context).edit()
            .putString(KEY_DANGER_PATTERNS, android.text.TextUtils.join("\n", patterns))
            .commit();
    }

    /** True when the user has saved their own pattern list (defaults otherwise). */
    public static boolean hasCustomDangerPatterns(@NonNull Context context) {
        return getPrefs(context).getString(KEY_DANGER_PATTERNS, null) != null;
    }

    /** Drops the user's pattern list, returning to the built-in defaults. */
    public static void resetDangerPatterns(@NonNull Context context) {
        getPrefs(context).edit().remove(KEY_DANGER_PATTERNS).commit();
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
