package com.termux.app.baby;

import android.content.Context;
import android.os.Build;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.termux.R;
import com.termux.shared.termux.TermuxConstants;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

/**
 * Zero-friction Quick Note BottomSheetDialog that saves plain-text memos into ~/notes/
 * with one tap and lists recent notes for easy reading.
 */
public final class BabyQuickNoteSheet extends BottomSheetDialog {

    public interface OnNoteSavedListener {
        void onNoteSaved();
    }

    private static final int MAX_RECENT_NOTES = 10;
    /** Viewer cap: a giant note file must not be loaded whole into a dialog. */
    private static final int MAX_VIEWER_CHARS = 100_000;

    /** File IO runs here, never on the UI thread; shut down on dismiss. */
    private final java.util.concurrent.ExecutorService ioExecutor =
        java.util.concurrent.Executors.newSingleThreadExecutor();
    private final android.os.Handler mainHandler =
        new android.os.Handler(android.os.Looper.getMainLooper());
    /** Set on dismiss so late IO completions stop touching the UI. */
    private volatile boolean dismissed;

    private final EditText noteInput;
    private final TextView feedbackText;
    private final LinearLayout recentContainer;
    private final TextView emptyListText;
    @Nullable
    private final OnNoteSavedListener onNoteSavedListener;

    public BabyQuickNoteSheet(
        @NonNull Context context,
        @Nullable OnNoteSavedListener onNoteSavedListener
    ) {
        super(context, R.style.ThemeOverlay_TermuxBaby_BottomSheetDialog);
        this.onNoteSavedListener = onNoteSavedListener;

        View root = LayoutInflater.from(context).inflate(R.layout.sheet_baby_note, null);
        setContentView(root);

        ImageButton closeButton = root.findViewById(R.id.note_close_button);
        noteInput = root.findViewById(R.id.note_input);
        feedbackText = root.findViewById(R.id.note_feedback_text);
        MaterialButton saveButton = root.findViewById(R.id.note_save_button);
        recentContainer = root.findViewById(R.id.note_recent_container);
        emptyListText = root.findViewById(R.id.note_empty_text);

        closeButton.setOnClickListener(v -> dismiss());
        saveButton.setOnClickListener(v -> saveCurrentNote());
        setOnDismissListener(d -> {
            dismissed = true;
            ioExecutor.shutdownNow();
        });

        refreshRecentNotes();
    }

    private void saveCurrentNote() {
        String content = noteInput.getText() != null ? noteInput.getText().toString().trim() : "";
        if (content.isEmpty()) {
            feedbackText.setVisibility(View.VISIBLE);
            feedbackText.setTextColor(ContextCompat.getColor(getContext(), R.color.baby_error));
            feedbackText.setText(R.string.baby_note_empty_error);
            return;
        }

        final String noteContent = content;
        ioExecutor.execute(() -> {
            File notesDir = resolveNotesDir();
            if (!notesDir.exists() && !notesDir.mkdirs()) {
                mainHandler.post(() -> showSaveError(R.string.baby_error_permission));
                return;
            }

            // Millisecond resolution plus an existence loop: two notes saved in
            // the same second must never overwrite each other.
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date());
            String filename = "note_" + timestamp + ".txt";
            File noteFile = new File(notesDir, filename);
            int suffix = 2;
            while (noteFile.exists()) {
                filename = "note_" + timestamp + "_" + suffix + ".txt";
                noteFile = new File(notesDir, filename);
                suffix++;
            }

            try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(noteFile), StandardCharsets.UTF_8
            )) {
                writer.write(noteContent);
                writer.write('\n');
            } catch (IOException e) {
                mainHandler.post(() -> showSaveError(R.string.baby_error_generic));
                return;
            }

