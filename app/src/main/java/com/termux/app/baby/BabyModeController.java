package com.termux.app.baby;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.termux.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Main UI controller for Baby Mode Home ({@code view_baby_home.xml}).
 *
 * <p>Easy Mode's contract (redesign): beginners LEARN commands by using them,
 * and advanced users get one-tap shortcuts. The home grid is therefore a
 * pinned set of real commands — catalog actions and user-registered custom
 * commands — where every card shows its command, cards grow more
 * command-forward as their run count rises, and the "next to learn" slot
 * walks the curriculum. Sheets (Runner, Catalog, Quick Note, Tool
 * Installer) and the Pro handoff hang off this controller.
 */
public final class BabyModeController implements BabyCommandRunnerSheet.RunSessionHost {

    /**
     * Callbacks implemented by {@code TermuxActivity} to coordinate Baby/Pro mode switching.
     */
    public interface HostCallbacks {
        /**
         * Switches the UI to Pro Terminal mode, optionally sending a command into the active
         * terminal session.
         */
        void onSwitchToProMode(@Nullable String optionalCommandToRun);

        /**
         * Switches to Pro Terminal mode and TYPES the command without running
         * it — the user presses Enter themselves.
         */
        default void onTypeInProMode(@Nullable String commandToType) {
            onSwitchToProMode(commandToType);
        }

        /**
         * Opens Termux Settings.
         */
        void onOpenSettings();

        /**
         * The set of tracked run sessions changed (started, output, finished) —
         * the host refreshes the session bar.
         */
        default void onRunSessionsChanged() {
        }
    }

    /** Run count at which a card promotes its command to the primary line. */
    static final int MASTERY_RUN_COUNT = 3;

    private final AppCompatActivity activity;
    private final View babyHomeRoot;
    private final HostCallbacks hostCallbacks;
    private final BabyCommandExecutor executor;
    private final BabyRunSessionRegistry runSessionRegistry = new BabyRunSessionRegistry();

    private final BabyCommandExecutor.ProTerminalBridge proTerminalBridge =
        new BabyCommandExecutor.ProTerminalBridge() {
            @Override
            public void openInProTerminal(@Nullable String commandToRun) {
                hostCallbacks.onSwitchToProMode(commandToRun);
            }

            @Override
            public void openInProTerminalToType(@Nullable String commandToType) {
                hostCallbacks.onTypeInProMode(commandToType);
            }
        };

    private final View headerRow;
    private final ImageView statusIconView;
    private final TextView statusTextView;
    private final LinearLayout gridContainer;
    private final View homeEmptyView;
    private final View nextSection;
    private final View nextCardRoot;
    private final MaterialButton findActionButton;

    @Nullable
    private Dialog activeSheet;
    private boolean hasAnimatedEntrance = false;
    private boolean environmentReady;

    /** Registry listener, kept in a field so destroy() can unregister it. */
    private final BabyRunSessionRegistry.Listener registryListener;
    /** Snapshot of "id=state" pairs last forwarded to the host's session bar. */
    private String lastForwardedSessionStates = "";
    /** AlertDialogs currently shown by this controller, for teardown. */
    private final java.util.List<AlertDialog> activeDialogs = new java.util.ArrayList<>();

    public BabyModeController(
        @NonNull AppCompatActivity activity,
        @NonNull View babyHomeRoot,
        @NonNull HostCallbacks hostCallbacks
    ) {
        this.activity = activity;
        this.babyHomeRoot = babyHomeRoot;
        this.hostCallbacks = hostCallbacks;
        this.executor = new BabyCommandExecutor();

        this.headerRow = babyHomeRoot.findViewById(R.id.baby_home_header_row);
        this.statusIconView = babyHomeRoot.findViewById(R.id.baby_home_status_icon);
        this.statusTextView = babyHomeRoot.findViewById(R.id.baby_home_status_text);
        this.gridContainer = babyHomeRoot.findViewById(R.id.baby_action_grid);
        this.homeEmptyView = babyHomeRoot.findViewById(R.id.baby_home_empty);
        this.nextSection = babyHomeRoot.findViewById(R.id.baby_next_section);
        this.nextCardRoot = babyHomeRoot.findViewById(R.id.baby_next_card);
        this.findActionButton = babyHomeRoot.findViewById(R.id.baby_home_find_action_button);

        this.environmentReady = executor.isBootstrapReady();

        registryListener = () -> {
            handleRunSessionCompletions();
            // The registry also fires for every output line; the session
            // bar only cares about the session set and their states, so
            // forward only when one of those actually changed.
            StringBuilder snapshot = new StringBuilder();
            for (BabyRunSession session : runSessionRegistry.getSessions()) {
                snapshot.append(session.id).append('=').append(session.getState()).append(';');
            }
            if (!snapshot.toString().equals(lastForwardedSessionStates)) {
                lastForwardedSessionStates = snapshot.toString();
                hostCallbacks.onRunSessionsChanged();
            }
        };
        runSessionRegistry.addListener(registryListener);

        findActionButton.setOnClickListener(v -> openCommandHelper());
        MaterialButton addCustomButton = babyHomeRoot.findViewById(R.id.baby_add_custom_button);
        addCustomButton.setOnClickListener(v -> showAddCustomDialog());

        buildGrid();
        updateStatusBadge();
    }

