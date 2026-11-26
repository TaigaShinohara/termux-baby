package com.termux.app.baby;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Build;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.termux.R;

/**
 * Calm, beginner-friendly BottomSheetDialog that displays a {@link BabyRunSession}.
 * The learning loop lives here: the command is always visible at the top with
 * tappable part-by-part meanings, the friendly summary is the hero of the
 * result, the raw output sits one tap away behind 「生の結果をくわしく見る」
 * (auto-opened on failure), and the graduation row offers
 * "run in terminal" vs "type it myself" (typed, not executed).
 *
 * <p>The sheet is only a view: the session owns the execution, so closing this
 * sheet never kills running work — it continues in the background and can be
 * re-opened from the session bar. Stopping is the explicit 「とめる」 button.
 */
public final class BabyCommandRunnerSheet extends BottomSheetDialog {

    /**
     * Creates, registers and starts a run session. Implemented by
     * {@code BabyModeController} so every run is tracked by the registry.
     */
    public interface RunSessionHost {
        @NonNull
        BabyRunSession startRunSession(
            @NonNull String title,
            @DrawableRes int iconResId,
            @NonNull String command,
            @NonNull BabyAction.OutputFormat outputFormat,
            int timeoutSeconds,
            @Nullable String displayName,
            @Nullable String sourceId
        );
    }

    private final RunSessionHost runSessionHost;
    @Nullable
    private final BabyCommandExecutor.ProTerminalBridge proTerminalBridge;

    private final ImageView actionIconView;
    private final TextView titleView;
    private final CircularProgressIndicator progressView;
    private final ImageView statusIconView;
    private final TextView statusHeadlineView;
    private final TextView summaryTextView;
    private final MaterialButton recoveryButton;
    private final View tokensSection;
    private final com.google.android.material.chip.ChipGroup tokenChips;
    private final TextView tokenMeaningView;
    private final TextView commandTextView;
    private final TextView rawOutputTextView;
    private final TextView rawToggleView;
    private final View rawScrollView;
    private final LinearProgressIndicator usageBar;
    private final float defaultSummaryTextSizePx;
    private boolean rawExpanded;
    private final View graduationRow;
    private final MaterialButton openProButton;
    private final MaterialButton typeMyselfButton;
    private final MaterialButton secondaryButton;
    private final MaterialButton primaryButton;

    @Nullable
    private BabyRunSession session;

    private final BabyRunSession.Listener sessionListener = new BabyRunSession.Listener() {
        @Override
        public void onOutputChanged(@NonNull BabyRunSession changed) {
            if (changed != session || !isShowing()) return;
            rawOutputTextView.setText(changed.getOutputText());
            if (changed.isRunning()
                && changed.outputFormat == BabyAction.OutputFormat.INSTALL
                && changed.getLastLine() != null) {
                summaryTextView.setVisibility(View.VISIBLE);
                summaryTextView.setText(changed.getLastLine());
            }
        }

        @Override
        public void onStateChanged(@NonNull BabyRunSession changed) {
            if (changed != session || !isShowing()) return;
            if (!changed.isRunning() && changed.getResult() != null) {
                renderResult(changed.getResult());
            }
        }
    };