            final String savedName = filename;
            mainHandler.post(() -> {
                if (dismissed) return;
                noteInput.setText("");
                feedbackText.setVisibility(View.VISIBLE);
                feedbackText.setTextColor(ContextCompat.getColor(getContext(), R.color.baby_success));
                feedbackText.setText(getContext().getString(R.string.baby_note_saved, "~/notes/" + savedName));

                performSuccessHaptic();
                BabyPreferences.setFirstActionCompleted(getContext(), true);
                if (onNoteSavedListener != null) {
                    onNoteSavedListener.onNoteSaved();
                }
                refreshRecentNotes();
            });
        });
    }

    private void showSaveError(int messageResId) {
        if (dismissed) return;
        feedbackText.setVisibility(View.VISIBLE);
        feedbackText.setTextColor(ContextCompat.getColor(getContext(), R.color.baby_error));
        feedbackText.setText(messageResId);
    }

    private void refreshRecentNotes() {
        recentContainer.removeAllViews();
        ioExecutor.execute(() -> {
            File notesDir = resolveNotesDir();
            File[] files = notesDir.exists()
                ? notesDir.listFiles((dir, name) -> name.endsWith(".txt")) : null;
            if (files == null || files.length == 0) {
                mainHandler.post(() -> {
                    if (!dismissed) emptyListText.setVisibility(View.VISIBLE);
                });
                return;
            }
            Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            int count = Math.min(files.length, MAX_RECENT_NOTES);
            java.util.List<File> top = new java.util.ArrayList<>();
            java.util.List<String> firstLines = new java.util.ArrayList<>();
            for (int i = 0; i < count; i++) {
                top.add(files[i]);
                firstLines.add(readFirstLine(files[i]));
            }
            mainHandler.post(() -> {
                if (!dismissed) buildRecentRows(top, firstLines);
            });
        });
    }

    private void buildRecentRows(
        @NonNull java.util.List<File> files,
        @NonNull java.util.List<String> firstLines
    ) {
        recentContainer.removeAllViews();
        emptyListText.setVisibility(View.GONE);

        Context ctx = getContext();
        float density = ctx.getResources().getDisplayMetrics().density;
        int padH = Math.round(12 * density);
        int padV = Math.round(10 * density);
        int marginBottom = Math.round(8 * density);

        for (int i = 0; i < files.size(); i++) {
            final File file = files.get(i);
            String firstLine = firstLines.get(i);

            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setBackgroundResource(R.drawable.baby_code_box);
            row.setPadding(padH, padV, padH, padV);
            row.setClickable(true);
            row.setFocusable(true);
            row.setMinimumHeight(Math.round(48 * density));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );
            lp.bottomMargin = marginBottom;
            row.setLayoutParams(lp);

            TextView preview = new TextView(ctx);
            preview.setTextAppearance(ctx, R.style.TextAppearance_Baby_Body);
            preview.setMaxLines(1);
            preview.setText(firstLine.isEmpty() ? file.getName() : firstLine);
            row.addView(preview);

            TextView meta = new TextView(ctx);
            meta.setTextAppearance(ctx, R.style.TextAppearance_Baby_Caption);
            meta.setText("~/notes/" + file.getName());
            row.addView(meta);

            row.setContentDescription((firstLine.isEmpty() ? file.getName() : firstLine) + ", " + file.getName());
            row.setOnClickListener(v -> showNoteViewer(file));

            recentContainer.addView(row);
        }
    }

    private void showNoteViewer(@NonNull File file) {
        ioExecutor.execute(() -> {
            String content = readWholeFileCapped(file);
            mainHandler.post(() -> {
                if (dismissed) return;
                new AlertDialog.Builder(getContext())
                    .setTitle(file.getName())
                    .setMessage(content)
                    .setPositiveButton(R.string.baby_note_close, null)
                    .show();
            });
        });
    }

    /**
     * Notes live in exactly one place: ~/notes (the UI shows that path, and
     * the user can open the files from the terminal). Never fall back to a
     * second directory — a transient mkdir failure would split the notes
     * across two locations and the fallback ones would silently disappear
     * from the recent list once $HOME works again. If the directory cannot
     * be created, saving fails with a visible error instead.
     */
    @NonNull
    private File resolveNotesDir() {
        return new File(TermuxConstants.TERMUX_HOME_DIR, "notes");
    }

    @NonNull
    private static String readFirstLine(@NonNull File file) {
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)
        )) {
            String line = reader.readLine();
            return line != null ? line.trim() : "";
        } catch (IOException ignored) {
            return "";
        }
    }

    @NonNull
    private static String readWholeFileCapped(@NonNull File file) {
        StringBuilder sb = new StringBuilder();
        boolean truncated = false;
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)
        )) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (sb.length() > 0) sb.append('\n');
                if (sb.length() + line.length() > MAX_VIEWER_CHARS) {
                    sb.append(line, 0, Math.max(0, MAX_VIEWER_CHARS - sb.length()));
                    truncated = true;
                    break;
                }
                sb.append(line);
            }
        } catch (IOException ignored) {
        }
        if (truncated) sb.append("\n…");
        return sb.toString();
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