    /**
     * Rebuilds the home grid (pins may have changed) and updates the
     * readiness banner.
     */
    public void refreshState() {
        if (executor.isBootstrapReady()) {
            this.environmentReady = true;
        } else if (this.environmentReady && !executor.isPrefixPresent()) {
            // The environment was wiped while the app is running (the user
            // removed $PREFIX in Pro mode, or a reset ran): stop claiming
            // "ready" so the home returns to the preparing state instead of
            // every card failing with bootstrap-missing errors.
            this.environmentReady = false;
        }
        buildGrid();
        updateStatusBadge();
    }

    /**
     * Allows {@code TermuxActivity} to update the preparing banner while
     * bootstrap is installing versus ready.
     */
    public void setEnvironmentReady(boolean ready) {
        this.environmentReady = ready;
        updateStatusBadge();
    }

    /**
     * Shows the Baby Mode Home view and runs a gentle staggered entrance animation once.
     */
    public void showBabyHome() {
        babyHomeRoot.setVisibility(View.VISIBLE);
        refreshState();
        if (!hasAnimatedEntrance) {
            hasAnimatedEntrance = true;
            animateCardEntranceIfEnabled();
        }
    }

    /**
     * Hides the Baby Mode Home view (when switching to Pro Terminal mode).
     */
    public void hideBabyHome() {
        dismissActiveSheet();
        babyHomeRoot.setVisibility(View.GONE);
    }

    public boolean isBabyHomeVisible() {
        return babyHomeRoot.getVisibility() == View.VISIBLE;
    }

    /**
     * Opens the command catalog (formerly "Find an action"): every catalog
     * item with its real command, pinnable to the home grid, plus the note
     * utility.
     */
    public void openCommandHelper() {
        dismissActiveSheet();
        BabyCommandHelperSheet sheet = new BabyCommandHelperSheet(
            activity,
            new BabyCommandHelperSheet.OnRecipeSelectedListener() {
                @Override
                public void onRecipeRun(@NonNull BabyAction action, @Nullable String resolvedCommand) {
                    if (action.actionType == BabyAction.ActionType.TOOL_INSTALLER) {
                        openToolInstaller();
                    } else if (action.actionType == BabyAction.ActionType.QUICK_NOTE) {
                        openQuickNote();
                    } else if (resolvedCommand != null) {
                        runCommandInSheet(
                            activity.getString(action.titleResId),
                            action.iconResId,
                            resolvedCommand,
                            action.outputFormat,
                            action.timeoutSeconds,
                            activity.getString(action.titleResId),
                            action.id
                        );
                    }
                }

                @Override
                public void onNoteRequested() {
                    openQuickNote();
                }

                @Override
                public void onPinsChanged() {
                    buildGrid();
                }
            }
        );
        activeSheet = sheet;
        sheet.show();
    }

    /**
     * Opens the guided 1-tap Tool Installer sheet.
     */
    public void openToolInstaller() {
        dismissActiveSheet();
        BabyToolInstallerSheet sheet = new BabyToolInstallerSheet(
            activity,
            executor,
            new BabyToolInstallerSheet.OnToolActionListener() {
                @Override
                public void onInstallTool(@NonNull BabyActionCatalog.ToolSpec tool) {
                    String toolName = activity.getString(tool.nameResId);
                    runCommandInSheet(
                        activity.getString(R.string.baby_installer_install) + " " + toolName,
                        tool.iconResId,
                        tool.buildInstallCommand(),
                        BabyAction.OutputFormat.INSTALL,
                        BabyCommandExecutor.INSTALL_TIMEOUT_SECONDS,
                        toolName,
                        "install:" + tool.packageName,
                        true
                    );
                }

                @Override
                public void onTryTool(@NonNull BabyActionCatalog.ToolSpec tool) {
                    if (tool.tryCommand == null) return;
                    String toolName = activity.getString(tool.nameResId);
                    runCommandInSheet(
                        toolName,
                        tool.iconResId,
                        tool.tryCommand,
                        tool.tryOutputFormat,
                        BabyCommandExecutor.DEFAULT_TIMEOUT_SECONDS,
                        toolName,
                        "try:" + tool.packageName,
                        true
                    );
                }

                @Override
                public void onInstallAgent(@NonNull BabyActionCatalog.AgentSpec agent) {
                    String agentName = activity.getString(agent.nameResId);
                    runCommandInSheet(
                        activity.getString(R.string.baby_installer_install) + " " + agentName,
                        agent.iconResId,
                        agent.installCommand,
                        BabyAction.OutputFormat.INSTALL,
                        BabyCommandExecutor.AGENT_INSTALL_TIMEOUT_SECONDS,
                        agentName,
                        "agent:" + agent.id,
                        true
                    );
                }

                @Override
                public void onLaunchAgent(@NonNull BabyActionCatalog.AgentSpec agent) {
                    // Agents are interactive TUIs: run them in the Pro Terminal session.
                    hostCallbacks.onSwitchToProMode(agent.launchCommand);
                }
            }
        );
        activeSheet = sheet;
        sheet.show();
    }

    /**
     * Opens the Quick Note sheet.
     */
    public void openQuickNote() {
        dismissActiveSheet();
        BabyQuickNoteSheet sheet = new BabyQuickNoteSheet(activity, this::refreshState);
        activeSheet = sheet;
        sheet.show();
    }

    /**
     * Executes any {@link BabyAction} from the home grid or external trigger.
     */
    public void runAction(@NonNull BabyAction action) {
        switch (action.actionType) {
            case TOOL_INSTALLER:
                openToolInstaller();
                break;
            case QUICK_NOTE:
                openQuickNote();
                break;
            case COMMAND_WITH_INPUT:
                showInputDialog(action);
                break;
            case TRY_PYTHON:
                handleTryPython(action);
                break;
            case DIRECT_COMMAND:
            default:
                if (action.commandTemplate != null) {
                    runCommandInSheet(
                        activity.getString(action.titleResId),
                        action.iconResId,
                        action.commandTemplate,
                        action.outputFormat,
                        action.timeoutSeconds,
                        activity.getString(action.titleResId),
                        action.id
                    );
                }
                break;
        }
    }

