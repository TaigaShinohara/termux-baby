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
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Safely executes beginner-friendly shell commands asynchronously using Termux's $PREFIX/bin/sh,
 * with timeout, cancellation, output size capping, and a bridge callback to Pro Terminal mode.
 */
public final class BabyCommandExecutor {

    public static final int DEFAULT_TIMEOUT_SECONDS = 20;
    public static final int INSTALL_TIMEOUT_SECONDS = 180;
    private static final int MAX_OUTPUT_CHARS = 65536;

    /**
     * Bridge for opening a command directly in the full Pro Terminal session.
     */
    public interface ProTerminalBridge {
        void openInProTerminal(@Nullable String commandToRun);
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
                destroyProcessQuietly(process);
            }
        }

        public boolean isCancelled() {
            return cancelled.get();
        }

        public void cancel() {
            if (cancelled.compareAndSet(false, true)) {
                Process p = this.process;
                if (p != null) {
                    destroyProcessQuietly(p);
                }
            }
        }

        private static void destroyProcessQuietly(@NonNull Process p) {
            try {
                p.destroy();
            } catch (Exception ignored) {
            }
        }
    }

    private final ExecutorService executor;
    private final Handler mainHandler;
    private final File prefixDir;
    private final File homeDir;

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

        Process process = null;
        try {
            process = pb.start();
            handle.attachProcess(process);

            final Process runningProcess = process;
            Thread stdoutReader = new Thread(() -> readStream(
                runningProcess.getInputStream(), stdoutBuilder, liveCallback
            ), "baby-cmd-stdout");
            Thread stderrReader = new Thread(() -> readStream(
                runningProcess.getErrorStream(), stderrBuilder, liveCallback
            ), "baby-cmd-stderr");
            stdoutReader.start();
            stderrReader.start();

            boolean finished = waitForProcess(process, timeoutSeconds, handle);
            if (!finished) {
                process.destroy();
                stdoutReader.join(500);
                stderrReader.join(500);
                return new ExecutionResult(
                    command,
                    handle.isCancelled() ? 130 : 124,
                    stdoutBuilder.toString().trim(),
                    stderrBuilder.toString().trim(),
                    !handle.isCancelled(),
                    handle.isCancelled(),
                    false,
                    System.currentTimeMillis() - startMs
                );
            }

            stdoutReader.join(2000);
            stderrReader.join(2000);
            int exitCode = process.exitValue();
            return new ExecutionResult(
                command,
                exitCode,
                stdoutBuilder.toString().trim(),
                stderrBuilder.toString().trim(),
                false,
                handle.isCancelled(),
                false,
                System.currentTimeMillis() - startMs
            );
        } catch (IOException e) {
            return new ExecutionResult(
                command,
                1,
                stdoutBuilder.toString().trim(),
                e.getMessage() != null ? e.getMessage() : "IO error",
                false,
                handle.isCancelled(),
                false,
                System.currentTimeMillis() - startMs
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (process != null) {
                process.destroy();
            }
            return new ExecutionResult(
                command,
                130,
                stdoutBuilder.toString().trim(),
                stderrBuilder.toString().trim(),
                false,
                true,
                false,
                System.currentTimeMillis() - startMs
            );
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
        @Nullable Callback liveCallback
    ) {
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(stream, StandardCharsets.UTF_8)
        )) {
            String line;
            while ((line = reader.readLine()) != null) {
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
                if (liveCallback != null) {
                    final String cleanLine = BabyOutputFormatter.stripAnsi(line).trim();
                    if (!cleanLine.isEmpty()) {
                        dispatchToMain(() -> liveCallback.onOutputLine(cleanLine));
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
