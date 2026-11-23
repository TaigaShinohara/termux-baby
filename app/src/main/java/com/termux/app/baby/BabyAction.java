package com.termux.app.baby;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

/**
 * Immutable definition of a beginner-friendly Baby Mode action card or Command Helper recipe.
 */
public final class BabyAction {

    public enum ActionType {
        /** Runs a safe command immediately in BabyCommandRunnerSheet. */
        DIRECT_COMMAND,
        /** Opens the guided Tool Installer sheet. */
        TOOL_INSTALLER,
        /** Opens the Quick Note sheet. */
        QUICK_NOTE,
        /** Checks Python installation and runs a friendly sample or offers 1-tap install. */
        TRY_PYTHON,
        /** Prompts the user for a single plain-language argument before safely running. */
        COMMAND_WITH_INPUT
    }

    public enum OutputFormat {
        FILES,
        NETWORK,
        DEVICE_INFO,
        PYTHON,
        INSTALL,
        GENERIC
    }

    public enum Category {
        ALL,
        FILES,
        NETWORK,
        LEARN,
        TOOLS
    }

    @NonNull
    public final String id;
    @StringRes
    public final int titleResId;
    @StringRes
    public final int subtitleResId;
    @DrawableRes
    public final int iconResId;
    @NonNull
    public final ActionType actionType;
    @NonNull
    public final Category category;
    @Nullable
    public final String commandTemplate;
    @NonNull
    public final OutputFormat outputFormat;
    @StringRes
    public final int inputLabelResId;
    @StringRes
    public final int inputHintResId;
    public final int timeoutSeconds;

    public BabyAction(
        @NonNull String id,
        @StringRes int titleResId,
        @StringRes int subtitleResId,
        @DrawableRes int iconResId,
        @NonNull ActionType actionType,
        @NonNull Category category,
        @Nullable String commandTemplate,
        @NonNull OutputFormat outputFormat,
        @StringRes int inputLabelResId,
        @StringRes int inputHintResId,
        int timeoutSeconds
    ) {
        this.id = id;
        this.titleResId = titleResId;
        this.subtitleResId = subtitleResId;
        this.iconResId = iconResId;
        this.actionType = actionType;
        this.category = category;
        this.commandTemplate = commandTemplate;
        this.outputFormat = outputFormat;
        this.inputLabelResId = inputLabelResId;
        this.inputHintResId = inputHintResId;
        this.timeoutSeconds = timeoutSeconds;
    }

    public boolean requiresUserInput() {
        return actionType == ActionType.COMMAND_WITH_INPUT;
    }
}
