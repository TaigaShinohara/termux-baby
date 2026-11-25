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
        // No goal chosen yet: the explore goal's defaults apply.
        List<String> defaults = BabyPreferences.getPinnedIds(context);
        assertEquals(
            Arrays.asList("recipe_pwd", "see_files", "recipe_find",
                "recipe_date", "check_network", "install_tools"),
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
    public void pinnedIds_defaultsFollowTheOnboardingGoal() {
        BabyPreferences.setOnboardingGoal(context, BabyPreferences.GOAL_TOOLS_PYTHON);
        assertEquals("try_python", BabyPreferences.getPinnedIds(context).get(0));
        assertEquals("try_python", BabyPreferences.getGoalLeadActionId(context));

        BabyPreferences.setOnboardingGoal(context, BabyPreferences.GOAL_FILES_DEVICE);
        assertEquals("see_files", BabyPreferences.getPinnedIds(context).get(0));
        assertEquals("see_files", BabyPreferences.getGoalLeadActionId(context));

        BabyPreferences.setOnboardingGoal(context, BabyPreferences.GOAL_EXPLORE);
        assertEquals("recipe_pwd", BabyPreferences.getPinnedIds(context).get(0));
        // Explore leads with finding things — NOT the same as files/device.
        assertEquals("recipe_find", BabyPreferences.getGoalLeadActionId(context));
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
    public void preview_pythonCalcIsTheTeachingForm() {
        BabyAction calc = BabyActionCatalog.findById("recipe_python_calc");
        assertNotNull(calc);
        // The executed command is a long safe-evaluator script (an
        // implementation detail). What the learner sees — and can type —
        // is the equivalent teaching form.
        assertEquals(
            "python3 -c 'print(式)'",
            BabyActionCatalog.getDisplayCommandPreview(calc)
        );
        assertEquals(
            "python3 -c 'print(2+3)'",
            BabyActionCatalog.buildDisplayCommand(calc.commandTemplate, "2+3")
        );
        // ...and a resolved executed command maps back to the same form.
        String resolved = BabyActionCatalog.buildSafeCommand(calc.commandTemplate, "2+3");
        assertEquals(
            "python3 -c 'print(2+3)'",
            BabyActionCatalog.getDisplayCommandForResolved(calc, resolved)
        );
    }

    @Test
    public void tokenNotes_everyTokenAppearsInTheDisplayPreview() {
        // The meanings must describe the very string on screen: for every
        // catalog item with notes, each note's token literally appears in
        // the item's display preview.
        java.util.List<BabyAction> all = new java.util.ArrayList<>();
        all.addAll(BabyActionCatalog.getHomeActions());
        all.addAll(BabyActionCatalog.getHelperRecipes());
        for (BabyAction action : all) {
            java.util.List<BabyAction.TokenNote> notes =
                BabyActionCatalog.getTokenNotes(action.id);
            if (notes.isEmpty()) continue;
            String preview = BabyActionCatalog.getDisplayCommandPreview(action);
            for (BabyAction.TokenNote note : notes) {
                assertTrue(
                    action.id + ": token '" + note.token + "' not in preview '" + preview + "'",
                    preview.contains(note.token)
                );
            }
        }
    }

    @Test
    public void tokenNotes_forRawCommands_coverInstalls() {
        assertFalse(BabyActionCatalog.getTokenNotesForCommand("pkg install -y python").isEmpty());
        assertFalse(BabyActionCatalog.getTokenNotesForCommand("pkg update -y").isEmpty());
        assertFalse(BabyActionCatalog.getTokenNotesForCommand("python3 -c 'print(1)'").isEmpty());
        assertTrue(BabyActionCatalog.getTokenNotesForCommand("some-unknown-tool --x").isEmpty());
        assertTrue(BabyActionCatalog.getTokenNotesForCommand(null).isEmpty());
    }

    @Test
    public void session_sourceIdIsCarried() {
        BabyRunSessionRegistry registry = new BabyRunSessionRegistry();
        BabyRunSession session = registry.create(
            "T", 0, "true", BabyAction.OutputFormat.GENERIC, 5, null, "see_files");
        assertEquals("see_files", session.sourceId);
    }
}
