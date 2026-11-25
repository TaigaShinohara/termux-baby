package com.termux.app;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.termux.R;
import com.termux.app.api.file.FileReceiverActivity;
import com.termux.app.baby.BabyModeController;
import com.termux.app.baby.BabyRunSession;
import com.termux.app.baby.BabyShellDefaults;
import com.termux.app.baby.BabyPreferences;
import com.termux.app.baby.onboarding.BabyOnboardingActivity;
import com.termux.app.terminal.TermuxActivityRootView;
import com.termux.app.terminal.TermuxTerminalSessionActivityClient;
import com.termux.app.terminal.io.TermuxTerminalExtraKeys;
import com.termux.shared.activities.ReportActivity;
import com.termux.shared.activity.ActivityUtils;
import com.termux.shared.activity.media.AppCompatActivityUtils;
import com.termux.shared.data.IntentUtils;
import com.termux.shared.android.PermissionUtils;
import com.termux.shared.data.DataUtils;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY;
import com.termux.app.activities.HelpActivity;
import com.termux.app.activities.SettingsActivity;
import com.termux.shared.termux.crash.TermuxCrashUtils;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.app.terminal.TermuxSessionsListViewController;
import com.termux.app.terminal.io.TerminalToolbarViewPager;
import com.termux.app.terminal.TermuxTerminalViewClient;
import com.termux.shared.termux.extrakeys.ExtraKeysView;
import com.termux.shared.termux.interact.TextInputDialogUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxUtils;
import com.termux.shared.termux.settings.properties.TermuxAppSharedProperties;
import com.termux.shared.termux.theme.TermuxThemeUtils;
import com.termux.shared.theme.NightMode;
import com.termux.shared.view.KeyboardUtils;
import com.termux.shared.view.ViewUtils;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;
import com.termux.view.TerminalView;
import com.termux.view.TerminalViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.viewpager.widget.ViewPager;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

/**
 * A terminal emulator activity.
 * <p/>
 * See
 * <ul>
 * <li>http://www.mongrel-phones.com.au/default/how_to_make_a_local_service_and_bind_to_it_in_android</li>
 * <li>https://code.google.com/p/android/issues/detail?id=6426</li>
 * </ul>
 * about memory leaks.
 */
public final class TermuxActivity extends AppCompatActivity implements ServiceConnection {

    /**
     * The connection to the {@link TermuxService}. Requested in {@link #onCreate(Bundle)} with a call to
     * {@link #bindService(Intent, ServiceConnection, int)}, and obtained and stored in
     * {@link #onServiceConnected(ComponentName, IBinder)}.
     */
    TermuxService mTermuxService;

    /**
     * The {@link TerminalView} shown in  {@link TermuxActivity} that displays the terminal.
     */
    TerminalView mTerminalView;

    /**
     *  The {@link TerminalViewClient} interface implementation to allow for communication between
     *  {@link TerminalView} and {@link TermuxActivity}.
     */
    TermuxTerminalViewClient mTermuxTerminalViewClient;

    /**
     *  The {@link TerminalSessionClient} interface implementation to allow for communication between
     *  {@link TerminalSession} and {@link TermuxActivity}.
     */
    TermuxTerminalSessionActivityClient mTermuxTerminalSessionActivityClient;

    /**
     * Termux app shared preferences manager.
     */
    private TermuxAppSharedPreferences mPreferences;

    /**
     * Termux app SharedProperties loaded from termux.properties
     */
    private TermuxAppSharedProperties mProperties;

    /**
     * The root view of the {@link TermuxActivity}.
     */
    TermuxActivityRootView mTermuxActivityRootView;

    /**
     * The space at the bottom of {@link @mTermuxActivityRootView} of the {@link TermuxActivity}.
     */
    View mTermuxActivityBottomSpaceView;

    /**
     * The terminal extra keys view.
     */
    ExtraKeysView mExtraKeysView;

    /**
     * The client for the {@link #mExtraKeysView}.
     */
    TermuxTerminalExtraKeys mTermuxTerminalExtraKeys;

    /** Last Extra Keys preset applied to {@link #mTermuxTerminalExtraKeys}. */
    private String mAppliedExtraKeysPreset;

    /**
     * The termux sessions list controller.
     */
    TermuxSessionsListViewController mTermuxSessionListViewController;

    /**
     * The {@link TermuxActivity} broadcast receiver for various things like terminal style configuration changes.
     */
    private final BroadcastReceiver mTermuxActivityBroadcastReceiver = new TermuxActivityBroadcastReceiver();

    /**
     * The last toast shown, used cancel current toast before showing new in {@link #showToast(String, boolean)}.
     */
    Toast mLastToast;

    /**
     * If between onResume() and onStop(). Note that only one session is in the foreground of the terminal view at the
     * time, so if the session causing a change is not in the foreground it should probably be treated as background.
     */
    private boolean mIsVisible;

    /**
     * If onResume() was called after onCreate().
     */
    private boolean mIsOnResumeAfterOnCreate = false;

    /**
     * If activity was restarted like due to call to {@link #recreate()} after receiving
     * {@link TERMUX_ACTIVITY#ACTION_RELOAD_STYLE}, system dark night mode was changed or activity
     * was killed by android.
     */
    private boolean mIsActivityRecreated = false;

    /**
     * The {@link TermuxActivity} is in an invalid state and must not be run.
     */
    private boolean mIsInvalidState;

    private int mNavBarHeight;

    private float mTerminalToolbarDefaultHeight;

    /** Termux Baby mode controller and chrome views. */
    private BabyModeController mBabyModeController;
    private View mBabyTopBar;
    private View mBabyProReturnPill;
    private TextView mBabyModeEasyTab;
    private TextView mBabyModeProTab;

    /** Session bar (now-running strip) views and its light poller. */
    private View mSessionBar;
    private android.widget.LinearLayout mSessionBarChips;
    private String mSessionBarSignature;
    private final android.os.Handler mSessionBarHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable mSessionBarPoll = new Runnable() {
        @Override
        public void run() {
            refreshSessionBar();
            mSessionBarHandler.postDelayed(this, 2500);
        }
    };
    private boolean mIsBabyModeActive = false;
    private boolean mIsOnboardingInProgress = false;
    private boolean mIsBootstrapSetupRunning = false;
    @Nullable
    private String mPendingProCommand;
    private long mPendingProCommandAtMs;
    private boolean mPendingProCommandExecute = true;

    /**
     * A parked Pro command ("Open" tapped before any session existed) is only
     * valid briefly: after this window it is dropped instead of being typed
     * into whatever session happens to be current much later.
     */
    private static final long PENDING_PRO_COMMAND_EXPIRY_MS = 60_000;
    private static final String ARG_PENDING_PRO_COMMAND = "pending_pro_command";
    private static final String ARG_PENDING_PRO_COMMAND_AT = "pending_pro_command_at";
    private static final String ARG_PENDING_PRO_COMMAND_EXECUTE = "pending_pro_command_execute";


    private static final int CONTEXT_MENU_SELECT_URL_ID = 0;
    private static final int CONTEXT_MENU_SHARE_TRANSCRIPT_ID = 1;
    private static final int CONTEXT_MENU_SHARE_SELECTED_TEXT = 10;
    private static final int CONTEXT_MENU_AUTOFILL_USERNAME = 11;
    private static final int CONTEXT_MENU_AUTOFILL_PASSWORD = 2;
    private static final int CONTEXT_MENU_RESET_TERMINAL_ID = 3;
    private static final int CONTEXT_MENU_KILL_PROCESS_ID = 4;
    private static final int CONTEXT_MENU_STYLING_ID = 5;
    private static final int CONTEXT_MENU_TOGGLE_KEEP_SCREEN_ON = 6;
    private static final int CONTEXT_MENU_HELP_ID = 7;
    private static final int CONTEXT_MENU_SETTINGS_ID = 8;
    private static final int CONTEXT_MENU_REPORT_ID = 9;

    private static final String ARG_TERMINAL_TOOLBAR_TEXT_INPUT = "terminal_toolbar_text_input";
    private static final String ARG_ACTIVITY_RECREATED = "activity_recreated";

