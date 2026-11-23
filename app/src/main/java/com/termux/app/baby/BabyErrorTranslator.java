package com.termux.app.baby;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Translates raw CLI failure codes and stderr text into calm, plain-language guidance
 * and optional 1-tap recovery actions (such as installing a missing package).
 * Core classification logic is pure Java for straightforward unit testing.
 */
public final class BabyErrorTranslator {

    private static final Pattern NOT_FOUND_CMD_PATTERN = Pattern.compile(
        "(?:^|:\\s*)([a-zA-Z0-9._-]+):\\s*(?:command\\s+)?not\\s+found",
        Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );

    private static final Map<String, String> BINARY_TO_PACKAGE;

    static {
        Map<String, String> map = new HashMap<>();
        map.put("python", "python");
        map.put("python3", "python");
        map.put("pip", "python");
        map.put("git", "git");
        map.put("nano", "nano");
        map.put("tree", "tree");
        map.put("curl", "curl");
        map.put("wget", "wget");
        map.put("node", "nodejs");
        map.put("vim", "vim");
        BINARY_TO_PACKAGE = Collections.unmodifiableMap(map);
    }

    public enum ErrorCategory {
        COMMAND_NOT_FOUND,
        NETWORK_UNREACHABLE,
        PERMISSION_DENIED,
        FILE_NOT_FOUND,
        TIMEOUT,
        CANCELLED,
        BOOTSTRAP_NOT_READY,
        UNKNOWN
    }

    /**
     * Pure-Java classification result.
     */
    public static final class TranslatedError {
        @NonNull
        public final ErrorCategory category;
        @Nullable
        public final String missingCommand;
        @Nullable
        public final String suggestedPackage;

        public TranslatedError(
            @NonNull ErrorCategory category,
            @Nullable String missingCommand,
            @Nullable String suggestedPackage
        ) {
            this.category = category;
            this.missingCommand = missingCommand;
            this.suggestedPackage = suggestedPackage;
        }

        public boolean hasInstallAction() {
            return suggestedPackage != null && !suggestedPackage.isEmpty();
        }
    }

    private BabyErrorTranslator() {
    }

    /**
     * Classifies a {@link BabyCommandExecutor.ExecutionResult} into a {@link TranslatedError}.
     */
    @NonNull
    public static TranslatedError classify(@NonNull BabyCommandExecutor.ExecutionResult result) {
        if (result.bootstrapMissing) {
            return new TranslatedError(ErrorCategory.BOOTSTRAP_NOT_READY, null, null);
        }
        return classify(result.exitCode, result.stdout, result.stderr, result.timedOut, result.cancelled);
    }

    /**
     * Pure-Java error classifier decoupled from Android framework types.
     */
    @NonNull
    public static TranslatedError classify(
        int exitCode,
        @Nullable String stdout,
        @Nullable String stderr,
        boolean timedOut,
        boolean cancelled
    ) {
        if (cancelled) {
            return new TranslatedError(ErrorCategory.CANCELLED, null, null);
        }
        if (timedOut) {
            return new TranslatedError(ErrorCategory.TIMEOUT, null, null);
        }

        String combined = BabyOutputFormatter.stripAnsi(
            (stderr != null ? stderr : "") + "\n" + (stdout != null ? stdout : "")
        );
        String lower = combined.toLowerCase(Locale.US);

        // 1. Command not found
        if (exitCode == 127 || lower.contains("command not found") || lower.contains(": not found")) {
            String missingCmd = extractMissingCommand(combined);
            String pkg = missingCmd != null ? mapBinaryToPackage(missingCmd) : null;
            return new TranslatedError(ErrorCategory.COMMAND_NOT_FOUND, missingCmd, pkg);
        }

        // 2. Network issues
        if (lower.contains("temporary failure resolving")
            || lower.contains("network is unreachable")
            || lower.contains("could not resolve host")
            || lower.contains("unable to resolve host")
            || lower.contains("name or service not known")
            || lower.contains("failed to fetch")
            || lower.contains("100% packet loss")
            || lower.contains("0 received")
            || lower.contains("connection timed out")
            || lower.contains("bad address")) {
            return new TranslatedError(ErrorCategory.NETWORK_UNREACHABLE, null, null);
        }

        // 3. Permission denied
        if (lower.contains("permission denied")
            || lower.contains("operation not permitted")
            || lower.contains("eacces")) {
            return new TranslatedError(ErrorCategory.PERMISSION_DENIED, null, null);
        }

        // 4. File / directory not found
        if (lower.contains("no such file or directory")
            || lower.contains("cannot access")
            || lower.contains("does not exist")) {
            return new TranslatedError(ErrorCategory.FILE_NOT_FOUND, null, null);
        }

        return new TranslatedError(ErrorCategory.UNKNOWN, null, null);
    }

    @Nullable
    static String extractMissingCommand(@NonNull String text) {
        Matcher matcher = NOT_FOUND_CMD_PATTERN.matcher(text);
        if (matcher.find()) {
            String cmd = matcher.group(1);
            if (cmd != null && !cmd.isEmpty()) {
                // Ignore shell name prefixes like "sh" or "bash"
                String lower = cmd.toLowerCase(Locale.US);
                if (!"sh".equals(lower) && !"bash".equals(lower)) {
                    return cmd;
                }
            }
        }
        return null;
    }

    @Nullable
    public static String mapBinaryToPackage(@NonNull String binaryName) {
        return BINARY_TO_PACKAGE.get(binaryName.toLowerCase(Locale.US));
    }

    /**
     * Resolves a calm, localized plain-language message for the given translated error.
     */
    @NonNull
    public static String getFriendlyMessage(@NonNull Context context, @NonNull TranslatedError error) {
        switch (error.category) {
            case COMMAND_NOT_FOUND:
                return context.getString(R.string.baby_error_command_not_found);
            case NETWORK_UNREACHABLE:
                return context.getString(R.string.baby_error_network);
            case PERMISSION_DENIED:
                return context.getString(R.string.baby_error_permission);
            case FILE_NOT_FOUND:
                return context.getString(R.string.baby_error_not_found);
            case TIMEOUT:
                return context.getString(R.string.baby_error_timeout);
            case BOOTSTRAP_NOT_READY:
                return context.getString(R.string.baby_home_status_preparing);
            case CANCELLED:
            case UNKNOWN:
            default:
                return context.getString(R.string.baby_error_generic);
        }
    }
}
