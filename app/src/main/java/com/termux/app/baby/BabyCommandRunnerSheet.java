package com.termux.app.baby;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Build;
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
import com.termux.R;

/**
 * Calm, beginner-friendly BottomSheetDialog that displays a {@link BabyRunSession},
 * with a plain-language summary (or friendly translated error with 1-tap recovery)
 * and raw terminal output tucked inside a collapsed-by-default accordion.
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
            @Nullable String displayName
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
    private final View detailsToggle;
    private final TextView detailsToggleText;
    private final ImageView detailsChevron;
    private final View detailsContainer;
    private final TextView commandTextView;
    private final TextView rawOutputTextView;
    private final MaterialButton openProButton;
    private final MaterialButton secondaryButton;
    private final MaterialButton primaryButton;

    private boolean detailsExpanded = false;

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
        detailsToggle = root.findViewById(R.id.runner_details_toggle);
        detailsToggleText = root.findViewById(R.id.runner_details_toggle_text);
        detailsChevron = root.findViewById(R.id.runner_details_chevron);
        detailsContainer = root.findViewById(R.id.runner_details_container);
        commandTextView = root.findViewById(R.id.runner_command_text);
        rawOutputTextView = root.findViewById(R.id.runner_raw_output_text);
        openProButton = root.findViewById(R.id.runner_open_pro_button);
        secondaryButton = root.findViewById(R.id.runner_secondary_button);
        primaryButton = root.findViewById(R.id.runner_primary_button);

        // Closing the sheet only detaches the view; the session keeps running.
        closeIcon.setOnClickListener(v -> dismiss());

        detailsToggle.setOnClickListener(v -> toggleDetails());

        if (proTerminalBridge == null) {
            openProButton.setVisibility(View.GONE);
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
                    current.displayName
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
        @Nullable String actionDisplayName
    ) {
        if (!BabyActionCatalog.isSafeForAutoRun(command)) {
            detachSession();
            actionIconView.setImageResource(iconResId);
            titleView.setText(title);
            commandTextView.setText(command);
            rawOutputTextView.setText("");
            setDetailsExpanded(false);
            detailsToggle.setVisibility(View.VISIBLE);
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
            title, iconResId, command, outputFormat, timeoutSeconds, actionDisplayName
        );
        bindSession(created);
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
        commandTextView.setText(newSession.command);
        rawOutputTextView.setText(newSession.getOutputText());

        setDetailsExpanded(false);
        detailsToggle.setVisibility(View.VISIBLE);
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
        setDetailsExpanded(false);
        detailsToggle.setVisibility(View.GONE);

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

        recoveryButton.setVisibility(View.VISIBLE);
        recoveryButton.setText(R.string.baby_runner_install_now);
        recoveryButton.setOnClickListener(v -> startExecution(
            displayToolName,
            R.drawable.ic_baby_install,
            BabyActionCatalog.buildPackageInstallCommand(packageName),
            BabyAction.OutputFormat.INSTALL,
            BabyCommandExecutor.INSTALL_TIMEOUT_SECONDS,
            displayToolName
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
        primaryButton.setText(R.string.baby_runner_cancel);
    }

    private void renderResult(@NonNull BabyCommandExecutor.ExecutionResult result) {
        BabyRunSession current = session;
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
                pkg
            ));
        } else {
            recoveryButton.setVisibility(View.GONE);
        }

        secondaryButton.setVisibility(View.VISIBLE);
        primaryButton.setText(R.string.baby_runner_close);
    }

    private void detachSession() {
        if (session != null) {
            session.removeListener(sessionListener);
            session = null;
        }
    }

    private void toggleDetails() {
        setDetailsExpanded(!detailsExpanded);
    }

    private void setDetailsExpanded(boolean expanded) {
        this.detailsExpanded = expanded;
        detailsContainer.setVisibility(expanded ? View.VISIBLE : View.GONE);
        detailsToggleText.setText(
            expanded ? R.string.baby_runner_hide_details : R.string.baby_runner_show_details
        );
        detailsChevron.setImageResource(
            expanded ? R.drawable.ic_baby_chevron_up : R.drawable.ic_baby_chevron_down
        );
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
