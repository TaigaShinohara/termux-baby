package com.termux.app.baby.onboarding;

import androidx.annotation.Nullable;

/**
 * State of the onboarding "try it" run, owned by
 * {@link BabyOnboardingActivity} and shared with the pager adapter.
 *
 * It lives outside the adapter on purpose: the adapter (and its pages) are
 * recreated when the activity is, and the try state must survive that —
 * otherwise a rotation would reset the page and let the user run (and
 * count) the try command a second time.
 */
public final class BabyOnboardingTryState {

    /** A try run is currently in flight. */
    public boolean running;

    /** The try run succeeded; the page is done and must not run again. */
    public boolean done;

    /** The last try run failed; the page offers a retry. */
    public boolean failed;

    /** The environment was not ready yet; the page says it is preparing. */
    public boolean notReady;

    /** Friendly one-line outcome of the last finished run, if any. */
    @Nullable
    public String summary;

    /** Raw output of the last finished run, if any. */
    @Nullable
    public String rawOutput;
}
