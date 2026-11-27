package com.termux.app.baby;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts raw CLI command output into calm, beginner-friendly summaries.
 * Core parsing logic is pure Java so it can be unit-tested without Android views.
 */
public final class BabyOutputFormatter {

    // OSC sequences (window titles, hyperlinks): ESC ] … ended by BEL or
    // ESC \ (an unterminated one runs to the end of the string).
    private static final Pattern ANSI_OSC_PATTERN = Pattern.compile(
        "\u001B\\][^\u0007\u001B]*(?:\u0007|\u001B\\\\|$)"
    );
    // CSI sequences plus the common single-character escapes (charset
    // designation ESC ( X, save/restore cursor ESC 7 / ESC 8, etc.).
    private static final Pattern ANSI_ESCAPE_PATTERN = Pattern.compile(
        "\u001B(?:\\[[;?\\d]*[ -/]*[@-~]|[()][0-9A-Za-z]|[@-Z\\\\-_]|[78])"
    );
    private static final Pattern PING_RTT_PATTERN = Pattern.compile(
        "(?:rtt|round-trip)\\s+min/avg/max(?:/[a-z-]+)?\\s*=\\s*[0-9.]+/([0-9.]+)/[0-9.]+"
    );
    private static final Pattern PING_TIME_PATTERN = Pattern.compile("time=([0-9.]+)\\s*ms");
    private static final Pattern WHITESPACE_SPLIT = Pattern.compile("\\s+");

    public enum SummaryKind {
        FILES_EMPTY,
        FILES_LIST,
        NETWORK_ONLINE,
        NETWORK_OFFLINE,
        DEVICE_INFO,
        PYTHON_OK,
        INSTALL_OK,
        UPDATE_OK,
        GENERIC_OK,
        GENERIC_EMPTY,
        /** The command exited non-zero; never shown as a success summary. */
        FAILED
    }

    /**
     * Pure-Java structured summary extracted from raw shell output.
     */
    public static final class ParsedSummary {
        @NonNull
        public final SummaryKind kind;
        public final int totalItems;
        public final int dirCount;
        public final int fileCount;
        @NonNull
        public final List<String> sampleItems;
        @Nullable
        public final String pingAvgMs;
        @Nullable
        public final String architecture;
        @Nullable
        public final String availableSpace;
        @Nullable
        public final String previewText;
        /** Total storage size (e.g. "228G") for DEVICE_INFO; null otherwise. */
        @Nullable
        public final String totalSpace;
        /** Used storage percent (0-100) for DEVICE_INFO; -1 when unknown. */
        public final int usedPercent;

        public ParsedSummary(
            @NonNull SummaryKind kind,
            int totalItems,
            int dirCount,
            int fileCount,
            @NonNull List<String> sampleItems,
            @Nullable String pingAvgMs,
            @Nullable String architecture,
            @Nullable String availableSpace,
            @Nullable String previewText
        ) {
            this(kind, totalItems, dirCount, fileCount, sampleItems, pingAvgMs,
                architecture, availableSpace, previewText, null, -1);
        }

        public ParsedSummary(
            @NonNull SummaryKind kind,
            int totalItems,
            int dirCount,
            int fileCount,
            @NonNull List<String> sampleItems,
            @Nullable String pingAvgMs,
            @Nullable String architecture,
            @Nullable String availableSpace,
            @Nullable String previewText,
            @Nullable String totalSpace,
            int usedPercent
        ) {
            this.kind = kind;
            this.totalItems = totalItems;
            this.dirCount = dirCount;
            this.fileCount = fileCount;
            this.sampleItems = Collections.unmodifiableList(new ArrayList<>(sampleItems));
            this.pingAvgMs = pingAvgMs;
            this.architecture = architecture;
            this.availableSpace = availableSpace;
            this.previewText = previewText;
            this.totalSpace = totalSpace;
            this.usedPercent = usedPercent;
        }
    }

    private BabyOutputFormatter() {
    }

    /**
     * Strips ANSI terminal escape codes so raw output is clean and readable.
     */
    @NonNull
    public static String stripAnsi(@Nullable String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        // OSC first: its terminator can itself contain an ESC, which the
        // single-character pattern would otherwise eat half of.
        String withoutOsc = ANSI_OSC_PATTERN.matcher(input).replaceAll("");
        return ANSI_ESCAPE_PATTERN.matcher(withoutOsc).replaceAll("");
    }

