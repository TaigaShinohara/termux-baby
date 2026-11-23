package com.termux.app.baby;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class BabyActionCatalogTest {

    @Test
    public void shellQuote_handlesEmptyNullAndPlainStrings() {
        assertEquals("''", BabyActionCatalog.shellQuote(null));
        assertEquals("''", BabyActionCatalog.shellQuote(""));
        assertEquals("'notes'", BabyActionCatalog.shellQuote("notes"));
        assertEquals("'my folder'", BabyActionCatalog.shellQuote("my folder"));
    }

    @Test
    public void shellQuote_escapesSingleQuotesAndMetacharacters() {
        assertEquals("'it'\\''s'", BabyActionCatalog.shellQuote("it's"));
        assertEquals("'; rm -rf /'", BabyActionCatalog.shellQuote("; rm -rf /"));
        assertEquals("'$(whoami) `id`'", BabyActionCatalog.shellQuote("$(whoami) `id`"));
    }

    @Test
    public void buildSafeCommand_passesThroughTemplateWithoutPlaceholder() {
        assertEquals("pwd", BabyActionCatalog.buildSafeCommand("pwd", null));
        assertEquals("ls -la", BabyActionCatalog.buildSafeCommand("ls -la", "ignored"));
    }

    @Test
    public void buildSafeCommand_quotesArgAndGlobPlaceholders() {
        String mkdirCmd = BabyActionCatalog.buildSafeCommand(
            "mkdir -p -- " + BabyActionCatalog.PLACEHOLDER_ARG,
            "  my photos; rm -rf /  "
        );
        assertEquals("mkdir -p -- 'my photos; rm -rf /'", mkdirCmd);

        String findCmd = BabyActionCatalog.buildSafeCommand(
            "find . -iname " + BabyActionCatalog.PLACEHOLDER_GLOB_ARG,
            "note'1"
        );
        assertEquals("find . -iname '*note'\\''1*'", findCmd);
    }

    @Test
    public void buildSafeCommand_rejectsBlankRequiredInput() {
        try {
            BabyActionCatalog.buildSafeCommand("mkdir -p -- " + BabyActionCatalog.PLACEHOLDER_ARG, "   ");
            fail("Expected IllegalArgumentException for blank argument");
        } catch (IllegalArgumentException expected) {
            // expected
        }

        try {
            BabyActionCatalog.buildSafeCommand("find . -iname " + BabyActionCatalog.PLACEHOLDER_GLOB_ARG, null);
            fail("Expected IllegalArgumentException for null argument");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void buildPackageInstallCommand_acceptsValidAndRejectsUnsafeNames() {
        assertEquals("pkg install -y python", BabyActionCatalog.buildPackageInstallCommand("python"));
        assertEquals("pkg install -y g++", BabyActionCatalog.buildPackageInstallCommand(" g++ "));
        assertEquals("pkg install -y python_3.11", BabyActionCatalog.buildPackageInstallCommand("python_3.11"));

        String[] invalid = {"", "   ", "python; rm -rf /", "-y", "../etc", "foo bar", "pkg$(id)"};
        for (String name : invalid) {
            try {
                BabyActionCatalog.buildPackageInstallCommand(name);
                fail("Expected IllegalArgumentException for: " + name);
            } catch (IllegalArgumentException expected) {
                // expected
            }
        }
    }

    @Test
    public void isSafeForAutoRun_allowsSafeAndBlocksDestructiveCommands() {
        assertTrue(BabyActionCatalog.isSafeForAutoRun("ls -la --color=never"));
        assertTrue(BabyActionCatalog.isSafeForAutoRun("ping -c 3 -W 4 1.1.1.1"));
        assertTrue(BabyActionCatalog.isSafeForAutoRun("pkg install -y python"));

        assertFalse(BabyActionCatalog.isSafeForAutoRun(null));
        assertFalse(BabyActionCatalog.isSafeForAutoRun("   "));
        assertFalse(BabyActionCatalog.isSafeForAutoRun("rm -rf /data"));
        assertFalse(BabyActionCatalog.isSafeForAutoRun("RM -FR ~"));
        assertFalse(BabyActionCatalog.isSafeForAutoRun("mkfs.ext4 /dev/sda"));
        assertFalse(BabyActionCatalog.isSafeForAutoRun("dd if=/dev/zero of=test"));
        assertFalse(BabyActionCatalog.isSafeForAutoRun(":(){ :|:& };:"));
    }

    @Test
    public void homeActions_hasSixUniqueValidSafeCards() {
        List<BabyAction> actions = BabyActionCatalog.getHomeActions();
        assertEquals(6, actions.size());

        Set<String> ids = new HashSet<>();
        for (BabyAction action : actions) {
            assertNotNull(action.id);
            assertTrue("Duplicate home action id: " + action.id, ids.add(action.id));
            assertTrue(action.titleResId != 0);
            assertTrue(action.subtitleResId != 0);
            assertTrue(action.iconResId != 0);
            if (action.commandTemplate != null) {
                assertTrue("Unsafe home command: " + action.id, BabyActionCatalog.isSafeForAutoRun(action.commandTemplate));
            }
        }

        assertTrue(ids.contains(BabyActionCatalog.ACTION_SEE_FILES));
        assertTrue(ids.contains(BabyActionCatalog.ACTION_INSTALL_TOOLS));
        assertTrue(ids.contains(BabyActionCatalog.ACTION_CHECK_NETWORK));
        assertTrue(ids.contains(BabyActionCatalog.ACTION_QUICK_NOTE));
        assertTrue(ids.contains(BabyActionCatalog.ACTION_DEVICE_INFO));
        assertTrue(ids.contains(BabyActionCatalog.ACTION_TRY_PYTHON));
    }

    @Test
    public void helperRecipes_andInstallableTools_areValidAndSafe() {
        List<BabyAction> recipes = BabyActionCatalog.getHelperRecipes();
        assertFalse(recipes.isEmpty());

        Set<String> ids = new HashSet<>();
        for (BabyAction recipe : recipes) {
            assertTrue("Duplicate recipe id: " + recipe.id, ids.add(recipe.id));
            assertNotNull(recipe.commandTemplate);
            assertTrue("Unsafe recipe command: " + recipe.id, BabyActionCatalog.isSafeForAutoRun(recipe.commandTemplate));

            if (recipe.requiresUserInput()) {
                assertTrue(recipe.inputLabelResId != 0);
                assertTrue(recipe.inputHintResId != 0);
                String built = BabyActionCatalog.buildSafeCommand(recipe.commandTemplate, "test");
                assertFalse(built.contains(BabyActionCatalog.PLACEHOLDER_ARG));
                assertFalse(built.contains(BabyActionCatalog.PLACEHOLDER_GLOB_ARG));
            }

            String preview = BabyActionCatalog.getDisplayCommandPreview(recipe);
            assertFalse(preview.isEmpty());
            assertFalse(preview.contains(BabyActionCatalog.PLACEHOLDER_ARG));
            assertFalse(preview.contains(BabyActionCatalog.PLACEHOLDER_GLOB_ARG));
        }

        List<BabyActionCatalog.ToolSpec> tools = BabyActionCatalog.getInstallableTools();
        assertEquals(4, tools.size());
        for (BabyActionCatalog.ToolSpec tool : tools) {
            assertTrue(tool.buildInstallCommand().startsWith("pkg install -y "));
        }
    }
}
