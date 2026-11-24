package com.termux.app.baby;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * One background-capable Baby Mode run (a card action, a tool install, an agent
 * install). The session owns the execution and its output, independent of any
 * UI: the runner sheet is just a view onto a session, so closing the sheet no
 * longer kills the work — the session keeps running and can be re-opened from
 * the session bar. All callbacks arrive on the main thread (the executor
 * dispatches there), so listeners are always notified on the main thread.
 */
public final class BabyRunSession {

    public enum State {
        RUNNING,
        SUCCESS,
        FAILED,
        CANCELLED
    }

    public interface Listener {
        void onOutputChanged(@NonNull BabyRunSession session);

        void onStateChanged(@NonNull BabyRunSession session);
    }

    private static final int MAX_OUTPUT_CHARS = 65536;

    public final long id;
    @NonNull
    public final String title;
    @DrawableRes
    public final int iconResId;
    @NonNull
    public final String command;
    @NonNull
    public final BabyAction.OutputFormat outputFormat;
    public final int timeoutSeconds;
    @Nullable
    public final String displayName;

    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final StringBuilder outputBuffer = new StringBuilder();

    // Volatile: callbacks are main-thread today only because the production
    // executor dispatches there; these fields must stay safe even if a
    // future executor delivers from a background thread.
    @NonNull
    private volatile State state = State.RUNNING;
    @Nullable
    private volatile String lastLine;
    @Nullable
    private volatile BabyCommandExecutor.ExecutionResult result;
    @Nullable
    private volatile BabyCommandExecutor.ExecutionHandle handle;
    private volatile boolean finishedSeen;
    private volatile boolean completionHandled;

    BabyRunSession(
        long id,
        @NonNull String title,
        @DrawableRes int iconResId,
        @NonNull String command,
        @NonNull BabyAction.OutputFormat outputFormat,
        int timeoutSeconds,
        @Nullable String displayName
    ) {
        this.id = id;
        this.title = title;
        this.iconResId = iconResId;
        this.command = command;
        this.outputFormat = outputFormat;
        this.timeoutSeconds = timeoutSeconds;
        this.displayName = displayName;
    }

    public void addListener(@NonNull Listener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(@NonNull Listener listener) {
        listeners.remove(listener);
    }

    @NonNull
    public State getState() {
        return state;
    }

    public boolean isRunning() {
        return state == State.RUNNING;
    }

    @Nullable
    public BabyCommandExecutor.ExecutionResult getResult() {
        return result;
    }

    @NonNull
    public synchronized String getOutputText() {
        return outputBuffer.toString();
    }

    @Nullable
    public synchronized String getLastLine() {
        return lastLine;
    }

    /** True once a finished session has been presented to the user. */
    public boolean isFinishedSeen() {
        return finishedSeen;
    }

    public void markFinishedSeen() {
        this.finishedSeen = true;
    }

    /** Bookkeeping guard so the controller reacts to completion exactly once. */
    public boolean isCompletionHandled() {
        return completionHandled;
    }

    public void markCompletionHandled() {
        this.completionHandled = true;
    }

    public void start(@NonNull BabyCommandExecutor executor) {
        handle = executor.executeAsync(command, timeoutSeconds, new BabyCommandExecutor.Callback() {
            @Override
            public void onOutputLine(@NonNull String line) {
                synchronized (BabyRunSession.this) {
                    if (outputBuffer.length() > 0) {
                        outputBuffer.append('\n');
                    }
                    int remaining = MAX_OUTPUT_CHARS - outputBuffer.length();
                    if (remaining > 0) {
                        outputBuffer.append(line, 0, Math.min(line.length(), remaining));
                    }
                    lastLine = line;
                }
                for (Listener listener : listeners) {
                    listener.onOutputChanged(BabyRunSession.this);
                }
            }

            @Override
            public void onComplete(@NonNull BabyCommandExecutor.ExecutionResult executionResult) {
                result = executionResult;
                handle = null;
                if (executionResult.cancelled) {
                    state = State.CANCELLED;
                } else if (executionResult.isSuccess()) {
                    state = State.SUCCESS;
                } else {
                    state = State.FAILED;
                }
                for (Listener listener : listeners) {
                    listener.onStateChanged(BabyRunSession.this);
                }
            }
        });
    }

    public void cancel() {
        BabyCommandExecutor.ExecutionHandle h = handle;
        if (h != null) {
            h.cancel();
        }
    }
}
