package com.termux.shared.termux.extrakeys;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.TypedValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledExecutorService;

import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.PopupWindow;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.termux.shared.R;
import com.termux.shared.termux.terminal.io.TerminalExtraKeys;
import com.termux.shared.theme.ThemeUtils;

/**
 * A {@link View} showing extra keys (such as Escape, Ctrl, Alt) not normally available on an Android soft
 * keyboards.
 *
 * To use it, add following to a layout file and import it in your activity layout file or inflate
 * it with a {@link androidx.viewpager.widget.ViewPager}.:
 * {@code
 * <?xml version="1.0" encoding="utf-8"?>
 * <com.termux.shared.termux.extrakeys.ExtraKeysView xmlns:android="http://schemas.android.com/apk/res/android"
 *     android:id="@+id/extra_keys"
 *     style="?android:attr/buttonBarStyle"
 *     android:layout_width="match_parent"
 *     android:layout_height="match_parent"
 *     android:layout_alignParentBottom="true"
 *     android:orientation="horizontal" />
 * }
 *
 * Then in your activity, get its reference by a call to {@link android.app.Activity#findViewById(int)}
 * or {@link LayoutInflater#inflate(int, ViewGroup)} if using {@link androidx.viewpager.widget.ViewPager}.
 * Then call {@link #setExtraKeysViewClient(IExtraKeysView)} and pass it the implementation of
 * {@link IExtraKeysView} so that you can receive callbacks. You can also override other values set
 * in {@link ExtraKeysView#ExtraKeysView(Context, AttributeSet)} by calling the respective functions.
 * If you extend {@link ExtraKeysView}, you can also set them in the constructor, but do call super().
 *
 * After this you will have to make a call to {@link ExtraKeysView#reload(ExtraKeysInfo, float) and pass
 * it the {@link ExtraKeysInfo} to load and display the extra keys. Read its class javadocs for more
 * info on how to create it.
 *
 * Termux app defines the view in res/layout/view_terminal_toolbar_extra_keys and
 * inflates it in TerminalToolbarViewPager.instantiateItem() and sets the {@link ExtraKeysView} client
 * and calls {@link ExtraKeysView#reload(ExtraKeysInfo).
 * The {@link ExtraKeysInfo} is created by TermuxAppSharedProperties.setExtraKeys().
 * Then its got and the view height is adjusted in TermuxActivity.setTerminalToolbarHeight().
 * The client used is TermuxTerminalExtraKeys, which extends
 * {@link TerminalExtraKeys } to handle Termux app specific logic and
 * leave the rest to the super class.
 */
public final class ExtraKeysView extends GridLayout {

    /** The client for the {@link ExtraKeysView}. */
    public interface IExtraKeysView {

        /**
         * This is called by {@link ExtraKeysView} when a button is clicked. This is also called
         * for {@link #mRepetitiveKeys} and {@link ExtraKeyButton} that have a popup set.
         * However, this is not called for {@link #mSpecialButtons}, whose state can instead be read
         * via a call to {@link #readSpecialButton(SpecialButton, boolean)}.
         *
         * @param view The view that was clicked.
         * @param buttonInfo The {@link ExtraKeyButton} for the button that was clicked.
         *                   The button may be a {@link ExtraKeyButton#KEY_MACRO} set which can be
         *                   checked with a call to {@link ExtraKeyButton#isMacro()}.
         * @param button The {@link MaterialButton} that was clicked.
         */
        void onExtraKeyButtonClick(View view, ExtraKeyButton buttonInfo, MaterialButton button);

        /**
         * This is called by {@link ExtraKeysView} when a button is clicked so that the client
         * can perform any hepatic feedback. This is only called in the {@link MaterialButton.OnClickListener}
         * and not for every repeat. Its also called for {@link #mSpecialButtons}.
         *
         * @param view The view that was clicked.
         * @param buttonInfo The {@link ExtraKeyButton} for the button that was clicked.
         * @param button The {@link MaterialButton} that was clicked.
         * @return Return {@code true} if the client handled the feedback, otherwise {@code false}
         * so that {@link ExtraKeysView#performExtraKeyButtonHapticFeedback(View, ExtraKeyButton, MaterialButton)}
         * can handle it depending on system settings.
         */
        boolean performExtraKeyButtonHapticFeedback(View view, ExtraKeyButton buttonInfo, MaterialButton button);

    }


