package com.termux.app.baby;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.TermuxConstants;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Safely executes beginner-friendly shell commands asynchronously using Termux's $PREFIX/bin/sh,
 * with timeout, cancellation, output size capping, and a bridge callback to Pro Terminal mode.
 */
public final class BabyCommandExecutor {

    public static final int DEFAULT_TIMEOUT_SECONDS = 20;
    public static final int INSTALL_TIMEOUT_SECONDS = 180;
    /** Agent installs (npm global packages, proot distro downloads) can take many minutes. */
    public static final int AGENT_INSTALL_TIMEOUT_SECONDS = 1800;
    private static final int MAX_OUTPUT_CHARS = 65536;

    /**
     * Bridge for opening a command directly in the full Pro Terminal session.
     */
    public interface ProTerminalBridge {
        void openInProTerminal(@Nullable String commandToRun);

        /**
         * Opens the Pro terminal with the command TYPED but not executed —
         * the last step of the learning loop, where the user presses Enter
         * themselves.
         */
        void openInProTerminalToType(@Nullable String commandToType);
    }

    /**
     * Result of a completed shell command execution. Pure Java for easy unit testing.
     */
    public static final class ExecutionResult {
        @NonNull
        public final String command;
        public final int exitCode;
        @NonNull
        public final String stdout;
        @NonNull
        public final String stderr;
        public final boolean timedOut;
        public final boolean cancelled;
        public final boolean bootstrapMissing;
        public final long durationMs;

        public ExecutionResult(
            @NonNull String command,
            int exitCode,
            @NonNull String stdout,
            @NonNull String stderr,
            boolean timedOut,
            boolean cancelled,
            boolean bootstrapMissing,
            long durationMs
        ) {
            this.command = command;
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
            this.timedOut = timedOut;
            this.cancelled = cancelled;
            this.bootstrapMissing = bootstrapMissing;
            this.durationMs = durationMs;
        }

        public boolean isSuccess() {
            return exitCode == 0 && !timedOut && !cancelled && !bootstrapMissing;
        }

        @NonNull
        public String getCombinedOutput() {
            if (stdout.isEmpty()) return stderr;
            if (stderr.isEmpty()) return stdout;
            return stdout + "\n" + stderr;
        }
    }

    /**
     * Callback invoked on the main thread during and after command execution.
     */
    public interface Callback {
        void onOutputLine(@NonNull String line);

        void onComplete(@NonNull ExecutionResult result);
    }

    /**
     * Handle returned from {@link #executeAsync} allowing safe cancellation.
     */
    public static final class ExecutionHandle {
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private volatile Process process;

        void attachProcess(@NonNull Process process) {
            this.process = process;
            if (cancelled.get()) {
                destroyProcessTree(process);
            }
        }

        public boolean isCancelled() {
            return cancelled.get();
        }

        public void cancel() {
            if (cancelled.compareAndSet(false, true)) {
                Process p = this.process;
                if (p != null) {
                    destroyProcessTree(p);
                }
            }
        }
    }

