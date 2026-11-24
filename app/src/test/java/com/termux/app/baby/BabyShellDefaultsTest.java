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
 * Guards the colored-shell defaults: written once on first launch, never
 * overwriting a file the user already has.
 */
@RunWith(RobolectricTestRunner.class)
public class BabyShellDefaultsTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void writesColoredBashrcAndProfileOnce() throws Exception {
        File home = tempFolder.newFolder("home");

        assertTrue(BabyShellDefaults.ensureDefaultShellFiles(home));

        File bashrc = new File(home, ".bashrc");
        File profile = new File(home, ".profile");
        assertTrue(bashrc.exists());
        assertTrue(profile.exists());

        String bashrcText = new String(Files.readAllBytes(bashrc.toPath()), StandardCharsets.UTF_8);
        assertTrue(bashrcText.contains(BabyShellDefaults.BASHRC_MARKER));
        assertTrue(bashrcText.contains("PS1="));
        assertTrue(bashrcText.contains("ls --color=auto"));

        String profileText = new String(Files.readAllBytes(profile.toPath()), StandardCharsets.UTF_8);
        assertTrue(profileText.contains(".bashrc"));

        // Second run writes nothing and preserves user edits.
        Files.write(bashrc.toPath(), "# my own setup\n".getBytes(StandardCharsets.UTF_8));
        assertFalse(BabyShellDefaults.ensureDefaultShellFiles(home));
        String after = new String(Files.readAllBytes(bashrc.toPath()), StandardCharsets.UTF_8);
        assertTrue(after.contains("my own setup"));
    }
}
