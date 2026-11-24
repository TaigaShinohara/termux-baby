package com.termux.app.baby;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.R;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.util.concurrent.Executors;

/**
 * Guards the background run-session model behind the session bar: sessions are
 * tracked by the registry, keep their output, finish into the right state, and
 * only leave the registry once a finished session has been seen.
 */
@RunWith(RobolectricTestRunner.class)
public class BabyRunSessionTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private BabyCommandExecutor newExecutor() throws Exception {
        File prefix = tempFolder.newFolder("prefix");
        File home = tempFolder.newFolder("home");
        // Null main handler: callbacks run inline on the executor thread,
        // which keeps the test synchronous enough to poll for state changes.
        return new BabyCommandExecutor(Executors.newCachedThreadPool(), null, prefix, home);
    }

    private BabyRunSession startEcho(BabyRunSessionRegistry registry, BabyCommandExecutor executor, String command) {
        BabyRunSession session = registry.create(
            "Test", R.drawable.ic_baby_sparkle, command,
            BabyAction.OutputFormat.GENERIC, 30, "Test", "test_source"
        );
        session.start(executor);
        return session;
    }

    private static void waitForState(BabyRunSession session, BabyRunSession.State expected) throws Exception {
        long deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline) {
            if (session.getState() == expected) return;
            Thread.sleep(50);
        }
        throw new AssertionError("session did not reach " + expected + ", was " + session.getState());
    }

    @Test
    public void registry_tracksCreatedSessions() {
        BabyRunSessionRegistry registry = new BabyRunSessionRegistry();
        BabyRunSession session = registry.create(
            "A", R.drawable.ic_baby_sparkle, "true",
            BabyAction.OutputFormat.GENERIC, 5, null, null
        );
        assertEquals(1, registry.getSessions().size());
        assertTrue(registry.hasRunning());
        registry.remove(session);
        assertTrue(registry.getSessions().isEmpty());
    }

    @Test
    public void session_completesWithSuccessAndKeepsOutput() throws Exception {
        BabyCommandExecutor executor = newExecutor();
        BabyRunSessionRegistry registry = new BabyRunSessionRegistry();
        BabyRunSession session = startEcho(registry, executor, "echo baby-hello");
        waitForState(session, BabyRunSession.State.SUCCESS);
        assertTrue(session.getOutputText().contains("baby-hello"));
        assertFalse(registry.hasRunning());
        executor.shutdown();
    }

    @Test
    public void session_completesWithFailureOnNonZeroExit() throws Exception {
        BabyCommandExecutor executor = newExecutor();
        BabyRunSessionRegistry registry = new BabyRunSessionRegistry();
        BabyRunSession session = startEcho(registry, executor, "exit 3");
        waitForState(session, BabyRunSession.State.FAILED);
        executor.shutdown();
    }

    @Test
    public void session_cancelStopsTheProcess() throws Exception {
        BabyCommandExecutor executor = newExecutor();
        BabyRunSessionRegistry registry = new BabyRunSessionRegistry();
        // A shell-builtin busy loop: the executor's PATH has no /usr/bin, so an
        // external binary like sleep would not even be found in this sandbox.
        BabyRunSession session = startEcho(registry, executor, "while :; do :; done");
        Thread.sleep(300);
        session.cancel();
        waitForState(session, BabyRunSession.State.CANCELLED);
        executor.shutdown();
    }

    @Test
    public void prune_removesOnlySeenFinishedSessions() throws Exception {
        BabyCommandExecutor executor = newExecutor();
        BabyRunSessionRegistry registry = new BabyRunSessionRegistry();

        BabyRunSession finished = startEcho(registry, executor, "true");
        waitForState(finished, BabyRunSession.State.SUCCESS);

        BabyRunSession running = registry.create(
            "B", R.drawable.ic_baby_sparkle, "sleep 30",
            BabyAction.OutputFormat.GENERIC, 60, null, null
        );

        // Unseen finished sessions stay (the bar still needs to show them).
        registry.pruneSeenFinished();
        assertEquals(2, registry.getSessions().size());

        finished.markFinishedSeen();
        registry.pruneSeenFinished();
        assertEquals(1, registry.getSessions().size());
        assertTrue(registry.getSessions().contains(running));

        running.cancel();
        executor.shutdown();
    }
}