    /** Defines the default value for {@link #mButtonTextColor} defined by current theme. */
    public static final int ATTR_BUTTON_TEXT_COLOR = R.attr.extraKeysButtonTextColor;
    /** Defines the default value for {@link #mButtonActiveTextColor} defined by current theme. */
    public static final int ATTR_BUTTON_ACTIVE_TEXT_COLOR = R.attr.extraKeysButtonActiveTextColor;
    /** Defines the default value for {@link #mButtonBackgroundColor} defined by current theme. */
    public static final int ATTR_BUTTON_BACKGROUND_COLOR = R.attr.extraKeysButtonBackgroundColor;
    /** Defines the default value for {@link #mButtonActiveBackgroundColor} defined by current theme. */
    public static final int ATTR_BUTTON_ACTIVE_BACKGROUND_COLOR = R.attr.extraKeysButtonActiveBackgroundColor;

    /** Defines the default fallback value for {@link #mButtonTextColor} if {@link #ATTR_BUTTON_TEXT_COLOR} is undefined. */
    public static final int DEFAULT_BUTTON_TEXT_COLOR = 0xFFF1F5F9;
    /** Defines the default fallback value for {@link #mButtonActiveTextColor} if {@link #ATTR_BUTTON_ACTIVE_TEXT_COLOR} is undefined. */
    public static final int DEFAULT_BUTTON_ACTIVE_TEXT_COLOR = 0xFF0D1B18;
    /** Defines the default fallback value for {@link #mButtonBackgroundColor} if {@link #ATTR_BUTTON_BACKGROUND_COLOR} is undefined. */
    public static final int DEFAULT_BUTTON_BACKGROUND_COLOR = 0xFF232834;
    /** Defines the default fallback value for {@link #mButtonActiveBackgroundColor} if {@link #ATTR_BUTTON_ACTIVE_BACKGROUND_COLOR} is undefined. */
    public static final int DEFAULT_BUTTON_ACTIVE_BACKGROUND_COLOR = 0xFF52C7AC;

    /** Warm Slate toolbar surface color behind rounded key cards. */
    public static final int DEFAULT_TOOLBAR_BACKGROUND_COLOR = 0xFF161920;
    /** Subtle key card border color in resting state. */
    public static final int DEFAULT_BUTTON_BORDER_COLOR = 0xFF2F3646;
    /** Key card fill color while pressed. */
    public static final int DEFAULT_BUTTON_PRESSED_BACKGROUND_COLOR = 0xFF313A4D;
    /** Subtle key card border color while pressed. */
    public static final int DEFAULT_BUTTON_PRESSED_BORDER_COLOR = 0xFF43506A;
    /** Muted slate hint color for top-right swipe-up popup labels (>= 4.5:1 on #232834). */
    public static final int DEFAULT_POPUP_HINT_TEXT_COLOR = 0xFF94A3B8;
    /** Dark teal hint color for top-right swipe-up popup labels when key is active. */
    public static final int DEFAULT_POPUP_HINT_ACTIVE_TEXT_COLOR = 0xFF1E4D42;



    /** Defines the minimum allowed duration in milliseconds for {@link #mLongPressTimeout}. */
    public static final int MIN_LONG_PRESS_DURATION = 200;
    /** Defines the maximum allowed duration in milliseconds for {@link #mLongPressTimeout}. */
    public static final int MAX_LONG_PRESS_DURATION = 3000;
    /** Defines the fallback duration in milliseconds for {@link #mLongPressTimeout}. */
    public static final int FALLBACK_LONG_PRESS_DURATION = 400;

    /** Defines the minimum allowed duration in milliseconds for {@link #mLongPressRepeatDelay}. */
    public static final int MIN_LONG_PRESS__REPEAT_DELAY = 5;
    /** Defines the maximum allowed duration in milliseconds for {@link #mLongPressRepeatDelay}. */
    public static final int MAX_LONG_PRESS__REPEAT_DELAY = 2000;
    /** Defines the default duration in milliseconds for {@link #mLongPressRepeatDelay}. */
    public static final int DEFAULT_LONG_PRESS_REPEAT_DELAY = 80;



    /** The implementation of the {@link IExtraKeysView} that acts as a client for the {@link ExtraKeysView}. */
    protected IExtraKeysView mExtraKeysViewClient;

    /** The map for the {@link SpecialButton} and their {@link SpecialButtonState}. Defaults to
     * the one returned by {@link #getDefaultSpecialButtons(ExtraKeysView)}. */
    protected Map<SpecialButton, SpecialButtonState> mSpecialButtons;

    /** The keys for the {@link SpecialButton} added to {@link #mSpecialButtons}. This is automatically
     * set when the call to {@link #setSpecialButtons(Map)} is made. */
    protected Set<String> mSpecialButtonsKeys;