    private static final String LOG_TAG = "TermuxActivity";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Logger.logDebug(LOG_TAG, "onCreate");
        mIsOnResumeAfterOnCreate = true;

        if (savedInstanceState != null) {
            mIsActivityRecreated = savedInstanceState.getBoolean(ARG_ACTIVITY_RECREATED, false);
            // Restore a parked Pro command across recreation (with its
            // original timestamp, so the expiry still applies).
            mPendingProCommand = savedInstanceState.getString(ARG_PENDING_PRO_COMMAND, null);
            mPendingProCommandAtMs = savedInstanceState.getLong(ARG_PENDING_PRO_COMMAND_AT, 0);
            mPendingProCommandExecute = savedInstanceState.getBoolean(ARG_PENDING_PRO_COMMAND_EXECUTE, true);
        }

        // Delete ReportInfo serialized object files from cache older than 14 days
        ReportActivity.deleteReportInfoFilesOlderThanXDays(this, 14, false);

        // Load Termux app SharedProperties from disk
        mProperties = TermuxAppSharedProperties.getProperties();
        reloadProperties();

        setActivityTheme();

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_termux);

        // Load termux shared preferences
        // This will also fail if TermuxConstants.TERMUX_PACKAGE_NAME does not equal applicationId
        mPreferences = TermuxAppSharedPreferences.build(this, true);
        if (mPreferences == null) {
            // An AlertDialog should have shown to kill the app, so we don't continue running activity code
            mIsInvalidState = true;
            return;
        }

        setMargins();

        mTermuxActivityRootView = findViewById(R.id.activity_termux_root_view);
        mTermuxActivityRootView.setActivity(this);
        mTermuxActivityBottomSpaceView = findViewById(R.id.activity_termux_bottom_space_view);
        mTermuxActivityRootView.setOnApplyWindowInsetsListener(new TermuxActivityRootView.WindowInsetsListener());

        View content = findViewById(android.R.id.content);
        content.setOnApplyWindowInsetsListener((v, insets) -> {
            mNavBarHeight = insets.getSystemWindowInsetBottom();
            return insets;
        });

        if (mProperties.isUsingFullScreen()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }

        setTermuxTerminalViewAndClients();

        setTerminalToolbarView(savedInstanceState);

        setSettingsButtonView();

        setNewSessionButtonView();

        setToggleKeyboardView();

        registerForContextMenu(mTerminalView);

        setBabyModeViews();

        ensureHushLoginOnce();

        FileReceiverActivity.updateFileReceiverActivityComponentsState(this);

        try {
            // Start the {@link TermuxService} and make it run regardless of who is bound to it
            Intent serviceIntent = new Intent(this, TermuxService.class);
            startService(serviceIntent);

            // Attempt to bind to the service, this will call the {@link #onServiceConnected(ComponentName, IBinder)}
            // callback if it succeeds.
            if (!bindService(serviceIntent, this, 0))
                throw new RuntimeException("bindService() failed");
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG,"TermuxActivity failed to start TermuxService", e);
            Logger.showToast(this,
                getString(e.getMessage() != null && e.getMessage().contains("app is in background") ?
                    R.string.error_termux_service_start_failed_bg : R.string.error_termux_service_start_failed_general),
                true);
            mIsInvalidState = true;
            return;
        }

        // Send the {@link TermuxConstants#BROADCAST_TERMUX_OPENED} broadcast to notify apps that Termux
        // app has been opened.
        TermuxUtils.sendTermuxOpenedBroadcast(this);

        // Launch gentle first-run onboarding if not yet completed
        if (!mIsActivityRecreated && BabyOnboardingActivity.shouldShowOnboarding(this)) {
            mIsOnboardingInProgress = true;
            startActivityForResult(
                BabyOnboardingActivity.createIntent(this),
                BabyOnboardingActivity.REQUEST_CODE_ONBOARDING
            );
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        Logger.logDebug(LOG_TAG, "onStart");

        if (mIsInvalidState) return;

        mIsVisible = true;

        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onStart();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onStart();

        if (mPreferences.isTerminalMarginAdjustmentEnabled())
            addTermuxActivityRootViewGlobalLayoutListener();

        registerTermuxActivityBroadcastReceiver();
    }

    @Override
    public void onResume() {
        super.onResume();

        Logger.logVerbose(LOG_TAG, "onResume");

        if (mIsInvalidState) return;

        mIsOnboardingInProgress = false;

        // Session bar: refresh now and keep it fresh while the activity is up.
        refreshSessionBar();
        mSessionBarHandler.removeCallbacks(mSessionBarPoll);
        mSessionBarHandler.postDelayed(mSessionBarPoll, 2500);

        // Sync Baby/Pro mode state before terminal view client adjusts soft keyboard
        if (mBabyModeController != null) {
            boolean shouldBeBabyMode = BabyPreferences.isBabyMode(this);
            if (shouldBeBabyMode != mIsBabyModeActive) {
                applyModeUi(shouldBeBabyMode, false);
            } else if (mIsBabyModeActive) {
                mBabyModeController.refreshState();
                updateBabySystemBarsAppearance(true);
            }
        }

        // Reload Extra Keys immediately if the user changed the preset in Settings
        String currentExtraKeysPreset = BabyPreferences.getExtraKeysPreset(this);
        if (mAppliedExtraKeysPreset != null && !mAppliedExtraKeysPreset.equals(currentExtraKeysPreset)) {
            mAppliedExtraKeysPreset = currentExtraKeysPreset;
            reloadExtraKeysView();
        }

        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onResume();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onResume();

        // If service connected while onboarding was on top, finish bootstrap/session setup now
        if (mIsVisible && mTermuxService != null && mTermuxService.isTermuxSessionsEmpty()) {
            ensureBootstrapAndInitialSession(null);
        }

        // Check if a crash happened on last run of the app or if a plugin crashed and show a
        // notification with the crash details if it did
        TermuxCrashUtils.notifyAppCrashFromCrashLogFile(this, LOG_TAG);

        mIsOnResumeAfterOnCreate = false;
    }

    @Override
    protected void onStop() {
        super.onStop();

        Logger.logDebug(LOG_TAG, "onStop");

        if (mIsInvalidState) return;

        mIsVisible = false;

        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onStop();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onStop();

        removeTermuxActivityRootViewGlobalLayoutListener();

        mSessionBarHandler.removeCallbacks(mSessionBarPoll);

        unregisterTermuxActivityBroadcastReceiver();
        getDrawer().closeDrawers();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        Logger.logDebug(LOG_TAG, "onDestroy");

        if (mBabyModeController != null) {
            mBabyModeController.destroy();
            mBabyModeController = null;
        }

        if (mIsInvalidState) return;

        if (mTermuxService != null) {
            // Do not leave service and session clients with references to activity.
            mTermuxService.unsetTermuxTerminalSessionClient();
            mTermuxService = null;
        }

        try {
            unbindService(this);
        } catch (Exception e) {
            // ignore.
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle savedInstanceState) {
        Logger.logVerbose(LOG_TAG, "onSaveInstanceState");

        super.onSaveInstanceState(savedInstanceState);
        saveTerminalToolbarTextInput(savedInstanceState);
        savedInstanceState.putBoolean(ARG_ACTIVITY_RECREATED, true);
        if (mPendingProCommand != null) {
            savedInstanceState.putString(ARG_PENDING_PRO_COMMAND, mPendingProCommand);
            savedInstanceState.putLong(ARG_PENDING_PRO_COMMAND_AT, mPendingProCommandAtMs);
            savedInstanceState.putBoolean(ARG_PENDING_PRO_COMMAND_EXECUTE, mPendingProCommandExecute);
        }
    }





    /**
     * Part of the {@link ServiceConnection} interface. The service is bound with
     * {@link #bindService(Intent, ServiceConnection, int)} in {@link #onCreate(Bundle)} which will cause a call to this
     * callback method.
     */
    @Override
    public void onServiceConnected(ComponentName componentName, IBinder service) {
        Logger.logDebug(LOG_TAG, "onServiceConnected");

        mTermuxService = ((TermuxService.LocalBinder) service).service;

        setTermuxSessionsListView();

        final Intent intent = getIntent();
        setIntent(null);

        if (mTermuxService.isTermuxSessionsEmpty()) {
            if (mIsVisible) {
                ensureBootstrapAndInitialSession(intent);
            } else if (!mIsOnboardingInProgress) {
                // The service connected while not in foreground - just bail out.
                finishActivityIfNotFinishing();
            }
        } else {
            ensureHushLoginOnce();
            BabyShellDefaults.ensureDefaultShellFiles();
            if (mBabyModeController != null) {
                mBabyModeController.setEnvironmentReady(true);
            }

            // If termux was started from launcher "New session" shortcut and activity is recreated,
            // then the original intent will be re-delivered, resulting in a new session being re-added
            // each time.
            if (!mIsActivityRecreated && intent != null && Intent.ACTION_RUN.equals(intent.getAction())) {
                // Android 7.1 app shortcut from res/xml/shortcuts.xml.
                boolean isFailSafe = intent.getBooleanExtra(TERMUX_ACTIVITY.EXTRA_FAILSAFE_SESSION, false);
                mTermuxTerminalSessionActivityClient.addNewSession(isFailSafe, null);
            } else {
                mTermuxTerminalSessionActivityClient.setCurrentSession(mTermuxTerminalSessionActivityClient.getCurrentStoredSessionOrLast());
            }
            flushPendingProCommand();
        }

        // Update the {@link TerminalSession} and {@link TerminalEmulator} clients.
        mTermuxService.setTermuxTerminalSessionClient(mTermuxTerminalSessionActivityClient);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        Logger.logDebug(LOG_TAG, "onServiceDisconnected");

        // Respect being stopped from the {@link TermuxService} notification action.
        finishActivityIfNotFinishing();
    }






    private void reloadProperties() {
        mProperties.loadTermuxPropertiesFromDisk();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onReloadProperties();
    }



    private void setActivityTheme() {
        // Update NightMode.APP_NIGHT_MODE
        TermuxThemeUtils.setAppNightMode(mProperties.getNightMode());

        // Set activity night mode. If NightMode.SYSTEM is set, then android will automatically
        // trigger recreation of activity when uiMode/dark mode configuration is changed so that
        // day or night theme takes affect.
        AppCompatActivityUtils.setNightMode(this, NightMode.getAppNightMode().getName(), true);
    }

    private void setMargins() {
        RelativeLayout relativeLayout = findViewById(R.id.activity_termux_root_relative_layout);
        if (mIsBabyModeActive) {
            ViewUtils.setLayoutMarginsInDp(relativeLayout, 0, 0, 0, 0);
            return;
        }
        int marginHorizontal = mProperties.getTerminalMarginHorizontal();
        int marginVertical = mProperties.getTerminalMarginVertical();
        ViewUtils.setLayoutMarginsInDp(relativeLayout, marginHorizontal, marginVertical, marginHorizontal, marginVertical);
    }



    public void addTermuxActivityRootViewGlobalLayoutListener() {
        getTermuxActivityRootView().getViewTreeObserver().addOnGlobalLayoutListener(getTermuxActivityRootView());
    }

    public void removeTermuxActivityRootViewGlobalLayoutListener() {
        if (getTermuxActivityRootView() != null)
            getTermuxActivityRootView().getViewTreeObserver().removeOnGlobalLayoutListener(getTermuxActivityRootView());
    }



    private void setTermuxTerminalViewAndClients() {
        // Set termux terminal view and session clients
        mTermuxTerminalSessionActivityClient = new TermuxTerminalSessionActivityClient(this);
        mTermuxTerminalViewClient = new TermuxTerminalViewClient(this, mTermuxTerminalSessionActivityClient);

        // Set termux terminal view
        mTerminalView = findViewById(R.id.terminal_view);
        mTerminalView.setTerminalViewClient(mTermuxTerminalViewClient);

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onCreate();

        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onCreate();
    }

    private void setTermuxSessionsListView() {
        ListView termuxSessionsListView = findViewById(R.id.terminal_sessions_list);
        mTermuxSessionListViewController = new TermuxSessionsListViewController(this, mTermuxService.getTermuxSessions());
        termuxSessionsListView.setAdapter(mTermuxSessionListViewController);
        termuxSessionsListView.setOnItemClickListener(mTermuxSessionListViewController);
        termuxSessionsListView.setOnItemLongClickListener(mTermuxSessionListViewController);
    }



    private void setTerminalToolbarView(Bundle savedInstanceState) {
        mAppliedExtraKeysPreset = BabyPreferences.getExtraKeysPreset(this);
        mTermuxTerminalExtraKeys = new TermuxTerminalExtraKeys(this, mTerminalView,
            mTermuxTerminalViewClient, mTermuxTerminalSessionActivityClient);

        final ViewPager terminalToolbarViewPager = getTerminalToolbarViewPager();
        if (mPreferences.shouldShowTerminalToolbar()) terminalToolbarViewPager.setVisibility(View.VISIBLE);

        ViewGroup.LayoutParams layoutParams = terminalToolbarViewPager.getLayoutParams();
        mTerminalToolbarDefaultHeight = layoutParams.height;

        setTerminalToolbarHeight();

        String savedTextInput = null;
        if (savedInstanceState != null)
            savedTextInput = savedInstanceState.getString(ARG_TERMINAL_TOOLBAR_TEXT_INPUT);

        terminalToolbarViewPager.setAdapter(new TerminalToolbarViewPager.PageAdapter(this, savedTextInput));
        terminalToolbarViewPager.addOnPageChangeListener(new TerminalToolbarViewPager.OnPageChangeListener(this, terminalToolbarViewPager));
    }

    private void reloadExtraKeysView() {
        if (mTermuxTerminalExtraKeys != null) {
            mTermuxTerminalExtraKeys.setExtraKeys();
            if (mExtraKeysView != null) {
                mExtraKeysView.setButtonTextAllCaps(mProperties.shouldExtraKeysTextBeAllCaps());
                mExtraKeysView.reload(mTermuxTerminalExtraKeys.getExtraKeysInfo(), mTerminalToolbarDefaultHeight);
            }
        }
        setTerminalToolbarHeight();
    }

    private void setTerminalToolbarHeight() {
        final ViewPager terminalToolbarViewPager = getTerminalToolbarViewPager();
        if (terminalToolbarViewPager == null) return;

        int rowCount = (mTermuxTerminalExtraKeys == null || mTermuxTerminalExtraKeys.getExtraKeysInfo() == null)
            ? 0
            : mTermuxTerminalExtraKeys.getExtraKeysInfo().getMatrix().length;

        float baseHeightPx;
        if (rowCount <= 0) {
            baseHeightPx = 0f;
        } else if (rowCount == 1) {
            // Comfortable 1-row touch strip (~48dp from layout_height)
            baseHeightPx = mTerminalToolbarDefaultHeight;
        } else {
            // Spacious multi-row layout (48dp per row = Material minimum touch
            // target, ~96dp for 2 rows) so extra keys stay easy to tap on phones
            float multiRowHeightPx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 48f, getResources().getDisplayMetrics());
            baseHeightPx = multiRowHeightPx * rowCount;
        }

        ViewGroup.LayoutParams layoutParams = terminalToolbarViewPager.getLayoutParams();
        layoutParams.height = Math.round(baseHeightPx * mProperties.getTerminalToolbarHeightScaleFactor());
        terminalToolbarViewPager.setLayoutParams(layoutParams);
    }

    public void toggleTerminalToolbar() {
        final ViewPager terminalToolbarViewPager = getTerminalToolbarViewPager();
        if (terminalToolbarViewPager == null) return;

        final boolean showNow = mPreferences.toogleShowTerminalToolbar();
        Logger.showToast(this, (showNow ? getString(R.string.msg_enabling_terminal_toolbar) : getString(R.string.msg_disabling_terminal_toolbar)), true);
        terminalToolbarViewPager.setVisibility((!mIsBabyModeActive && showNow) ? View.VISIBLE : View.GONE);
        if (!mIsBabyModeActive && showNow && isTerminalToolbarTextInputViewSelected()) {
            // Focus the text input view if just revealed.
            findViewById(R.id.terminal_toolbar_text_input).requestFocus();
        }
    }

    private void saveTerminalToolbarTextInput(Bundle savedInstanceState) {
        if (savedInstanceState == null) return;

        final EditText textInputView = findViewById(R.id.terminal_toolbar_text_input);
        if (textInputView != null) {
            String textInput = textInputView.getText().toString();
            if (!textInput.isEmpty()) savedInstanceState.putString(ARG_TERMINAL_TOOLBAR_TEXT_INPUT, textInput);
        }
    }



    private void setSettingsButtonView() {
        ImageButton settingsButton = findViewById(R.id.settings_button);
        settingsButton.setOnClickListener(v -> {
            ActivityUtils.startActivity(this, new Intent(this, SettingsActivity.class));
        });
    }

    private void setNewSessionButtonView() {
        View newSessionButton = findViewById(R.id.new_session_button);
        newSessionButton.setOnClickListener(v -> mTermuxTerminalSessionActivityClient.addNewSession(false, null));
        newSessionButton.setOnLongClickListener(v -> {
            TextInputDialogUtils.textInput(TermuxActivity.this, R.string.title_create_named_session, null,
                R.string.action_create_named_session_confirm, text -> mTermuxTerminalSessionActivityClient.addNewSession(false, text),
                R.string.action_new_session_failsafe, text -> mTermuxTerminalSessionActivityClient.addNewSession(true, text),
                -1, null, null);
            return true;
        });
    }

    private void setToggleKeyboardView() {
        findViewById(R.id.toggle_keyboard_button).setOnClickListener(v -> {
            mTermuxTerminalViewClient.onToggleSoftKeyboardRequest();
            getDrawer().closeDrawers();
        });

        findViewById(R.id.toggle_keyboard_button).setOnLongClickListener(v -> {
            toggleTerminalToolbar();
            return true;
        });
    }

    private void setBabyModeViews() {
        mBabyTopBar = findViewById(R.id.baby_top_bar);
        mBabyProReturnPill = findViewById(R.id.baby_pro_return_pill);
        mBabyModeEasyTab = findViewById(R.id.baby_mode_easy_tab);
        mBabyModeProTab = findViewById(R.id.baby_mode_pro_tab);
        View babySettingsButton = findViewById(R.id.baby_settings_button);
        View babyHomeRoot = findViewById(R.id.baby_home_root);

        if (babyHomeRoot != null) {
            mBabyModeController = new BabyModeController(this, babyHomeRoot, new BabyModeController.HostCallbacks() {
                @Override
                public void onSwitchToProMode(@Nullable String optionalCommandToRun) {
                    switchToProMode(optionalCommandToRun);
                }

                @Override
                public void onTypeInProMode(@Nullable String commandToType) {
                    typeCommandInProTerminal(commandToType);
                }

                @Override
                public void onOpenSettings() {
                    ActivityUtils.startActivity(TermuxActivity.this, new Intent(TermuxActivity.this, SettingsActivity.class));
                }

                @Override
                public void onRunSessionsChanged() {
                    refreshSessionBar();
                }
            });
        }

        if (mBabyModeEasyTab != null) {
            mBabyModeEasyTab.setOnClickListener(v -> switchToBabyMode());
        }
        if (mBabyModeProTab != null) {
            mBabyModeProTab.setOnClickListener(v -> switchToProMode(null));
        }
        // The Pro-mode pill is a two-tab switcher (Easy | Terminal): only the
        // Easy tab is actionable; the Terminal tab is the selected state.
        View proPillEasyTab = findViewById(R.id.baby_pro_pill_easy_tab);
        if (proPillEasyTab != null) {
            proPillEasyTab.setOnClickListener(v -> switchToBabyMode());
        }

        mSessionBar = findViewById(R.id.baby_session_bar);
        mSessionBarChips = findViewById(R.id.baby_session_bar_chips);
        refreshSessionBar();
        if (babySettingsButton != null) {
            babySettingsButton.setOnClickListener(v ->
                ActivityUtils.startActivity(this, new Intent(this, SettingsActivity.class))
            );
        }

        applyModeUi(BabyPreferences.isBabyMode(this), false);
    }

    /**
     * Closing a terminal session from the bar kills its shell instantly, so
     * it always asks first — an agent running in that terminal would
     * otherwise be destroyed by a tidy-up tap. Closing the last session also
     * exits the app (stock Termux behavior), which the message calls out.
     */
    private void confirmCloseTerminalSession(@NonNull TerminalSession terminalSession) {
        if (mTermuxService == null) return;
        boolean isLast = mTermuxService.getTermuxSessions().size() <= 1;
        String message = getString(R.string.baby_session_close_terminal_message);
        if (isLast) {
            message += "\n" + getString(R.string.baby_session_close_last_message);
        }
        new AlertDialog.Builder(this)
            .setTitle(R.string.baby_session_close_title)
            .setMessage(message)
            .setNegativeButton(R.string.baby_install_confirm_cancel, null)
            .setPositiveButton(R.string.baby_session_close_button, (dialog, which) -> {
                if (mTermuxService != null) {
                    mTermuxService.removeTermuxSession(terminalSession);
                }
                mSessionBarSignature = null;
                refreshSessionBar();
            })
            .show();
    }

    /** Asks before the bar's close button stops a still-running Baby run. */
    private void confirmCloseRunSession(@NonNull BabyRunSession runSession) {
        new AlertDialog.Builder(this)
            .setTitle(R.string.baby_session_close_title)
            .setMessage(getString(R.string.baby_session_close_run_message, runSession.title))
            .setNegativeButton(R.string.baby_install_confirm_cancel, null)
            .setPositiveButton(R.string.baby_session_close_button, (dialog, which) -> {
                if (mBabyModeController != null) {
                    mBabyModeController.dismissRunSession(runSession);
                }
                refreshSessionBar();
            })
            .show();
    }

    /**
     * Places the session bar (and the Pro mode pill) where the current mode
     * expects them. Easy mode: the bar is a strip in the root column under
     * the top bar, and the pill floats over the terminal's top end. Pro
     * mode: BOTH views move into the shared top row in the column — the
     * bar scrolls in the weight-1 slot, the pill sits after it. Sharing a
     * row makes overlap impossible by construction (an earlier overlay
     * placement guessed the pill's width and the chips slid under it) and
     * the terminal keeps its full height.
     */
    static void placeSessionBarForMode(@NonNull View sessionBar, @Nullable View topBar,
                                       @Nullable View pill, @Nullable ViewGroup proRow,
                                       @Nullable ViewGroup proContainer, boolean proMode) {
        float density = sessionBar.getResources().getDisplayMetrics().density;
        if (proMode) {
            if (proRow == null) return;
            if (sessionBar.getParent() != proRow) {
                ViewGroup old = (ViewGroup) sessionBar.getParent();
                if (old != null) old.removeView(sessionBar);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                proRow.addView(sessionBar, 0, lp);
            }
            sessionBar.setBackgroundColor(Color.TRANSPARENT);
            sessionBar.setElevation(0f);
            if (pill != null && pill.getParent() != proRow) {
                ViewGroup old = (ViewGroup) pill.getParent();
                if (old != null) old.removeView(pill);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, Math.round(34 * density));
                lp.setMarginStart(Math.round(8 * density));
                proRow.addView(pill, lp);
            }
            proRow.setVisibility(View.VISIBLE);
        } else {
            if (proRow != null) proRow.setVisibility(View.GONE);
            if (pill != null && proContainer != null && pill.getParent() != proContainer) {
                ViewGroup old = (ViewGroup) pill.getParent();
                if (old != null) old.removeView(pill);
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, Math.round(34 * density));
                lp.gravity = Gravity.TOP | Gravity.END;
                lp.topMargin = Math.round(4 * density);
                lp.setMarginEnd(Math.round(10 * density));
                proContainer.addView(pill, lp);
            }
            if (topBar == null || topBar.getParent() == null) return;
            ViewGroup root = (ViewGroup) topBar.getParent();
            if (sessionBar.getParent() != root) {
                ViewGroup old = (ViewGroup) sessionBar.getParent();
                if (old != null) old.removeView(sessionBar);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                root.addView(sessionBar, root.indexOfChild(topBar) + 1, lp);
            }
            sessionBar.setBackgroundColor(
                ContextCompat.getColor(sessionBar.getContext(), R.color.baby_bg));
            sessionBar.setElevation(0f);
        }
    }

    private void placeSessionBar(boolean proMode) {
        if (mSessionBar == null) return;
        ViewGroup proRow = findViewById(R.id.baby_pro_top_row);
        ViewGroup proContainer = findViewById(R.id.termux_content_container);
        placeSessionBarForMode(
            mSessionBar, mBabyTopBar, mBabyProReturnPill, proRow, proContainer, proMode);
    }

    /** Gentle alpha pulse for a live session chip's dot; stops on detach. */
    static void startSessionDotPulse(@NonNull final View dot) {
        final ObjectAnimator animator = ObjectAnimator.ofFloat(dot, View.ALPHA, 1f, 0.25f);
        animator.setDuration(950L);
        animator.setRepeatMode(ValueAnimator.REVERSE);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        dot.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                animator.cancel();
                v.removeOnAttachStateChangeListener(this);
            }
        });
        animator.start();
    }

    /**
     * Rebuilds the session bar chips from the two session sources: Baby run
     * sessions (installs and card actions tracked by the controller registry)
     * and live terminal sessions from TermuxService. The bar shows in BOTH
     * modes; in Pro mode the session currently on screen gets a chip too,
     * marked "showing". A cheap signature check avoids rebuilding on every
     * poll.
     */
    private void refreshSessionBar() {
        if (mSessionBar == null || mSessionBarChips == null) return;

        if (mBabyModeController != null) {
            // Finished sessions the user already saw (e.g. watched to
            // completion in the runner sheet) leave the bar here.
            mBabyModeController.getRunSessionRegistry().pruneSeenFinishedQuietly();
        }

        java.util.List<BabyRunSession> runSessions = mBabyModeController != null
            ? mBabyModeController.getRunSessionRegistry().getSessions()
            : java.util.Collections.emptyList();

        // The bar shows in BOTH modes (it is also the terminal tab's session
        // switcher): every live terminal session gets a chip, including the
        // one currently on screen in Pro mode, which is marked "showing".
        final TerminalSession currentSession = mIsBabyModeActive ? null : getCurrentSession();
        java.util.List<TerminalSession> terminalSessions = new java.util.ArrayList<>();
        if (mTermuxService != null) {
            for (TermuxSession termuxSession : mTermuxService.getTermuxSessions()) {
                TerminalSession terminalSession = termuxSession.getTerminalSession();
                if (terminalSession == null || !terminalSession.isRunning()) continue;
                terminalSessions.add(terminalSession);
            }
        }

        StringBuilder signature = new StringBuilder();
        for (BabyRunSession s : runSessions) {
            signature.append('r').append(s.id).append(':').append(s.getState()).append(';');
        }
        for (TerminalSession t : terminalSessions) {
            signature.append('t').append(System.identityHashCode(t));
            if (t == currentSession) signature.append('*');
            signature.append(';');
        }
        String newSignature = signature.toString();
        if (newSignature.equals(mSessionBarSignature)) return;
        mSessionBarSignature = newSignature;

        mSessionBarChips.removeAllViews();
        android.view.LayoutInflater inflater = android.view.LayoutInflater.from(this);

        for (BabyRunSession runSession : runSessions) {
            View chip = inflater.inflate(R.layout.item_baby_session_chip, mSessionBarChips, false);
            View spinner = chip.findViewById(R.id.chip_spinner);
            android.widget.ImageView icon = chip.findViewById(R.id.chip_icon);
            TextView title = chip.findViewById(R.id.chip_title);
            TextView state = chip.findViewById(R.id.chip_state);

            title.setText(runSession.title);
            if (runSession.isRunning()) {
                spinner.setVisibility(View.VISIBLE);
                icon.setVisibility(View.GONE);
                state.setText(R.string.baby_session_bar_running);
            } else {
                spinner.setVisibility(View.GONE);
                icon.setVisibility(View.VISIBLE);
                boolean success = runSession.getState() == BabyRunSession.State.SUCCESS;
                icon.setImageResource(success ? R.drawable.ic_baby_check : R.drawable.ic_baby_warning);
                state.setText(success ? R.string.baby_session_bar_done : R.string.baby_session_bar_failed);
            }
            chip.setContentDescription(runSession.title + ". " + state.getText());
            chip.setOnClickListener(v -> {
                if (mBabyModeController != null) {
                    mBabyModeController.openRunSession(runSession);
                }
            });
            View runClose = chip.findViewById(R.id.chip_close);
            runClose.setOnClickListener(v -> {
                if (runSession.isRunning()) {
                    confirmCloseRunSession(runSession);
                } else if (mBabyModeController != null) {
                    mBabyModeController.dismissRunSession(runSession);
                    refreshSessionBar();
                }
            });
            mSessionBarChips.addView(chip);
        }

        for (TerminalSession terminalSession : terminalSessions) {
            View chip = inflater.inflate(R.layout.item_baby_session_chip, mSessionBarChips, false);
            View spinner = chip.findViewById(R.id.chip_spinner);
            android.widget.ImageView icon = chip.findViewById(R.id.chip_icon);
            TextView title = chip.findViewById(R.id.chip_title);
            TextView state = chip.findViewById(R.id.chip_state);

            String name = terminalSession.mSessionName;
            title.setText(name != null && !name.trim().isEmpty()
                ? name
                : getString(R.string.baby_session_bar_terminal));
            spinner.setVisibility(View.GONE);
            icon.setVisibility(View.VISIBLE);
            icon.setImageResource(R.drawable.ic_baby_terminal);
            // A live terminal session gets a gently pulsing dot, so the chip
            // itself shows that something is alive behind it.
            View dot = chip.findViewById(R.id.chip_dot);
            if (dot != null) {
                dot.setVisibility(View.VISIBLE);
                startSessionDotPulse(dot);
            }
            boolean isCurrent = terminalSession == currentSession;
            state.setText(isCurrent
                ? R.string.baby_session_bar_current
                : R.string.baby_session_bar_running);
            chip.setContentDescription(title.getText() + ". " + state.getText());
            chip.setOnClickListener(v -> {
                if (isCurrent) return; // Already on screen.
                clearPendingProCommand();
                if (mTermuxTerminalSessionActivityClient != null) {
                    mTermuxTerminalSessionActivityClient.setCurrentSession(terminalSession);
                }
                switchToProMode(null);
            });
            View termClose = chip.findViewById(R.id.chip_close);
            termClose.setOnClickListener(v -> confirmCloseTerminalSession(terminalSession));
            mSessionBarChips.addView(chip);
        }

        mSessionBar.setVisibility(mSessionBarChips.getChildCount() > 0 ? View.VISIBLE : View.GONE);
    }

    public boolean isBabyModeActive() {
        return mIsBabyModeActive;
    }

    public void switchToBabyMode() {
        clearPendingProCommand();
        BabyPreferences.setBabyMode(this, true);
        applyModeUi(true, false);
    }

    public void switchToProMode(@Nullable String optionalCommandToRun) {
        BabyPreferences.setBabyMode(this, false);
        applyModeUi(false, true);
        if (optionalCommandToRun != null && !optionalCommandToRun.trim().isEmpty()) {
            sendCommandToProTerminal(optionalCommandToRun);
        }
    }

    /**
     * Switches to Pro mode and TYPES the command without running it, so the
     * user presses Enter themselves (Easy Mode learning loop, final step).
     */
    public void typeCommandInProTerminal(@Nullable String commandToType) {
        BabyPreferences.setBabyMode(this, false);
        applyModeUi(false, true);
        if (commandToType != null && !commandToType.trim().isEmpty()) {
            sendCommandToProTerminal(commandToType, false);
        }
    }

    /**
     * Termux Baby: in Pro mode the "back to Easy" pill used to float on top of the
     * terminal output and hide its top-right corner. Reserve a dedicated top strip
     * for it instead: the TerminalView gets a top margin in Pro mode so it renders
     * below the pill and no output is ever covered. (Padding the shared container
     * would not work: the pill is a child of the same container and would be
     * pushed into the terminal area together with it.)
     * Strip height = pill height (34dp) + its top margin (4dp) + breathing room.
     */
    private void setProReturnPillTopInset(boolean proMode) {
        if (mTerminalView == null) return;
        ViewGroup.LayoutParams params = mTerminalView.getLayoutParams();
        if (!(params instanceof FrameLayout.LayoutParams)) return;
        FrameLayout.LayoutParams frameParams = (FrameLayout.LayoutParams) params;
        int topPx = proMode
            ? (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 42, getResources().getDisplayMetrics())
            : 0;
        if (frameParams.topMargin != topPx) {
            frameParams.topMargin = topPx;
            mTerminalView.setLayoutParams(frameParams);
        }
    }

    private void applyModeUi(boolean babyMode, boolean restoreSoftKeyboard) {
        mIsBabyModeActive = babyMode;
        DrawerLayout drawer = getDrawer();
        ViewPager toolbarPager = getTerminalToolbarViewPager();

        if (babyMode) {
            if (drawer != null) {
                drawer.closeDrawers();
                drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
            }
            if (mBabyTopBar != null) mBabyTopBar.setVisibility(View.VISIBLE);
            if (mBabyProReturnPill != null) mBabyProReturnPill.setVisibility(View.GONE);
            setProReturnPillTopInset(false);
            updateModeSwitcherTabs(true);

            if (mBabyModeController != null) {
                mBabyModeController.showBabyHome();
            }
            if (toolbarPager != null) {
                toolbarPager.setVisibility(View.GONE);
            }
            setMargins();

            if (mTerminalView != null) {
                KeyboardUtils.hideSoftKeyboard(this, mTerminalView);
                mTerminalView.clearFocus();
            }
            getWindow().getDecorView().setBackgroundColor(ContextCompat.getColor(this, R.color.baby_bg));
            updateBabySystemBarsAppearance(true);
        } else {
            if (mBabyTopBar != null) mBabyTopBar.setVisibility(View.GONE);
            if (mBabyProReturnPill != null) mBabyProReturnPill.setVisibility(View.VISIBLE);
            setProReturnPillTopInset(true);
            updateModeSwitcherTabs(false);

            if (mBabyModeController != null) {
                mBabyModeController.hideBabyHome();
            }
            if (drawer != null) {
                drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
            }
            setMargins();
            if (toolbarPager != null && mPreferences != null) {
                toolbarPager.setVisibility(mPreferences.shouldShowTerminalToolbar() ? View.VISIBLE : View.GONE);
            }

            updateBabySystemBarsAppearance(false);
            if (mTermuxTerminalSessionActivityClient != null) {
                mTermuxTerminalSessionActivityClient.updateBackgroundColor();
            }
            if (mTerminalView != null) {
                mTerminalView.requestFocus();
                mTerminalView.onScreenUpdated();
            }
            if (restoreSoftKeyboard && mTermuxTerminalViewClient != null) {
                mTermuxTerminalViewClient.setSoftKeyboardState(false, false);
            }
        }

        placeSessionBar(!babyMode);
        refreshSessionBar();
    }

    private void updateModeSwitcherTabs(boolean babyActive) {
        if (mBabyModeEasyTab != null && mBabyModeProTab != null) {
            int onPrimary = ContextCompat.getColor(this, R.color.baby_on_primary);
            int secondary = ContextCompat.getColor(this, R.color.baby_text_secondary);
            if (babyActive) {
                mBabyModeEasyTab.setBackgroundResource(R.drawable.baby_pill_selected);
                mBabyModeEasyTab.setTextColor(onPrimary);
                mBabyModeProTab.setBackground(null);
                mBabyModeProTab.setTextColor(secondary);
            } else {
                mBabyModeEasyTab.setBackground(null);
                mBabyModeEasyTab.setTextColor(secondary);
                mBabyModeProTab.setBackgroundResource(R.drawable.baby_pill_selected);
                mBabyModeProTab.setTextColor(onPrimary);
            }
        }
    }

    private void updateBabySystemBarsAppearance(boolean babyMode) {
        Window window = getWindow();
        if (window == null) return;
        window.clearFlags(
            WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
                | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION
        );
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);

        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(window, window.getDecorView());
        if (babyMode) {
            int babyBg = ContextCompat.getColor(this, R.color.baby_bg);
            window.setStatusBarColor(babyBg);
            window.setNavigationBarColor(babyBg);

            int nightMask = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            boolean isNight = (nightMask == Configuration.UI_MODE_NIGHT_YES);
            controller.setAppearanceLightStatusBars(!isNight);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                controller.setAppearanceLightNavigationBars(!isNight);
            }
        } else {
            int proBarColor = 0xFF161920;
            TerminalSession session = getCurrentSession();
            if (session != null && session.getEmulator() != null) {
                proBarColor = session.getEmulator().mColors.mCurrentColors[com.termux.terminal.TextStyle.COLOR_INDEX_BACKGROUND];
            }
            window.setStatusBarColor(proBarColor);
            window.setNavigationBarColor(proBarColor);

            controller.setAppearanceLightStatusBars(false);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                controller.setAppearanceLightNavigationBars(false);
            }
        }
    }

    private void ensureBootstrapAndInitialSession(@Nullable final Intent intent) {
        if (mIsBootstrapSetupRunning) return;
        mIsBootstrapSetupRunning = true;
        TermuxInstaller.setupBootstrapIfNeeded(TermuxActivity.this, () -> {
            mIsBootstrapSetupRunning = false;
            if (mTermuxService == null) return; // Activity might have been destroyed.
            ensureHushLoginOnce();
            BabyShellDefaults.ensureDefaultShellFiles();
            if (mBabyModeController != null) {
                mBabyModeController.setEnvironmentReady(true);
            }
            try {
                boolean launchFailsafe = false;
                if (intent != null && intent.getExtras() != null) {
                    launchFailsafe = intent.getExtras().getBoolean(TERMUX_ACTIVITY.EXTRA_FAILSAFE_SESSION, false);
                }
                mTermuxTerminalSessionActivityClient.addNewSession(launchFailsafe, null);
                flushPendingProCommand();
            } catch (WindowManager.BadTokenException e) {
                // Activity finished - ignore.
            }
        });
    }

    private void ensureHushLoginOnce() {
        try {
            if (BabyPreferences.isHushLoginInitialized(getApplicationContext())) {
                return;
            }
            File homeDir = TermuxConstants.TERMUX_HOME_DIR;
            if (homeDir != null && (homeDir.isDirectory() || homeDir.mkdirs())) {
                File hushLogin = new File(homeDir, ".hushlogin");
                // Only record success when the file is actually there;
                // flagging a failed creation would disable retry forever
                // and the login banner would keep appearing.
                boolean created = hushLogin.exists() || hushLogin.createNewFile();
                if (created) {
                    BabyPreferences.setHushLoginInitialized(getApplicationContext(), true);
                }
            }
        } catch (IOException | SecurityException e) {
            Logger.logDebug(LOG_TAG, "Skipping ~/.hushlogin initialization: " + e.getMessage());
        }
    }

    private void sendCommandToProTerminal(@NonNull String command) {
        sendCommandToProTerminal(command, true);
    }

    /**
     * Writes a command into the current Pro session. With {@code execute}
     * false the command is only TYPED (no newline): the user presses Enter
     * themselves — the final step of the Easy Mode learning loop.
     */
    private void sendCommandToProTerminal(@NonNull String command, boolean execute) {
        TerminalSession session = getCurrentSession();
        if (session != null && session.isRunning()) {
            session.write(execute && !command.endsWith("\n") ? command + "\n" : command);
        } else {
            // No session to receive it yet: park it briefly. It is flushed
            // only by the bootstrap/service-connected paths that create the
            // session it was meant for, and expires quickly (see
            // flushPendingProCommand) so it can never be typed into an
            // unrelated session much later.
            mPendingProCommand = command;
            mPendingProCommandAtMs = System.currentTimeMillis();
            mPendingProCommandExecute = execute;
        }
    }

    private void flushPendingProCommand() {
        if (mPendingProCommand == null) return;
        if (System.currentTimeMillis() - mPendingProCommandAtMs > PENDING_PRO_COMMAND_EXPIRY_MS) {
            mPendingProCommand = null;
            return;
        }
        TerminalSession session = getCurrentSession();
        if (session != null && session.isRunning()) {
            String cmd = mPendingProCommand;
            boolean execute = mPendingProCommandExecute;
            mPendingProCommand = null;
            session.write(execute && !cmd.endsWith("\n") ? cmd + "\n" : cmd);
        }
    }

    /** Drops a parked Pro command (the user navigated somewhere themselves). */
    private void clearPendingProCommand() {
        mPendingProCommand = null;
    }





    @SuppressLint("RtlHardcoded")
    @Override
    public void onBackPressed() {
        if (getDrawer().isDrawerOpen(Gravity.LEFT)) {
            getDrawer().closeDrawers();
        } else {
            finishActivityIfNotFinishing();
        }
    }

    public void finishActivityIfNotFinishing() {
        // prevent duplicate calls to finish() if called from multiple places
        if (!TermuxActivity.this.isFinishing()) {
            finish();
        }
    }

    /** Show a toast and dismiss the last one if still visible. */
    public void showToast(String text, boolean longDuration) {
        if (text == null || text.isEmpty()) return;
        if (mLastToast != null) mLastToast.cancel();
        mLastToast = Toast.makeText(TermuxActivity.this, text, longDuration ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT);
        mLastToast.setGravity(Gravity.TOP, 0, 0);
        mLastToast.show();
    }



    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        TerminalSession currentSession = getCurrentSession();
        if (currentSession == null) return;

        boolean autoFillEnabled = mTerminalView.isAutoFillEnabled();

        menu.add(Menu.NONE, CONTEXT_MENU_SELECT_URL_ID, Menu.NONE, R.string.action_select_url);
        menu.add(Menu.NONE, CONTEXT_MENU_SHARE_TRANSCRIPT_ID, Menu.NONE, R.string.action_share_transcript);
        if (!DataUtils.isNullOrEmpty(mTerminalView.getStoredSelectedText()))
            menu.add(Menu.NONE, CONTEXT_MENU_SHARE_SELECTED_TEXT, Menu.NONE, R.string.action_share_selected_text);
        if (autoFillEnabled)
            menu.add(Menu.NONE, CONTEXT_MENU_AUTOFILL_USERNAME, Menu.NONE, R.string.action_autofill_username);
        if (autoFillEnabled)
            menu.add(Menu.NONE, CONTEXT_MENU_AUTOFILL_PASSWORD, Menu.NONE, R.string.action_autofill_password);
        menu.add(Menu.NONE, CONTEXT_MENU_RESET_TERMINAL_ID, Menu.NONE, R.string.action_reset_terminal);
        menu.add(Menu.NONE, CONTEXT_MENU_KILL_PROCESS_ID, Menu.NONE, getResources().getString(R.string.action_kill_process, getCurrentSession().getPid())).setEnabled(currentSession.isRunning());
        menu.add(Menu.NONE, CONTEXT_MENU_STYLING_ID, Menu.NONE, R.string.action_style_terminal);
        menu.add(Menu.NONE, CONTEXT_MENU_TOGGLE_KEEP_SCREEN_ON, Menu.NONE, R.string.action_toggle_keep_screen_on).setCheckable(true).setChecked(mPreferences.shouldKeepScreenOn());
        menu.add(Menu.NONE, CONTEXT_MENU_HELP_ID, Menu.NONE, R.string.action_open_help);
        menu.add(Menu.NONE, CONTEXT_MENU_SETTINGS_ID, Menu.NONE, R.string.action_open_settings);
        menu.add(Menu.NONE, CONTEXT_MENU_REPORT_ID, Menu.NONE, R.string.action_report_issue);
    }

    /** Hook system menu to show context menu instead. */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        mTerminalView.showContextMenu();
        return false;
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        TerminalSession session = getCurrentSession();

        switch (item.getItemId()) {
            case CONTEXT_MENU_SELECT_URL_ID:
                mTermuxTerminalViewClient.showUrlSelection();
                return true;
            case CONTEXT_MENU_SHARE_TRANSCRIPT_ID:
                mTermuxTerminalViewClient.shareSessionTranscript();
                return true;
            case CONTEXT_MENU_SHARE_SELECTED_TEXT:
                mTermuxTerminalViewClient.shareSelectedText();
                return true;
            case CONTEXT_MENU_AUTOFILL_USERNAME:
                mTerminalView.requestAutoFillUsername();
                return true;
            case CONTEXT_MENU_AUTOFILL_PASSWORD:
                mTerminalView.requestAutoFillPassword();
                return true;
            case CONTEXT_MENU_RESET_TERMINAL_ID:
                onResetTerminalSession(session);
                return true;
            case CONTEXT_MENU_KILL_PROCESS_ID:
                showKillSessionDialog(session);
                return true;
            case CONTEXT_MENU_STYLING_ID:
                showStylingDialog();
                return true;
            case CONTEXT_MENU_TOGGLE_KEEP_SCREEN_ON:
                toggleKeepScreenOn();
                return true;
            case CONTEXT_MENU_HELP_ID:
                ActivityUtils.startActivity(this, new Intent(this, HelpActivity.class));
                return true;
            case CONTEXT_MENU_SETTINGS_ID:
                ActivityUtils.startActivity(this, new Intent(this, SettingsActivity.class));
                return true;
            case CONTEXT_MENU_REPORT_ID:
                mTermuxTerminalViewClient.reportIssueFromTranscript();
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    @Override
    public void onContextMenuClosed(Menu menu) {
        super.onContextMenuClosed(menu);
        // onContextMenuClosed() is triggered twice if back button is pressed to dismiss instead of tap for some reason
        mTerminalView.onContextMenuClosed(menu);
    }

    private void showKillSessionDialog(TerminalSession session) {
        if (session == null) return;

        final AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setIcon(android.R.drawable.ic_dialog_alert);
        b.setMessage(R.string.title_confirm_kill_process);
        b.setPositiveButton(android.R.string.yes, (dialog, id) -> {
            dialog.dismiss();
            session.finishIfRunning();
        });
        b.setNegativeButton(android.R.string.no, null);
        b.show();
    }

    private void onResetTerminalSession(TerminalSession session) {
        if (session != null) {
            session.reset();
            showToast(getResources().getString(R.string.msg_terminal_reset), true);

            if (mTermuxTerminalSessionActivityClient != null)
                mTermuxTerminalSessionActivityClient.onResetTerminalSession();
        }
    }

    private void showStylingDialog() {
        Intent stylingIntent = new Intent();
        stylingIntent.setClassName(TermuxConstants.TERMUX_STYLING_PACKAGE_NAME, TermuxConstants.TERMUX_STYLING_APP.TERMUX_STYLING_ACTIVITY_NAME);
        try {
            startActivity(stylingIntent);
        } catch (ActivityNotFoundException | IllegalArgumentException e) {
            // The startActivity() call is not documented to throw IllegalArgumentException.
            // However, crash reporting shows that it sometimes does, so catch it here.
            new AlertDialog.Builder(this).setMessage(getString(R.string.error_styling_not_installed))
                .setPositiveButton(R.string.action_styling_install,
                    (dialog, which) -> ActivityUtils.startActivity(this, new Intent(Intent.ACTION_VIEW, Uri.parse(TermuxConstants.TERMUX_STYLING_FDROID_PACKAGE_URL))))
                .setNegativeButton(android.R.string.cancel, null).show();
        }
    }
    private void toggleKeepScreenOn() {
        if (mTerminalView.getKeepScreenOn()) {
            mTerminalView.setKeepScreenOn(false);
            mPreferences.setKeepScreenOn(false);
        } else {
            mTerminalView.setKeepScreenOn(true);
            mPreferences.setKeepScreenOn(true);
        }
    }



    /**
     * For processes to access primary external storage (/sdcard, /storage/emulated/0, ~/storage/shared),
     * termux needs to be granted legacy WRITE_EXTERNAL_STORAGE or MANAGE_EXTERNAL_STORAGE permissions
     * if targeting targetSdkVersion 30 (android 11) and running on sdk 30 (android 11) and higher.
     */
    public void requestStoragePermission(boolean isPermissionCallback) {
        new Thread() {
            @Override
            public void run() {
                // Do not ask for permission again
                int requestCode = isPermissionCallback ? -1 : PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION;

                // If permission is granted, then also setup storage symlinks.
                if(PermissionUtils.checkAndRequestLegacyOrManageExternalStoragePermission(
                    TermuxActivity.this, requestCode, !isPermissionCallback)) {
                    if (isPermissionCallback)
                        Logger.logInfoAndShowToast(TermuxActivity.this, LOG_TAG,
                            getString(com.termux.shared.R.string.msg_storage_permission_granted_on_request));

                    TermuxInstaller.setupStorageSymlinks(TermuxActivity.this);
                } else {
                    if (isPermissionCallback)
                        Logger.logInfoAndShowToast(TermuxActivity.this, LOG_TAG,
                            getString(com.termux.shared.R.string.msg_storage_permission_not_granted_on_request));
                }
            }
        }.start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Logger.logVerbose(LOG_TAG, "onActivityResult: requestCode: " + requestCode + ", resultCode: "  + resultCode + ", data: "  + IntentUtils.getIntentString(data));
        if (requestCode == PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION) {
            requestStoragePermission(true);
        } else if (requestCode == BabyOnboardingActivity.REQUEST_CODE_ONBOARDING) {
            mIsOnboardingInProgress = false;
            applyModeUi(BabyPreferences.isBabyMode(this), false);
            if (mBabyModeController != null) {
                mBabyModeController.refreshState();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Logger.logVerbose(LOG_TAG, "onRequestPermissionsResult: requestCode: " + requestCode + ", permissions: "  + Arrays.toString(permissions) + ", grantResults: "  + Arrays.toString(grantResults));
        if (requestCode == PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION) {
            requestStoragePermission(true);
        }
    }



    public int getNavBarHeight() {
        return mNavBarHeight;
    }

    public TermuxActivityRootView getTermuxActivityRootView() {
        return mTermuxActivityRootView;
    }

    public View getTermuxActivityBottomSpaceView() {
        return mTermuxActivityBottomSpaceView;
    }

    public ExtraKeysView getExtraKeysView() {
        return mExtraKeysView;
    }

    public TermuxTerminalExtraKeys getTermuxTerminalExtraKeys() {
        return mTermuxTerminalExtraKeys;
    }

    public void setExtraKeysView(ExtraKeysView extraKeysView) {
        mExtraKeysView = extraKeysView;
    }

    public DrawerLayout getDrawer() {
        return (DrawerLayout) findViewById(R.id.drawer_layout);
    }


    public ViewPager getTerminalToolbarViewPager() {
        return (ViewPager) findViewById(R.id.terminal_toolbar_view_pager);
    }

    public float getTerminalToolbarDefaultHeight() {
        return mTerminalToolbarDefaultHeight;
    }

    public boolean isTerminalViewSelected() {
        return getTerminalToolbarViewPager().getCurrentItem() == 0;
    }

    public boolean isTerminalToolbarTextInputViewSelected() {
        return getTerminalToolbarViewPager().getCurrentItem() == 1;
    }


    public void termuxSessionListNotifyUpdated() {
        mTermuxSessionListViewController.notifyDataSetChanged();
    }

    public boolean isVisible() {
        return mIsVisible;
    }

    public boolean isOnResumeAfterOnCreate() {
        return mIsOnResumeAfterOnCreate;
    }

    public boolean isActivityRecreated() {
        return mIsActivityRecreated;
    }



    public TermuxService getTermuxService() {
        return mTermuxService;
    }

    public TerminalView getTerminalView() {
        return mTerminalView;
    }

    public TermuxTerminalViewClient getTermuxTerminalViewClient() {
        return mTermuxTerminalViewClient;
    }

    public TermuxTerminalSessionActivityClient getTermuxTerminalSessionClient() {
        return mTermuxTerminalSessionActivityClient;
    }

    @Nullable
    public TerminalSession getCurrentSession() {
        if (mTerminalView != null)
            return mTerminalView.getCurrentSession();
        else
            return null;
    }

    public TermuxAppSharedPreferences getPreferences() {
        return mPreferences;
    }

    public TermuxAppSharedProperties getProperties() {
        return mProperties;
    }




    public static void updateTermuxActivityStyling(Context context, boolean recreateActivity) {
        // Make sure that terminal styling is always applied.
        Intent stylingIntent = new Intent(TERMUX_ACTIVITY.ACTION_RELOAD_STYLE);
        stylingIntent.putExtra(TERMUX_ACTIVITY.EXTRA_RECREATE_ACTIVITY, recreateActivity);
        context.sendBroadcast(stylingIntent);
    }

    private void registerTermuxActivityBroadcastReceiver() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(TERMUX_ACTIVITY.ACTION_NOTIFY_APP_CRASH);
        intentFilter.addAction(TERMUX_ACTIVITY.ACTION_RELOAD_STYLE);
        intentFilter.addAction(TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS);

        registerReceiver(mTermuxActivityBroadcastReceiver, intentFilter);
    }

    private void unregisterTermuxActivityBroadcastReceiver() {
        unregisterReceiver(mTermuxActivityBroadcastReceiver);
    }

    private void fixTermuxActivityBroadcastReceiverIntent(Intent intent) {
        if (intent == null) return;

        String extraReloadStyle = intent.getStringExtra(TERMUX_ACTIVITY.EXTRA_RELOAD_STYLE);
        if ("storage".equals(extraReloadStyle)) {
            intent.removeExtra(TERMUX_ACTIVITY.EXTRA_RELOAD_STYLE);
            intent.setAction(TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS);
        }
    }

    class TermuxActivityBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;

            if (mIsVisible) {
                fixTermuxActivityBroadcastReceiverIntent(intent);

                switch (intent.getAction()) {
                    case TERMUX_ACTIVITY.ACTION_NOTIFY_APP_CRASH:
                        Logger.logDebug(LOG_TAG, "Received intent to notify app crash");
                        TermuxCrashUtils.notifyAppCrashFromCrashLogFile(context, LOG_TAG);
                        return;
                    case TERMUX_ACTIVITY.ACTION_RELOAD_STYLE:
                        Logger.logDebug(LOG_TAG, "Received intent to reload styling");
                        reloadActivityStyling(intent.getBooleanExtra(TERMUX_ACTIVITY.EXTRA_RECREATE_ACTIVITY, true));
                        return;
                    case TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS:
                        Logger.logDebug(LOG_TAG, "Received intent to request storage permissions");
                        requestStoragePermission(false);
                        return;
                    default:
                }
            }
        }
    }

    private void reloadActivityStyling(boolean recreateActivity) {
        if (mProperties != null) {
            reloadProperties();

            if (mTermuxTerminalExtraKeys != null) {
                mAppliedExtraKeysPreset = BabyPreferences.getExtraKeysPreset(this);
                mTermuxTerminalExtraKeys.setExtraKeys();
            }

            if (mExtraKeysView != null) {
                mExtraKeysView.setButtonTextAllCaps(mProperties.shouldExtraKeysTextBeAllCaps());
                mExtraKeysView.reload(mTermuxTerminalExtraKeys.getExtraKeysInfo(), mTerminalToolbarDefaultHeight);
            }

            // Update NightMode.APP_NIGHT_MODE
            TermuxThemeUtils.setAppNightMode(mProperties.getNightMode());
        }

        setMargins();
        setTerminalToolbarHeight();

        FileReceiverActivity.updateFileReceiverActivityComponentsState(this);

        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onReloadActivityStyling();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onReloadActivityStyling();

        // To change the activity and drawer theme, activity needs to be recreated.
        // It will destroy the activity, including all stored variables and views, and onCreate()
        // will be called again. Extra keys input text, terminal sessions and transcripts will be preserved.
        if (recreateActivity) {
            Logger.logDebug(LOG_TAG, "Recreating activity");
            TermuxActivity.this.recreate();
        }
    }



    public static void startTermuxActivity(@NonNull final Context context) {
        ActivityUtils.startActivity(context, newInstance(context));
    }

    public static Intent newInstance(@NonNull final Context context) {
        Intent intent = new Intent(context, TermuxActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

}