    /** Runs a user-registered custom shortcut command. */
    public void runCustomCommand(@NonNull BabyCustomCommand custom) {
        // A custom command is arbitrary text the user pasted or typed:
        // the very first run asks once, showing the command itself, so
        // nothing unreviewed ever auto-runs from a card tap. A dangerous
        // command skips this dialog — the danger confirmation in
        // runCommandInSheet shows the command and asks every time instead.
        boolean neverRan = BabyPreferences.getRunCount(activity, custom.id) == 0
            && BabyPreferences.getLastResultSummary(activity, custom.id) == null
            && dangerMatch(custom.command) == null;
        if (neverRan) {
            AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(custom.label)
                .setMessage(activity.getString(R.string.baby_custom_first_run, custom.command))
                .setNegativeButton(R.string.baby_helper_input_cancel, null)
                .setPositiveButton(R.string.baby_helper_run, (d, which) ->
                    runCommandInSheet(
                        custom.label,
                        R.drawable.ic_baby_terminal,
                        custom.command,
                        BabyAction.OutputFormat.GENERIC,
                        BabyCommandExecutor.DEFAULT_TIMEOUT_SECONDS,
                        custom.label,
                        custom.id
                    ))
                .create();
            trackDialog(dialog);
            dialog.show();
            return;
        }
        runCommandInSheet(
            custom.label,
            R.drawable.ic_baby_terminal,
            custom.command,
            BabyAction.OutputFormat.GENERIC,
            BabyCommandExecutor.DEFAULT_TIMEOUT_SECONDS,
            custom.label,
            custom.id
        );
    }

    /**
     * Releases sheets and background executor cleanly. Call from {@code TermuxActivity.onDestroy()}.
     */
    public void destroy() {
        runSessionRegistry.removeListener(registryListener);
        dismissActiveDialogs();
        dismissActiveSheet();
        executor.shutdown();
    }

    /** Registers a dialog for teardown in destroy(); unregisters on dismiss. */
    private void trackDialog(@NonNull AlertDialog dialog) {
        activeDialogs.add(dialog);
        dialog.setOnDismissListener(d -> activeDialogs.remove(dialog));
    }

    private void dismissActiveDialogs() {
        for (AlertDialog dialog : new java.util.ArrayList<>(activeDialogs)) {
            if (dialog.isShowing()) dialog.dismiss();
        }
        activeDialogs.clear();
    }

    private void handleTryPython(@NonNull BabyAction action) {
        if (executor.isBinaryInstalled("python3")) {
            runCommandInSheet(
                activity.getString(action.titleResId),
                action.iconResId,
                BabyActionCatalog.PYTHON_HELLO_COMMAND,
                BabyAction.OutputFormat.PYTHON,
                action.timeoutSeconds,
                activity.getString(R.string.baby_tool_python_name),
                action.id
            );
        } else {
            dismissActiveSheet();
            BabyCommandRunnerSheet sheet = createRunnerSheet();
            activeSheet = sheet;
            sheet.showInstallPrompt(
                activity.getString(action.titleResId),
                action.iconResId,
                activity.getString(R.string.baby_summary_python_missing),
                "python",
                activity.getString(R.string.baby_tool_python_name)
            );
        }
    }

    /**
     * Input prompt for COMMAND_WITH_INPUT actions (folder name, search word,
     * an expression): shows the REAL resolved command live as the user types
     * — no fake shortened previews.
     */
    private void showInputDialog(@NonNull BabyAction action) {
        if (action.commandTemplate == null) return;
        Context ctx = activity;
        int pad = Math.round(20 * ctx.getResources().getDisplayMetrics().density);
        int topPad = Math.round(12 * ctx.getResources().getDisplayMetrics().density);

        LinearLayout container = new LinearLayout(ctx);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(pad, topPad, pad, 0);

        EditText input = new EditText(ctx);
        input.setSingleLine(true);
        if (action.inputHintResId != 0) {
            input.setHint(action.inputHintResId);
        }
        input.setBackgroundResource(R.drawable.baby_input_bg);
        int innerPad = Math.round(12 * ctx.getResources().getDisplayMetrics().density);
        input.setPadding(innerPad, innerPad, innerPad, innerPad);
        container.addView(input);

        TextView previewView = new TextView(ctx);
        previewView.setPadding(0, topPad, 0, 0);
        previewView.setTextAppearance(ctx, R.style.TextAppearance_Baby_Code);
        previewView.setText(ctx.getString(
            R.string.baby_helper_command_preview,
            BabyActionCatalog.getDisplayCommandPreview(action)
        ));
        container.addView(previewView);

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String value = s != null ? s.toString().trim() : "";
                String preview = value.isEmpty()
                    ? BabyActionCatalog.getDisplayCommandPreview(action)
                    : BabyActionCatalog.truncateForDisplay(
                        BabyActionCatalog.buildDisplayCommand(action.commandTemplate, value));
                previewView.setText(ctx.getString(R.string.baby_helper_command_preview, preview));
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        AlertDialog dialog = new AlertDialog.Builder(ctx)
            .setTitle(action.inputLabelResId != 0 ? action.inputLabelResId : action.titleResId)
            .setView(container)
            .setNegativeButton(R.string.baby_helper_input_cancel, null)
            .setPositiveButton(R.string.baby_helper_run, null)
            .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            .setOnClickListener(v -> {
                String value = input.getText() != null ? input.getText().toString().trim() : "";
                if (value.isEmpty()) {
                    input.setError(ctx.getString(action.inputHintResId != 0
                        ? action.inputHintResId : R.string.baby_note_empty_error));
                    return;
                }
                String resolved = BabyActionCatalog.buildSafeCommand(action.commandTemplate, value);
                dialog.dismiss();
                runCommandInSheet(
                    activity.getString(action.titleResId),
                    action.iconResId,
                    resolved,
                    action.outputFormat,
                    action.timeoutSeconds,
                    activity.getString(action.titleResId),
                    action.id
                );
            }));

