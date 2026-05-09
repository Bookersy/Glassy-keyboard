package com.glassykeyboard;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class GlassyKeyboardView extends LinearLayout {
    public interface Listener {
        void onKey(String key);

        void onSuggestion(String suggestion);

        void onGestureWord(String trace);

        void onClipboardToggle();

        void onClipboardItem(int index);

        void onSpaceCursorMove(int delta);
    }

    public static final String KEY_SHIFT = "{shift}";
    public static final String KEY_BACKSPACE = "{backspace}";
    public static final String KEY_ENTER = "{enter}";
    public static final String KEY_SPACE = "{space}";
    public static final String KEY_CLIPBOARD = "{clipboard}";

    private static final String KEY_NUMBERS = "{numbers}";
    private static final String KEY_SYMBOLS = "{symbols}";
    private static final String KEY_LETTERS = "{letters}";

    private static final int MODE_LETTERS = 0;
    private static final int MODE_NUMBERS = 1;
    private static final int MODE_SYMBOLS = 2;

    private static final int LONG_PRESS_MS = 360;
    private static final int REPEAT_MS = 65;

    private final Listener listener;
    private final LinearLayout suggestionRow;
    private final LinearLayout rowsContainer;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<TextView> letterKeys = new ArrayList<TextView>();
    private final List<TextView> allKeys = new ArrayList<TextView>();
    private final Map<String, String> longPressAlternates = new HashMap<String, String>();

    private boolean shifted;
    private int keyboardMode = MODE_LETTERS;
    private int currentImeOptions;
    private boolean clipboardMode;
    private List<String> clipboardLabels = Collections.emptyList();
    private List<String> lastSuggestions = Collections.emptyList();

    private TextView activeKey;
    private boolean slideActive;
    private boolean longPressConsumed;
    private boolean repeatActive;
    private boolean spaceTrackActive;
    private float downRawX;
    private float lastSpaceRawX;
    private final StringBuilder slideTrace = new StringBuilder();
    private final Rect hitRect = new Rect();

    public GlassyKeyboardView(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        setOrientation(VERTICAL);
        setPadding(dp(8), dp(8), dp(8), dp(32));
        setBackgroundColor(Color.BLACK);
        seedLongPressAlternates();

        suggestionRow = new LinearLayout(context);
        suggestionRow.setGravity(Gravity.CENTER);
        suggestionRow.setOrientation(HORIZONTAL);
        addView(suggestionRow, new LayoutParams(LayoutParams.MATCH_PARENT, dp(44)));

        rowsContainer = new LinearLayout(context);
        rowsContainer.setOrientation(VERTICAL);
        addView(rowsContainer, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        buildKeyboard();
        setSuggestions(Collections.<String>emptyList());
    }

    public void setSuggestions(List<String> suggestions) {
        lastSuggestions = suggestions == null ? Collections.<String>emptyList() : suggestions;
        if (!clipboardMode) {
            renderSuggestionRow();
        }
    }

    public void setClipboardLabels(List<String> labels) {
        clipboardLabels = labels == null ? Collections.<String>emptyList() : labels;
        if (clipboardMode) {
            renderClipboardRow();
        }
    }

    public void setClipboardMode(boolean enabled) {
        clipboardMode = enabled;
        if (clipboardMode) {
            renderClipboardRow();
        } else {
            renderSuggestionRow();
        }
    }

    public void setImeOptions(int imeOptions) {
        currentImeOptions = imeOptions;
        TextView enter = findViewWithTag(KEY_ENTER);
        if (enter != null) {
            enter.setText(labelFor(KEY_ENTER));
        }
    }

    private void buildKeyboard() {
        rowsContainer.removeAllViews();
        letterKeys.clear();
        allKeys.clear();

        if (keyboardMode == MODE_LETTERS) {
            addRow(new String[]{"q", "w", "e", "r", "t", "y", "u", "i", "o", "p"}, 1f);
            addRow(new String[]{"a", "s", "d", "f", "g", "h", "j", "k", "l"}, 1f);
            addRow(new String[]{KEY_SHIFT, "z", "x", "c", "v", "b", "n", "m", KEY_BACKSPACE}, 1.1f);
            addRow(new String[]{KEY_NUMBERS, ",", KEY_CLIPBOARD, KEY_SPACE, ".", KEY_ENTER}, 1f);
        } else if (keyboardMode == MODE_NUMBERS) {
            addRow(new String[]{"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"}, 1f);
            addRow(new String[]{"@", "#", "$", "_", "&", "-", "+", "(", ")"}, 1f);
            addRow(new String[]{KEY_SYMBOLS, "*", "\"", "'", ":", ";", "!", "?", KEY_BACKSPACE}, 1.1f);
            addRow(new String[]{KEY_LETTERS, ",", KEY_CLIPBOARD, KEY_SPACE, ".", KEY_ENTER}, 1f);
        } else {
            addRow(new String[]{"[", "]", "{", "}", "\\", "|", "~", "^", "`"}, 1f);
            addRow(new String[]{"<", ">", "=", "%", "/", ".", ",", "?", "!"}, 1f);
            addRow(new String[]{KEY_NUMBERS, "*", "\"", "'", ":", ";", "-", "+", KEY_BACKSPACE}, 1.1f);
            addRow(new String[]{KEY_LETTERS, ",", KEY_CLIPBOARD, KEY_SPACE, ".", KEY_ENTER}, 1f);
        }
        updateShiftState();
    }

    private void addRow(String[] keys, float defaultWeight) {
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.CENTER);
        row.setOrientation(HORIZONTAL);
        LayoutParams rowParams = new LayoutParams(LayoutParams.MATCH_PARENT, dp(56));
        rowParams.topMargin = dp(6);
        rowsContainer.addView(row, rowParams);

        for (String key : keys) {
            TextView keyView = makeKeyView(key);
            float weight = defaultWeight;
            if (KEY_SPACE.equals(key)) {
                weight = 4.7f;
            } else if (isWideKey(key)) {
                weight = 1.45f;
            }
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, weight);
            params.leftMargin = dp(3);
            params.rightMargin = dp(3);
            row.addView(keyView, params);
        }
    }

    private TextView makeKeyView(final String key) {
        final TextView view = new TextView(getContext());
        view.setTag(key);
        view.setGravity(Gravity.CENTER);
        view.setTextColor(isIconKey(key) ? 0xD5FFFFFF : Color.WHITE);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setTextSize(KEY_SPACE.equals(key) ? 15f : isIconKey(key) ? 22f : 18f);
        view.setText(labelFor(key));
        view.setBackground(glassDrawable(false));
        view.setIncludeFontPadding(false);
        view.setClickable(true);
        view.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return handleKeyTouch(view, key, event);
            }
        });
        if (isLetterKey(key)) {
            letterKeys.add(view);
        }
        allKeys.add(view);
        return view;
    }

    private boolean handleKeyTouch(final TextView view, final String key, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                activeKey = view;
                slideActive = false;
                longPressConsumed = false;
                repeatActive = false;
                spaceTrackActive = false;
                downRawX = event.getRawX();
                lastSpaceRawX = event.getRawX();
                slideTrace.setLength(0);
                view.setBackground(glassDrawable(true));
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                if (isLetterKey(key)) {
                    appendTraceKey(key);
                }
                startLongPressTimer(view, key);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (KEY_SPACE.equals(key) && (spaceTrackActive || Math.abs(event.getRawX() - downRawX) > dp(18))) {
                    spaceTrackActive = true;
                    moveCursorFromSpace(event.getRawX());
                    return true;
                }
                if (isLetterKey(key)) {
                    TextView hit = findLetterUnder(event.getRawX(), event.getRawY());
                    if (hit != null) {
                        String hitKey = String.valueOf(hit.getTag());
                        if (appendTraceKey(hitKey)) {
                            slideActive = true;
                            hit.setBackground(glassDrawable(true));
                        }
                    }
                }
                return true;
            case MotionEvent.ACTION_UP:
                cancelTimers();
                view.setBackground(glassDrawable(false));
                restoreUnpressedKeys();
                if (slideActive && slideTrace.length() > 1) {
                    listener.onGestureWord(slideTrace.toString());
                    return true;
                }
                if (!longPressConsumed && !repeatActive && !spaceTrackActive) {
                    tapKey(key);
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                cancelTimers();
                view.setBackground(glassDrawable(false));
                restoreUnpressedKeys();
                return true;
            default:
                return true;
        }
    }

    private void tapKey(String key) {
        if (KEY_SHIFT.equals(key)) {
            shifted = !shifted;
            updateShiftState();
        } else if (KEY_NUMBERS.equals(key)) {
            keyboardMode = MODE_NUMBERS;
            buildKeyboard();
        } else if (KEY_SYMBOLS.equals(key)) {
            keyboardMode = MODE_SYMBOLS;
            buildKeyboard();
        } else if (KEY_LETTERS.equals(key)) {
            keyboardMode = MODE_LETTERS;
            buildKeyboard();
        } else if (KEY_CLIPBOARD.equals(key)) {
            listener.onClipboardToggle();
        } else {
            listener.onKey(resolveOutputKey(key));
            if (shifted && isLetterKey(key)) {
                shifted = false;
                updateShiftState();
            }
        }
    }

    private void startLongPressTimer(final TextView view, final String key) {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (activeKey != view || slideActive) {
                    return;
                }
                if (KEY_BACKSPACE.equals(key)) {
                    repeatActive = true;
                    repeatBackspace();
                } else if (KEY_SPACE.equals(key)) {
                    longPressConsumed = true;
                    spaceTrackActive = true;
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                } else {
                    String alternate = longPressAlternates.get(key);
                    if (alternate != null) {
                        longPressConsumed = true;
                        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                        listener.onKey(alternate);
                    }
                }
            }
        }, LONG_PRESS_MS);
    }

    private void repeatBackspace() {
        if (!repeatActive) {
            return;
        }
        listener.onKey(KEY_BACKSPACE);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                repeatBackspace();
            }
        }, REPEAT_MS);
    }

    private void cancelTimers() {
        activeKey = null;
        handler.removeCallbacksAndMessages(null);
    }

    private void moveCursorFromSpace(float rawX) {
        int step = dp(18);
        int delta = (int) ((rawX - lastSpaceRawX) / step);
        if (delta != 0) {
            listener.onSpaceCursorMove(delta);
            lastSpaceRawX += delta * step;
        }
    }

    private boolean appendTraceKey(String key) {
        String output = shifted ? key.toUpperCase(Locale.US) : key;
        if (slideTrace.length() == 0 || slideTrace.charAt(slideTrace.length() - 1) != output.charAt(0)) {
            slideTrace.append(output);
            return true;
        }
        return false;
    }

    private TextView findLetterUnder(float rawX, float rawY) {
        for (TextView key : letterKeys) {
            key.getGlobalVisibleRect(hitRect);
            if (hitRect.contains((int) rawX, (int) rawY)) {
                return key;
            }
        }
        return null;
    }

    private void restoreUnpressedKeys() {
        for (TextView key : allKeys) {
            key.setBackground(glassDrawable(false));
        }
    }

    private void renderSuggestionRow() {
        suggestionRow.removeAllViews();
        int count = Math.min(3, lastSuggestions.size());
        if (count == 0) {
            addSuggestion("glassy", false);
            addSuggestion("keyboard", false);
            addSuggestion("hello", false);
            return;
        }
        for (int i = 0; i < count; i++) {
            addSuggestion(lastSuggestions.get(i), true);
        }
    }

    private void renderClipboardRow() {
        suggestionRow.removeAllViews();
        int count = Math.min(3, clipboardLabels.size());
        if (count == 0) {
            addClipboardChip("Clipboard empty", -1, false);
            addClipboardChip("Copy text or image", -1, false);
            addClipboardChip("Tap clip key", -1, false);
            return;
        }
        for (int i = 0; i < count; i++) {
            addClipboardChip(clipboardLabels.get(i), i, true);
        }
    }

    private void addSuggestion(final String suggestion, boolean enabled) {
        TextView chip = chipView(suggestion, enabled);
        if (enabled) {
            chip.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    listener.onSuggestion(suggestion);
                }
            });
        }
        addChip(chip);
    }

    private void addClipboardChip(String label, final int index, boolean enabled) {
        TextView chip = chipView(label, enabled);
        if (enabled) {
            chip.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    listener.onClipboardItem(index);
                }
            });
        }
        addChip(chip);
    }

    private TextView chipView(String text, boolean enabled) {
        TextView chip = new TextView(getContext());
        chip.setGravity(Gravity.CENTER);
        chip.setSingleLine(true);
        chip.setText(text);
        chip.setTextColor(enabled ? Color.WHITE : 0x99FFFFFF);
        chip.setTextSize(15f);
        chip.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        chip.setBackground(glassDrawable(false));
        chip.setIncludeFontPadding(false);
        chip.setEnabled(enabled);
        return chip;
    }

    private void addChip(TextView chip) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f);
        params.leftMargin = dp(4);
        params.rightMargin = dp(4);
        params.topMargin = dp(4);
        params.bottomMargin = dp(4);
        suggestionRow.addView(chip, params);
    }

    private void updateShiftState() {
        for (TextView key : letterKeys) {
            String text = String.valueOf(key.getTag());
            key.setText(shifted ? text.toUpperCase(Locale.US) : text);
        }
        TextView shift = findViewWithTag(KEY_SHIFT);
        if (shift != null) {
            shift.setAlpha(shifted ? 1f : 0.82f);
        }
    }

    private String resolveOutputKey(String key) {
        if (KEY_BACKSPACE.equals(key) || KEY_ENTER.equals(key)) {
            return key;
        }
        if (KEY_SPACE.equals(key)) {
            return " ";
        }
        if (isLetterKey(key) && shifted) {
            return key.toUpperCase(Locale.US);
        }
        return key;
    }

    private String labelFor(String key) {
        if (KEY_SHIFT.equals(key)) {
            return "\u21e7";
        }
        if (KEY_BACKSPACE.equals(key)) {
            return "\u232b";
        }
        if (KEY_ENTER.equals(key)) {
            return enterLabel();
        }
        if (KEY_SPACE.equals(key)) {
            return "space";
        }
        if (KEY_CLIPBOARD.equals(key)) {
            return "\u2398";
        }
        if (KEY_NUMBERS.equals(key)) {
            return "123";
        }
        if (KEY_SYMBOLS.equals(key)) {
            return "#+=";
        }
        if (KEY_LETTERS.equals(key)) {
            return "ABC";
        }
        return key;
    }

    private String enterLabel() {
        int action = currentImeOptions & EditorInfo.IME_MASK_ACTION;
        if (action == EditorInfo.IME_ACTION_SEARCH) {
            return "\u2315";
        }
        if (action == EditorInfo.IME_ACTION_SEND) {
            return "\u27a4";
        }
        if (action == EditorInfo.IME_ACTION_GO || action == EditorInfo.IME_ACTION_DONE) {
            return "\u2713";
        }
        return "\u21b5";
    }

    private GradientDrawable glassDrawable(boolean pressed) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                pressed
                        ? new int[]{0xAAFFFFFF, 0x553A3A44, 0x22202028}
                        : new int[]{0x55FFFFFF, 0x221A1A22, 0x0DFFFFFF});
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(dp(24));
        drawable.setStroke(dp(1), pressed ? 0xD8FFFFFF : 0x66FFFFFF);
        return drawable;
    }

    private void seedLongPressAlternates() {
        longPressAlternates.put("q", "1");
        longPressAlternates.put("w", "2");
        longPressAlternates.put("e", "3");
        longPressAlternates.put("r", "4");
        longPressAlternates.put("t", "5");
        longPressAlternates.put("y", "6");
        longPressAlternates.put("u", "7");
        longPressAlternates.put("i", "8");
        longPressAlternates.put("o", "9");
        longPressAlternates.put("p", "0");
        longPressAlternates.put("a", "@");
        longPressAlternates.put("s", "#");
        longPressAlternates.put("d", "$");
        longPressAlternates.put("f", "&");
        longPressAlternates.put("g", "-");
        longPressAlternates.put("h", "+");
        longPressAlternates.put("j", "(");
        longPressAlternates.put("k", ")");
        longPressAlternates.put("l", "/");
        longPressAlternates.put(",", "!");
        longPressAlternates.put(".", "?");
        longPressAlternates.put("'", "\"");
        longPressAlternates.put("-", "_");
    }

    private static boolean isLetterKey(String key) {
        return key.length() == 1 && key.charAt(0) >= 'a' && key.charAt(0) <= 'z';
    }

    private static boolean isIconKey(String key) {
        return KEY_SHIFT.equals(key)
                || KEY_BACKSPACE.equals(key)
                || KEY_ENTER.equals(key)
                || KEY_CLIPBOARD.equals(key);
    }

    private static boolean isWideKey(String key) {
        return KEY_SHIFT.equals(key)
                || KEY_BACKSPACE.equals(key)
                || KEY_ENTER.equals(key)
                || KEY_NUMBERS.equals(key)
                || KEY_SYMBOLS.equals(key)
                || KEY_LETTERS.equals(key)
                || KEY_CLIPBOARD.equals(key);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
