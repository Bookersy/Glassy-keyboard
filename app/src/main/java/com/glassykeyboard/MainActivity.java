package com.glassykeyboard;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.setGravity(Gravity.CENTER);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(24), dp(24), dp(24));

        TextView title = new TextView(this);
        title.setText("Glassy Keyboard");
        title.setTextColor(Color.WHITE);
        title.setTextSize(34f);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, fullWidthParams());

        TextView subtitle = new TextView(this);
        subtitle.setText("An OLED-black, rounded glass keyboard with autocorrect and next-word suggestions.");
        subtitle.setTextColor(0xCCFFFFFF);
        subtitle.setTextSize(16f);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subtitleParams = fullWidthParams();
        subtitleParams.topMargin = dp(10);
        root.addView(subtitle, subtitleParams);

        TextView enable = makeButton("1. Enable Glassy Keyboard");
        enable.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
            }
        });
        LinearLayout.LayoutParams enableParams = fullWidthParams();
        enableParams.topMargin = dp(34);
        root.addView(enable, enableParams);

        TextView choose = makeButton("2. Choose Default Keyboard");
        choose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showInputMethodPicker();
                }
            }
        });
        LinearLayout.LayoutParams chooseParams = fullWidthParams();
        chooseParams.topMargin = dp(14);
        root.addView(choose, chooseParams);

        TextView help = new TextView(this);
        help.setText("Android requires you to enable new keyboards in Settings before they can become the default input method.");
        help.setTextColor(0x99FFFFFF);
        help.setTextSize(13f);
        help.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams helpParams = fullWidthParams();
        helpParams.topMargin = dp(18);
        root.addView(help, helpParams);

        setContentView(root);
    }

    private TextView makeButton(String text) {
        TextView button = new TextView(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(17f);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(58));
        button.setBackground(glassDrawable());
        button.setClickable(true);
        button.setPadding(dp(18), dp(14), dp(18), dp(14));
        return button;
    }

    private LinearLayout.LayoutParams fullWidthParams() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private GradientDrawable glassDrawable() {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x44FFFFFF, 0x1BFFFFFF});
        drawable.setCornerRadius(dp(26));
        drawable.setStroke(dp(1), 0x66FFFFFF);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