    public BabyCommandRunnerSheet(
        @NonNull Context context,
        @NonNull RunSessionHost runSessionHost,
        @Nullable BabyCommandExecutor.ProTerminalBridge proTerminalBridge
    ) {
        super(context, R.style.ThemeOverlay_TermuxBaby_BottomSheetDialog);
        this.runSessionHost = runSessionHost;
        this.proTerminalBridge = proTerminalBridge;

        View root = LayoutInflater.from(context).inflate(R.layout.sheet_baby_runner, null);
        setContentView(root);

        actionIconView = root.findViewById(R.id.runner_action_icon);
        titleView = root.findViewById(R.id.runner_title);
        ImageButton closeIcon = root.findViewById(R.id.runner_close_icon);
        progressView = root.findViewById(R.id.runner_progress);
        statusIconView = root.findViewById(R.id.runner_status_icon);
        statusHeadlineView = root.findViewById(R.id.runner_status_headline);
        summaryTextView = root.findViewById(R.id.runner_summary_text);
        recoveryButton = root.findViewById(R.id.runner_recovery_button);
        tokensSection = root.findViewById(R.id.runner_tokens_section);
        tokenChips = root.findViewById(R.id.runner_token_chips);
        tokenMeaningView = root.findViewById(R.id.runner_token_meaning);
        commandTextView = root.findViewById(R.id.runner_command_text);
        rawOutputTextView = root.findViewById(R.id.runner_raw_output_text);
        rawToggleView = root.findViewById(R.id.runner_raw_toggle);
        rawScrollView = root.findViewById(R.id.runner_raw_scroll);
        usageBar = root.findViewById(R.id.runner_usage_bar);
        defaultSummaryTextSizePx = summaryTextView.getTextSize();
        rawToggleView.setOnClickListener(v -> setRawExpanded(!rawExpanded));
        graduationRow = root.findViewById(R.id.runner_graduation_row);
        openProButton = root.findViewById(R.id.runner_open_pro_button);
        typeMyselfButton = root.findViewById(R.id.runner_type_myself_button);
        secondaryButton = root.findViewById(R.id.runner_secondary_button);
        primaryButton = root.findViewById(R.id.runner_primary_button);

        // Closing the sheet only detaches the view; the session keeps running.
        closeIcon.setOnClickListener(v -> dismiss());

        if (proTerminalBridge == null) {
            graduationRow.setVisibility(View.GONE);
        } else {
            openProButton.setOnClickListener(v -> {
                BabyRunSession current = session;
                String cmd = current != null ? current.command : null;
                if (current != null && current.isRunning()) {
                    current.cancel();
                }
                dismiss();
                proTerminalBridge.openInProTerminal(cmd);
            });
            typeMyselfButton.setOnClickListener(v -> {
                BabyRunSession current = session;
                // Typing practices the DISPLAY form (for the Python
                // calculator that is the learnable print() form, not the
                // internal safe-evaluator script).
                String cmd = current != null ? current.displayCommand : null;
                if (current != null && current.isRunning()) {
                    current.cancel();
                }
                dismiss();
                proTerminalBridge.openInProTerminalToType(cmd);
            });
        }

        secondaryButton.setOnClickListener(v -> {
            BabyRunSession current = session;
            if (current != null) {
                startExecution(
                    current.title,
                    current.iconResId,
                    current.command,
                    current.outputFormat,
                    current.timeoutSeconds,
                    current.displayName,
                    current.sourceId
                );
            }
        });

        primaryButton.setOnClickListener(v -> {
            BabyRunSession current = session;
            if (current != null && current.isRunning()) {
                current.cancel();
            }
            dismiss();
        });

        setOnDismissListener(dialog -> detachSession());
    }

    /**
     * Starts a brand-new run session through the host and displays it.
     */
    public void startExecution(
        @NonNull String title,
        @DrawableRes int iconResId,
        @NonNull String command,
        @NonNull BabyAction.OutputFormat outputFormat,
        int timeoutSeconds,
        @Nullable String actionDisplayName,
        @Nullable String sourceId
    ) {
        if (!BabyActionCatalog.isSafeForAutoRun(command)) {
            detachSession();
            actionIconView.setImageResource(iconResId);
            titleView.setText(title);
            BabyAction source = sourceId != null ? BabyActionCatalog.findById(sourceId) : null;
            commandTextView.setText(source != null
                ? BabyActionCatalog.getDisplayCommandForResolved(source, command)
                : command);
            rawOutputTextView.setText("");
            bindTokens(sourceId, command);
            recoveryButton.setVisibility(View.GONE);
            secondaryButton.setVisibility(View.GONE);
            showErrorState(
                new BabyErrorTranslator.TranslatedError(
                    BabyErrorTranslator.ErrorCategory.UNKNOWN, null, null
                ),
                ""
            );
            if (!isShowing()) show();
            return;
        }

        BabyRunSession created = runSessionHost.startRunSession(
            title, iconResId, command, outputFormat, timeoutSeconds, actionDisplayName, sourceId
        );
        bindSession(created);
    }

