package com.glassykeyboard;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class GlassyKeyboardView extends LinearLayout {
    public interface Listener {
        void onKey(String key);

        void onSuggestion(String suggestion);
    }

    private static final String KEY_SHIFT = "{shift}";
    private static final String KEY_BACKSPACE = "{backspace}";
    private static final String KEY_ENTER = "{enter}";
    private static final String KEY_SPACE = "{space}";

    private final Listener listener;
    private final LinearLayout suggestionRow;
    private final List<TextView> letterKeys = new ArrayList<>();
    private boolean shifted;

    public GlassyKeyboardView(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        setOrientation(VERTICAL);
        setPadding(dp(8), dp(8), dp(8), dp(10));
        setBackgroundColor(Color.BLACK);

        suggestionRow = new LinearLayout(context);
        suggestionRow.setGravity(Gravity.CENTER);
        suggestionRow.setOrientation(HORIZONTAL);
        addView(suggestionRow, new LayoutParams(LayoutParams.MATCH_PARENT, dp(44)));

        addRow(new String[]{"q", "w", "e", "r", "t", "y", "u", "i", "o", "p"}, 1f);
        addRow(new String[]{"a", "s", "d", "f", "g", "h", "j", "k", "l"}, 1f);
        addRow(new String[]{KEY_SHIFT, "z", "x", "c", "v", "b", "n", "m", KEY_BACKSPACE}, 1.1f);
        addRow(new String[]{"123", ",", KEY_SPACE, ".", KEY_ENTER}, 1f);
        setSuggestions(Collections.<String>emptyList());
    }

    public void setSuggestions(List<String> suggestions) {
        suggestionRow.removeAllViews();
        int count = Math.min(3, suggestions.size());
        if (count == 0) {
            addSuggestion("glassy", false);
            addSuggestion("keyboard", false);
            addSuggestion("hello", false);
            return;
        }
        for (int i = 0; i < count; i++) {
            addSuggestion(suggestions.get(i), true);
        }
    }

    public void setImeOptions(int imeOptions) {
        TextView enter = findViewWithTag(KEY_ENTER);
        if (enter == null) {
            return;
        }
        int action = imeOptions & EditorInfo.IME_MASK_ACTION;
        if (action == EditorInfo.IME_ACTION_SEARCH) {
            enter.setText("search");
        } else if (action == EditorInfo.IME_ACTION_SEND) {
            enter.setText("send");
        } else if (action == EditorInfo.IME_ACTION_GO) {
            enter.setText("go");
        } else if (action == EditorInfo.IME_ACTION_DONE) {
            enter.setText("done");
        } else {
            enter.setText("return");
        }
    }

    private void addRow(String[] keys, float defaultWeight) {
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.CENTER);
        row.setOrientation(HORIZONTAL);
        LayoutParams rowParams = new LayoutParams(LayoutParams.MATCH_PARENT, dp(56));
        rowParams.topMargin = dp(6);
        addView(row, rowParams);

        for (String key : keys) {
            TextView keyView = makeKeyView(key);
            float weight = defaultWeight;
            if (KEY_SPACE.equals(key)) {
                weight = 4.8f;
            } else if (KEY_SHIFT.equals(key) || KEY_BACKSPACE.equals(key) || KEY_ENTER.equals(key) || "123".equals(key)) {
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
        view.setTextColor(Color.WHITE);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setTextSize(KEY_SPACE.equals(key) ? 15f : 18f);
        view.setText(labelFor(key));
        view.setBackground(glassDrawable(false));
        view.setIncludeFontPadding(false);
        view.setClickable(true);
        view.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    v.setBackground(glassDrawable(true));
                } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    v.setBackground(glassDrawable(false));
                }
                return false;
            }
        });
        view.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                if (KEY_SHIFT.equals(key)) {
                    shifted = !shifted;
                    updateShiftState();
                } else {
                    listener.onKey(resolveOutputKey(key));
                    if (shifted && isLetterKey(key)) {
                        shifted = false;
                        updateShiftState();
                    }
                }
            }
        });
        if (isLetterKey(key)) {
            letterKeys.add(view);
        }
        return view;
    }

    private void addSuggestion(final String suggestion, boolean enabled) {
        TextView chip = new TextView(getContext());
        chip.setGravity(Gravity.CENTER);
        chip.setSingleLine(true);
        chip.setText(suggestion);
        chip.setTextColor(enabled ? Color.WHITE : 0x99FFFFFF);
        chip.setTextSize(15f);
        chip.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        chip.setBackground(glassDrawable(false));
        chip.setIncludeFontPadding(false);
        chip.setEnabled(enabled);
        if (enabled) {
            chip.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    listener.onSuggestion(suggestion);
                }
            });
        }
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
            shift.setText(shifted ? "SHIFT" : "shift");
        }
    }

    private String resolveOutputKey(String key) {
        if (KEY_BACKSPACE.equals(key)) {
            return KEY_BACKSPACE;
        }
        if (KEY_ENTER.equals(key)) {
            return KEY_ENTER;
        }
        if (KEY_SPACE.equals(key)) {
            return " ";
        }
        if ("123".equals(key)) {
            return "?";
        }
        if (isLetterKey(key) && shifted) {
            return key.toUpperCase(Locale.US);
        }
        return key;
    }

    private static boolean isLetterKey(String key) {
        return key.length() == 1 && key.charAt(0) >= 'a' && key.charAt(0) <= 'z';
    }

    private static String labelFor(String key) {
        if (KEY_SHIFT.equals(key)) {
            return "shift";
        }
        if (KEY_BACKSPACE.equals(key)) {
            return "del";
        }
        if (KEY_ENTER.equals(key)) {
            return "return";
        }
        if (KEY_SPACE.equals(key)) {
            return "space";
        }
        return key;
    }

    private GradientDrawable glassDrawable(boolean pressed) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                pressed
                        ? new int[]{0x99FFFFFF, 0x553A3A44}
                        : new int[]{0x42FFFFFF, 0x1F101018});
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(dp(20));
        drawable.setStroke(dp(1), pressed ? 0xCCFFFFFF : 0x55FFFFFF);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            drawable.setPadding(dp(1), dp(1), dp(1), dp(1));
        }
        return drawable;
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }
}
