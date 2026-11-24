package com.termux.app.baby;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.termux.R;

import java.util.List;

/**
 * Main UI controller for Baby Mode Home ({@code view_baby_home.xml}).
 * Manages the 6 one-tap action cards, onboarding recommendation highlight, environment status
 * badge, bottom sheets (Runner, Command Helper, Quick Note, Tool Installer), and Pro Mode handoff.
 */
public final class BabyModeController {

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
         * Opens Termux Settings.
         */
        void onOpenSettings();
    }

    private static final int[] CARD_VIEW_IDS = new int[]{
        R.id.baby_card_0,
        R.id.baby_card_1,
        R.id.baby_card_2,
        R.id.baby_card_3,
        R.id.baby_card_4,
        R.id.baby_card_5
    };

    private final AppCompatActivity activity;
    private final View babyHomeRoot;
    private final HostCallbacks hostCallbacks;
    private final BabyCommandExecutor executor;
    private final List<BabyAction> homeActions;

    private final ImageView statusIconView;
    private final TextView statusTextView;
    private final MaterialButton findActionButton;

    @Nullable
    private Dialog activeSheet;
    private boolean hasAnimatedEntrance = false;
    private boolean environmentReady;

    public BabyModeController(
        @NonNull AppCompatActivity activity,
        @NonNull View babyHomeRoot,
        @NonNull HostCallbacks hostCallbacks
    ) {
        this.activity = activity;
        this.babyHomeRoot = babyHomeRoot;
        this.hostCallbacks = hostCallbacks;
        this.executor = new BabyCommandExecutor();
        this.homeActions = BabyActionCatalog.getHomeActions();

        this.statusIconView = babyHomeRoot.findViewById(R.id.baby_home_status_icon);
        this.statusTextView = babyHomeRoot.findViewById(R.id.baby_home_status_text);
        this.findActionButton = babyHomeRoot.findViewById(R.id.baby_home_find_action_button);

        this.environmentReady = executor.isBootstrapReady();

        findActionButton.setOnClickListener(v -> openCommandHelper());

        bindCards();
        updateStatusBadge();
    }

    /**
     * Re-binds card highlight state (e.g., after onboarding or completing the first action)
     * and updates the readiness badge.
     */
    public void refreshState() {
        this.environmentReady = this.environmentReady || executor.isBootstrapReady();
        bindCards();
        updateStatusBadge();
    }

    /**
     * Allows {@code TermuxActivity} to update the 1-line status badge while bootstrap is installing
     * versus ready.
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
     * Opens the "Find an action" plain-language Command Helper sheet.
     */
    public void openCommandHelper() {
        dismissActiveSheet();
        BabyCommandHelperSheet sheet = new BabyCommandHelperSheet(
            activity,
            (action, resolvedCommand) -> runCommandInSheet(
                activity.getString(action.titleResId),
                action.iconResId,
                resolvedCommand,
                action.outputFormat,
                action.timeoutSeconds,
                activity.getString(action.titleResId)
            )
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
                        toolName
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
                        toolName
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
                        agentName
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
                        activity.getString(action.titleResId)
                    );
                }
                break;
        }
    }

    /**
     * Releases sheets and background executor cleanly. Call from {@code TermuxActivity.onDestroy()}.
     */
    public void destroy() {
        dismissActiveSheet();
        executor.shutdown();
    }

    private void handleTryPython(@NonNull BabyAction action) {
        if (executor.isBinaryInstalled("python3")) {
            runCommandInSheet(
                activity.getString(action.titleResId),
                action.iconResId,
                BabyActionCatalog.PYTHON_HELLO_COMMAND,
                BabyAction.OutputFormat.PYTHON,
                action.timeoutSeconds,
                activity.getString(R.string.baby_tool_python_name)
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

    private void runCommandInSheet(
        @NonNull String title,
        int iconResId,
        @NonNull String command,
        @NonNull BabyAction.OutputFormat outputFormat,
        int timeoutSeconds,
        @Nullable String displayName
    ) {
        dismissActiveSheet();
        BabyCommandRunnerSheet sheet = createRunnerSheet();
        activeSheet = sheet;
        sheet.startExecution(title, iconResId, command, outputFormat, timeoutSeconds, displayName);
    }

    @NonNull
    private BabyCommandRunnerSheet createRunnerSheet() {
        return new BabyCommandRunnerSheet(
            activity,
            executor,
            hostCallbacks::onSwitchToProMode,
            success -> {
                if (success) {
                    refreshState();
                }
            }
        );
    }

    private void bindCards() {
        String recommendedId = BabyPreferences.getRecommendedActionId(activity);
        float density = activity.getResources().getDisplayMetrics().density;
        int strokeNormal = Math.max(1, Math.round(1 * density));
        int strokeHighlighted = Math.max(2, Math.round(2 * density));

        int colorSurface = ContextCompat.getColor(activity, R.color.baby_surface);
        int colorPrimaryContainer = ContextCompat.getColor(activity, R.color.baby_primary_container);
        int colorBorder = ContextCompat.getColor(activity, R.color.baby_border);
        int colorPrimary = ContextCompat.getColor(activity, R.color.baby_primary);

        int count = Math.min(homeActions.size(), CARD_VIEW_IDS.length);
        for (int i = 0; i < count; i++) {
            final BabyAction action = homeActions.get(i);
            View cardRoot = babyHomeRoot.findViewById(CARD_VIEW_IDS[i]);
            if (!(cardRoot instanceof MaterialCardView)) {
                continue;
            }
            MaterialCardView card = (MaterialCardView) cardRoot;

            ImageView iconView = card.findViewById(R.id.baby_card_icon);
            TextView titleView = card.findViewById(R.id.baby_card_title);
            TextView subtitleView = card.findViewById(R.id.baby_card_subtitle);
            TextView badgeView = card.findViewById(R.id.baby_card_recommended_badge);

            String title = activity.getString(action.titleResId);
            String subtitle = activity.getString(action.subtitleResId);

            iconView.setImageResource(action.iconResId);
            titleView.setText(title);
            subtitleView.setText(subtitle);

            boolean isRecommended = action.id.equals(recommendedId);
            badgeView.setVisibility(isRecommended ? View.VISIBLE : View.GONE);
            card.setCardBackgroundColor(isRecommended ? colorPrimaryContainer : colorSurface);
            card.setStrokeColor(isRecommended ? colorPrimary : colorBorder);
            card.setStrokeWidth(isRecommended ? strokeHighlighted : strokeNormal);

            String a11y = isRecommended
                ? title + ". " + subtitle + ". " + activity.getString(R.string.baby_home_recommended_badge)
                : title + ". " + subtitle;
            card.setContentDescription(a11y);

            card.setOnClickListener(v -> {
                animateCardPressIfEnabled(card);
                runAction(action);
            });
        }
    }

    private void updateStatusBadge() {
        if (environmentReady) {
            statusTextView.setText(R.string.baby_home_status_ready);
            statusIconView.setImageResource(R.drawable.ic_baby_check);
            ImageViewCompat.setImageTintList(
                statusIconView,
                ColorStateList.valueOf(ContextCompat.getColor(activity, R.color.baby_on_primary_container))
            );
        } else {
            statusTextView.setText(R.string.baby_home_status_preparing);
            statusIconView.setImageResource(R.drawable.ic_baby_sparkle);
            ImageViewCompat.setImageTintList(
                statusIconView,
                ColorStateList.valueOf(ContextCompat.getColor(activity, R.color.baby_on_primary_container))
            );
        }
    }

    private void animateCardEntranceIfEnabled() {
        if (!BabyPreferences.areAnimationsEnabled(activity)) {
            return;
        }
        float translateY = 12f * activity.getResources().getDisplayMetrics().density;
        DecelerateInterpolator interpolator = new DecelerateInterpolator();
        for (int i = 0; i < CARD_VIEW_IDS.length; i++) {
            View card = babyHomeRoot.findViewById(CARD_VIEW_IDS[i]);
            if (card == null) continue;
            card.setAlpha(0f);
            card.setTranslationY(translateY);
            card.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(i * 60L)
                .setDuration(220L)
                .setInterpolator(interpolator)
                .start();
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
