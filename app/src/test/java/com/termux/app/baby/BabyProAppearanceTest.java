package com.termux.app.baby;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;

import org.junit.Test;

/**
 * Guards the Pro (full terminal) mode smartphone readability defaults:
 * breathing room around the text, a findable cursor, and margins that keep
 * output off the edges of the phone screen. Users can still override every
 * one of these via termux.properties; these tests only pin the fresh defaults.
 */
public class BabyProAppearanceTest {

    @Test
    public void verticalMargin_givesTopAndBottomBreathingRoom() {
        assertEquals(4, TermuxPropertyConstants.DEFAULT_IVALUE_TERMINAL_MARGIN_VERTICAL);
    }

    @Test
    public void horizontalMargin_keepsTextOffScreenEdges() {
        assertEquals(8, TermuxPropertyConstants.DEFAULT_IVALUE_TERMINAL_MARGIN_HORIZONTAL);
    }

    @Test
    public void cursorBlink_gentleAndWithinSupportedRange() {
        int rate = TermuxPropertyConstants.DEFAULT_IVALUE_TERMINAL_CURSOR_BLINK_RATE;
        assertEquals(800, rate);
        assertTrue(rate >= TermuxPropertyConstants.IVALUE_TERMINAL_CURSOR_BLINK_RATE_MIN);
        assertTrue(rate <= TermuxPropertyConstants.IVALUE_TERMINAL_CURSOR_BLINK_RATE_MAX);
    }
}