    /**
     * The list of keys for which auto repeat of key should be triggered if its extra keys button
     * is long pressed. This is done by calling {@link IExtraKeysView#onExtraKeyButtonClick(View, ExtraKeyButton, MaterialButton)}
     * every {@link #mLongPressRepeatDelay} seconds after {@link #mLongPressTimeout} has passed.
     * The default keys are defined by {@link ExtraKeysConstants#PRIMARY_REPETITIVE_KEYS}.
     */
    protected List<String> mRepetitiveKeys;


    /** The text color for the extra keys button. Defaults to {@link #DEFAULT_BUTTON_TEXT_COLOR}. */
    protected int mButtonTextColor;
    /** The text color for the extra keys button when its active.
     * Defaults to {@link #DEFAULT_BUTTON_ACTIVE_TEXT_COLOR}. */
    protected int mButtonActiveTextColor;
    /** The background color for the extra keys button. Defaults to {@link #DEFAULT_BUTTON_BACKGROUND_COLOR}. */
    protected int mButtonBackgroundColor;
    /** The background color for the extra keys button when its active. Defaults to
     * {@link #DEFAULT_BUTTON_ACTIVE_BACKGROUND_COLOR}. */
    protected int mButtonActiveBackgroundColor;

    /** Defines whether text for the extra keys button should be all capitalized automatically. */
    protected boolean mButtonTextAllCaps = true;


    /**
     * Defines the duration in milliseconds before a press turns into a long press. The default
     * duration used is the one returned by a call to {@link ViewConfiguration#getLongPressTimeout()}
     * which will return the system defined duration which can be changed in accessibility settings.
     * The duration must be in between {@link #MIN_LONG_PRESS_DURATION} and {@link #MAX_LONG_PRESS_DURATION},
     * otherwise {@link #FALLBACK_LONG_PRESS_DURATION} is used.
     */
    protected int mLongPressTimeout;

    /**
     * Defines the duration in milliseconds for the delay between trigger of each repeat of
     * {@link #mRepetitiveKeys}. The default value is defined by {@link #DEFAULT_LONG_PRESS_REPEAT_DELAY}.
     * The duration must be in between {@link #MIN_LONG_PRESS__REPEAT_DELAY} and
     * {@link #MAX_LONG_PRESS__REPEAT_DELAY}, otherwise {@link #DEFAULT_LONG_PRESS_REPEAT_DELAY} is used.
     */
    protected int mLongPressRepeatDelay;


    /** The popup window shown if {@link ExtraKeyButton#getPopup()} returns a {@code non-null} value
     * and a swipe up action is done on an extra key. */
    protected PopupWindow mPopupWindow;

    protected ScheduledExecutorService mScheduledExecutor;
    protected Handler mHandler;
    protected SpecialButtonsLongHoldRunnable mSpecialButtonsLongHoldRunnable;
    protected int mLongPressCount;


    public ExtraKeysView(Context context, AttributeSet attrs) {
        super(context, attrs);

        setRepetitiveKeys(ExtraKeysConstants.PRIMARY_REPETITIVE_KEYS);
        setSpecialButtons(getDefaultSpecialButtons(this));

        setButtonColors(
            ThemeUtils.getSystemAttrColor(context, ATTR_BUTTON_TEXT_COLOR, DEFAULT_BUTTON_TEXT_COLOR),
            ThemeUtils.getSystemAttrColor(context, ATTR_BUTTON_ACTIVE_TEXT_COLOR, DEFAULT_BUTTON_ACTIVE_TEXT_COLOR),
            ThemeUtils.getSystemAttrColor(context, ATTR_BUTTON_BACKGROUND_COLOR, DEFAULT_BUTTON_BACKGROUND_COLOR),
            ThemeUtils.getSystemAttrColor(context, ATTR_BUTTON_ACTIVE_BACKGROUND_COLOR, DEFAULT_BUTTON_ACTIVE_BACKGROUND_COLOR));

        setBackgroundColor(DEFAULT_TOOLBAR_BACKGROUND_COLOR);
        int padH = dpToPx(4f);
        int padV = dpToPx(4f);
        setPadding(padH, padV, padH, padV);

        setLongPressTimeout(ViewConfiguration.getLongPressTimeout());
        setLongPressRepeatDelay(DEFAULT_LONG_PRESS_REPEAT_DELAY);
    }