    /**
     * Pure-Java parser turning stdout/stderr/exitCode into a {@link ParsedSummary}.
     */
    @NonNull
    public static ParsedSummary parse(
        @NonNull BabyAction.OutputFormat format,
        @Nullable String rawStdout,
        @Nullable String rawStderr,
        int exitCode
    ) {
        String stdout = stripAnsi(rawStdout).trim();
        String stderr = stripAnsi(rawStderr).trim();

        if (exitCode != 0 && format != BabyAction.OutputFormat.NETWORK) {
            // Every format must honour the exit code, not just NETWORK:
            // a failed run (e.g. ls on a missing folder, a pkg error)
            // must never be summarised as "found items" / "installed".
            // NETWORK keeps its own mapping (failure = offline summary).
            String preview = extractFirstLines(!stderr.isEmpty() ? stderr : stdout, 2);
            return new ParsedSummary(
                SummaryKind.FAILED, 0, 0, 0, Collections.emptyList(),
                null, null, null, preview
            );
        }

        switch (format) {
            case FILES:
                return parseFiles(stdout);
            case NETWORK:
                return parseNetwork(stdout, stderr, exitCode);
            case DEVICE_INFO:
                return parseDeviceInfo(stdout);
            case PYTHON:
                return parsePython(stdout);
            case INSTALL:
                return new ParsedSummary(
                    SummaryKind.INSTALL_OK, 0, 0, 0, Collections.emptyList(),
                    null, null, null, extractFirstLines(stdout, 2)
                );
            case UPDATE:
                return new ParsedSummary(
                    SummaryKind.UPDATE_OK, 0, 0, 0, Collections.emptyList(),
                    null, null, null, null
                );
            case GENERIC:
            default:
                return parseGeneric(stdout);
        }
    }

