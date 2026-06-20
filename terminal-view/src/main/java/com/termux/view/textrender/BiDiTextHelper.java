package com.termux.view.textrender;

/**
 * Helper class for Arabic/RTL text detection.
 */
public class BiDiTextHelper {

    public static final int DIRECTION_LTR = 0;
    public static final int DIRECTION_RTL = 1;

    /**
     * Checks if the text contains any RTL (Arabic/Hebrew) characters.
     */
    public static boolean containsRtlCharacters(char[] text, int start, int length) {
        if (text == null || length == 0) return false;
        int end = Math.min(start + length, text.length);
        for (int i = start; i < end; i++) {
            if (isArabicCharacter(text[i])) return true;
        }
        return false;
    }

    /**
     * Determines if the text is primarily RTL.
     */
    public static int getTextDirection(char[] text, int start, int length) {
        if (text == null || length == 0) return DIRECTION_LTR;
        int end = Math.min(start + length, text.length);
        for (int i = start; i < end; i++) {
            byte direction = Character.getDirectionality(text[i]);
            if (direction == Character.DIRECTIONALITY_RIGHT_TO_LEFT ||
                direction == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC) {
                return DIRECTION_RTL;
            } else if (direction == Character.DIRECTIONALITY_LEFT_TO_RIGHT) {
                return DIRECTION_LTR;
            }
        }
        return DIRECTION_LTR;
    }

    /**
     * Checks if a character is Arabic.
     */
    public static boolean isArabicCharacter(char c) {
        return (c >= 0x0600 && c <= 0x06FF) ||
               (c >= 0x0750 && c <= 0x077F) ||
               (c >= 0x08A0 && c <= 0x08FF) ||
               (c >= 0xFB50 && c <= 0xFDFF) ||
               (c >= 0xFE70 && c <= 0xFEFF);
    }

    /**
     * Checks if the text needs Arabic shaping (contains Arabic characters).
     */
    public static boolean needsTextShaping(char[] text, int start, int length) {
        return containsRtlCharacters(text, start, length);
    }
}
