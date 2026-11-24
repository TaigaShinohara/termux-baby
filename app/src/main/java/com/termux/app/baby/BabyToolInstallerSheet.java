package com.termux.app.baby;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.termux.R;

import java.util.List;

/**
 * Guided 1-tap Tool Installer BottomSheetDialog for curated beginner-friendly Termux packages
 * (Python, Git, Nano, Tree). Shows live installed state and lets users install or try each tool.
 */
public final class BabyToolInstallerSheet extends BottomSheetDialog {

    public interface OnToolActionListener {
        void onInstallTool(@NonNull BabyActionCatalog.ToolSpec tool);

        void onTryTool(@NonNull BabyActionCatalog.ToolSpec tool);

        void onInstallAgent(@NonNull BabyActionCatalog.AgentSpec agent);

        void onLaunchAgent(@NonNull BabyActionCatalog.AgentSpec agent);
    }

    private final BabyCommandExecutor executor;
    private final OnToolActionListener listener;
    private final LinearLayout toolsContainer;
    private final LinearLayout agentsContainer;

    public BabyToolInstallerSheet(
        @NonNull Context context,
        @NonNull BabyCommandExecutor executor,
        @NonNull OnToolActionListener listener
    ) {
        super(context, R.style.ThemeOverlay_TermuxBaby_BottomSheetDialog);
        this.executor = executor;
        this.listener = listener;

        View root = LayoutInflater.from(context).inflate(R.layout.sheet_baby_installer, null);
        setContentView(root);

        ImageButton closeButton = root.findViewById(R.id.installer_close_button);
        toolsContainer = root.findViewById(R.id.installer_tools_container);
        agentsContainer = root.findViewById(R.id.installer_agents_container);

        closeButton.setOnClickListener(v -> dismiss());
        populateTools();
        populateAgents();
    }

    public void populateTools() {
        toolsContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(getContext());
        List<BabyActionCatalog.ToolSpec> tools = BabyActionCatalog.getInstallableTools();

        for (BabyActionCatalog.ToolSpec tool : tools) {
            View item = inflater.inflate(R.layout.item_baby_installer_tool, toolsContainer, false);
            ImageView icon = item.findViewById(R.id.tool_icon);
            TextView name = item.findViewById(R.id.tool_name);
            TextView desc = item.findViewById(R.id.tool_desc);
            TextView installedBadge = item.findViewById(R.id.tool_installed_badge);
            MaterialButton actionButton = item.findViewById(R.id.tool_action_button);

            String toolName = getContext().getString(tool.nameResId);
            String toolDesc = getContext().getString(tool.descResId);

            icon.setImageResource(tool.iconResId);
            name.setText(toolName);
            desc.setText(toolDesc);

            boolean installed = executor.isBinaryInstalled(tool.binaryName);
            if (installed) {
                installedBadge.setVisibility(View.VISIBLE);
                if (tool.tryCommand != null) {
                    actionButton.setVisibility(View.VISIBLE);
                    actionButton.setText(R.string.baby_installer_try);
                    actionButton.setContentDescription(
                        getContext().getString(R.string.baby_installer_try) + " " + toolName
                    );
                    actionButton.setOnClickListener(v -> {
                        dismiss();
                        listener.onTryTool(tool);
                    });
                } else {
                    actionButton.setVisibility(View.GONE);
                }
            } else {
                installedBadge.setVisibility(View.GONE);
                actionButton.setVisibility(View.VISIBLE);
                actionButton.setText(R.string.baby_installer_install);
                actionButton.setContentDescription(
                    getContext().getString(R.string.baby_installer_install) + " " + toolName
                );
                actionButton.setOnClickListener(v ->
                    confirmInstall(toolName, () -> {
                        dismiss();
                        listener.onInstallTool(tool);
                    })
                );
            }

            toolsContainer.addView(item);
        }
    }

    /**
     * Populates the AI Agents section. Installed agents (binary or wrapper present in
     * $PREFIX/bin) get an "Open" button that launches them in the Pro Terminal;
     * the rest get an "Install" button that runs the one-tap setup.
     */
    public void populateAgents() {
        agentsContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(getContext());
        List<BabyActionCatalog.AgentSpec> agents = BabyActionCatalog.getInstallableAgents();

        for (BabyActionCatalog.AgentSpec agent : agents) {
            View item = inflater.inflate(R.layout.item_baby_installer_tool, agentsContainer, false);
            ImageView icon = item.findViewById(R.id.tool_icon);
            TextView name = item.findViewById(R.id.tool_name);
            TextView desc = item.findViewById(R.id.tool_desc);
            TextView installedBadge = item.findViewById(R.id.tool_installed_badge);
            MaterialButton actionButton = item.findViewById(R.id.tool_action_button);

            String agentName = getContext().getString(agent.nameResId);

            icon.setImageResource(agent.iconResId);
            name.setText(agentName);
            desc.setText(getContext().getString(agent.descResId));

            boolean installed = executor.isBinaryInstalled(agent.binaryName);
            actionButton.setVisibility(View.VISIBLE);
            if (installed) {
                installedBadge.setVisibility(View.VISIBLE);
                actionButton.setText(R.string.baby_installer_launch);
                actionButton.setContentDescription(
                    getContext().getString(R.string.baby_installer_launch) + " " + agentName
                );
                actionButton.setOnClickListener(v -> {
                    dismiss();
                    listener.onLaunchAgent(agent);
                });
            } else {
                installedBadge.setVisibility(View.GONE);
                actionButton.setText(R.string.baby_installer_install);
                actionButton.setContentDescription(
                    getContext().getString(R.string.baby_installer_install) + " " + agentName
                );
                actionButton.setOnClickListener(v ->
                    confirmInstall(agentName, () -> {
                        dismiss();
                        listener.onInstallAgent(agent);
                    })
                );
            }

            agentsContainer.addView(item);
        }
    }

    /**
     * Confirmation step before any install starts (user request): installing
     * downloads data and can take minutes, so a tap on "Install" never starts
     * work immediately — the user confirms the item first.
     */
    private void confirmInstall(@NonNull String itemName, @NonNull Runnable onConfirmed) {
        Context ctx = getContext();
        new AlertDialog.Builder(ctx)
            .setTitle(ctx.getString(R.string.baby_install_confirm_title, itemName))
            .setMessage(R.string.baby_install_confirm_message)
            .setNegativeButton(R.string.baby_install_confirm_cancel, null)
            .setPositiveButton(R.string.baby_installer_install, (dialog, which) -> onConfirmed.run())
            .show();
    }
}
