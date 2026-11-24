package com.termux.app.terminal.io;

import android.annotation.SuppressLint;
import android.view.Gravity;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.drawerlayout.widget.DrawerLayout;

import com.termux.app.TermuxActivity;
import com.termux.app.baby.BabyPreferences;
import com.termux.app.terminal.TermuxTerminalSessionActivityClient;
import com.termux.app.terminal.TermuxTerminalViewClient;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.extrakeys.ExtraKeysConstants;
import com.termux.shared.termux.extrakeys.ExtraKeysInfo;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;
import com.termux.shared.termux.settings.properties.TermuxSharedProperties;
import com.termux.shared.termux.terminal.io.TerminalExtraKeys;
import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;

import org.json.JSONException;

public class TermuxTerminalExtraKeys extends TerminalExtraKeys {

    private ExtraKeysInfo mExtraKeysInfo;

    final TermuxActivity mActivity;
    final TermuxTerminalViewClient mTermuxTerminalViewClient;
    final TermuxTerminalSessionActivityClient mTermuxTerminalSessionActivityClient;

    private static final String LOG_TAG = "TermuxTerminalExtraKeys";

    /** 1-tap interrupt key name handled directly by {@link #onTerminalExtraKeyButtonClick}. */
    public static final String KEY_CTRL_C = "CTRL_C";

    /**
     * Simple preset (default): plain control panel, 2 rows × 7 keys, one label per key
     * and no swipe-up popups. Shows exactly the keys a phone keyboard lacks:
     * control keys (ESC / TAB / CTRL / ALT), navigation keys (HOME / END / PGUP / PGDN),
     * an inverted-T arrow cluster (UP above LEFT / DOWN / RIGHT), and '-' / '/'.
     */
    public static final String PRESET_SIMPLE_EXTRA_KEYS =
        "[['ESC', 'TAB', 'CTRL', 'ALT', 'UP', '-', '/'], " +
        "['HOME', 'END', 'PGUP', 'LEFT', 'DOWN', 'RIGHT', 'PGDN']]";

    /** Balanced preset: 2 rows × 6 spacious keys with inverted-T arrows. No popups. */
    public static final String PRESET_BALANCED_EXTRA_KEYS =
        "[['ESC', 'TAB', {key: 'CTRL_C', display: '^C'}, {key: 'PASTE', display: 'Paste'}, 'UP', '-'], " +
        "['CTRL', 'ALT', '/', 'LEFT', 'DOWN', 'RIGHT']]";

    /** Returns the Extra Keys JSON specification for the given {@link BabyPreferences} preset. */
    @NonNull
    public static String getExtraKeysJsonForPreset(@Nullable String preset) {
        String normalized = BabyPreferences.normalizeExtraKeysPreset(preset);
        if (BabyPreferences.EXTRA_KEYS_PRESET_BALANCED.equals(normalized)) {
            return PRESET_BALANCED_EXTRA_KEYS;
        }
        if (BabyPreferences.EXTRA_KEYS_PRESET_CLASSIC.equals(normalized)) {
            return TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS;
        }
        return PRESET_SIMPLE_EXTRA_KEYS;
    }

    public TermuxTerminalExtraKeys(TermuxActivity activity, @NonNull TerminalView terminalView,
                                   TermuxTerminalViewClient termuxTerminalViewClient,
                                   TermuxTerminalSessionActivityClient termuxTerminalSessionActivityClient) {
        super(terminalView);

        mActivity = activity;
        mTermuxTerminalViewClient = termuxTerminalViewClient;
        mTermuxTerminalSessionActivityClient = termuxTerminalSessionActivityClient;

        setExtraKeys();
    }


