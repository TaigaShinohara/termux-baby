package com.termux.app.baby;

import androidx.annotation.NonNull;

/**
 * A user-registered shortcut command for the Easy Mode home grid: the
 * advanced-user half of Easy Mode. Persisted by {@link BabyPreferences}.
 */
public final class BabyCustomCommand {

    @NonNull
    public final String id;
    @NonNull
    public final String label;
    @NonNull
    public final String command;

    public BabyCustomCommand(@NonNull String id, @NonNull String label, @NonNull String command) {
        this.id = id;
        this.label = label;
        this.command = command;
    }
}
