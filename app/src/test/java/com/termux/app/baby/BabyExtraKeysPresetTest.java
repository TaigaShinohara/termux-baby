package com.termux.app.baby;

import android.content.Context;

import com.termux.app.terminal.io.TermuxTerminalExtraKeys;
import com.termux.shared.termux.extrakeys.ExtraKeyButton;
import com.termux.shared.termux.extrakeys.ExtraKeysConstants;
import com.termux.shared.termux.extrakeys.ExtraKeysInfo;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
public class BabyExtraKeysPresetTest {

    @Test
    public void simplePreset_parsesAs1x6WithExpectedKeysAndPopups() throws Exception {
        String json = TermuxTerminalExtraKeys.getExtraKeysJsonForPreset(BabyPreferences.EXTRA_KEYS_PRESET_SIMPLE);
        ExtraKeysInfo info = new ExtraKeysInfo(
            json,
            TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE,
            ExtraKeysConstants.CONTROL_CHARS_ALIASES
        );

        ExtraKeyButton[][] matrix = info.getMatrix();
        assertEquals(1, matrix.length);
        assertEquals(6, matrix[0].length);

        assertKeyAndPopup(matrix[0][0], "TAB", null, "ESC");
        assertKeyAndPopup(matrix[0][1], TermuxTerminalExtraKeys.KEY_CTRL_C, "^C", "CTRL");
        assertKeyAndPopup(matrix[0][2], "PASTE", "Paste", "ALT");
        assertKeyAndPopup(matrix[0][3], "/", null, "-");
        assertKeyAndPopup(matrix[0][4], "UP", null, "LEFT");
        assertKeyAndPopup(matrix[0][5], "DOWN", null, "RIGHT");
    }

    @Test
    public void balancedPreset_parsesAs2x6WithInvertedTArrows() throws Exception {
        String json = TermuxTerminalExtraKeys.getExtraKeysJsonForPreset(BabyPreferences.EXTRA_KEYS_PRESET_BALANCED);
        ExtraKeysInfo info = new ExtraKeysInfo(
            json,
            TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE,
            ExtraKeysConstants.CONTROL_CHARS_ALIASES
        );

        ExtraKeyButton[][] matrix = info.getMatrix();
        assertEquals(2, matrix.length);
        assertEquals(6, matrix[0].length);
        assertEquals(6, matrix[1].length);

        // Row 1: ESC, TAB, CTRL_C, PASTE, UP, {key: '-', popup: '|'}
        assertEquals("ESC", matrix[0][0].getKey());
        assertEquals("TAB", matrix[0][1].getKey());
        assertEquals(TermuxTerminalExtraKeys.KEY_CTRL_C, matrix[0][2].getKey());
        assertEquals("^C", matrix[0][2].getDisplay());
        assertEquals("PASTE", matrix[0][3].getKey());
        assertEquals("Paste", matrix[0][3].getDisplay());
        assertEquals("UP", matrix[0][4].getKey());
        assertKeyAndPopup(matrix[0][5], "-", null, "|");

        // Row 2: CTRL, ALT, /, LEFT, DOWN, RIGHT
        assertEquals("CTRL", matrix[1][0].getKey());
        assertEquals("ALT", matrix[1][1].getKey());
        assertEquals("/", matrix[1][2].getKey());
        assertEquals("LEFT", matrix[1][3].getKey());
        assertEquals("DOWN", matrix[1][4].getKey());
        assertEquals("RIGHT", matrix[1][5].getKey());
    }

    @Test
    public void classicPreset_parsesAsOriginal2x7TermuxLayout() throws Exception {
        String json = TermuxTerminalExtraKeys.getExtraKeysJsonForPreset(BabyPreferences.EXTRA_KEYS_PRESET_CLASSIC);
        assertEquals(TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS, json);

        ExtraKeysInfo info = new ExtraKeysInfo(
            json,
            TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE,
            ExtraKeysConstants.CONTROL_CHARS_ALIASES
        );

        ExtraKeyButton[][] matrix = info.getMatrix();
        assertEquals(2, matrix.length);
        assertEquals(7, matrix[0].length);
        assertEquals(7, matrix[1].length);
    }

    @Test
    public void unknownPreset_fallsBackToSimple() {
        assertEquals(
            TermuxTerminalExtraKeys.PRESET_SIMPLE_EXTRA_KEYS,
            TermuxTerminalExtraKeys.getExtraKeysJsonForPreset(null)
        );
        assertEquals(
            TermuxTerminalExtraKeys.PRESET_SIMPLE_EXTRA_KEYS,
            TermuxTerminalExtraKeys.getExtraKeysJsonForPreset("unknown_value")
        );
    }

    @Test
    public void babyPreferences_extraKeysPresetDefaultsAndPersists() {
        Context context = RuntimeEnvironment.getApplication();

        assertEquals(BabyPreferences.EXTRA_KEYS_PRESET_SIMPLE, BabyPreferences.getExtraKeysPreset(context));

        BabyPreferences.setExtraKeysPreset(context, BabyPreferences.EXTRA_KEYS_PRESET_BALANCED);
        assertEquals(BabyPreferences.EXTRA_KEYS_PRESET_BALANCED, BabyPreferences.getExtraKeysPreset(context));

        BabyPreferences.setExtraKeysPreset(context, BabyPreferences.EXTRA_KEYS_PRESET_CLASSIC);
        assertEquals(BabyPreferences.EXTRA_KEYS_PRESET_CLASSIC, BabyPreferences.getExtraKeysPreset(context));

        BabyPreferences.setExtraKeysPreset(context, "invalid");
        assertEquals(BabyPreferences.EXTRA_KEYS_PRESET_SIMPLE, BabyPreferences.getExtraKeysPreset(context));

        BabyPreferences.setExtraKeysPreset(context, null);
        assertEquals(BabyPreferences.EXTRA_KEYS_PRESET_SIMPLE, BabyPreferences.getExtraKeysPreset(context));
    }

    private static void assertKeyAndPopup(ExtraKeyButton button, String expectedKey, String expectedDisplay, String expectedPopupKey) {
        assertNotNull(button);
        assertEquals(expectedKey, button.getKey());
        if (expectedDisplay != null) {
            assertEquals(expectedDisplay, button.getDisplay());
        }
        if (expectedPopupKey == null) {
            assertNull(button.getPopup());
        } else {
            assertNotNull("Expected popup for key " + expectedKey, button.getPopup());
            assertEquals(expectedPopupKey, button.getPopup().getKey());
        }
    }
}