    /**
     * Shows the "what does this part do?" chips for the session's source
     * action. When the source has no catalog notes (installs, agent
     * setups), notes are derived from the command itself, so a
     * {@code pkg install} run explains its parts too. Hidden only when
     * neither source knows the command — it is still always visible.
     */
    private void bindTokens(@Nullable String sourceId, @Nullable String command) {
        java.util.List<BabyAction.TokenNote> notes = sourceId != null
            ? BabyActionCatalog.getTokenNotes(sourceId)
            : java.util.Collections.emptyList();
        if (notes.isEmpty()) {
            notes = BabyActionCatalog.getTokenNotesForCommand(command);
        }
        tokenChips.removeAllViews();
        tokenMeaningView.setVisibility(View.GONE);
        if (notes.isEmpty()) {
            tokensSection.setVisibility(View.GONE);
            return;
        }
        tokensSection.setVisibility(View.VISIBLE);
        Context ctx = getContext();
        float density = ctx.getResources().getDisplayMetrics().density;
        for (BabyAction.TokenNote note : notes) {
            com.google.android.material.chip.Chip chip =
                new com.google.android.material.chip.Chip(ctx);
            chip.setText(note.token);
            chip.setCheckable(true);
            chip.setChipMinHeight(44 * density);
            chip.setEnsureMinTouchTargetSize(true);
            chip.setOnClickListener(v -> {
                tokenMeaningView.setText(note.meaningResId);
                tokenMeaningView.setVisibility(View.VISIBLE);
            });
            tokenChips.addView(chip);
        }
    }

    /** Collapses/expands the raw output block and swaps the toggle label. */
    private void setRawExpanded(boolean expanded) {
        rawExpanded = expanded;
        rawScrollView.setVisibility(expanded ? View.VISIBLE : View.GONE);
        rawToggleView.setText(expanded
            ? R.string.baby_runner_raw_hide
            : R.string.baby_runner_raw_show);
    }

