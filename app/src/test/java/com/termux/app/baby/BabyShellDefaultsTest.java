package com.termux.app.baby;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Guards the colored-shell defaults: the app manages only the marked block
 * inside ~/.bashrc and ~/.profile. User lines outside the block survive
 * upgrades, v1 whole-file templates migrate without losing additions, and
 * files with no Baby markers are never touched.
 */
@RunWith(RobolectricTestRunner.class)
public class BabyShellDefaultsTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private static String read(File file) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static void write(File file, String content) throws Exception {
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void writesManagedBlocksOnce() throws Exception {
        File home = tempFolder.newFolder("home");

        assertTrue(BabyShellDefaults.ensureDefaultShellFiles(home));

        String bashrcText = read(new File(home, ".bashrc"));
        assertTrue(bashrcText.contains("Termux Baby " + BabyShellDefaults.SETUP_VERSION));
        assertTrue(bashrcText.contains("PROMPT_COMMAND=__baby_ps1"));
        assertTrue(bashrcText.contains("ls --color=auto"));
        assertTrue(bashrcText.contains("1;32m")); // bold color codes

        String profileText = read(new File(home, ".profile"));
        assertTrue(profileText.contains(".bashrc"));

        // Second run writes nothing.
        assertFalse(BabyShellDefaults.ensureDefaultShellFiles(home));
    }

    @Test
    public void migratesV1FileKeepingUserAdditions() throws Exception {
        File home = tempFolder.newFolder("home");
        File bashrc = new File(home, ".bashrc");
        // The exact v1 template plus one line the user added underneath.
        write(bashrc,
            "# " + BabyShellDefaults.SETUP_MARKER + " — created once, edit freely.\n" +
            "# Colored prompt: teal user, green path, yellow $ marker.\n" +
            "PS1='\\[\\e[1;36m\\]\\u\\[\\e[0m\\] \\[\\e[1;32m\\]\\w\\[\\e[0m\\] \\[\\e[1;33m\\]$\\[\\e[0m\\] '\n" +
            "\n" +
            "# Colors for everyday commands.\n" +
            "alias ls='ls --color=auto'\n" +
            "alias grep='grep --color=auto'\n" +
            "alias egrep='egrep --color=auto'\n" +
            "alias diff='diff --color=auto'\n" +
            "export LS_COLORS='di=1;36:ln=1;35:ex=1;32:*.tar=31:*.gz=31:*.zip=31:*.jpg=35:*.png=35:*.mp3=33:*.mp4=33'\n" +
            "alias zz='top'\n");

        assertTrue(BabyShellDefaults.ensureDefaultShellFiles(home));

        String migrated = read(bashrc);
        assertTrue(migrated.contains("PROMPT_COMMAND=__baby_ps1")); // v2 block in
        assertTrue(migrated.contains("alias zz='top'")); // user line kept
        assertFalse(migrated.contains("created once, edit freely")); // v1 line gone
        // And it is stable afterwards.
        assertFalse(BabyShellDefaults.ensureDefaultShellFiles(home));
        assertTrue(read(bashrc).contains("alias zz='top'"));
    }

    @Test
    public void neverTouchesUserOwnedFiles() throws Exception {
        File home = tempFolder.newFolder("home");
        File bashrc = new File(home, ".bashrc");
        write(bashrc, "# my own setup\nalias x='y'\n");
        BabyShellDefaults.ensureDefaultShellFiles(home);
        String text = read(bashrc);
        assertTrue(text.contains("my own setup"));
        assertFalse(text.contains("Termux Baby"));
    }

    @Test
    public void keepsUserLinesOutsideTheBlock() throws Exception {
        File home = tempFolder.newFolder("home");
        assertTrue(BabyShellDefaults.ensureDefaultShellFiles(home));
        File bashrc = new File(home, ".bashrc");
        String edited = read(bashrc) + "\n# my extra line\nalias zz='top'\n";
        write(bashrc, edited);

        assertFalse(BabyShellDefaults.ensureDefaultShellFiles(home));
        assertTrue(read(bashrc).contains("my extra line"));
    }
}
