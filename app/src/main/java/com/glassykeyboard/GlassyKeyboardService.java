package com.glassykeyboard;

import android.inputmethodservice.InputMethodService;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class GlassyKeyboardService extends InputMethodService implements GlassyKeyboardView.Listener {
    private static final String KEY_BACKSPACE = "{backspace}";
    private static final String KEY_ENTER = "{enter}";

    private static final List<String> DICTIONARY = Arrays.asList(
            "about", "after", "again", "always", "android", "awesome", "because", "before",
            "best", "better", "call", "can", "chat", "check", "coming", "cool", "day",
            "default", "device", "done", "feel", "fine", "for", "friend", "from", "glass",
            "glassy", "going", "good", "great", "happy", "have", "hello", "help", "home",
            "how", "keyboard", "know", "later", "like", "love", "make", "message", "more",
            "morning", "need", "next", "night", "now", "oled", "okay", "phone", "please",
            "ready", "right", "see", "send", "soon", "sounds", "sure", "thanks", "that",
            "the", "then", "there", "think", "this", "today", "tomorrow", "want", "what",
            "when", "where", "with", "work", "would", "yes", "you", "your");

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
        addCorrection("glasy", "glassy");

        addNext("i", "am", "can", "will");
        addNext("im", "ready", "here", "good");
        addNext("i'm", "ready", "here", "good");
        addNext("you", "can", "are", "will");
        addNext("the", "keyboard", "best", "next");
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
    private GlassyKeyboardView keyboardView;
    private String lastCommittedWord = "";
    private int currentImeOptions;

    @Override
    public android.view.View onCreateInputView() {
        keyboardView = new GlassyKeyboardView(this, this);
        keyboardView.setImeOptions(currentImeOptions);
        updateSuggestions();
        return keyboardView;
    }

    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        currentWord.setLength(0);
        currentImeOptions = attribute != null ? attribute.imeOptions : 0;
        updateSuggestions();
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        currentImeOptions = info != null ? info.imeOptions : 0;
        if (keyboardView != null) {
            keyboardView.setImeOptions(currentImeOptions);
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
        } else if (".".equals(key) || ",".equals(key)) {
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
        InputConnection connection = getCurrentInputConnection();
        if (connection == null || suggestion == null || suggestion.length() == 0) {
            return;
        }

        if (currentWord.length() > 0) {
            connection.deleteSurroundingText(currentWord.length(), 0);
        }
        connection.commitText(matchCapitalization(suggestion, currentWord.toString()) + " ", 1);
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
        } else {
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

    private static String correctionFor(String typed) {
        String normalized = normalizeWord(typed);
        String corrected = AUTOCORRECT.get(normalized);
        if (corrected == null) {
            corrected = normalized;
        }
        return matchCapitalization(corrected, typed);
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

    private static boolean isWordCharacter(String key) {
        return key.length() == 1 && Character.isLetter(key.charAt(0));
    }

    private static String normalizeWord(String word) {
        return word == null
                ? ""
                : word.toLowerCase(Locale.US).replaceAll("[^a-z']", "");
    }

    private static void addCorrection(String typo, String correction) {
        AUTOCORRECT.put(typo, correction);
    }

    private static void addNext(String word, String first, String second, String third) {
        NEXT_WORDS.put(word, Arrays.asList(first, second, third));
    }
}
