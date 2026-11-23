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
 * Calm, beginner-friendly BottomSheetDialog that runs a shell command asynchronously,
 * displays a plain-language summary (or friendly translated error with 1-tap recovery),
 * and keeps raw terminal output tucked inside a collapsed-by-default accordion.
 */
public final class BabyCommandRunnerSheet extends BottomSheetDialog {

    public interface CompletionListener {
        void onActionFinished(boolean success);
    }

    private final BabyCommandExecutor executor;
    @Nullable
    private final BabyCommandExecutor.ProTerminalBridge proTerminalBridge;
    @Nullable
    private final CompletionListener completionListener;

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

    @Nullable
    private BabyCommandExecutor.ExecutionHandle activeHandle;
    private boolean isRunning = false;
    private boolean detailsExpanded = false;

    @Nullable
    private String currentCommand;
    @Nullable
    private String currentTitle;
    @DrawableRes
    private int currentIconResId = R.drawable.ic_baby_sparkle;
    @NonNull
    private BabyAction.OutputFormat currentFormat = BabyAction.OutputFormat.GENERIC;
    private int currentTimeoutSeconds = BabyCommandExecutor.DEFAULT_TIMEOUT_SECONDS;
    @Nullable
    private String currentDisplayName;

    public BabyCommandRunnerSheet(
        @NonNull Context context,
        @NonNull BabyCommandExecutor executor,
        @Nullable BabyCommandExecutor.ProTerminalBridge proTerminalBridge,
        @Nullable CompletionListener completionListener
    ) {
        super(context, R.style.ThemeOverlay_TermuxBaby_BottomSheetDialog);
        this.executor = executor;
        this.proTerminalBridge = proTerminalBridge;
        this.completionListener = completionListener;

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

        closeIcon.setOnClickListener(v -> {
            cancelIfRunning();
            dismiss();
        });

        detailsToggle.setOnClickListener(v -> toggleDetails());

        if (proTerminalBridge == null) {
            openProButton.setVisibility(View.GONE);
        } else {
            openProButton.setOnClickListener(v -> {
                String cmd = currentCommand;
                cancelIfRunning();
                dismiss();
                proTerminalBridge.openInProTerminal(cmd);
            });
        }

        secondaryButton.setOnClickListener(v -> {
            if (currentCommand != null && currentTitle != null) {
                startExecution(
                    currentTitle,
                    currentIconResId,
                    currentCommand,
                    currentFormat,
                    currentTimeoutSeconds,
                    currentDisplayName
                );
            }
        });

        primaryButton.setOnClickListener(v -> {
            if (isRunning) {
                cancelIfRunning();
            } else {
                dismiss();
            }
        });

        setOnDismissListener(dialog -> cancelIfRunning());
    }

    /**
     * Shows the sheet and immediately starts executing the specified safe shell command.
     */
    public void startExecution(
        @NonNull String title,
        @DrawableRes int iconResId,
        @NonNull String command,
        @NonNull BabyAction.OutputFormat outputFormat,
        int timeoutSeconds,
        @Nullable String actionDisplayName
    ) {
        cancelIfRunning();

        this.currentTitle = title;
        this.currentIconResId = iconResId;
        this.currentCommand = command;
        this.currentFormat = outputFormat;
        this.currentTimeoutSeconds = timeoutSeconds;
        this.currentDisplayName = actionDisplayName;

        actionIconView.setImageResource(iconResId);
        titleView.setText(title);
        commandTextView.setText(command);
        rawOutputTextView.setText("");

        setDetailsExpanded(false);
        detailsToggle.setVisibility(View.VISIBLE);
        recoveryButton.setVisibility(View.GONE);
        secondaryButton.setVisibility(View.GONE);

        if (!BabyActionCatalog.isSafeForAutoRun(command)) {
            showErrorState(
                new BabyErrorTranslator.TranslatedError(
                    BabyErrorTranslator.ErrorCategory.UNKNOWN, null, null
                ),
                ""
            );
            if (!isShowing()) show();
            return;
        }

        isRunning = true;
        progressView.setVisibility(View.VISIBLE);
        statusIconView.setVisibility(View.GONE);
        statusHeadlineView.setText(R.string.baby_runner_running);
        statusHeadlineView.setTextColor(ContextCompat.getColor(getContext(), R.color.baby_text_primary));
        summaryTextView.setText("");
        summaryTextView.setVisibility(View.GONE);
        primaryButton.setText(R.string.baby_runner_cancel);

        if (!isShowing()) {
            show();
        }

        final StringBuilder liveBuffer = new StringBuilder();
        activeHandle = executor.executeAsync(command, timeoutSeconds, new BabyCommandExecutor.Callback() {
            @Override
            public void onOutputLine(@NonNull String line) {
                if (!isShowing()) return;
                if (liveBuffer.length() > 0) {
                    liveBuffer.append('\n');
                }
                liveBuffer.append(line);
                rawOutputTextView.setText(liveBuffer.toString());
                if (isRunning && outputFormat == BabyAction.OutputFormat.INSTALL) {
                    summaryTextView.setVisibility(View.VISIBLE);
                    summaryTextView.setText(line);
                }
            }

            @Override
            public void onComplete(@NonNull BabyCommandExecutor.ExecutionResult result) {
                isRunning = false;
                activeHandle = null;
                if (!isShowing()) return;
                renderResult(result);
            }
        });
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
        cancelIfRunning();

        this.currentTitle = title;
        this.currentIconResId = iconResId;
        this.currentCommand = BabyActionCatalog.buildPackageInstallCommand(packageName);
        this.currentFormat = BabyAction.OutputFormat.INSTALL;
        this.currentTimeoutSeconds = BabyCommandExecutor.INSTALL_TIMEOUT_SECONDS;
        this.currentDisplayName = displayToolName;

        actionIconView.setImageResource(iconResId);
        titleView.setText(title);
        commandTextView.setText(currentCommand);
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

    private void renderResult(@NonNull BabyCommandExecutor.ExecutionResult result) {
        String combinedClean = BabyOutputFormatter.stripAnsi(result.getCombinedOutput()).trim();
        if (combinedClean.isEmpty()) {
            rawOutputTextView.setText(R.string.baby_runner_empty_output);
        } else {
            rawOutputTextView.setText(combinedClean);
        }

        if (result.isSuccess()) {
            BabyOutputFormatter.ParsedSummary summary = BabyOutputFormatter.parse(
                currentFormat, result.stdout, result.stderr, result.exitCode
            );
            String friendlyText = BabyOutputFormatter.formatForDisplay(
                getContext(), summary, currentDisplayName
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
            BabyPreferences.setFirstActionCompleted(getContext(), true);
            if (completionListener != null) {
                completionListener.onActionFinished(true);
            }
        } else {
            BabyErrorTranslator.TranslatedError error = BabyErrorTranslator.classify(result);
            showErrorState(error, combinedClean);
            if (completionListener != null) {
                completionListener.onActionFinished(false);
            }
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

    private void cancelIfRunning() {
        BabyCommandExecutor.ExecutionHandle handle = this.activeHandle;
        if (handle != null) {
            handle.cancel();
            this.activeHandle = null;
        }
        isRunning = false;
    }
}