    @NonNull
    private static ParsedSummary parseFiles(@NonNull String stdout) {
        if (stdout.isEmpty()) {
            return new ParsedSummary(
                SummaryKind.FILES_EMPTY, 0, 0, 0, Collections.emptyList(),
                null, null, null, null
            );
        }

        int dirs = 0;
        int files = 0;
        List<String> samples = new ArrayList<>();
        String[] lines = stdout.split("\\r?\\n");
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("total ")) {
                continue;
            }
            // ls -la columns: perms links owner group size month day time name...
            String[] parts = WHITESPACE_SPLIT.split(line, 9);
            String name;
            boolean isDir = line.startsWith("d");
            if (parts.length >= 9) {
                name = parts[8];
            } else {
                name = line;
            }
            if (".".equals(name) || "..".equals(name)) {
                continue;
            }
            if (isDir) {
                dirs++;
            } else {
                files++;
            }
            if (samples.size() < 6) {
                samples.add(isDir ? name + "/" : name);
            }
        }

        int total = dirs + files;
        if (total == 0) {
            return new ParsedSummary(
                SummaryKind.FILES_EMPTY, 0, 0, 0, Collections.emptyList(),
                null, null, null, null
            );
        }

        return new ParsedSummary(
            SummaryKind.FILES_LIST, total, dirs, files, samples,
            null, null, null, joinLines(samples)
        );
    }

    @NonNull
    private static ParsedSummary parseNetwork(@NonNull String stdout, @NonNull String stderr, int exitCode) {
        String combined = stdout + "\n" + stderr;
        if (exitCode != 0 || combined.contains("0 received") || combined.contains("100% packet loss")) {
            return new ParsedSummary(
                SummaryKind.NETWORK_OFFLINE, 0, 0, 0, Collections.emptyList(),
                null, null, null, null
            );
        }

        String avgMs = null;
        Matcher rttMatcher = PING_RTT_PATTERN.matcher(combined);
        if (rttMatcher.find()) {
            avgMs = formatDecimalShort(rttMatcher.group(1));
        } else {
            Matcher timeMatcher = PING_TIME_PATTERN.matcher(combined);
            if (timeMatcher.find()) {
                avgMs = formatDecimalShort(timeMatcher.group(1));
            }
        }

        return new ParsedSummary(
            SummaryKind.NETWORK_ONLINE, 0, 0, 0, Collections.emptyList(),
            avgMs, null, null, null
        );
    }

    @NonNull
    private static ParsedSummary parseDeviceInfo(@NonNull String stdout) {
        // The command is `uname -m && df -h "$HOME"`. The uname line is
        // deliberately NOT surfaced: "aarch64" means nothing to a beginner
        // (user feedback 2026-11-25 — the result screen read as pure
        // jargon). The answer this action exists for is the free space.
        String size = null;
        String avail = null;
        int usedPercent = -1;

        for (String rawLine : stdout.split("\\r?\\n")) {
            String[] cols = WHITESPACE_SPLIT.split(rawLine.trim());
            // df data line: Filesystem Size Used Avail Use% Mounted on —
            // identified by the numeric percent column (the header's
            // "Use%" does not match). The last match wins.
            if (cols.length >= 5 && cols[4].matches("\\d+%")) {
                size = cols[1];
                avail = cols[3];
                try {
                    usedPercent = Integer.parseInt(
                        cols[4].substring(0, cols[4].length() - 1));
                } catch (NumberFormatException ignored) {
                    usedPercent = -1;
                }
            }
        }

        return new ParsedSummary(
            SummaryKind.DEVICE_INFO, 0, 0, 0, Collections.emptyList(),
            null, null, avail, null, size, usedPercent
        );
    }

    @NonNull
    private static ParsedSummary parsePython(@NonNull String stdout) {
        String preview = extractFirstLines(stdout, 4);
        return new ParsedSummary(
            SummaryKind.PYTHON_OK, 0, 0, 0, Collections.emptyList(),
            null, null, null, preview
        );
    }

    @NonNull
    private static ParsedSummary parseGeneric(@NonNull String stdout) {
        if (stdout.isEmpty()) {
            return new ParsedSummary(
                SummaryKind.GENERIC_EMPTY, 0, 0, 0, Collections.emptyList(),
                null, null, null, null
            );
        }
        String preview = extractFirstLines(stdout, 5);
        return new ParsedSummary(
            SummaryKind.GENERIC_OK, 0, 0, 0, Collections.emptyList(),
            null, null, null, preview
        );
    }

    @Nullable
    private static String extractFirstLines(@NonNull String text, int maxLines) {
        if (text.isEmpty()) {
            return null;
        }
        String[] lines = text.split("\\r?\\n");
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (count > 0) {
                sb.append('\n');
            }
            sb.append(trimmed);
            count++;
            if (count >= maxLines) {
                break;
            }
        }
        return count > 0 ? sb.toString() : null;
    }

    @NonNull
    private static String joinLines(@NonNull List<String> items) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append("• ").append(items.get(i));
        }
        return sb.toString();
    }

    @NonNull
    private static String formatDecimalShort(@Nullable String raw) {
        if (raw == null) return "";
        try {
            double val = Double.parseDouble(raw);
            if (val >= 10.0) {
                return String.valueOf(Math.round(val));
            }
            return String.format(java.util.Locale.US, "%.1f", val);
        } catch (NumberFormatException ignored) {
            return raw;
        }
    }

    /**
     * Formats a {@link ParsedSummary} into localized, human-friendly text for display.
     */
    @NonNull
    public static String formatForDisplay(
        @NonNull Context context,
        @NonNull ParsedSummary summary,
        @Nullable String actionDisplayName
    ) {
        switch (summary.kind) {
            case FILES_EMPTY:
                return context.getString(R.string.baby_summary_files_empty);
            case FILES_LIST: {
                String header = context.getString(R.string.baby_summary_files_count, summary.totalItems);
                if (summary.previewText != null && !summary.previewText.isEmpty()) {
                    return header + "\n\n" + summary.previewText;
                }
                return header;
            }
            case NETWORK_ONLINE:
                if (summary.pingAvgMs != null && !summary.pingAvgMs.isEmpty()) {
                    return context.getString(R.string.baby_summary_network_online_ms, summary.pingAvgMs);
                }
                return context.getString(R.string.baby_summary_network_online);
            case NETWORK_OFFLINE:
                return context.getString(R.string.baby_summary_network_offline);
            case DEVICE_INFO: {
                if (summary.availableSpace == null) {
                    return context.getString(R.string.baby_summary_generic_done);
                }
                if (summary.totalSpace != null && summary.usedPercent >= 0) {
                    return context.getString(
                        R.string.baby_summary_device_info,
                        summary.availableSpace, summary.totalSpace, summary.usedPercent
                    );
                }
                if (summary.totalSpace != null) {
                    return context.getString(
                        R.string.baby_summary_device_info_no_pct,
                        summary.availableSpace, summary.totalSpace
                    );
                }
                return context.getString(
                    R.string.baby_summary_device_info_avail_only, summary.availableSpace
                );
            }
            case UPDATE_OK:
                return context.getString(R.string.baby_summary_update_done);
            case PYTHON_OK: {
                String headline = context.getString(R.string.baby_summary_python_hello);
                if (summary.previewText != null && !summary.previewText.isEmpty()) {
                    return headline + "\n\n" + summary.previewText;
                }
                return headline;
            }
            case INSTALL_OK:
                if (actionDisplayName != null && !actionDisplayName.isEmpty()) {
                    return context.getString(R.string.baby_summary_install_done, actionDisplayName);
                }
                return context.getString(R.string.baby_summary_generic_done);
            case GENERIC_EMPTY:
                return context.getString(R.string.baby_runner_empty_output);
            case FAILED: {
                String headline = context.getString(R.string.baby_error_generic);
                if (summary.previewText != null && !summary.previewText.isEmpty()) {
                    return headline + "\n\n" + summary.previewText;
                }
                return headline;
            }
            case GENERIC_OK:
            default: {
                if (summary.previewText != null && !summary.previewText.isEmpty()) {
                    return summary.previewText;
                }
                return context.getString(R.string.baby_summary_generic_done);
            }
        }
    }
}