    /**
     * Kills a process and its whole descendant tree. Killing only the
     * {@code sh -c} wrapper leaves children (pkg, npm, node…) alive: they keep
     * the dpkg/apt lock, so the next install fails, and they keep the output
     * pipes open, so the reader threads block forever. Descendants are found
     * by scanning /proc (same-UID children are readable) and SIGKILLed first,
     * then the wrapper itself is destroyed.
     */
    static void destroyProcessTree(@NonNull Process process) {
        try {
            int pid = pidOf(process);
            if (pid > 0) {
                for (int descendant : collectDescendantPids(pid)) {
                    try {
                        android.os.Process.sendSignal(descendant, 9 /* SIGKILL */);
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
        }
        try {
            process.destroy();
        } catch (Exception ignored) {
        }
    }

    private static int pidOf(@NonNull Process process) {
        try {
            java.lang.reflect.Field pidField = process.getClass().getDeclaredField("pid");
            pidField.setAccessible(true);
            return pidField.getInt(process);
        } catch (Exception e) {
            return -1;
        }
    }

    @NonNull
    private static java.util.List<Integer> collectDescendantPids(int rootPid) {
        java.util.Map<Integer, java.util.List<Integer>> childrenByParent = new java.util.HashMap<>();
        File proc = new File("/proc");
        File[] entries = proc.listFiles();
        if (entries == null) return java.util.Collections.emptyList();
        for (File entry : entries) {
            String name = entry.getName();
            if (name.isEmpty() || !Character.isDigit(name.charAt(0))) continue;
            try {
                String stat = new String(
                    Files.readAllBytes(new File(entry, "stat").toPath()), StandardCharsets.UTF_8);
                int closeParen = stat.lastIndexOf(')');
                if (closeParen < 0) continue;
                String[] fields = stat.substring(closeParen + 2).split(" ");
                // After "(comm)": fields[0]=state, fields[1]=ppid.
                int ppid = Integer.parseInt(fields[1]);
                int pid = Integer.parseInt(name);
                childrenByParent.computeIfAbsent(ppid, k -> new ArrayList<>()).add(pid);
            } catch (Exception ignored) {
                // Unreadable or already gone — skip.
            }
        }
        java.util.List<Integer> descendants = new ArrayList<>();
        java.util.Deque<Integer> stack = new java.util.ArrayDeque<>();
        stack.push(rootPid);
        while (!stack.isEmpty()) {
            java.util.List<Integer> children = childrenByParent.get(stack.pop());
            if (children == null) continue;
            for (int child : children) {
                descendants.add(child);
                stack.push(child);
            }
        }
        return descendants;
    }

    private final ExecutorService executor;
    private final Handler mainHandler;
    private final File prefixDir;
    private final File homeDir;

    /**
     * Serializes executions: package operations (pkg/dpkg/npm) corrupt or
     * lock each other out when two trees run at once, so Baby runs execute
     * strictly one at a time, in submission order. A queued run's timeout
     * clock only starts once it actually begins.
     */
    private final ReentrantLock executionLock = new ReentrantLock(true);

    public BabyCommandExecutor() {
        this(
            Executors.newCachedThreadPool(),
            new Handler(Looper.getMainLooper()),
            TermuxConstants.TERMUX_PREFIX_DIR,
            TermuxConstants.TERMUX_HOME_DIR
        );
    }

    /**
     * Package-private constructor for custom paths or testing.
     */
    BabyCommandExecutor(
        @NonNull ExecutorService executor,
        @Nullable Handler mainHandler,
        @NonNull File prefixDir,
        @NonNull File homeDir
    ) {
        this.executor = executor;
        this.mainHandler = mainHandler;
        this.prefixDir = prefixDir;
        this.homeDir = homeDir;
    }

    /**
     * Returns true if Termux bootstrap shell ($PREFIX/bin/sh) is present and executable.
     */
    public boolean isBootstrapReady() {
        File sh = new File(new File(prefixDir, "bin"), "sh");
        return sh.exists() && sh.canExecute();
    }

    /** True while the $PREFIX directory itself still exists on disk. */
    public boolean isPrefixPresent() {
        return prefixDir.isDirectory();
    }

    /**
     * Returns true if a binary (e.g., "python3", "git", "nano", "tree") is installed in $PREFIX/bin.
     */
    public boolean isBinaryInstalled(@NonNull String binaryName) {
        if (binaryName.isEmpty() || binaryName.contains("/") || binaryName.contains("..")) {
            return false;
        }
        File bin = new File(new File(prefixDir, "bin"), binaryName);
        return bin.exists() && bin.canExecute();
    }

    /**
     * Runs a shell command asynchronously with the given timeout.
     */
    @NonNull
    public ExecutionHandle executeAsync(
        @NonNull String command,
        int timeoutSeconds,
        @NonNull Callback callback
    ) {
        final ExecutionHandle handle = new ExecutionHandle();
        final int effectiveTimeout = timeoutSeconds > 0 ? timeoutSeconds : DEFAULT_TIMEOUT_SECONDS;

        executor.execute(() -> {
            ExecutionResult result = executeBlocking(command, effectiveTimeout, handle, callback);
            dispatchToMain(() -> callback.onComplete(result));
        });

        return handle;
    }

    /**
     * Runs a shell command synchronously. Intended for background threads or unit tests.
     */
    @NonNull
    public ExecutionResult executeBlocking(
        @NonNull String command,
        int timeoutSeconds,
        @NonNull ExecutionHandle handle,
        @Nullable Callback liveCallback
    ) {
        long queueStartMs = System.currentTimeMillis();
        try {
            executionLock.lockInterruptibly();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ExecutionResult(
                command, 130, "", "", false, true, false,
                System.currentTimeMillis() - queueStartMs
            );
        }
        try {
            return executeBlockingLocked(command, timeoutSeconds, handle, liveCallback);
        } finally {
            executionLock.unlock();
        }
    }

    @NonNull
    private ExecutionResult executeBlockingLocked(
        @NonNull String command,
        int timeoutSeconds,
        @NonNull ExecutionHandle handle,
        @Nullable Callback liveCallback
    ) {
        long startMs = System.currentTimeMillis();

        File binDir = new File(prefixDir, "bin");
        File termuxSh = new File(binDir, "sh");
        File shellFile = termuxSh;
        if (!shellFile.exists() || !shellFile.canExecute()) {
            // Fallback to /system/bin/sh only if not on Android Termux prefix (or report bootstrap missing)
            File systemSh = new File("/system/bin/sh");
            if (prefixDir.getAbsolutePath().startsWith("/data/data/")) {
                return new ExecutionResult(
                    command,
                    127,
                    "",
                    "Termux environment is still setting up.",
                    false,
                    false,
                    true,
                    System.currentTimeMillis() - startMs
                );
            } else if (systemSh.exists() && systemSh.canExecute()) {
                shellFile = systemSh;
            } else {
                shellFile = new File("/bin/sh");
            }
        }

        if (!homeDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            homeDir.mkdirs();
        }
        File tmpDir = new File(prefixDir, "tmp");
        if (prefixDir.exists() && !tmpDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            tmpDir.mkdirs();
        }

        ProcessBuilder pb = new ProcessBuilder(shellFile.getAbsolutePath(), "-c", command);
        if (homeDir.exists() && homeDir.isDirectory()) {
            pb.directory(homeDir);
        }

        Map<String, String> env = pb.environment();
        env.put("PREFIX", prefixDir.getAbsolutePath());
        env.put("HOME", homeDir.getAbsolutePath());
        env.put("TMPDIR", tmpDir.getAbsolutePath());
        env.put("PATH", binDir.getAbsolutePath() + ":/system/bin:/system/xbin");
        env.put("LANG", "en_US.UTF-8");
        env.put("TERM", "xterm-256color");

        StringBuilder stdoutBuilder = new StringBuilder();
        StringBuilder stderrBuilder = new StringBuilder();
        LineDispatcher dispatcher = liveCallback != null ? new LineDispatcher(liveCallback) : null;

        Process process = null;
        try {
            process = pb.start();
            handle.attachProcess(process);

            final Process runningProcess = process;
            Thread stdoutReader = new Thread(() -> readStream(
                runningProcess.getInputStream(), stdoutBuilder, dispatcher
            ), "baby-cmd-stdout");
            Thread stderrReader = new Thread(() -> readStream(
                runningProcess.getErrorStream(), stderrBuilder, dispatcher
            ), "baby-cmd-stderr");
            stdoutReader.start();
            stderrReader.start();

            boolean finished = waitForProcess(process, timeoutSeconds, handle);
            if (!finished) {
                // Cancelled or timed out: kill the whole tree and close the
                // pipes so the reader threads can actually exit.
                destroyProcessTree(process);
                closeQuietly(process.getInputStream());
                closeQuietly(process.getErrorStream());
                stdoutReader.join(2000);
                stderrReader.join(2000);
                if (dispatcher != null) dispatcher.flushNow();
                return new ExecutionResult(
                    command,
                    handle.isCancelled() ? 130 : 124,
                    snapshot(stdoutBuilder),
                    snapshot(stderrBuilder),
                    !handle.isCancelled(),
                    handle.isCancelled(),
                    false,
                    System.currentTimeMillis() - startMs
                );
            }

            stdoutReader.join(2000);
            stderrReader.join(2000);
            if (stdoutReader.isAlive() || stderrReader.isAlive()) {
                // A grandchild inherited the pipes and is holding them open;
                // cut the streams so the readers stop instead of leaking.
                closeQuietly(process.getInputStream());
                closeQuietly(process.getErrorStream());
                stdoutReader.join(500);
                stderrReader.join(500);
            }
            if (dispatcher != null) dispatcher.flushNow();
            int exitCode = process.exitValue();
            return new ExecutionResult(
                command,
                exitCode,
                snapshot(stdoutBuilder),
                snapshot(stderrBuilder),
                false,
                handle.isCancelled(),
                false,
                System.currentTimeMillis() - startMs
            );
        } catch (IOException e) {
            return new ExecutionResult(
                command,
                1,
                snapshot(stdoutBuilder),
                e.getMessage() != null ? e.getMessage() : "IO error",
                false,
                handle.isCancelled(),
                false,
                System.currentTimeMillis() - startMs
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (process != null) {
                destroyProcessTree(process);
            }
            return new ExecutionResult(
                command,
                130,
                snapshot(stdoutBuilder),
                snapshot(stderrBuilder),
                false,
                true,
                false,
                System.currentTimeMillis() - startMs
            );
        }
    }

    /** Thread-safe snapshot: reader threads may still be appending. */
    @NonNull
    private static String snapshot(@NonNull StringBuilder builder) {
        synchronized (builder) {
            return builder.toString().trim();
        }
    }

    private static void closeQuietly(@Nullable java.io.Closeable closeable) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (IOException ignored) {
        }
    }

    /**
     * Coalesces live output lines before they reach the main thread: a chatty
     * command (pkg progress output) would otherwise flood the main looper
     * with one runnable per line. Lines are flushed in batches every
     * ~120 ms; {@link #flushNow()} runs before completion is delivered so
     * lines never arrive after the result. Without a main handler (tests),
     * lines are delivered synchronously.
     */
    private final class LineDispatcher {
        private static final long FLUSH_DELAY_MS = 120;

        private final Callback callback;
        private final java.util.List<String> pending = new ArrayList<>();
        private boolean flushScheduled;

        LineDispatcher(@NonNull Callback callback) {
            this.callback = callback;
        }

        void offer(@NonNull String line) {
            if (mainHandler == null) {
                callback.onOutputLine(line);
                return;
            }
            synchronized (pending) {
                pending.add(line);
                if (flushScheduled) return;
                flushScheduled = true;
            }
            mainHandler.postDelayed(this::flush, FLUSH_DELAY_MS);
        }

        void flushNow() {
            if (mainHandler == null) {
                flush();
            } else {
                // Posted before the completion runnable on the same looper,
                // so buffered lines are delivered first (FIFO).
                dispatchToMain(this::flush);
            }
        }

        private void flush() {
            java.util.List<String> batch;
            synchronized (pending) {
                if (pending.isEmpty()) {
                    flushScheduled = false;
                    return;
                }
                batch = new ArrayList<>(pending);
                pending.clear();
                flushScheduled = false;
            }
            for (String line : batch) {
                callback.onOutputLine(line);
            }
        }
    }

    /**
     * API 21-compatible waitFor with timeout and cancellation polling.
     */
    private boolean waitForProcess(
        @NonNull Process process,
        int timeoutSeconds,
        @NonNull ExecutionHandle handle
    ) throws InterruptedException {
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        while (true) {
            try {
                process.exitValue();
                return true;
            } catch (IllegalThreadStateException stillRunning) {
                if (handle.isCancelled()) {
                    return false;
                }
                if (System.nanoTime() >= deadlineNanos) {
                    return false;
                }
                Thread.sleep(50);
            }
        }
    }

    private void readStream(
        @NonNull java.io.InputStream stream,
        @NonNull StringBuilder out,
        @Nullable LineDispatcher dispatcher
    ) {
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(stream, StandardCharsets.UTF_8)
        )) {
            String line;
            while ((line = reader.readLine()) != null) {
                synchronized (out) {
                    if (out.length() < MAX_OUTPUT_CHARS) {
                        if (out.length() > 0) {
                            out.append('\n');
                        }
                        int remaining = MAX_OUTPUT_CHARS - out.length();
                        if (line.length() > remaining) {
                            out.append(line, 0, remaining);
                        } else {
                            out.append(line);
                        }
                    }
                }
                if (dispatcher != null) {
                    final String cleanLine = BabyOutputFormatter.stripAnsi(line).trim();
                    if (!cleanLine.isEmpty()) {
                        dispatcher.offer(cleanLine);
                    }
                }
            }
        } catch (IOException ignored) {
            // Stream closed on process termination/cancellation.
        }
    }

    private void dispatchToMain(@NonNull Runnable runnable) {
        if (mainHandler != null) {
            mainHandler.post(runnable);
        } else {
            runnable.run();
        }
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