    private int dpToPx(float dp) {
        return Math.round(TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics()));
    }

    private float spToPx(float sp) {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, sp, getResources().getDisplayMetrics());
    }

    /**
     * Lightweight {@link MaterialButton} subclass that draws a subtle top-right popup hint label
     * when a key has a swipe-up popup action, without adding extra ViewGroup hierarchy.
     */
    private static final class ExtraKeyMaterialButton extends MaterialButton {
        @Nullable
        private String mPopupHint;
        private final TextPaint mHintPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final float mHintInsetEndPx;
        private final float mHintInsetTopPx;

        ExtraKeyMaterialButton(Context context) {
            super(context, null, android.R.attr.buttonBarButtonStyle);
            float density = context.getResources().getDisplayMetrics().density;
            mHintInsetEndPx = 5f * density;
            mHintInsetTopPx = 2.5f * density;
            mHintPaint.setTextAlign(Paint.Align.RIGHT);
            mHintPaint.setTextSize(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, 8.5f, context.getResources().getDisplayMetrics()));
            mHintPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            mHintPaint.setColor(DEFAULT_POPUP_HINT_TEXT_COLOR);
        }

        void setPopupHint(@Nullable String hint) {
            mPopupHint = hint;
            invalidate();
        }

        void setPopupHintColor(int color) {
            if (mHintPaint.getColor() != color) {
                mHintPaint.setColor(color);
                invalidate();
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (mPopupHint != null && !mPopupHint.isEmpty() && getWidth() > 0 && getHeight() > 0) {
                Paint.FontMetrics fm = mHintPaint.getFontMetrics();
                float baselineY = getScrollY() + mHintInsetTopPx - fm.top;
                float rightX = getScrollX() + getWidth() - mHintInsetEndPx;
                canvas.drawText(mPopupHint, rightX, baselineY, mHintPaint);
            }
        }
    }

    private boolean isSpecialButtonActive(@NonNull ExtraKeyButton buttonInfo) {
        if (isSpecialButton(buttonInfo)) {
            SpecialButtonState state = mSpecialButtons.get(SpecialButton.valueOf(buttonInfo.getKey()));
            return state != null && state.isActive;
        }
        ExtraKeyButton popup = buttonInfo.getPopup();
        if (popup != null && isSpecialButton(popup)) {
            SpecialButtonState popupState = mSpecialButtons.get(SpecialButton.valueOf(popup.getKey()));
            return popupState != null && popupState.isActive;
        }
        return false;
    }

    private void configureKeyCardButton(@NonNull MaterialButton button, boolean hasPopupHint, boolean compactRow) {
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setMinimumWidth(0);
        button.setMinimumHeight(0);
        button.setMaxLines(1);
        button.setIncludeFontPadding(false);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, compactRow ? 13.5f : 14.5f);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setCornerRadius(dpToPx(10f));
        button.setStrokeWidth(dpToPx(1f));
        button.setRippleColor(ColorStateList.valueOf(Color.TRANSPARENT));
        button.setPadding(dpToPx(2f), hasPopupHint ? dpToPx(5f) : 0, dpToPx(2f), 0);
    }

    /** Updates a key card's fill, border, text, and popup hint colors for its pressed/active state. */
    void updateButtonVisualState(@NonNull MaterialButton button, boolean isPressed, boolean isActive) {
        int bgColor;
        int borderColor;
        int textColor;
        int hintColor;

        if (isActive) {
            bgColor = mButtonActiveBackgroundColor;
            borderColor = mButtonActiveBackgroundColor;
            textColor = mButtonActiveTextColor;
            hintColor = DEFAULT_POPUP_HINT_ACTIVE_TEXT_COLOR;
        } else if (isPressed) {
            bgColor = DEFAULT_BUTTON_PRESSED_BACKGROUND_COLOR;
            borderColor = DEFAULT_BUTTON_PRESSED_BORDER_COLOR;
            textColor = mButtonTextColor;
            hintColor = DEFAULT_POPUP_HINT_TEXT_COLOR;
        } else {
            bgColor = mButtonBackgroundColor;
            borderColor = DEFAULT_BUTTON_BORDER_COLOR;
            textColor = mButtonTextColor;
            hintColor = DEFAULT_POPUP_HINT_TEXT_COLOR;
        }

        button.setBackgroundTintList(ColorStateList.valueOf(bgColor));
        button.setStrokeColor(ColorStateList.valueOf(borderColor));
        button.setTextColor(textColor);
        if (button instanceof ExtraKeyMaterialButton) {
            ((ExtraKeyMaterialButton) button).setPopupHintColor(hintColor);
        }
    }


    /** Get {@link #mExtraKeysViewClient}. */
    public IExtraKeysView getExtraKeysViewClient() {
        return mExtraKeysViewClient;
    }

    /** Set {@link #mExtraKeysViewClient}. */
    public void setExtraKeysViewClient(IExtraKeysView extraKeysViewClient) {
        mExtraKeysViewClient = extraKeysViewClient;
    }


    /** Get {@link #mRepetitiveKeys}. */
    public List<String> getRepetitiveKeys() {
        if (mRepetitiveKeys == null) return null;
        return mRepetitiveKeys.stream().map(String::new).collect(Collectors.toList());
    }

    /** Set {@link #mRepetitiveKeys}. Must not be {@code null}. */
    public void setRepetitiveKeys(@NonNull List<String> repetitiveKeys) {
        mRepetitiveKeys = repetitiveKeys;
    }


    /** Get {@link #mSpecialButtons}. */
    public Map<SpecialButton, SpecialButtonState> getSpecialButtons() {
        if (mSpecialButtons == null) return null;
        return mSpecialButtons.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /** Get {@link #mSpecialButtonsKeys}. */
    public Set<String> getSpecialButtonsKeys() {
        if (mSpecialButtonsKeys == null) return null;
        return mSpecialButtonsKeys.stream().map(String::new).collect(Collectors.toSet());
    }

    /** Set {@link #mSpecialButtonsKeys}. Must not be {@code null}. */
    public void setSpecialButtons(@NonNull Map<SpecialButton, SpecialButtonState> specialButtons) {
        mSpecialButtons = specialButtons;
        mSpecialButtonsKeys = this.mSpecialButtons.keySet().stream().map(SpecialButton::getKey).collect(Collectors.toSet());
    }


    /**
     * Set the {@link ExtraKeysView} button colors.
     *
     * @param buttonTextColor The value for {@link #mButtonTextColor}.
     * @param buttonActiveTextColor The value for {@link #mButtonActiveTextColor}.
     * @param buttonBackgroundColor The value for {@link #mButtonBackgroundColor}.
     * @param buttonActiveBackgroundColor The value for {@link #mButtonActiveBackgroundColor}.
     */
    public void setButtonColors(int buttonTextColor, int buttonActiveTextColor, int buttonBackgroundColor, int buttonActiveBackgroundColor) {
        mButtonTextColor = buttonTextColor;
        mButtonActiveTextColor = buttonActiveTextColor;
        mButtonBackgroundColor = buttonBackgroundColor;
        mButtonActiveBackgroundColor = buttonActiveBackgroundColor;
    }


    /** Get {@link #mButtonTextColor}. */
    public int getButtonTextColor() {
        return mButtonTextColor;
    }

    /** Set {@link #mButtonTextColor}. */
    public void setButtonTextColor(int buttonTextColor) {
        mButtonTextColor = buttonTextColor;
    }


    /** Get {@link #mButtonActiveTextColor}. */
    public int getButtonActiveTextColor() {
        return mButtonActiveTextColor;
    }

    /** Set {@link #mButtonActiveTextColor}. */
    public void setButtonActiveTextColor(int buttonActiveTextColor) {
        mButtonActiveTextColor = buttonActiveTextColor;
    }


    /** Get {@link #mButtonBackgroundColor}. */
    public int getButtonBackgroundColor() {
        return mButtonBackgroundColor;
    }

    /** Set {@link #mButtonBackgroundColor}. */
    public void setButtonBackgroundColor(int buttonBackgroundColor) {
        mButtonBackgroundColor = buttonBackgroundColor;
    }


    /** Get {@link #mButtonActiveBackgroundColor}. */
    public int getButtonActiveBackgroundColor() {
        return mButtonActiveBackgroundColor;
    }

    /** Set {@link #mButtonActiveBackgroundColor}. */
    public void setButtonActiveBackgroundColor(int buttonActiveBackgroundColor) {
        mButtonActiveBackgroundColor = buttonActiveBackgroundColor;
    }

    /** Set {@link #mButtonTextAllCaps}. */
    public void setButtonTextAllCaps(boolean buttonTextAllCaps) {
        mButtonTextAllCaps = buttonTextAllCaps;
    }


    /** Get {@link #mLongPressTimeout}. */
    public int getLongPressTimeout() {
        return mLongPressTimeout;
    }

    /** Set {@link #mLongPressTimeout}. */
    public void setLongPressTimeout(int longPressDuration) {
        if (longPressDuration >= MIN_LONG_PRESS_DURATION && longPressDuration <= MAX_LONG_PRESS_DURATION) {
            mLongPressTimeout = longPressDuration;
        } else {
            mLongPressTimeout = FALLBACK_LONG_PRESS_DURATION;
        }
    }

    /** Get {@link #mLongPressRepeatDelay}. */
    public int getLongPressRepeatDelay() {
        return mLongPressRepeatDelay;
    }

    /** Set {@link #mLongPressRepeatDelay}. */
    public void setLongPressRepeatDelay(int longPressRepeatDelay) {
        if (mLongPressRepeatDelay >= MIN_LONG_PRESS__REPEAT_DELAY && mLongPressRepeatDelay <= MAX_LONG_PRESS__REPEAT_DELAY) {
            mLongPressRepeatDelay = longPressRepeatDelay;
        } else {
            mLongPressRepeatDelay = DEFAULT_LONG_PRESS_REPEAT_DELAY;
        }
    }


    /** Get the default map that can be used for {@link #mSpecialButtons}. */
    @NonNull
    public Map<SpecialButton, SpecialButtonState> getDefaultSpecialButtons(ExtraKeysView extraKeysView) {
        return new HashMap<SpecialButton, SpecialButtonState>() {{
            put(SpecialButton.CTRL, new SpecialButtonState(extraKeysView));
            put(SpecialButton.ALT, new SpecialButtonState(extraKeysView));
            put(SpecialButton.SHIFT, new SpecialButtonState(extraKeysView));
            put(SpecialButton.FN, new SpecialButtonState(extraKeysView));
        }};
    }



    /**
     * Reload this instance of {@link ExtraKeysView} with the info passed in {@code extraKeysInfo}.
     *
     * @param extraKeysInfo The {@link ExtraKeysInfo} that defines the necessary info for the extra keys.
     * @param heightPx The height in pixels of the parent surrounding the {@link ExtraKeysView}. It must
     *                 be a single child.
     */
    @SuppressLint("ClickableViewAccessibility")
    public void reload(ExtraKeysInfo extraKeysInfo, float heightPx) {
        if (extraKeysInfo == null)
            return;

        for(SpecialButtonState state : mSpecialButtons.values())
            state.buttons = new ArrayList<>();

        removeAllViews();

        ExtraKeyButton[][] buttons = extraKeysInfo.getMatrix();

        setRowCount(buttons.length);
        setColumnCount(maximumLength(buttons));

        final boolean compactRow = buttons.length >= 2;
        final int marginH = dpToPx(4f);
        final int marginV = dpToPx(compactRow ? 3f : 4f);

        for (int row = 0; row < buttons.length; row++) {
            for (int col = 0; col < buttons[row].length; col++) {
                final ExtraKeyButton buttonInfo = buttons[row][col];

                MaterialButton button;
                if (isSpecialButton(buttonInfo)) {
                    button = createSpecialButton(buttonInfo.getKey(), true);
                    if (button == null) return;
                } else {
                    button = new ExtraKeyMaterialButton(getContext());
                    if (buttonInfo.getPopup() != null && isSpecialButton(buttonInfo.getPopup())) {
                        SpecialButtonState popupState = mSpecialButtons.get(SpecialButton.valueOf(buttonInfo.getPopup().getKey()));
                        if (popupState != null) {
                            popupState.setIsCreated(true);
                            popupState.buttons.add(button);
                        }
                    }
                }

                // One label per key: the popup hint overlay is intentionally not drawn.
                // Drawing the swipe-up popup's label in the button's corner made the
                // panel hard to read (two overlapping labels per key). Swipe-up and
                // long-press popups still work; they are just not advertised on the key.
                boolean hasPopupHint = false;

                button.setText(buttonInfo.getDisplay());
                button.setAllCaps(mButtonTextAllCaps);
                configureKeyCardButton(button, hasPopupHint, compactRow);
                updateButtonVisualState(button, false, isSpecialButtonActive(buttonInfo));

                button.setOnClickListener(view -> {
                    performExtraKeyButtonHapticFeedback(view, buttonInfo, button);
                    onAnyExtraKeyButtonClick(view, buttonInfo, button);
                });

                button.setOnTouchListener((view, event) -> {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            updateButtonVisualState(button, true, isSpecialButtonActive(buttonInfo));
                            // Start long press scheduled executors which will be stopped in next MotionEvent
                            startScheduledExecutors(view, buttonInfo, button);
                            return true;

                        case MotionEvent.ACTION_MOVE:
                            if (buttonInfo.getPopup() != null) {
                                // Show popup on swipe up
                                if (mPopupWindow == null && event.getY() < 0) {
                                    stopScheduledExecutors();
                                    updateButtonVisualState(button, false, isSpecialButtonActive(buttonInfo));
                                    showPopup(view, buttonInfo.getPopup());
                                }
                                if (mPopupWindow != null && event.getY() > 0) {
                                    updateButtonVisualState(button, true, isSpecialButtonActive(buttonInfo));
                                    dismissPopup();
                                }
                            }
                            return true;

                        case MotionEvent.ACTION_CANCEL:
                            updateButtonVisualState(button, false, isSpecialButtonActive(buttonInfo));
                            stopScheduledExecutors();
                            return true;

                        case MotionEvent.ACTION_UP:
                            updateButtonVisualState(button, false, isSpecialButtonActive(buttonInfo));
                            stopScheduledExecutors();
                            // If ACTION_UP up was not from a repetitive key or was with a key with a popup button
                            if (mLongPressCount == 0 || mPopupWindow != null) {
                                // Trigger popup button click if swipe up complete
                                if (mPopupWindow != null) {
                                    dismissPopup();
                                    if (buttonInfo.getPopup() != null) {
                                        performExtraKeyButtonHapticFeedback(view, buttonInfo.getPopup(), button);
                                        onAnyExtraKeyButtonClick(view, buttonInfo.getPopup(), button);
                                    }
                                } else {
                                    view.performClick();
                                }
                            }
                            return true;

                        default:
                            return true;
                    }
                });

                LayoutParams param = new GridLayout.LayoutParams();
                param.width = 0;
                if(Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP) {
                   param.height = (int)(heightPx + 0.5);
                } else {
                    param.height = 0;
                }
                param.setMargins(marginH, marginV, marginH, marginV);
                param.columnSpec = GridLayout.spec(col, GridLayout.FILL, 1.f);
                param.rowSpec = GridLayout.spec(row, GridLayout.FILL, 1.f);
                button.setLayoutParams(param);

                addView(button);
            }
        }
    }



    public void onExtraKeyButtonClick(View view, ExtraKeyButton buttonInfo, MaterialButton button) {
        if (mExtraKeysViewClient != null)
            mExtraKeysViewClient.onExtraKeyButtonClick(view, buttonInfo, button);
    }

    public void performExtraKeyButtonHapticFeedback(View view, ExtraKeyButton buttonInfo, MaterialButton button) {
        if (mExtraKeysViewClient != null) {
            // If client handled the feedback, then just return
            if (mExtraKeysViewClient.performExtraKeyButtonHapticFeedback(view, buttonInfo, button))
                return;
        }

        if (Settings.System.getInt(getContext().getContentResolver(),
            Settings.System.HAPTIC_FEEDBACK_ENABLED, 0) != 0) {

            if (Build.VERSION.SDK_INT >= 28) {
                button.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            } else {
                // Perform haptic feedback only if no total silence mode enabled.
                if (Settings.Global.getInt(getContext().getContentResolver(), "zen_mode", 0) != 2) {
                    button.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                }
            }
        }
    }



    public void onAnyExtraKeyButtonClick(View view, @NonNull ExtraKeyButton buttonInfo, MaterialButton button) {
        if (isSpecialButton(buttonInfo)) {
            if (mLongPressCount > 0) return;
            SpecialButtonState state = mSpecialButtons.get(SpecialButton.valueOf(buttonInfo.getKey()));
            if (state == null) return;

            // Toggle active state and disable lock state if new state is not active
            state.setIsActive(!state.isActive);
            if (!state.isActive)
                state.setIsLocked(false);
        } else {
            onExtraKeyButtonClick(view, buttonInfo, button);
        }
    }


    public void startScheduledExecutors(View view, ExtraKeyButton buttonInfo, MaterialButton button) {
        stopScheduledExecutors();
        mLongPressCount = 0;
        if (mRepetitiveKeys.contains(buttonInfo.getKey())) {
            // Auto repeat key if long pressed until ACTION_UP stops it by calling stopScheduledExecutors.
            // Currently, only one (last) repeat key can run at a time. Old ones are stopped.
            mScheduledExecutor = Executors.newSingleThreadScheduledExecutor();
            mScheduledExecutor.scheduleWithFixedDelay(() -> {
                mLongPressCount++;
                onExtraKeyButtonClick(view, buttonInfo, button);
            }, mLongPressTimeout, mLongPressRepeatDelay, TimeUnit.MILLISECONDS);
        } else if (isSpecialButton(buttonInfo)) {
            // Lock the key if long pressed by running mSpecialButtonsLongHoldRunnable after
            // waiting for mLongPressTimeout milliseconds. If user does not long press, then the
            // ACTION_UP triggered will cancel the runnable by calling stopScheduledExecutors before
            // it has a chance to run.
            SpecialButtonState state = mSpecialButtons.get(SpecialButton.valueOf(buttonInfo.getKey()));
            if (state == null) return;
            if (mHandler == null)
                mHandler = new Handler(Looper.getMainLooper());
            mSpecialButtonsLongHoldRunnable = new SpecialButtonsLongHoldRunnable(state);
            mHandler.postDelayed(mSpecialButtonsLongHoldRunnable, mLongPressTimeout);
        }
    }

    public void stopScheduledExecutors() {
        if (mScheduledExecutor != null) {
            mScheduledExecutor.shutdownNow();
            mScheduledExecutor = null;
        }

        if (mSpecialButtonsLongHoldRunnable != null && mHandler != null) {
            mHandler.removeCallbacks(mSpecialButtonsLongHoldRunnable);
            mSpecialButtonsLongHoldRunnable = null;
        }
    }

    public class SpecialButtonsLongHoldRunnable implements Runnable {
        public final SpecialButtonState mState;

        public SpecialButtonsLongHoldRunnable(SpecialButtonState state) {
            mState = state;
        }

        public void run() {
            // Toggle active and lock state
            mState.setIsLocked(!mState.isActive);
            mState.setIsActive(!mState.isActive);
            mLongPressCount++;
        }
    }



    void showPopup(View view, ExtraKeyButton extraButton) {
        int width = view.getMeasuredWidth();
        int height = view.getMeasuredHeight();
        MaterialButton button;
        boolean isActiveSpecial = false;
        if (isSpecialButton(extraButton)) {
            button = createSpecialButton(extraButton.getKey(), false);
            if (button == null) return;
            isActiveSpecial = isSpecialButtonActive(extraButton);
        } else {
            button = new ExtraKeyMaterialButton(getContext());
        }
        button.setText(extraButton.getDisplay());
        button.setAllCaps(mButtonTextAllCaps);
        configureKeyCardButton(button, false, false);
        button.setWidth(width);
        button.setHeight(height);
        if (isActiveSpecial) {
            updateButtonVisualState(button, false, true);
        } else {
            button.setBackgroundTintList(ColorStateList.valueOf(DEFAULT_BUTTON_PRESSED_BACKGROUND_COLOR));
            button.setStrokeColor(ColorStateList.valueOf(mButtonActiveBackgroundColor));
            button.setTextColor(mButtonTextColor);
        }
        mPopupWindow = new PopupWindow(this);
        mPopupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        mPopupWindow.setWidth(LayoutParams.WRAP_CONTENT);
        mPopupWindow.setHeight(LayoutParams.WRAP_CONTENT);
        mPopupWindow.setContentView(button);
        mPopupWindow.setOutsideTouchable(true);
        mPopupWindow.setFocusable(false);
        mPopupWindow.showAsDropDown(view, 0, -2 * height);
    }

    public void dismissPopup() {
        mPopupWindow.setContentView(null);
        mPopupWindow.dismiss();
        mPopupWindow = null;
    }



    /** Check whether a {@link ExtraKeyButton} is a {@link SpecialButton}. */
    public boolean isSpecialButton(ExtraKeyButton button) {
        return mSpecialButtonsKeys.contains(button.getKey());
    }

    /**
     * Read whether {@link SpecialButton} registered in {@link #mSpecialButtons} is active or not.
     *
     * @param specialButton The {@link SpecialButton} to read.
     * @param autoSetInActive Set to {@code true} if {@link SpecialButtonState#isActive} should be
     *                        set {@code false} if button is not locked.
     * @return Returns {@code null} if button does not exist in {@link #mSpecialButtons}. If button
     *         exists, then returns {@code true} if the button is created in {@link ExtraKeysView}
     *         and is active, otherwise {@code false}.
     */
    @Nullable
    public Boolean readSpecialButton(SpecialButton specialButton, boolean autoSetInActive) {
        SpecialButtonState state = mSpecialButtons.get(specialButton);
        if (state == null) return null;

        if (!state.isCreated || !state.isActive)
            return false;

        // Disable active state only if not locked
        if (autoSetInActive && !state.isLocked)
            state.setIsActive(false);

        return true;
    }

    public MaterialButton createSpecialButton(String buttonKey, boolean needUpdate) {
        SpecialButtonState state = mSpecialButtons.get(SpecialButton.valueOf(buttonKey));
        if (state == null) return null;
        state.setIsCreated(true);
        MaterialButton button = new ExtraKeyMaterialButton(getContext());
        updateButtonVisualState(button, false, state.isActive);
        if (needUpdate) {
            state.buttons.add(button);
        }
        return button;
    }



    /**
     * General util function to compute the longest column length in a matrix.
     */
    public static int maximumLength(Object[][] matrix) {
        int m = 0;
        for (Object[] row : matrix)
            m = Math.max(m, row.length);
        return m;
    }

}