        trackDialog(dialog);
        dialog.show();
    }

    private void runCommandInSheet(
        @NonNull String title,
        int iconResId,
        @NonNull String command,
        @NonNull BabyAction.OutputFormat outputFormat,
        int timeoutSeconds,
        @Nullable String displayName,
        @Nullable String sourceId
    ) {
        runCommandInSheet(title, iconResId, command, outputFormat, timeoutSeconds,
            displayName, sourceId, false);
    }

    /**
     * @param skipDangerGate for installer-sheet runs: those commands are
     * fixed catalog entries that already passed the installer's own
     * confirmation dialog, so the danger gate must not ask a second time.
     */
    private void runCommandInSheet(
        @NonNull String title,
        int iconResId,
        @NonNull String command,
        @NonNull BabyAction.OutputFormat outputFormat,
        int timeoutSeconds,
        @Nullable String displayName,
        @Nullable String sourceId,
        boolean skipDangerGate
    ) {
        if (!skipDangerGate) {
            String danger = dangerMatch(command);
            if (danger != null) {
                showDangerConfirmDialog(danger, command, () ->
                    openRunnerSheet(title, iconResId, command, outputFormat,
                        timeoutSeconds, displayName, sourceId));
                return;
            }
        }
        openRunnerSheet(title, iconResId, command, outputFormat, timeoutSeconds,
            displayName, sourceId);
    }

    private void openRunnerSheet(
        @NonNull String title,
        int iconResId,
        @NonNull String command,
        @NonNull BabyAction.OutputFormat outputFormat,
        int timeoutSeconds,
        @Nullable String displayName,
        @Nullable String sourceId
    ) {
        dismissActiveSheet();
        BabyCommandRunnerSheet sheet = createRunnerSheet();
        activeSheet = sheet;
        sheet.startExecution(title, iconResId, command, outputFormat, timeoutSeconds,
            displayName, sourceId);
    }

    /**
     * Returns the matched danger pattern when the command is dangerous AND
     * the confirmation setting is on; null otherwise (safe, or the user
     * turned confirmations off in Settings).
     */
    @Nullable
    private String dangerMatch(@NonNull String command) {
        if (!BabyPreferences.isDangerConfirmEnabled(activity)) return null;
        return BabyActionCatalog.assessDanger(
            command, BabyPreferences.getDangerPatterns(activity));
    }

    /** Asks "this command is dangerous — run it anyway?" before a run. */
    private void showDangerConfirmDialog(
        @NonNull String matchedPattern,
        @NonNull String command,
        @NonNull Runnable onConfirmed
    ) {
        AlertDialog dialog = new AlertDialog.Builder(activity)
            .setTitle(R.string.baby_danger_title)
            .setMessage(activity.getString(
                R.string.baby_danger_message, command, matchedPattern))
            .setNegativeButton(R.string.baby_helper_input_cancel, null)
            .setPositiveButton(R.string.baby_danger_run, (d, which) -> onConfirmed.run())
            .create();
        trackDialog(dialog);
        dialog.show();
    }

    @NonNull
    private BabyCommandRunnerSheet createRunnerSheet() {
        BabyCommandRunnerSheet sheet = new BabyCommandRunnerSheet(activity, this, proTerminalBridge);
        sheet.setDangerGate((command, matchedPattern, onConfirmed) ->
            showDangerConfirmDialog(matchedPattern, command, onConfirmed));
        return sheet;
    }

    /**
     * Creates, registers and starts a run session. Called by the runner sheet;
     * the session keeps running even if the sheet is closed afterwards.
     */
    @NonNull
    @Override
    public BabyRunSession startRunSession(
        @NonNull String title,
        int iconResId,
        @NonNull String command,
        @NonNull BabyAction.OutputFormat outputFormat,
        int timeoutSeconds,
        @Nullable String displayName,
        @Nullable String sourceId
    ) {
        BabyRunSession session = runSessionRegistry.create(
            title, iconResId, command, outputFormat, timeoutSeconds, displayName, sourceId
        );
        BabyAction source = sourceId != null ? BabyActionCatalog.findById(sourceId) : null;
        if (source != null) {
            session.displayCommand =
                BabyActionCatalog.getDisplayCommandForResolved(source, command);
        }
        session.start(executor);
        return session;
    }

    @NonNull
    public BabyRunSessionRegistry getRunSessionRegistry() {
        return runSessionRegistry;
    }

    /**
     * Re-opens the runner sheet for an existing session (session bar tap).
     * A finished session counts as seen once presented, so its chip goes away.
     */
    public void openRunSession(@NonNull BabyRunSession session) {
        if (!session.isRunning()) {
            session.markFinishedSeen();
            runSessionRegistry.pruneSeenFinished();
        }
        dismissActiveSheet();
        BabyCommandRunnerSheet sheet = createRunnerSheet();
        activeSheet = sheet;
        sheet.bindSession(session);
    }

    /**
     * Closes a run session from the session bar: a running session is cancelled
     * first, then the session leaves the registry (and the bar) immediately.
     */
    public void dismissRunSession(@NonNull BabyRunSession session) {
        if (session.isRunning()) {
            session.cancel();
        }
        runSessionRegistry.remove(session);
    }

    /**
     * Records learning progress for finished runs. Mastery counts SUCCESS
     * only — a failed run still leaves its one-line result on the card (so
     * the user sees what happened), but it must not advance the "learned"
     * fade, and a cancelled run leaves no trace at all.
     */
    private void handleRunSessionCompletions() {
        boolean gridDirty = false;
        for (BabyRunSession session : runSessionRegistry.getSessions()) {
            if (session.isRunning() || session.isCompletionHandled()) continue;
            session.markCompletionHandled();
            if (recordCompletion(session)) {
                gridDirty = true;
            }
        }
        if (gridDirty) {
            buildGrid();
        }
    }

    /**
     * Records one finished session into the learning state. Returns true
     * when the grid should rebuild. Package-private for tests.
     */
    boolean recordCompletion(@NonNull BabyRunSession session) {
        if (session.getState() == BabyRunSession.State.CANCELLED) return false;
        boolean success = session.getState() == BabyRunSession.State.SUCCESS;
        if (success) {
            BabyPreferences.setFirstActionCompleted(activity, true);
        }
        if (session.sourceId == null || session.getResult() == null) return false;
        if (success) {
            BabyPreferences.incrementRunCount(activity, session.sourceId);
        }
        BabyPreferences.setLastResult(
            activity, session.sourceId, success, summarizeSession(session));
        return true;
    }

    @Nullable
    private String summarizeSession(@NonNull BabyRunSession session) {
        BabyCommandExecutor.ExecutionResult result = session.getResult();
        if (result == null) return null;
        String text;
        if (result.isSuccess()) {
            BabyOutputFormatter.ParsedSummary summary = BabyOutputFormatter.parse(
                session.outputFormat, result.stdout, result.stderr, result.exitCode);
            text = BabyOutputFormatter.formatForDisplay(activity, summary, session.displayName);
        } else {
            text = BabyErrorTranslator.getFriendlyMessage(
                activity, BabyErrorTranslator.classify(result));
        }
        if (text == null) return null;
        int newline = text.indexOf('\n');
        if (newline >= 0) text = text.substring(0, newline);
        text = text.trim();
        if (text.length() > 42) text = text.substring(0, 42).trim() + "…";
        return text.isEmpty() ? null : text;
    }

    // --- Home grid ---

    /** One resolved grid entry: either a catalog action or a custom command. */
    private static final class GridItem {
        @NonNull final String id;
        @Nullable final BabyAction action;
        @Nullable final BabyCustomCommand custom;

        GridItem(@NonNull BabyAction action) {
            this.id = action.id;
            this.action = action;
            this.custom = null;
        }

        GridItem(@NonNull BabyCustomCommand custom) {
            this.id = custom.id;
            this.action = null;
            this.custom = custom;
        }
    }

    @NonNull
    private List<GridItem> resolvePinnedItems() {
        List<GridItem> items = new ArrayList<>();
        for (String id : BabyPreferences.getPinnedIds(activity)) {
            BabyAction action = BabyActionCatalog.findById(id);
            if (action != null) {
                items.add(new GridItem(action));
                continue;
            }
            BabyCustomCommand custom = BabyPreferences.findCustomCommand(activity, id);
            if (custom != null) {
                items.add(new GridItem(custom));
            }
        }
        return items;
    }

    private void buildGrid() {
        gridContainer.removeAllViews();
        List<GridItem> items = resolvePinnedItems();
        if (homeEmptyView != null) {
            homeEmptyView.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        }
        float density = activity.getResources().getDisplayMetrics().density;
        LayoutInflater inflater = LayoutInflater.from(activity);

        for (int rowStart = 0; rowStart < items.size(); rowStart += 2) {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setBaselineAligned(false);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (rowStart > 0) rowParams.topMargin = Math.round(12 * density);
            row.setLayoutParams(rowParams);

            for (int col = 0; col < 2; col++) {
                int index = rowStart + col;
                if (index >= items.size()) {
                    // Keep the surviving card at half width on an odd row.
                    View spacer = new View(activity);
                    spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
                    row.addView(spacer);
                    break;
                }
                GridItem item = items.get(index);
                View cardView = inflater.inflate(R.layout.item_baby_action_card, row, false);
                LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                if (col == 0) {
                    cardParams.setMarginEnd(Math.round(6 * density));
                } else {
                    cardParams.setMarginStart(Math.round(6 * density));
                }
                cardView.setLayoutParams(cardParams);
                bindCard(cardView, item, index, items.size());
                row.addView(cardView);
            }
            gridContainer.addView(row);
        }

        bindNextCard(items);
    }

    /**
     * Binds one card. The emphasis is the lesson: at first the Japanese
     * title leads and the command supports it; after
     * {@link #MASTERY_RUN_COUNT} runs the command takes the lead, the
     * Japanese shrinks to a caption, and a learned badge appears.
     */
    private void bindCard(@NonNull View cardRoot, @NonNull GridItem item, int index, int total) {
        if (!(cardRoot instanceof MaterialCardView)) return;
        MaterialCardView card = (MaterialCardView) cardRoot;

        ImageView iconView = card.findViewById(R.id.baby_card_icon);
        TextView titleView = card.findViewById(R.id.baby_card_title);
        TextView commandView = card.findViewById(R.id.baby_card_command);
        TextView subtitleView = card.findViewById(R.id.baby_card_subtitle);
        TextView badgeView = card.findViewById(R.id.baby_card_recommended_badge);
        View lastRow = card.findViewById(R.id.baby_card_last_row);
        TextView lastResultView = card.findViewById(R.id.baby_card_last_result);
        ImageButton rerunButton = card.findViewById(R.id.baby_card_rerun);

        String title;
        String subtitle;
        String command;
        int iconResId;
        boolean directRunnable;
        if (item.action != null) {
            title = activity.getString(item.action.titleResId);
            subtitle = activity.getString(item.action.subtitleResId);
            if (item.action.commandTemplate != null) {
                command = BabyActionCatalog.getDisplayCommandPreview(item.action);
            } else if (item.action.actionType == BabyAction.ActionType.TOOL_INSTALLER) {
                // The installer has no single command, but the card must
                // not be the one blank card on the grid: show what it does
                // in command form.
                command = activity.getString(R.string.baby_card_install_command);
            } else {
                command = null;
            }
            iconResId = item.action.iconResId;
            directRunnable = item.action.actionType == BabyAction.ActionType.DIRECT_COMMAND;
        } else {
            title = item.custom.label;
            subtitle = "";
            command = item.custom.command;
            iconResId = R.drawable.ic_baby_terminal;
            directRunnable = true;
        }

        int runCount = BabyPreferences.getRunCount(activity, item.id);
        boolean mastered = command != null && runCount >= MASTERY_RUN_COUNT;

        iconView.setImageResource(iconResId);
        titleView.setText(title);
        subtitleView.setText(subtitle);
        subtitleView.setVisibility(subtitle.isEmpty() ? View.GONE : View.VISIBLE);

        if (command != null) {
            commandView.setVisibility(View.VISIBLE);
            commandView.setText(command);
            if (mastered) {
                commandView.setTextSize(15);
                commandView.setTextColor(
                    ContextCompat.getColor(activity, R.color.baby_text_primary));
                titleView.setTextSize(13);
            } else if (runCount >= 1) {
                commandView.setTextSize(13);
                commandView.setTextColor(
                    ContextCompat.getColor(activity, R.color.baby_primary));
                titleView.setTextSize(16);
            } else {
                commandView.setTextSize(12);
                commandView.setTextColor(
                    ContextCompat.getColor(activity, R.color.baby_text_secondary));
                titleView.setTextSize(16);
            }
        } else {
            commandView.setVisibility(View.GONE);
            titleView.setTextSize(16);
        }

        boolean isNextToLearn = item.action != null
            && item.id.equals(findNextToLearnId());
        if (mastered) {
            badgeView.setText(R.string.baby_card_mastered);
            badgeView.setVisibility(View.VISIBLE);
        } else if (isNextToLearn) {
            badgeView.setText(R.string.baby_home_next_title);
            badgeView.setVisibility(View.VISIBLE);
        } else {
            badgeView.setVisibility(View.GONE);
        }

        boolean running = isSourceRunning(item.id);
        String lastSummary = BabyPreferences.getLastResultSummary(activity, item.id);
        if (running) {
            lastRow.setVisibility(View.VISIBLE);
            lastResultView.setText(R.string.baby_card_running);
            rerunButton.setVisibility(View.GONE);
        } else if (lastSummary != null) {
            lastRow.setVisibility(View.VISIBLE);
            boolean ok = BabyPreferences.getLastResultOk(activity, item.id);
            lastResultView.setText((ok ? "✓ " : "✗ ") + lastSummary);
            rerunButton.setVisibility(directRunnable ? View.VISIBLE : View.GONE);
            // Re-run is the shortcut gesture: no sheet, the result comes
            // back to this card (and the session bar while it runs).
            rerunButton.setOnClickListener(v -> runGridItemDirect(item));
        } else {
            lastRow.setVisibility(View.GONE);
        }

        card.setContentDescription(title + (command != null ? ". " + command : "")
            + (subtitle.isEmpty() ? "" : ". " + subtitle));
        card.setOnClickListener(v -> {
            animateCardPressIfEnabled(card);
            // A mastered card IS the shortcut: tapping runs it in place.
            // Before mastery, the tap opens the runner sheet, where the
            // meanings and the full output teach the command.
            if (mastered && directRunnable) {
                runGridItemDirect(item);
            } else {
                runGridItem(item);
            }
        });
        card.setOnLongClickListener(v -> {
            showCardMenu(item, index, total);
            return true;
        });
    }

    private void runGridItem(@NonNull GridItem item) {
        if (item.action != null) {
            runAction(item.action);
        } else if (item.custom != null) {
            runCustomCommand(item.custom);
        }
    }

    private boolean isSourceRunning(@NonNull String sourceId) {
        for (BabyRunSession session : runSessionRegistry.getSessions()) {
            if (session.isRunning() && sourceId.equals(session.sourceId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The shortcut path: starts the run WITHOUT opening the runner sheet.
     * The session bar shows it while it runs; the result lands back on the
     * card. Used by the re-run button and by taps on mastered cards.
     */
    private void runGridItemDirect(@NonNull GridItem item) {
        // The shortcut path skips the runner sheet, so the danger gate
        // lives here: a dangerous command asks first, even on this path.
        String command = item.action != null ? item.action.commandTemplate
            : item.custom != null ? item.custom.command : null;
        if (command == null) return;
        String danger = dangerMatch(command);
        if (danger != null) {
            showDangerConfirmDialog(danger, command, () -> runGridItemDirectUngated(item));
            return;
        }
        runGridItemDirectUngated(item);
    }

    private void runGridItemDirectUngated(@NonNull GridItem item) {
        if (item.action != null) {
            BabyAction action = item.action;
            if (action.commandTemplate == null) return;
            startRunSession(
                activity.getString(action.titleResId),
                action.iconResId,
                action.commandTemplate,
                action.outputFormat,
                action.timeoutSeconds,
                activity.getString(action.titleResId),
                action.id
            );
        } else if (item.custom != null) {
            startRunSession(
                item.custom.label,
                R.drawable.ic_baby_terminal,
                item.custom.command,
                BabyAction.OutputFormat.GENERIC,
                BabyCommandExecutor.DEFAULT_TIMEOUT_SECONDS,
                item.custom.label,
                item.custom.id
            );
        }
        buildGrid();
    }

    /**
     * The curriculum's next unmastered item: the onboarding goal's lead
     * item goes first while it is unlearned (the goal keeps shaping the
     * path beyond the first run), then plain curriculum order.
     */
    @Nullable
    private String findNextToLearnId() {
        String lead = BabyPreferences.getGoalLeadActionId(activity);
        if (lead != null
            && BabyPreferences.getRunCount(activity, lead) < MASTERY_RUN_COUNT) {
            return lead;
        }
        for (String id : BabyActionCatalog.getCurriculumIds()) {
            if (BabyPreferences.getRunCount(activity, id) < MASTERY_RUN_COUNT) {
                return id;
            }
        }
        return null;
    }

    private void bindNextCard(@NonNull List<GridItem> pinnedItems) {
        String nextId = findNextToLearnId();
        BabyAction next = nextId != null ? BabyActionCatalog.findById(nextId) : null;
        if (next == null) {
            nextSection.setVisibility(View.GONE);
            return;
        }
        // If the next item is already on the grid, its badge says so instead.
        for (GridItem item : pinnedItems) {
            if (item.id.equals(nextId)) {
                nextSection.setVisibility(View.GONE);
                return;
            }
        }
        nextSection.setVisibility(View.VISIBLE);
        bindCard(nextCardRoot, new GridItem(next), -1, -1);
        // The section heading already says "next to learn"; the card's own
        // badge would say the same words twice on one screen.
        View badge = nextCardRoot.findViewById(R.id.baby_card_recommended_badge);
        if (badge != null) badge.setVisibility(View.GONE);
    }

    private void showCardMenu(@NonNull GridItem item, int index, int total) {
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();

        String typeableCommand = null;
        if (item.action != null
            && item.action.actionType == BabyAction.ActionType.DIRECT_COMMAND) {
            typeableCommand = item.action.commandTemplate;
        } else if (item.custom != null) {
            typeableCommand = item.custom.command;
        }
        if (typeableCommand != null) {
            final String cmd = typeableCommand;
            labels.add(activity.getString(R.string.baby_card_menu_type));
            actions.add(() -> hostCallbacks.onTypeInProMode(cmd));
        }
        // Meanings are learnable BEFORE running, not only inside the
        // runner sheet after a run has started.
        java.util.List<BabyAction.TokenNote> notes = item.action != null
            ? BabyActionCatalog.getTokenNotes(item.action.id)
            : BabyActionCatalog.getTokenNotesForCommand(
                item.custom != null ? item.custom.command : null);
        if (!notes.isEmpty()) {
            labels.add(activity.getString(R.string.baby_card_menu_meanings));
            actions.add(() -> {
                StringBuilder message = new StringBuilder();
                for (BabyAction.TokenNote note : notes) {
                    if (message.length() > 0) message.append('\n');
                    message.append(note.token).append(" — ")
                        .append(activity.getString(note.meaningResId));
                }
                AlertDialog explainDialog = new AlertDialog.Builder(activity)
                    .setTitle(item.action != null
                        ? activity.getString(item.action.titleResId) : item.custom.label)
                    .setMessage(message.toString())
                    .setPositiveButton(R.string.baby_runner_close, null)
                    .create();
                trackDialog(explainDialog);
                explainDialog.show();
            });
        }
        if (index > 0) {
            labels.add(activity.getString(R.string.baby_card_menu_up));
            actions.add(() -> movePinned(item.id, -1));
        }
        if (index >= 0 && index < total - 1) {
            labels.add(activity.getString(R.string.baby_card_menu_down));
            actions.add(() -> movePinned(item.id, 1));
        }
        if (index >= 0) {
            labels.add(activity.getString(R.string.baby_card_menu_unpin));
            actions.add(() -> {
                List<String> ids = BabyPreferences.getPinnedIds(activity);
                ids.remove(item.id);
                BabyPreferences.setPinnedIds(activity, ids);
                buildGrid();
            });
        }
        if (item.custom != null) {
            labels.add(activity.getString(R.string.baby_card_menu_delete));
            actions.add(() -> {
                List<BabyCustomCommand> customs = BabyPreferences.getCustomCommands(activity);
                customs.removeIf(c -> c.id.equals(item.custom.id));
                BabyPreferences.setCustomCommands(activity, customs);
                List<String> ids = BabyPreferences.getPinnedIds(activity);
                ids.remove(item.id);
                BabyPreferences.setPinnedIds(activity, ids);
                buildGrid();
            });
        }
        if (labels.isEmpty()) return;

        String title = item.action != null
            ? activity.getString(item.action.titleResId) : item.custom.label;
        AlertDialog menuDialog = new AlertDialog.Builder(activity)
            .setTitle(title)
            .setItems(labels.toArray(new CharSequence[0]),
                (dialog, which) -> actions.get(which).run())
            .create();
        trackDialog(menuDialog);
        menuDialog.show();
    }

    private void movePinned(@NonNull String id, int delta) {
        List<String> ids = BabyPreferences.getPinnedIds(activity);
        int from = ids.indexOf(id);
        int to = from + delta;
        if (from < 0 || to < 0 || to >= ids.size()) return;
        ids.remove(from);
        ids.add(to, id);
        BabyPreferences.setPinnedIds(activity, ids);
        buildGrid();
    }

    private void showAddCustomDialog() {
        Context ctx = activity;
        int pad = Math.round(20 * ctx.getResources().getDisplayMetrics().density);
        int topPad = Math.round(12 * ctx.getResources().getDisplayMetrics().density);

        LinearLayout container = new LinearLayout(ctx);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(pad, topPad, pad, 0);

        EditText labelInput = new EditText(ctx);
        labelInput.setSingleLine(true);
        labelInput.setHint(R.string.baby_custom_label_hint);
        labelInput.setBackgroundResource(R.drawable.baby_input_bg);
        int innerPad = Math.round(12 * ctx.getResources().getDisplayMetrics().density);
        labelInput.setPadding(innerPad, innerPad, innerPad, innerPad);
        container.addView(labelInput);

        EditText commandInput = new EditText(ctx);
        commandInput.setSingleLine(true);
        commandInput.setHint(R.string.baby_custom_command_hint);
        commandInput.setBackgroundResource(R.drawable.baby_input_bg);
        commandInput.setPadding(innerPad, innerPad, innerPad, innerPad);
        LinearLayout.LayoutParams commandParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        commandParams.topMargin = topPad;
        commandInput.setLayoutParams(commandParams);
        container.addView(commandInput);

        AlertDialog dialog = new AlertDialog.Builder(ctx)
            .setTitle(R.string.baby_custom_title)
            .setView(container)
            .setNegativeButton(R.string.baby_helper_input_cancel, null)
            .setPositiveButton(R.string.baby_custom_add, null)
            .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            .setOnClickListener(v -> {
                String label = labelInput.getText() != null
                    ? labelInput.getText().toString().trim() : "";
                String command = commandInput.getText() != null
                    ? commandInput.getText().toString().trim() : "";
                if (label.isEmpty() || command.isEmpty()) {
                    commandInput.setError(ctx.getString(R.string.baby_custom_empty));
                    return;
                }
                // Dangerous commands may be registered: they are not
                // blocked here, they ask for confirmation at run time
                // (the danger gate), which is the moment that matters.
                String id = "custom_" + System.currentTimeMillis();
                List<BabyCustomCommand> customs = BabyPreferences.getCustomCommands(activity);
                customs.add(new BabyCustomCommand(id, label, command));
                BabyPreferences.setCustomCommands(activity, customs);
                List<String> ids = BabyPreferences.getPinnedIds(activity);
                ids.add(id);
                BabyPreferences.setPinnedIds(activity, ids);
                dialog.dismiss();
                buildGrid();
            }));

        trackDialog(dialog);
        dialog.show();
    }

    private void updateStatusBadge() {
        // The header row is a preparing banner only: once the environment
        // is ready it leaves the home entirely (no permanent greeting noise
        // on a shortcut surface).
        headerRow.setVisibility(environmentReady ? View.GONE : View.VISIBLE);
        if (!environmentReady) {
            statusTextView.setText(R.string.baby_home_status_preparing);
            statusIconView.setImageResource(R.drawable.ic_baby_sparkle);
            ImageViewCompat.setImageTintList(
                statusIconView,
                ColorStateList.valueOf(
                    ContextCompat.getColor(activity, R.color.baby_on_primary_container))
            );
        }
    }

    private void animateCardEntranceIfEnabled() {
        if (!BabyPreferences.areAnimationsEnabled(activity)) {
            return;
        }
        float translateY = 12f * activity.getResources().getDisplayMetrics().density;
        DecelerateInterpolator interpolator = new DecelerateInterpolator();
        int delayIndex = 0;
        for (int i = 0; i < gridContainer.getChildCount(); i++) {
            View row = gridContainer.getChildAt(i);
            if (!(row instanceof LinearLayout)) continue;
            LinearLayout rowLayout = (LinearLayout) row;
            for (int j = 0; j < rowLayout.getChildCount(); j++) {
                View card = rowLayout.getChildAt(j);
                if (!(card instanceof MaterialCardView)) continue;
                card.setAlpha(0f);
                card.setTranslationY(translateY);
                card.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(delayIndex * 60L)
                    .setDuration(220L)
                    .setInterpolator(interpolator)
                    .start();
                delayIndex++;
            }
        }
    }

    private void animateCardPressIfEnabled(@NonNull View card) {
        if (!BabyPreferences.areAnimationsEnabled(activity)) {
            return;
        }
        card.animate()
            .scaleX(0.97f)
            .scaleY(0.97f)
            .setDuration(90L)
            .withEndAction(() -> card.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(110L)
                .start())
            .start();
    }

    private void dismissActiveSheet() {
        if (activeSheet != null && activeSheet.isShowing()) {
            activeSheet.dismiss();
        }
        activeSheet = null;
    }
}
