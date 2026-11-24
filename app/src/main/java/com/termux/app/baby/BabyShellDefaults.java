package com.termux.app.baby;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Maintains the Termux Baby default shell setup (~/.bashrc + ~/.profile), so a
 * fresh terminal is readable from the first prompt: a bold, colorful prompt
 * (with a green/red dot showing whether the last command worked) and colorized
 * output for everyday commands instead of plain white text.
 *
 * <p>Ownership rule: the app only ever edits the block between the
 * {@code # >>> Termux Baby … >>>} and {@code # <<< Termux Baby <<<} markers.
 * Anything the user writes outside the block is theirs and survives upgrades.
 * Files with no Baby markers at all are never touched. Files created by the
 * v1 template (whole-file, no block) are migrated once: the known v1 lines
 * are replaced by the block and every other line is preserved below it.
 * Writes go through a temp file plus rename, so a crash mid-write cannot
 * leave a broken shell config.
 */
public final class BabyShellDefaults {

    static final String SETUP_MARKER = "Termux Baby default shell setup";
    static final String SETUP_VERSION = "v2";

    private static final String BLOCK_BEGIN_PREFIX = "# >>> Termux Baby";
    private static final String BLOCK_BEGIN = BLOCK_BEGIN_PREFIX + " " + SETUP_VERSION + " >>>";
    private static final String BLOCK_END = "# <<< Termux Baby <<<";

    private static final String BASHRC_HEADER =
        "# Termux Baby がお世話する設定は下のブロックの中だけです。\n" +
        "# 自分の設定はブロックの外に書いてね（アップデートで消えません）。\n";

    /** The managed .bashrc block: prompt + colors (v2 template). */
    private static final List<String> BASHRC_BLOCK = Arrays.asList(
        "# Prompt: green dot = last command worked, red dot = it failed.",
        "# Everything bold: magenta time, teal user, green folder, yellow $.",
        "__baby_ps1() {",
        "    local ec=$?",
        "    local dot",
        "    if [ $ec -eq 0 ]; then",
        "        dot='\\[\\e[1;32m\\]●\\[\\e[0m\\]'",
        "    else",
        "        dot='\\[\\e[1;31m\\]●\\[\\e[0m\\]'",
        "    fi",
        "    PS1=\"$dot \\[\\e[1;35m\\]\\t\\[\\e[0m\\] \\[\\e[1;36m\\]\\u\\[\\e[0m\\] \\[\\e[1;32m\\]\\w\\[\\e[0m\\] \\[\\e[1;33m\\]$\\[\\e[0m\\] \"",
        "}",
        "PROMPT_COMMAND=__baby_ps1",
        "__baby_ps1",
        "",
        "# Colors for everyday commands.",
        "alias ls='ls --color=auto'",
        "alias ll='ls -lh --color=auto'",
        "alias grep='grep --color=auto'",
        "alias egrep='egrep --color=auto'",
        "alias diff='diff --color=auto'",
        "alias ip='ip -color=auto'",
        "export LS_COLORS='di=1;36:ln=1;35:ex=1;32:or=1;31:mi=1;31:*.tar=31:*.gz=31:*.zip=31:*.7z=31:*.jpg=35:*.jpeg=35:*.png=35:*.gif=35:*.mp3=33:*.wav=33:*.mp4=33:*.mkv=33:*.pdf=34:*.md=36:*.txt=36:*.json=33:*.sh=1;32'",
        "export GREP_COLORS='ms=1;31:mc=1;31:sl=:cx=:fn=35:ln=32:bn=32:se=36'",
        "export GCC_COLORS='error=1;31:warning=1;33:note=1;36:caret=1;32:locus=1;34:quote=1;35'"
    );

    /** Exact lines of the v1 whole-file .bashrc template (commit d0a2009f). */
    private static final Set<String> BASHRC_V1_LINES = new HashSet<>(Arrays.asList(
        "# " + SETUP_MARKER + " — created once, edit freely.",
        "# Colored prompt: teal user, green path, yellow $ marker.",
        "PS1='\\[\\e[1;36m\\]\\u\\[\\e[0m\\] \\[\\e[1;32m\\]\\w\\[\\e[0m\\] \\[\\e[1;33m\\]$\\[\\e[0m\\] '",
        "# Colors for everyday commands.",
        "alias ls='ls --color=auto'",
        "alias grep='grep --color=auto'",
        "alias egrep='egrep --color=auto'",
        "alias diff='diff --color=auto'",
        "export LS_COLORS='di=1;36:ln=1;35:ex=1;32:*.tar=31:*.gz=31:*.zip=31:*.jpg=35:*.png=35:*.mp3=33:*.mp4=33'"
    ));

    private static final String PROFILE_HEADER =
        "# Termux Baby: login shells use the same setup as interactive shells.\n";

    /** The managed .profile block: pull in .bashrc for login shells. */
    private static final List<String> PROFILE_BLOCK = Arrays.asList(
        "if [ -f \"$HOME/.bashrc\" ]; then",
        "    . \"$HOME/.bashrc\"",
        "fi"
    );

    /** Exact lines of the v1 .profile template (no marker line of its own). */
    private static final Set<String> PROFILE_V1_LINES = new HashSet<>(Arrays.asList(
        "# Termux Baby: login shells use the same setup as interactive shells.",
        "if [ -f \"$HOME/.bashrc\" ]; then",
        "    . \"$HOME/.bashrc\"",
        "fi"
    ));

    private BabyShellDefaults() {
    }

    /** Brings the default shell files in the real Termux $HOME up to date. */
    public static void ensureDefaultShellFiles() {
        ensureDefaultShellFiles(TermuxConstants.TERMUX_HOME_DIR);
    }

    /** Package-private overload for tests. Returns true if any file was written. */
    static boolean ensureDefaultShellFiles(@NonNull File homeDir) {
        boolean wrote = false;
        wrote |= ensureManagedFile(
            new File(homeDir, ".bashrc"), BASHRC_HEADER, BASHRC_BLOCK, BASHRC_V1_LINES, SETUP_MARKER);
        wrote |= ensureManagedFile(
            new File(homeDir, ".profile"), PROFILE_HEADER, PROFILE_BLOCK, PROFILE_V1_LINES, null);
        return wrote;
    }

    /**
     * Creates or upgrades one managed file. Returns true when it wrote.
     *
     * @param legacyMarker a line fragment identifying a v1 whole-file template
     *                     (migration source), or null when v1 files are
     *                     recognized purely by their exact line set.
     */
    private static boolean ensureManagedFile(
        @NonNull File file,
        @NonNull String header,
        @NonNull List<String> blockLines,
        @NonNull Set<String> legacyLines,
        @Nullable String legacyMarker
    ) {
        if (!file.exists()) {
            List<String> lines = new ArrayList<>();
            addHeader(lines, header);
            lines.addAll(blockWithMarkers(blockLines));
            return writeAtomically(file, lines);
        }

        String existing = readSmallFile(file);
        if (existing == null) return false;
        List<String> lines = new ArrayList<>(Arrays.asList(existing.split("\n", -1)));
        // Drop the phantom empty line produced by a trailing newline; the
        // writer adds exactly one back.
        if (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
            lines.remove(lines.size() - 1);
        }

        int beginIdx = -1;
        int endIdx = -1;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.startsWith(BLOCK_BEGIN_PREFIX)) beginIdx = i;
            if (line.equals(BLOCK_END)) endIdx = i;
        }
        if (beginIdx >= 0 && endIdx > beginIdx) {
            if (lines.get(beginIdx).equals(BLOCK_BEGIN)) {
                return false; // Block already at the current version.
            }
            List<String> rebuilt = new ArrayList<>();
            rebuilt.addAll(lines.subList(0, beginIdx));
            rebuilt.addAll(blockWithMarkers(blockLines));
            rebuilt.addAll(lines.subList(endIdx + 1, lines.size()));
            return writeAtomically(file, rebuilt);
        }

        boolean looksLegacy = legacyMarker != null && existing.contains(legacyMarker);
        if (!looksLegacy) {
            // A .profile has no marker line; treat it as legacy only if every
            // non-blank line is a known v1 line or it sources .bashrc the v1 way.
            looksLegacy = !existing.contains(BLOCK_BEGIN_PREFIX)
                && containsAnyLine(lines, legacyLines)
                && allLinesKnownOrBlank(lines, legacyLines);
        }
        if (!looksLegacy) {
            return false; // The user's own file — never touched.
        }

        // Migrate a v1 whole file: drop the known template lines, keep every
        // other line (the user's own additions) below the new managed block.
        List<String> userLines = new ArrayList<>();
        for (String line : lines) {
            if (!legacyLines.contains(line)) {
                userLines.add(line);
            }
        }
        trimBlankEdges(userLines);
        List<String> rebuilt = new ArrayList<>();
        addHeader(rebuilt, header);
        rebuilt.addAll(blockWithMarkers(blockLines));
        if (!userLines.isEmpty()) {
            rebuilt.add("");
            rebuilt.addAll(userLines);
        }
        return writeAtomically(file, rebuilt);
    }

    private static void addHeader(@NonNull List<String> lines, @NonNull String header) {
        for (String line : header.split("\n")) {
            lines.add(line);
        }
    }

    @NonNull
    private static List<String> blockWithMarkers(@NonNull List<String> blockLines) {
        List<String> block = new ArrayList<>();
        block.add(BLOCK_BEGIN);
        block.addAll(blockLines);
        block.add(BLOCK_END);
        return block;
    }

    private static boolean containsAnyLine(@NonNull List<String> lines, @NonNull Set<String> known) {
        for (String line : lines) {
            if (known.contains(line)) return true;
        }
        return false;
    }

    private static boolean allLinesKnownOrBlank(@NonNull List<String> lines, @NonNull Set<String> known) {
        for (String line : lines) {
            if (!line.isEmpty() && !known.contains(line)) return false;
        }
        return true;
    }

    private static void trimBlankEdges(@NonNull List<String> lines) {
        while (!lines.isEmpty() && lines.get(0).isEmpty()) lines.remove(0);
        while (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) lines.remove(lines.size() - 1);
    }

    @Nullable
    private static String readSmallFile(@NonNull File file) {
        try {
            if (file.length() > 256 * 1024) return null;
            return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    private static boolean writeAtomically(@NonNull File file, @NonNull List<String> lines) {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }
        String content = String.join("\n", lines) + "\n";
        File tmp = new File(parent, file.getName() + ".baby-tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return false;
        }
        if (!tmp.renameTo(file)) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return false;
        }
        return true;
    }
}
