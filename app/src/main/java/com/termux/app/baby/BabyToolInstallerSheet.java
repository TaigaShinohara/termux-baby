package com.termux.app.baby;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

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
    }

    private final BabyCommandExecutor executor;
    private final OnToolActionListener listener;
    private final LinearLayout toolsContainer;

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

        closeButton.setOnClickListener(v -> dismiss());
        populateTools();
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
                actionButton.setOnClickListener(v -> {
                    dismiss();
                    listener.onInstallTool(tool);
                });
            }

            toolsContainer.addView(item);
        }
    }
}