    /**
     * Set the terminal extra keys and style.
     * Respects an explicit user-defined {@code extra-keys} entry in {@code ~/.termux/termux.properties};
     * otherwise applies the Termux Baby preset selected in {@link BabyPreferences}.
     */
    public void setExtraKeys() {
        mExtraKeysInfo = null;

        try {
            // Respect explicit user-defined extra-keys in termux.properties; otherwise use Termux Baby preset.
            String rawUserExtraKeys = mActivity.getProperties().getPropertyValue(TermuxPropertyConstants.KEY_EXTRA_KEYS, null, true);
            String extrakeys;
            if (rawUserExtraKeys != null && !rawUserExtraKeys.trim().isEmpty()) {
                extrakeys = (String) mActivity.getProperties().getInternalPropertyValue(TermuxPropertyConstants.KEY_EXTRA_KEYS, true);
            } else {
                extrakeys = getExtraKeysJsonForPreset(BabyPreferences.getExtraKeysPreset(mActivity));
            }
            String extraKeysStyle = (String) mActivity.getProperties().getInternalPropertyValue(TermuxPropertyConstants.KEY_EXTRA_KEYS_STYLE, true);

            ExtraKeysConstants.ExtraKeyDisplayMap extraKeyDisplayMap = ExtraKeysInfo.getCharDisplayMapForStyle(extraKeysStyle);
            if (ExtraKeysConstants.EXTRA_KEY_DISPLAY_MAPS.DEFAULT_CHAR_DISPLAY.equals(extraKeyDisplayMap) && !TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE.equals(extraKeysStyle)) {
                Logger.logError(TermuxSharedProperties.LOG_TAG, "The style \"" + extraKeysStyle + "\" for the key \"" + TermuxPropertyConstants.KEY_EXTRA_KEYS_STYLE + "\" is invalid. Using default style instead.");
                extraKeysStyle = TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE;
            }

            mExtraKeysInfo = new ExtraKeysInfo(extrakeys, extraKeysStyle, ExtraKeysConstants.CONTROL_CHARS_ALIASES);
        } catch (JSONException e) {
            Logger.showToast(mActivity, "Could not load and set the \"" + TermuxPropertyConstants.KEY_EXTRA_KEYS + "\" property from the properties file: " + e.toString(), true);
            Logger.logStackTraceWithMessage(LOG_TAG, "Could not load and set the \"" + TermuxPropertyConstants.KEY_EXTRA_KEYS + "\" property from the properties file: ", e);

            try {
                mExtraKeysInfo = new ExtraKeysInfo(PRESET_SIMPLE_EXTRA_KEYS, TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE, ExtraKeysConstants.CONTROL_CHARS_ALIASES);
            } catch (JSONException e2) {
                Logger.showToast(mActivity, "Can't create default extra keys",true);
                Logger.logStackTraceWithMessage(LOG_TAG, "Could create default extra keys: ", e);
                mExtraKeysInfo = null;
            }
        }
    }

    public ExtraKeysInfo getExtraKeysInfo() {
        return mExtraKeysInfo;
    }

    @SuppressLint("RtlHardcoded")
    @Override
    public void onTerminalExtraKeyButtonClick(View view, String key, boolean ctrlDown, boolean altDown, boolean shiftDown, boolean fnDown) {
        if ("KEYBOARD".equals(key)) {
            if(mTermuxTerminalViewClient != null)
                mTermuxTerminalViewClient.onToggleSoftKeyboardRequest();
        } else if ("DRAWER".equals(key)) {
            DrawerLayout drawerLayout = mTermuxTerminalViewClient.getActivity().getDrawer();
            if (drawerLayout.isDrawerOpen(Gravity.LEFT))
                drawerLayout.closeDrawer(Gravity.LEFT);
            else
                drawerLayout.openDrawer(Gravity.LEFT);
        } else if ("PASTE".equals(key)) {
            if(mTermuxTerminalSessionActivityClient != null)
                mTermuxTerminalSessionActivityClient.onPasteTextFromClipboard(null);
        } else if (KEY_CTRL_C.equals(key)) {
            TerminalView terminalView = mActivity != null ? mActivity.getTerminalView() : null;
            if (terminalView != null) {
                TerminalSession session = terminalView.getCurrentSession();
                if (session != null && session.isRunning()) {
                    session.write("\u0003");
                }
            }
        } else if ("SCROLL".equals(key)) {
            TerminalView terminalView = mTermuxTerminalViewClient.getActivity().getTerminalView();
            if (terminalView != null && terminalView.mEmulator != null)
                terminalView.mEmulator.toggleAutoScrollDisabled();
        } else {
            super.onTerminalExtraKeyButtonClick(view, key, ctrlDown, altDown, shiftDown, fnDown);
        }
    }

}
