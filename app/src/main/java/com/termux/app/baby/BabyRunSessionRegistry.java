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
        BabyRunSession session = new BabyRunSession(
            nextId++, title, iconResId, command, outputFormat, timeoutSeconds, displayName
        );
        session.addListener(sessionForwarder);
        sessions.add(session);
        notifyChanged();
        return session;
    }

    public void remove(@NonNull BabyRunSession session) {
        if (sessions.remove(session)) {
            session.removeListener(sessionForwarder);
            notifyChanged();
        }
    }

    /** Drops finished sessions the user has already seen; running ones stay. */
    public void pruneSeenFinished() {
        boolean changed = false;
        for (int i = sessions.size() - 1; i >= 0; i--) {
            BabyRunSession session = sessions.get(i);
            if (!session.isRunning() && session.isFinishedSeen()) {
                sessions.remove(i);
                session.removeListener(sessionForwarder);
                changed = true;
            }
        }
        if (changed) {
            notifyChanged();
        }
    }

    @NonNull
    public List<BabyRunSession> getSessions() {
        return Collections.unmodifiableList(new ArrayList<>(sessions));
    }

    public boolean hasRunning() {
        for (BabyRunSession session : sessions) {
            if (session.isRunning()) return true;
        }
        return false;
    }

    private void notifyChanged() {
        for (Listener listener : listeners) {
            listener.onSessionsChanged();
        }
    }
}
