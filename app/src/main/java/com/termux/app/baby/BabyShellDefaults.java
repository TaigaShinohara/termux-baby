package com.termux.app.baby;

import androidx.annotation.NonNull;

import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Writes the Termux Baby default shell setup (~/.bashrc + ~/.profile) exactly once,
 * so a fresh terminal is readable from the first prompt: a colored prompt and
 * colorized output for everyday commands (ls, grep, diff) instead of plain white
 * text. Files that already exist are never touched — the user's own edits win.
 */
public final class BabyShellDefaults {

    static final String BASHRC_MARKER = "Termux Baby default shell setup";

    private static final String BASHRC_CONTENT =
        "# " + BASHRC_MARKER + " — created once, edit freely.\n" +
        "# Colored prompt: teal user, green path, yellow $ marker.\n" +
        "PS1='\\[\\e[1;36m\\]\\u\\[\\e[0m\\] \\[\\e[1;32m\\]\\w\\[\\e[0m\\] \\[\\e[1;33m\\]$\\[\\e[0m\\] '\n" +
        "\n" +
        "# Colors for everyday commands.\n" +
        "alias ls='ls --color=auto'\n" +
        "alias grep='grep --color=auto'\n" +
        "alias egrep='egrep --color=auto'\n" +
        "alias diff='diff --color=auto'\n" +
        "export LS_COLORS='di=1;36:ln=1;35:ex=1;32:*.tar=31:*.gz=31:*.zip=31:*.jpg=35:*.png=35:*.mp3=33:*.mp4=33'\n";

    private static final String PROFILE_CONTENT =
        "# Termux Baby: login shells use the same setup as interactive shells.\n" +
        "if [ -f \"$HOME/.bashrc\" ]; then\n" +
        "    . \"$HOME/.bashrc\"\n" +
        "fi\n";

    private BabyShellDefaults() {
    }

    /** Writes the default shell files into the real Termux $HOME if missing. */
    public static void ensureDefaultShellFiles() {
        ensureDefaultShellFiles(TermuxConstants.TERMUX_HOME_DIR);
    }

    /** Package-private overload for tests. Returns true if any file was written. */
    static boolean ensureDefaultShellFiles(@NonNull File homeDir) {
        boolean wrote = false;
        wrote |= writeIfMissing(new File(homeDir, ".bashrc"), BASHRC_CONTENT);
        wrote |= writeIfMissing(new File(homeDir, ".profile"), PROFILE_CONTENT);
        return wrote;
    }

    private static boolean writeIfMissing(@NonNull File file, @NonNull String content) {
        if (file.exists()) return false;
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException ignored) {
            // Shell setup is a nicety, never a blocker for launching the terminal.
            return false;
        }
    }
}
