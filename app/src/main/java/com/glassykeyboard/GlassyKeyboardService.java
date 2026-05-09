package com.glassykeyboard;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.inputmethodservice.InputMethodService;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputContentInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class GlassyKeyboardService extends InputMethodService implements GlassyKeyboardView.Listener {
    private static final String KEY_BACKSPACE = GlassyKeyboardView.KEY_BACKSPACE;
    private static final String KEY_ENTER = GlassyKeyboardView.KEY_ENTER;

    private static final int MAX_CLIPBOARD_ITEMS = 6;

    private static final List<String> DICTIONARY = Arrays.asList(
            "about", "after", "again", "always", "am", "and", "android", "app", "are",
            "awesome", "because", "before", "best", "better", "call", "can", "character",
            "chat", "check", "clipboard", "coming", "cool", "day", "default", "delete",
            "device", "done", "enter", "feel", "fine", "for", "friend", "from", "glass",
            "glassy", "going", "good", "great", "happy", "have", "hello", "help", "history",
            "home", "how", "image", "keyboard", "know", "later", "like", "love", "make",
            "message", "more", "morning", "move", "need", "next", "night", "now", "oled",
            "okay", "paste", "phone", "please", "ready", "return", "right", "see", "send",
            "shift", "slide", "soon", "sounds", "spacebar", "spelled", "spelt", "sure",
            "thanks", "that", "the", "them", "then", "there", "think", "this", "today",
            "tomorrow", "vibrate", "want", "what", "when", "where", "with", "word", "work",
            "would", "yes", "you", "your");

    private static final Map<String, String> AUTOCORRECT = new HashMap<String, String>();
    private static final Map<String, List<String>> NEXT_WORDS = new HashMap<String, List<String>>();

    static {
        addCorrection("teh", "the");
        addCorrection("adn", "and");
        addCorrection("dont", "don't");
        addCorrection("cant", "can't");
        addCorrection("wont", "won't");
        addCorrection("im", "I'm");
        addCorrection("ive", "I've");
        addCorrection("ill", "I'll");
        addCorrection("youre", "you're");
        addCorrection("thats", "that's");
        addCorrection("whats", "what's");
        addCorrection("recieve", "receive");
        addCorrection("definately", "definitely");
        addCorrection("becuase", "because");
        addCorrection("tomorow", "tomorrow");
        addCorrection("wierd", "weird");
        addCorrection("adress", "address");
        addCorrection("keybaord", "keyboard");
        addCorrection("keybord", "keyboard");
        addCorrection("glasy", "glassy");
        addCorrection("yor", "your");
        addCorrection("thm", "them");
        addCorrection("charcter", "character");
        addCorrection("speling", "spelling");
        addCorrection("speach", "speech");

        addNext("i", "am", "can", "will");
        addNext("im", "ready", "here", "good");
        addNext("i'm", "ready", "here", "good");
        addNext("you", "can", "are", "will");
        addNext("your", "keyboard", "device", "clipboard");
        addNext("the", "keyboard", "clipboard", "next");
        addNext("glassy", "keyboard", "look", "style");
        addNext("glass", "keyboard", "effect", "style");
        addNext("keyboard", "app", "is", "settings");
        addNext("good", "morning", "night", "idea");
        addNext("see", "you", "this", "that");
        addNext("thank", "you", "them", "that");
        addNext("thanks", "for", "again", "so");
        addNext("what", "are", "is", "do");
        addNext("where", "are", "is", "can");
        addNext("when", "are", "is", "can");
        addNext("please", "send", "call", "check");
    }

    private final StringBuilder currentWord = new StringBuilder();
    private final List<ClipboardEntry> clipboardHistory = new ArrayList<ClipboardEntry>();

    private GlassyKeyboardView keyboardView;
    private String lastCommittedWord = "";
    private int currentImeOptions;
    private EditorInfo currentEditorInfo;
    private Vibrator vibrator;
    private ClipboardManager clipboardManager;
    private boolean clipboardMode;

    private final ClipboardManager.OnPrimaryClipChangedListener clipListener =
            new ClipboardManager.OnPrimaryClipChangedListener() {
                @Override
                public void onPrimaryClipChanged() {
                    capturePrimaryClip();
                    updateClipboardView();
                }
            };

    @Override
    public void onCreate() {
        super.onCreate();
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboardManager != null) {
            clipboardManager.addPrimaryClipChangedListener(clipListener);
            capturePrimaryClip();
        }
    }

    @Override
    public void onDestroy() {
        if (clipboardManager != null) {
            clipboardManager.removePrimaryClipChangedListener(clipListener);
        }
        super.onDestroy();
    }

    @Override
    public android.view.View onCreateInputView() {
        keyboardView = new GlassyKeyboardView(this, this);
        keyboardView.setImeOptions(currentImeOptions);
        keyboardView.setClipboardLabels(clipboardLabels());
        keyboardView.setClipboardMode(clipboardMode);
        updateSuggestions();
        return keyboardView;
    }

    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        currentWord.setLength(0);
        currentEditorInfo = attribute;
        currentImeOptions = attribute != null ? attribute.imeOptions : 0;
        clipboardMode = false;
        updateSuggestions();
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        currentEditorInfo = info;
        currentImeOptions = info != null ? info.imeOptions : 0;
        capturePrimaryClip();
        if (keyboardView != null) {
            keyboardView.setImeOptions(currentImeOptions);
            keyboardView.setClipboardLabels(clipboardLabels());
            keyboardView.setClipboardMode(clipboardMode);
        }
        updateSuggestions();
    }

    @Override
    public void onUpdateSelection(
            int oldSelStart,
            int oldSelEnd,
            int newSelStart,
            int newSelEnd,
            int candidatesStart,
            int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd);
        if (newSelStart != oldSelStart + 1 || newSelStart != newSelEnd) {
            currentWord.setLength(0);
            updateSuggestions();
        }
    }

    @Override
    public void onKey(String key) {
        vibrateKey();
        InputConnection connection = getCurrentInputConnection();
        if (connection == null) {
            return;
        }

        if (KEY_BACKSPACE.equals(key)) {
            handleBackspace(connection);
        } else if (KEY_ENTER.equals(key)) {
            handleEnter(connection);
        } else if (" ".equals(key)) {
            finishCurrentWord(connection, " ");
        } else if (isWordTerminator(key)) {
            finishCurrentWord(connection, key);
        } else if (isWordCharacter(key)) {
            connection.commitText(key, 1);
            currentWord.append(key);
        } else {
            connection.commitText(key, 1);
            currentWord.setLength(0);
        }
        updateSuggestions();
    }

    @Override
    public void onSuggestion(String suggestion) {
        vibrateKey();
        commitSuggestion(suggestion);
    }

    @Override
    public void onGestureWord(String trace) {
        vibrateKey();
        String word = bestGestureWord(trace);
        if (word.length() == 0) {
            return;
        }
        InputConnection connection = getCurrentInputConnection();
        if (connection == null) {
            return;
        }
        if (currentWord.length() > 0) {
            connection.deleteSurroundingText(currentWord.length(), 0);
        }
        connection.commitText(matchCapitalization(word, trace) + " ", 1);
        lastCommittedWord = normalizeWord(word);
        currentWord.setLength(0);
        updateSuggestions();
    }

    @Override
    public void onClipboardToggle() {
        vibrateKey();
        capturePrimaryClip();
        clipboardMode = !clipboardMode;
        if (keyboardView != null) {
            keyboardView.setClipboardLabels(clipboardLabels());
            keyboardView.setClipboardMode(clipboardMode);
        }
    }

    @Override
    public void onClipboardItem(int index) {
        vibrateKey();
        if (index < 0 || index >= clipboardHistory.size()) {
            return;
        }
        InputConnection connection = getCurrentInputConnection();
        if (connection == null) {
            return;
        }

        ClipboardEntry entry = clipboardHistory.get(index);
        if (entry.text != null) {
            finishCurrentWord(connection, "");
            connection.commitText(entry.text, 1);
        } else if (entry.uri != null && !pasteImage(connection, entry)) {
            connection.commitText("[image paste not supported here]", 1);
        }
        currentWord.setLength(0);
        updateSuggestions();
    }

    @Override
    public void onSpaceCursorMove(int delta) {
        InputConnection connection = getCurrentInputConnection();
        if (connection == null || delta == 0) {
            return;
        }
        int keyCode = delta > 0 ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT;
        int count = Math.min(12, Math.abs(delta));
        for (int i = 0; i < count; i++) {
            connection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
            connection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
        }
        currentWord.setLength(0);
        updateSuggestions();
    }

    private void commitSuggestion(String suggestion) {
        InputConnection connection = getCurrentInputConnection();
        if (connection == null || suggestion == null || suggestion.length() == 0) {
            return;
        }

        String typed = currentWord.toString();
        if (currentWord.length() > 0) {
            connection.deleteSurroundingText(currentWord.length(), 0);
        }
        connection.commitText(matchCapitalization(suggestion, typed) + " ", 1);
        lastCommittedWord = normalizeWord(suggestion);
        currentWord.setLength(0);
        updateSuggestions();
    }

    private void handleBackspace(InputConnection connection) {
        connection.deleteSurroundingText(1, 0);
        if (currentWord.length() > 0) {
            currentWord.deleteCharAt(currentWord.length() - 1);
        } else {
            lastCommittedWord = "";
        }
    }

    private void handleEnter(InputConnection connection) {
        int action = currentImeOptions & EditorInfo.IME_MASK_ACTION;
        if (action == EditorInfo.IME_ACTION_GO
                || action == EditorInfo.IME_ACTION_SEARCH
                || action == EditorInfo.IME_ACTION_SEND
                || action == EditorInfo.IME_ACTION_DONE) {
            connection.performEditorAction(action);
        } else {
            finishCurrentWord(connection, "\n");
        }
    }

    private void finishCurrentWord(InputConnection connection, String suffix) {
        if (currentWord.length() > 0) {
            String typed = currentWord.toString();
            String corrected = correctionFor(typed);
            if (!typed.equals(corrected)) {
                connection.deleteSurroundingText(typed.length(), 0);
                connection.commitText(corrected + suffix, 1);
            } else {
                connection.commitText(suffix, 1);
            }
            lastCommittedWord = normalizeWord(corrected);
            currentWord.setLength(0);
        } else if (suffix.length() > 0) {
            connection.commitText(suffix, 1);
            if ("\n".equals(suffix)) {
                lastCommittedWord = "";
            }
        }
    }

    private void updateSuggestions() {
        if (keyboardView == null) {
            return;
        }
        keyboardView.setSuggestions(suggestionsForCurrentState());
    }

    private List<String> suggestionsForCurrentState() {
        Set<String> suggestions = new LinkedHashSet<String>();
        String typed = currentWord.toString();
        String normalizedTyped = normalizeWord(typed);

        if (normalizedTyped.length() > 0) {
            String corrected = correctionFor(typed);
            if (!normalizeWord(corrected).equals(normalizedTyped)) {
                suggestions.add(corrected);
            }
            for (String word : DICTIONARY) {
                if (word.startsWith(normalizedTyped) && !word.equals(normalizedTyped)) {
                    suggestions.add(matchCapitalization(word, typed));
                }
                if (suggestions.size() >= 3) {
                    break;
                }
            }
        } else {
            List<String> next = NEXT_WORDS.get(lastCommittedWord);
            if (next != null) {
                suggestions.addAll(next);
            }
            if (suggestions.size() < 3) {
                suggestions.add("the");
                suggestions.add("you");
                suggestions.add("glassy");
            }
        }

        return new ArrayList<String>(suggestions);
    }

    private void capturePrimaryClip() {
        if (clipboardManager == null || !clipboardManager.hasPrimaryClip()) {
            return;
        }
        ClipData data = clipboardManager.getPrimaryClip();
        if (data == null) {
            return;
        }
        ClipDescription description = data.getDescription();
        for (int i = 0; i < data.getItemCount(); i++) {
            ClipData.Item item = data.getItemAt(i);
            Uri uri = item.getUri();
            String mime = imageMimeType(description, uri);
            if (uri != null && mime != null) {
                addClipboardEntry(ClipboardEntry.image(uri, mime));
                continue;
            }
            CharSequence text = item.coerceToText(this);
            if (text != null && text.length() > 0) {
                addClipboardEntry(ClipboardEntry.text(text.toString()));
            }
        }
    }

    private String imageMimeType(ClipDescription description, Uri uri) {
        if (description != null) {
            for (int i = 0; i < description.getMimeTypeCount(); i++) {
                String mime = description.getMimeType(i);
                if (mime != null && mime.startsWith("image/")) {
                    return mime;
                }
            }
        }
        if (uri != null) {
            String mime = getContentResolver().getType(uri);
            if (mime != null && mime.startsWith("image/")) {
                return mime;
            }
        }
        return null;
    }

    private void addClipboardEntry(ClipboardEntry entry) {
        for (int i = 0; i < clipboardHistory.size(); i++) {
            if (clipboardHistory.get(i).sameContent(entry)) {
                clipboardHistory.remove(i);
                break;
            }
        }
        clipboardHistory.add(0, entry);
        while (clipboardHistory.size() > MAX_CLIPBOARD_ITEMS) {
            clipboardHistory.remove(clipboardHistory.size() - 1);
        }
    }

    private void updateClipboardView() {
        if (keyboardView != null) {
            keyboardView.setClipboardLabels(clipboardLabels());
        }
    }

    private List<String> clipboardLabels() {
        List<String> labels = new ArrayList<String>();
        for (ClipboardEntry entry : clipboardHistory) {
            labels.add(entry.label());
        }
        return labels;
    }

    private boolean pasteImage(InputConnection connection, ClipboardEntry entry) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) {
            return false;
        }
        if (!editorAcceptsMime(entry.mimeType)) {
            return false;
        }
        ClipDescription description = new ClipDescription("Glassy Keyboard image", new String[]{entry.mimeType});
        InputContentInfo contentInfo = new InputContentInfo(entry.uri, description, null);
        return connection.commitContent(
                contentInfo,
                InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION,
                null);
    }

    private boolean editorAcceptsMime(String mimeType) {
        if (currentEditorInfo == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) {
            return false;
        }
        String[] supported = currentEditorInfo.contentMimeTypes;
        if (supported == null || supported.length == 0) {
            return false;
        }
        for (String mime : supported) {
            if (ClipDescription.compareMimeTypes(mimeType, mime)) {
                return true;
            }
        }
        return false;
    }

    private void vibrateKey() {
        if (vibrator == null || !vibrator.hasVibrator()) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(10, 80));
        } else {
            vibrator.vibrate(10);
        }
    }

    private static String correctionFor(String typed) {
        String normalized = normalizeWord(typed);
        String corrected = AUTOCORRECT.get(normalized);
        if (corrected == null) {
            corrected = bestAutocorrect(normalized);
        }
        return matchCapitalization(corrected, typed);
    }

    private static String bestAutocorrect(String normalized) {
        if (normalized.length() < 3) {
            return normalized;
        }
        String best = normalized;
        int bestDistance = Integer.MAX_VALUE;
        for (String word : DICTIONARY) {
            if (Math.abs(word.length() - normalized.length()) > 2) {
                continue;
            }
            int distance = editDistance(normalized, word);
            int allowed = normalized.length() <= 4 ? 1 : 2;
            if (distance <= allowed && distance < bestDistance) {
                best = word;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static String bestGestureWord(String trace) {
        String normalized = compressRepeated(normalizeWord(trace));
        if (normalized.length() == 0) {
            return "";
        }
        String best = "";
        int bestScore = Integer.MAX_VALUE;
        for (String word : DICTIONARY) {
            int score = gestureScore(normalized, word);
            if (score < bestScore) {
                best = word;
                bestScore = score;
            }
        }
        return bestScore <= 6 ? best : normalized;
    }

    private static int gestureScore(String trace, String word) {
        if (trace.length() == 0 || word.length() == 0 || trace.charAt(0) != word.charAt(0)) {
            return 1000;
        }
        int traceIndex = 0;
        int matches = 0;
        for (int i = 0; i < word.length() && traceIndex < trace.length(); i++) {
            if (word.charAt(i) == trace.charAt(traceIndex)) {
                matches++;
                traceIndex++;
            }
        }
        int missedTrace = trace.length() - traceIndex;
        int missedWord = word.length() - matches;
        int endingPenalty = trace.charAt(trace.length() - 1) == word.charAt(word.length() - 1) ? 0 : 2;
        return missedTrace + missedWord + endingPenalty + Math.abs(trace.length() - word.length()) / 2;
    }

    private static int editDistance(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(
                        Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + cost);
            }
            int[] temp = previous;
            previous = current;
            current = temp;
        }
        return previous[right.length()];
    }

    private static String matchCapitalization(String value, String typed) {
        if (value.length() == 0 || typed.length() == 0) {
            return value;
        }
        if (typed.toUpperCase(Locale.US).equals(typed)) {
            return value.toUpperCase(Locale.US);
        }
        if (Character.isUpperCase(typed.charAt(0))) {
            return Character.toUpperCase(value.charAt(0)) + value.substring(1);
        }
        return value;
    }

    private static boolean isWordTerminator(String key) {
        return key.length() == 1 && ".,!?:;".contains(key);
    }

    private static boolean isWordCharacter(String key) {
        return key.length() == 1 && Character.isLetter(key.charAt(0));
    }

    private static String normalizeWord(String word) {
        return word == null
                ? ""
                : word.toLowerCase(Locale.US).replaceAll("[^a-z']", "");
    }

    private static String compressRepeated(String value) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (builder.length() == 0 || builder.charAt(builder.length() - 1) != ch) {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private static void addCorrection(String typo, String correction) {
        AUTOCORRECT.put(typo, correction);
    }

    private static void addNext(String word, String first, String second, String third) {
        NEXT_WORDS.put(word, Arrays.asList(first, second, third));
    }

    private static final class ClipboardEntry {
        final String text;
        final Uri uri;
        final String mimeType;

        private ClipboardEntry(String text, Uri uri, String mimeType) {
            this.text = text;
            this.uri = uri;
            this.mimeType = mimeType;
        }

        static ClipboardEntry text(String text) {
            return new ClipboardEntry(text, null, null);
        }

        static ClipboardEntry image(Uri uri, String mimeType) {
            return new ClipboardEntry(null, uri, mimeType);
        }

        boolean sameContent(ClipboardEntry other) {
            if (text != null && other.text != null) {
                return text.equals(other.text);
            }
            if (uri != null && other.uri != null) {
                return uri.equals(other.uri);
            }
            return false;
        }

        String label() {
            if (text != null) {
                String singleLine = text.replace('\n', ' ').trim();
                return singleLine.length() > 18 ? singleLine.substring(0, 18) + "..." : singleLine;
            }
            String last = uri == null ? "image" : uri.getLastPathSegment();
            if (last == null || last.length() == 0) {
                last = "image";
            }
            return "Image: " + last;
        }
    }
}
