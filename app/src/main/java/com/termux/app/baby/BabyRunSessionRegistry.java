package com.termux.app.baby;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Holds every {@link BabyRunSession} the user may still care about: sessions
 * that are running, plus finished sessions they have not looked at yet. The
 * session bar renders straight from this registry.
 */
public final class BabyRunSessionRegistry {

    public interface Listener {
        void onSessionsChanged();
    }

    /** Finished sessions kept for the bar; older ones are pruned on create. */
    private static final int MAX_FINISHED_RETAINED = 5;

    // Guarded by synchronized blocks on `sessions` itself: callbacks are
    // main-thread by construction today, but the registry must not silently
    // corrupt if that ever changes.
    private final List<BabyRunSession> sessions = new ArrayList<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private long nextId = 1;

    private final BabyRunSession.Listener sessionForwarder = new BabyRunSession.Listener() {
        @Override
        public void onOutputChanged(@NonNull BabyRunSession session) {
            notifyChanged();
        }

        @Override
        public void onStateChanged(@NonNull BabyRunSession session) {
            notifyChanged();
        }
    };

    public void addListener(@NonNull Listener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(@NonNull Listener listener) {
        listeners.remove(listener);
    }

    @NonNull
    public BabyRunSession create(
        @NonNull String title,
        int iconResId,
        @NonNull String command,
        @NonNull BabyAction.OutputFormat outputFormat,
        int timeoutSeconds,
        @Nullable String displayName
    ) {
        BabyRunSession session;
        synchronized (sessions) {
            session = new BabyRunSession(
                nextId++, title, iconResId, command, outputFormat, timeoutSeconds, displayName
            );
            session.addListener(sessionForwarder);
            sessions.add(session);
            pruneLocked();
        }
        notifyChanged();
        return session;
    }

    public void remove(@NonNull BabyRunSession session) {
        boolean removed;
        synchronized (sessions) {
            removed = sessions.remove(session);
        }
        if (removed) {
            session.removeListener(sessionForwarder);
            notifyChanged();
        }
    }

    /** Drops finished sessions the user has already seen; running ones stay. */
    public void pruneSeenFinished() {
        if (pruneSeenFinishedQuietly()) {
            notifyChanged();
        }
    }

    /**
     * Same pruning without the change notification — for callers that are
     * already rebuilding their view in the same pass. Returns true if
     * anything was removed.
     */
    public boolean pruneSeenFinishedQuietly() {
        synchronized (sessions) {
            return pruneLocked();
        }
    }

    /**
     * Bounds registry growth: seen finished sessions go first, then the
     * oldest finished ones beyond {@link #MAX_FINISHED_RETAINED} (each can
     * hold ~192 KB of output). Caller must hold the sessions lock.
     */
    private boolean pruneLocked() {
        boolean changed = false;
        for (int i = sessions.size() - 1; i >= 0; i--) {
            BabyRunSession session = sessions.get(i);
            if (!session.isRunning() && session.isFinishedSeen()) {
                sessions.remove(i);
                session.removeListener(sessionForwarder);
                changed = true;
            }
        }
        int finishedCount = 0;
        for (BabyRunSession session : sessions) {
            if (!session.isRunning()) finishedCount++;
        }
        for (int i = 0; i < sessions.size() && finishedCount > MAX_FINISHED_RETAINED; i++) {
            BabyRunSession session = sessions.get(i);
            if (!session.isRunning()) {
                sessions.remove(i);
                session.removeListener(sessionForwarder);
                finishedCount--;
                changed = true;
                i--;
            }
        }
        return changed;
    }

    @NonNull
    public List<BabyRunSession> getSessions() {
        synchronized (sessions) {
            return Collections.unmodifiableList(new ArrayList<>(sessions));
        }
    }

    public boolean hasRunning() {
        synchronized (sessions) {
            for (BabyRunSession session : sessions) {
                if (session.isRunning()) return true;
            }
        }
        return false;
    }

    private void notifyChanged() {
        for (Listener listener : listeners) {
            listener.onSessionsChanged();
        }
    }
}
