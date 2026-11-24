package com.termux.app.baby;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Learning-state tests for the Easy Mode redesign: run counts (mastery
 * fade), pinned grid contents, custom shortcut commands, token notes and
 * honest command previews.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class BabyLearningStateTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences("termux_baby_prefs", Context.MODE_PRIVATE)
            .edit().clear().commit();
    }

    @Test
    public void runCounts_incrementAndPersist() {
        assertEquals(0, BabyPreferences.getRunCount(context, "see_files"));
        assertEquals(1, BabyPreferences.incrementRunCount(context, "see_files"));
        assertEquals(2, BabyPreferences.incrementRunCount(context, "see_files"));
        assertEquals(2, BabyPreferences.getRunCount(context, "see_files"));
        assertEquals(0, BabyPreferences.getRunCount(context, "recipe_pwd"));
    }

    @Test
    public void lastResult_roundTrips() {
        assertNull(BabyPreferences.getLastResultSummary(context, "see_files"));
        BabyPreferences.setLastResult(context, "see_files", true, "12 files");
        assertEquals("12 files", BabyPreferences.getLastResultSummary(context, "see_files"));
        assertTrue(BabyPreferences.getLastResultOk(context, "see_files"));
        BabyPreferences.setLastResult(context, "see_files", false, "failed");
        assertFalse(BabyPreferences.getLastResultOk(context, "see_files"));
    }

    @Test
    public void pinnedIds_defaultAndRoundTrip() {
        List<String> defaults = BabyPreferences.getPinnedIds(context);
        assertEquals(
            Arrays.asList("see_files", "recipe_pwd", "check_network",
                "device_info", "try_python", "install_tools"),
            defaults
        );
        // Every default pin must resolve to something renderable.
        for (String id : defaults) {
            assertNotNull("unresolvable default pin: " + id, BabyActionCatalog.findById(id));
        }
        BabyPreferences.setPinnedIds(context, Arrays.asList("recipe_date", "see_files"));
        assertEquals(
            Arrays.asList("recipe_date", "see_files"),
            BabyPreferences.getPinnedIds(context)
        );
    }

    @Test
    public void customCommands_jsonRoundTrip() {
        assertTrue(BabyPreferences.getCustomCommands(context).isEmpty());
        BabyCustomCommand custom = new BabyCustomCommand(
            "custom_1", "Update all", "pkg upgrade -y");
        BabyPreferences.setCustomCommands(context, Arrays.asList(custom));
        List<BabyCustomCommand> loaded = BabyPreferences.getCustomCommands(context);
        assertEquals(1, loaded.size());
        assertEquals("custom_1", loaded.get(0).id);
        assertEquals("Update all", loaded.get(0).label);
        assertEquals("pkg upgrade -y", loaded.get(0).command);
        assertNotNull(BabyPreferences.findCustomCommand(context, "custom_1"));
        assertNull(BabyPreferences.findCustomCommand(context, "nope"));
    }

    @Test
    public void curriculum_everyItemResolvableWithTokenNotes() {
        for (String id : BabyActionCatalog.getCurriculumIds()) {
            assertNotNull("curriculum id not in catalog: " + id, BabyActionCatalog.findById(id));
            assertFalse(
                "curriculum item without token notes: " + id,
                BabyActionCatalog.getTokenNotes(id).isEmpty()
            );
        }
    }

    @Test
    public void catalog_recipeDiskDuplicationRemoved() {
        assertNull(BabyActionCatalog.findById("recipe_disk"));
    }

    @Test
    public void preview_pythonCalcIsTheRealCommand() {
        BabyAction calc = BabyActionCatalog.findById("recipe_python_calc");
        assertNotNull(calc);
        String preview = BabyActionCatalog.getDisplayCommandPreview(calc);
        // The old fake preview ("python3 -c 'print(...)'") taught a command
        // that does not exist; the preview must be the real template's start.
        assertTrue(preview.startsWith("python3 -c 'import ast"));
        assertFalse(preview.contains("print(...)"));
    }

    @Test
    public void session_sourceIdIsCarried() {
        BabyRunSessionRegistry registry = new BabyRunSessionRegistry();
        BabyRunSession session = registry.create(
            "T", 0, "true", BabyAction.OutputFormat.GENERIC, 5, null, "see_files");
        assertEquals("see_files", session.sourceId);
    }
}