    /** Hides the storage meter and restores the summary's normal size. */
    private void resetSummaryChrome() {
        usageBar.setVisibility(View.GONE);
        summaryTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, defaultSummaryTextSizePx);
    }

    /**
     * The graduation row acts on the session, which would cancel a run
     * still in flight — so it is only offered once the run has finished
     * (and never when there is no Pro bridge or no session).
     */
    private void updateGraduationRow(boolean finished) {
        boolean show = finished && proTerminalBridge != null && session != null;
        graduationRow.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    /**
     * Displays an existing session (live or finished) and shows the sheet.
     * Used when the user taps a chip on the session bar.
     */
    public void bindSession(@NonNull BabyRunSession newSession) {
        detachSession();
        this.session = newSession;
        newSession.addListener(sessionListener);

        actionIconView.setImageResource(newSession.iconResId);
        titleView.setText(newSession.title);
        commandTextView.setText(newSession.displayCommand);
        rawOutputTextView.setText(newSession.getOutputText());
        setRawExpanded(false);
        resetSummaryChrome();

        bindTokens(newSession.sourceId, newSession.command);
        recoveryButton.setVisibility(View.GONE);
        secondaryButton.setVisibility(View.GONE);

        if (newSession.isRunning()) {
            renderRunningState();
            if (newSession.outputFormat == BabyAction.OutputFormat.INSTALL
                && newSession.getLastLine() != null) {
                summaryTextView.setVisibility(View.VISIBLE);
                summaryTextView.setText(newSession.getLastLine());
            }
        } else if (newSession.getResult() != null) {
            renderResult(newSession.getResult());
        }

        if (!isShowing()) {
            show();
        }
    }

    /**
     * Shows a calm 1-tap install prompt (for example when Python is not yet installed).
     */
    public void showInstallPrompt(
        @NonNull String title,
        @DrawableRes int iconResId,
        @NonNull String friendlyMessage,
        @NonNull String packageName,
        @NonNull String displayToolName
    ) {
        detachSession();

        String promptCommand = BabyActionCatalog.buildPackageInstallCommand(packageName);

        actionIconView.setImageResource(iconResId);
        titleView.setText(title);
        commandTextView.setText(promptCommand);
        rawOutputTextView.setText("");
        bindTokens(null, promptCommand);
        updateGraduationRow(false);

        progressView.setVisibility(View.GONE);
        statusIconView.setVisibility(View.VISIBLE);
        statusIconView.setImageResource(R.drawable.ic_baby_install);
        ImageViewCompat.setImageTintList(
            statusIconView,
            ColorStateList.valueOf(ContextCompat.getColor(getContext(), R.color.baby_primary))
        );
        statusHeadlineView.setText(title);
        statusHeadlineView.setTextColor(ContextCompat.getColor(getContext(), R.color.baby_text_primary));
        summaryTextView.setVisibility(View.VISIBLE);
        summaryTextView.setText(friendlyMessage);
        resetSummaryChrome();
        setRawExpanded(false);

        recoveryButton.setVisibility(View.VISIBLE);
        recoveryButton.setText(R.string.baby_runner_install_now);
        recoveryButton.setOnClickListener(v -> startExecution(
            displayToolName,
            R.drawable.ic_baby_install,
            BabyActionCatalog.buildPackageInstallCommand(packageName),
            BabyAction.OutputFormat.INSTALL,
            BabyCommandExecutor.INSTALL_TIMEOUT_SECONDS,
            displayToolName,
            "install:" + packageName
        ));

        secondaryButton.setVisibility(View.GONE);
        primaryButton.setText(R.string.baby_runner_close);

        if (!isShowing()) {
            show();
        }
    }

    private void renderRunningState() {
        progressView.setVisibility(View.VISIBLE);
        statusIconView.setVisibility(View.GONE);
        statusHeadlineView.setText(R.string.baby_runner_running);
        statusHeadlineView.setTextColor(ContextCompat.getColor(getContext(), R.color.baby_text_primary));
        summaryTextView.setText("");
        summaryTextView.setVisibility(View.GONE);
        resetSummaryChrome();
        primaryButton.setText(R.string.baby_runner_cancel);
        updateGraduationRow(false);
    }

    private void renderResult(@NonNull BabyCommandExecutor.ExecutionResult result) {
        BabyRunSession current = session;
        if (current != null) {
            // The user is looking at the finished result right now, so the
            // session bar must not keep a stale "finished" chip for it.
            current.markFinishedSeen();
        }
        updateGraduationRow(true);
        BabyAction.OutputFormat format = current != null ? current.outputFormat : BabyAction.OutputFormat.GENERIC;
        String displayName = current != null ? current.displayName : null;

        String combinedClean = BabyOutputFormatter.stripAnsi(result.getCombinedOutput()).trim();
        if (combinedClean.isEmpty()) {
            rawOutputTextView.setText(R.string.baby_runner_empty_output);
        } else {
            rawOutputTextView.setText(combinedClean);
        }

        if (result.isSuccess()) {
            BabyOutputFormatter.ParsedSummary summary = BabyOutputFormatter.parse(
                format, result.stdout, result.stderr, result.exitCode
            );
            String friendlyText = BabyOutputFormatter.formatForDisplay(
                getContext(), summary, displayName
            );

            progressView.setVisibility(View.GONE);
            statusIconView.setVisibility(View.VISIBLE);
            statusIconView.setImageResource(R.drawable.ic_baby_check);
            ImageViewCompat.setImageTintList(
                statusIconView,
                ColorStateList.valueOf(ContextCompat.getColor(getContext(), R.color.baby_success))
            );
            statusHeadlineView.setText(R.string.baby_runner_done);
            statusHeadlineView.setTextColor(ContextCompat.getColor(getContext(), R.color.baby_success));
            summaryTextView.setVisibility(View.VISIBLE);
            summaryTextView.setText(friendlyText);
            if (summary.kind == BabyOutputFormatter.SummaryKind.DEVICE_INFO) {
                // The free-space answer is the hero of its sheet: bigger
                // type, and the used percent as a meter (warning colour
                // once the phone is nearly full).
                summaryTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f);
                if (summary.usedPercent >= 0) {
                    usageBar.setVisibility(View.VISIBLE);
                    usageBar.setProgressCompat(summary.usedPercent, false);
                    usageBar.setIndicatorColor(ContextCompat.getColor(
                        getContext(),
                        summary.usedPercent >= 90
                            ? R.color.baby_warning
                            : R.color.baby_primary));
                } else {
                    usageBar.setVisibility(View.GONE);
                }
            } else {
                resetSummaryChrome();
            }

            recoveryButton.setVisibility(View.GONE);
            secondaryButton.setVisibility(View.VISIBLE);
            primaryButton.setText(R.string.baby_runner_close);

            performSuccessHaptic();
        } else {
            BabyErrorTranslator.TranslatedError error = BabyErrorTranslator.classify(result);
            showErrorState(error, combinedClean);
        }
    }

    private void showErrorState(
        @NonNull BabyErrorTranslator.TranslatedError error,
        @NonNull String rawOutput
    ) {
        progressView.setVisibility(View.GONE);
        statusIconView.setVisibility(View.VISIBLE);

        boolean isWarning = error.category == BabyErrorTranslator.ErrorCategory.NETWORK_UNREACHABLE
            || error.category == BabyErrorTranslator.ErrorCategory.TIMEOUT
            || error.category == BabyErrorTranslator.ErrorCategory.BOOTSTRAP_NOT_READY
            || error.category == BabyErrorTranslator.ErrorCategory.CANCELLED;

        statusIconView.setImageResource(isWarning ? R.drawable.ic_baby_warning : R.drawable.ic_baby_error);
        int tintColor = ContextCompat.getColor(
            getContext(),
            isWarning ? R.color.baby_warning : R.color.baby_error
        );
        ImageViewCompat.setImageTintList(statusIconView, ColorStateList.valueOf(tintColor));

        statusHeadlineView.setText(R.string.baby_runner_error_title);
        statusHeadlineView.setTextColor(tintColor);

        String friendlyMessage = BabyErrorTranslator.getFriendlyMessage(getContext(), error);
        summaryTextView.setVisibility(View.VISIBLE);
        summaryTextView.setText(friendlyMessage);
        resetSummaryChrome();
        // On failure the raw detail IS the explanation — open it instead
        // of leaving the beginner with only the friendly line.
        setRawExpanded(true);

        if (rawOutput.isEmpty()) {
            rawOutputTextView.setText(R.string.baby_runner_empty_output);
        } else {
            rawOutputTextView.setText(rawOutput);
        }

        if (error.hasInstallAction() && error.suggestedPackage != null) {
            final String pkg = error.suggestedPackage;
            recoveryButton.setVisibility(View.VISIBLE);
            recoveryButton.setText(R.string.baby_runner_install_now);
            recoveryButton.setOnClickListener(v -> startExecution(
                pkg,
                R.drawable.ic_baby_install,
                BabyActionCatalog.buildPackageInstallCommand(pkg),
                BabyAction.OutputFormat.INSTALL,
                BabyCommandExecutor.INSTALL_TIMEOUT_SECONDS,
                pkg,
                "install:" + pkg
            ));
        } else {
            recoveryButton.setVisibility(View.GONE);
        }

        secondaryButton.setVisibility(View.VISIBLE);
        primaryButton.setText(R.string.baby_runner_close);
        updateGraduationRow(true);
    }

    private void detachSession() {
        if (session != null) {
            session.removeListener(sessionListener);
            session = null;
        }
    }

    private void performSuccessHaptic() {
        View decor = getWindow() != null ? getWindow().getDecorView() : null;
        if (decor == null) return;
        int feedback = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
            ? HapticFeedbackConstants.CONFIRM
            : HapticFeedbackConstants.VIRTUAL_KEY;
        decor.performHapticFeedback(feedback);
    }
}
