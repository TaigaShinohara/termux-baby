package com.termux.app.baby;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.chip.ChipGroup;
import com.termux.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The command catalog (formerly "Find an action"): every catalog item —
 * home actions and recipes in curriculum order — with its REAL command
 * shown prominently, pinnable to the home grid. This is the full shelf;
 * the home grid is its pinned subset. The quick note utility lives at the
 * top (it is not a command, so it is not a home card anymore).
 */
public final class BabyCommandHelperSheet extends BottomSheetDialog {

    public interface OnRecipeSelectedListener {
        /** resolvedCommand is null for sheet-opening actions (installer, note). */
        void onRecipeRun(@NonNull BabyAction action, @Nullable String resolvedCommand);

        default void onNoteRequested() {
        }

        default void onPinsChanged() {
        }
    }

    private final List<BabyAction> allRecipes;
    private final OnRecipeSelectedListener listener;
    private final RecipeAdapter adapter;
    private final TextView emptyView;
    private final RecyclerView recyclerView;

    private String searchQuery = "";
    private BabyAction.Category selectedCategory = BabyAction.Category.ALL;

    public BabyCommandHelperSheet(
        @NonNull Context context,
        @NonNull OnRecipeSelectedListener listener
    ) {
        super(context, R.style.ThemeOverlay_TermuxBaby_BottomSheetDialog);
        this.listener = listener;
        this.allRecipes = buildCatalog();

        View root = LayoutInflater.from(context).inflate(R.layout.sheet_baby_helper, null);
        setContentView(root);

        ImageButton closeButton = root.findViewById(R.id.helper_close_button);
        EditText searchInput = root.findViewById(R.id.helper_search_input);
        ChipGroup categoryChips = root.findViewById(R.id.helper_category_chips);
        recyclerView = root.findViewById(R.id.helper_recipe_list);
        emptyView = root.findViewById(R.id.helper_empty_view);

        closeButton.setOnClickListener(v -> dismiss());

        View noteButton = root.findViewById(R.id.helper_note_button);
        noteButton.setOnClickListener(v -> {
            dismiss();
            listener.onNoteRequested();
        });

        adapter = new RecipeAdapter(this::handleRecipeClick, listener::onPinsChanged);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        recyclerView.setAdapter(adapter);

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s != null ? s.toString().trim() : "";
                applyFilter();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        categoryChips.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                selectedCategory = BabyAction.Category.ALL;
            } else {
                int id = checkedIds.get(0);
                if (id == R.id.helper_chip_files) {
                    selectedCategory = BabyAction.Category.FILES;
                } else if (id == R.id.helper_chip_network) {
                    selectedCategory = BabyAction.Category.NETWORK;
                } else if (id == R.id.helper_chip_learn) {
                    selectedCategory = BabyAction.Category.LEARN;
                } else if (id == R.id.helper_chip_tools) {
                    selectedCategory = BabyAction.Category.TOOLS;
                } else {
                    selectedCategory = BabyAction.Category.ALL;
                }
            }
            applyFilter();
        });

        applyFilter();
    }

    private void applyFilter() {
        List<BabyAction> filtered = new ArrayList<>();
        String queryLower = searchQuery.toLowerCase(Locale.getDefault());
        Context ctx = getContext();

        for (BabyAction action : allRecipes) {
            if (selectedCategory != BabyAction.Category.ALL && action.category != selectedCategory) {
                continue;
            }
            if (!queryLower.isEmpty()) {
                String title = ctx.getString(action.titleResId).toLowerCase(Locale.getDefault());
                String sub = ctx.getString(action.subtitleResId).toLowerCase(Locale.getDefault());
                String preview = BabyActionCatalog.getDisplayCommandPreview(action).toLowerCase(Locale.US);
                if (!title.contains(queryLower) && !sub.contains(queryLower) && !preview.contains(queryLower)) {
                    continue;
                }
            }
            filtered.add(action);
        }

        adapter.submitList(filtered);
        boolean isEmpty = filtered.isEmpty();
        emptyView.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    /**
     * The unified catalog: curriculum order first, then everything else.
     * Items without a command (the note, the tool installer) are NOT rows:
     * the note lives on the button at the top, the installer on its home
     * card — a catalog row promises a real command.
     */
    @NonNull
    private static List<BabyAction> buildCatalog() {
        List<BabyAction> all = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (String id : BabyActionCatalog.getCurriculumIds()) {
            BabyAction action = BabyActionCatalog.findById(id);
            if (action != null && action.commandTemplate != null && seen.add(action.id)) {
                all.add(action);
            }
        }
        for (BabyAction action : BabyActionCatalog.getHomeActions()) {
            if (action.commandTemplate != null && seen.add(action.id)) all.add(action);
        }
        for (BabyAction action : BabyActionCatalog.getHelperRecipes()) {
            if (action.commandTemplate != null && seen.add(action.id)) all.add(action);
        }
        return all;
    }

    private void handleRecipeClick(@NonNull BabyAction action) {
        if (action.actionType == BabyAction.ActionType.TOOL_INSTALLER
            || action.actionType == BabyAction.ActionType.QUICK_NOTE) {
            dismiss();
            listener.onRecipeRun(action, null);
            return;
        }
        if (action.commandTemplate == null) {
            return;
        }
        if (action.requiresUserInput()) {
            showInputDialog(action);
        } else {
            dismiss();
            listener.onRecipeRun(action, action.commandTemplate);
        }
    }

    private void showInputDialog(@NonNull BabyAction action) {
        Context ctx = getContext();
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
                if (value.isEmpty() || action.commandTemplate == null) {
                    previewView.setText(ctx.getString(
                        R.string.baby_helper_command_preview,
                        BabyActionCatalog.getDisplayCommandPreview(action)
                    ));
                } else {
                    // The display form of the resolved command (the
                    // teaching form for the Python calculator), honestly
                    // truncated — never an invented shortened form.
                    String previewCmd = BabyActionCatalog.truncateForDisplay(
                        BabyActionCatalog.buildDisplayCommand(action.commandTemplate, value));
                    previewView.setText(ctx.getString(R.string.baby_helper_command_preview, previewCmd));
                }
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

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = input.getText() != null ? input.getText().toString().trim() : "";
            if (value.isEmpty() || action.commandTemplate == null) {
                input.setError(ctx.getString(action.inputHintResId != 0 ? action.inputHintResId : R.string.baby_note_empty_error));
                return;
            }
            String resolved = BabyActionCatalog.buildSafeCommand(action.commandTemplate, value);
            dialog.dismiss();
            dismiss();
            listener.onRecipeRun(action, resolved);
        }));

        dialog.show();
    }

    private static final class RecipeAdapter extends RecyclerView.Adapter<RecipeViewHolder> {

        interface OnClick {
            void onClick(@NonNull BabyAction action);
        }

        private final List<BabyAction> items = new ArrayList<>();
        private final OnClick onClick;
        private final Runnable pinsChanged;

        RecipeAdapter(@NonNull OnClick onClick, @NonNull Runnable pinsChanged) {
            this.onClick = onClick;
            this.pinsChanged = pinsChanged;
        }

        void submitList(@NonNull List<BabyAction> newItems) {
            items.clear();
            items.addAll(newItems);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public RecipeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_baby_helper_recipe, parent, false);
            return new RecipeViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull RecipeViewHolder holder, int position) {
            BabyAction action = items.get(position);
            Context ctx = holder.itemView.getContext();
            holder.icon.setImageResource(action.iconResId);
            String title = ctx.getString(action.titleResId);
            String sub = ctx.getString(action.subtitleResId);
            holder.title.setText(title);
            holder.subtitle.setText(sub);
            holder.commandPreview.setText(BabyActionCatalog.getDisplayCommandPreview(action));
            holder.itemView.setContentDescription(title + ". " + sub);
            holder.itemView.setOnClickListener(v -> onClick.onClick(action));

            java.util.List<BabyAction.TokenNote> notes =
                BabyActionCatalog.getTokenNotes(action.id);
            holder.meanings.removeAllViews();
            holder.meanings.setVisibility(View.GONE);
            if (notes.isEmpty()) {
                holder.meaningButton.setVisibility(View.GONE);
            } else {
                holder.meaningButton.setVisibility(View.VISIBLE);
                for (BabyAction.TokenNote note : notes) {
                    TextView line = new TextView(ctx);
                    line.setText(note.token + " — " + ctx.getString(note.meaningResId));
                    line.setTextAppearance(ctx, R.style.TextAppearance_Baby_Body);
                    line.setPadding(0, Math.round(3 * ctx.getResources().getDisplayMetrics().density), 0,
                        Math.round(3 * ctx.getResources().getDisplayMetrics().density));
                    holder.meanings.addView(line);
                }
                holder.meaningButton.setOnClickListener(v -> {
                    boolean show = holder.meanings.getVisibility() != View.VISIBLE;
                    holder.meanings.setVisibility(show ? View.VISIBLE : View.GONE);
                });
            }

            boolean pinned = BabyPreferences.getPinnedIds(ctx).contains(action.id);
            holder.pinButton.setText(pinned ? R.string.baby_catalog_unpin : R.string.baby_catalog_pin);
            holder.pinButton.setOnClickListener(v -> {
                List<String> ids = BabyPreferences.getPinnedIds(ctx);
                if (ids.contains(action.id)) {
                    ids.remove(action.id);
                } else {
                    ids.add(action.id);
                }
                BabyPreferences.setPinnedIds(ctx, ids);
                notifyItemChanged(holder.getAdapterPosition());
                pinsChanged.run();
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    private static final class RecipeViewHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView title;
        final TextView subtitle;
        final TextView commandPreview;
        final TextView pinButton;
        final TextView meaningButton;
        final LinearLayout meanings;

        RecipeViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.recipe_icon);
            title = itemView.findViewById(R.id.recipe_title);
            subtitle = itemView.findViewById(R.id.recipe_subtitle);
            commandPreview = itemView.findViewById(R.id.recipe_command_preview);
            pinButton = itemView.findViewById(R.id.recipe_pin_button);
            meaningButton = itemView.findViewById(R.id.recipe_meaning_button);
            meanings = itemView.findViewById(R.id.recipe_meanings);
        }
    }
}
