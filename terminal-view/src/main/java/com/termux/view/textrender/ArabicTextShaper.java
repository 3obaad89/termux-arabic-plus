package com.termux.view.textrender;

/**
 * Arabic text reshaper - converts Arabic characters to their proper joining forms
 * (isolated, initial, medial, final) so they render connected in the terminal.
 *
 * This is the Java equivalent of python-arabic-reshaper. It replaces base Unicode
 * characters with their shaped variants (the joining forms from the Presentation Forms-B block).
 *
 * After reshaping, drawTextRun() will render the correct connected forms.
 */
public class ArabicTextShaper {

    // Arabic Unicode ranges
    private static final int ARABIC_BASE_START = 0x0600;
    private static final int ARABIC_BASE_END = 0x06FF;

    // Presentation Forms-B (shaped Arabic glyphs)
    private static final int PF_B_BASE = 0xFE70;

    // Connectivity types for Arabic letters
    // 0 = joins to nothing (transparent or standalone)
    // 1 = joins to right only (DAL, THAL, RA, ZAIN, WAW, etc.)
    // 2 = joins to both sides (most Arabic letters)
    // 3 = joins to left only (ALEF MADDA, ALEF WITH HAMZA ABOVE, etc.)

    private static final int[] CONNECTIVITY = new int[0x600];

    static {
        // Initialize connectivity table
        // Letters that don't connect to the right (join only from right → left)
        // These are "right-joining only" letters
        setConnectivity(0x0621, 0); // HAMZA
        setConnectivity(0x0622, 3); // ALEF WITH MADDA ABOVE
        setConnectivity(0x0623, 3); // ALEF WITH HAMZA ABOVE
        setConnectivity(0x0624, 0); // WAW WITH HAMZA ABOVE
        setConnectivity(0x0625, 3); // ALEF WITH HAMZA BELOW
        setConnectivity(0x0626, 2); // YEH WITH HAMZA ABOVE
        setConnectivity(0x0627, 3); // ALEF
        setConnectivity(0x0628, 2); // BEH
        setConnectivity(0x0629, 0); // TEH MARBUTA
        setConnectivity(0x062A, 2); // TEH
        setConnectivity(0x062B, 2); // THEH
        setConnectivity(0x062C, 2); // JEEM
        setConnectivity(0x062D, 2); // HAH
        setConnectivity(0x062E, 2); // KHAH
        setConnectivity(0x062F, 1); // DAL
        setConnectivity(0x0630, 1); // THAL
        setConnectivity(0x0631, 1); // REH
        setConnectivity(0x0632, 1); // ZAIN
        setConnectivity(0x0633, 2); // SEEN
        setConnectivity(0x0634, 2); // SHEEN
        setConnectivity(0x0635, 2); // SAD
        setConnectivity(0x0636, 2); // DAD
        setConnectivity(0x0637, 2); // TAH
        setConnectivity(0x0638, 2); // ZAH
        setConnectivity(0x0639, 2); // AIN
        setConnectivity(0x063A, 2); // GHAIN
        setConnectivity(0x0640, 0); // TATWEEL (connects but is not a letter)
        setConnectivity(0x0641, 2); // FEH
        setConnectivity(0x0642, 2); // QAF
        setConnectivity(0x0643, 2); // KAF
        setConnectivity(0x0644, 2); // LAM
        setConnectivity(0x0645, 2); // MEEM
        setConnectivity(0x0646, 2); // NOON
        setConnectivity(0x0647, 2); // HEH
        setConnectivity(0x0648, 1); // WAW
        setConnectivity(0x0649, 2); // ALEF MAKSORA / YEH
        setConnectivity(0x064A, 2); // YEH

        // Extended Arabic
        setConnectivity(0x0671, 3); // ALEF WASLA
        setConnectivity(0x0677, 0); // U WITH HAMZA ABOVE
        setConnectivity(0x0679, 2); // TTEH
        setConnectivity(0x067A, 2); // TEHEH
        setConnectivity(0x067B, 2); // BEEH
        setConnectivity(0x067E, 2); // PEH
        setConnectivity(0x067F, 2); // TEHEH
        setConnectivity(0x0680, 2); // BEHEH
        setConnectivity(0x0683, 2); // NYEH
        setConnectivity(0x0684, 2); // DYEH
        setConnectivity(0x0686, 2); // TCHEH
        setConnectivity(0x0687, 2); // TCHEHEH
        setConnectivity(0x0688, 1); // DDAL
        setConnectivity(0x068C, 1); // DAHAL
        setConnectivity(0x068D, 1); // DDAHAL
        setConnectivity(0x068E, 1); // DUL
        setConnectivity(0x0691, 1); // RREH
        setConnectivity(0x0698, 1); // JEH
        setConnectivity(0x06A4, 2); // VEH
        setConnectivity(0x06A6, 2); // PEHEH
        setConnectivity(0x06A9, 2); // KEHEH
        setConnectivity(0x06AD, 2); // NG
        setConnectivity(0x06AF, 2); // GAF
        setConnectivity(0x06B1, 2); // NGOEH
        setConnectivity(0x06B3, 2); // GUEH
        setConnectivity(0x06BA, 1); // NOON GHUNNA (no dots)
        setConnectivity(0x06BB, 2); // RNOON
        setConnectivity(0x06BE, 2); // HEH DOACHASHMEE
        setConnectivity(0x06C0, 0); // HEH WITH YEH ABOVE
        setConnectivity(0x06C1, 2); // HEH GOAL
        setConnectivity(0x06C5, 1); // KIRGHIZ OE
        setConnectivity(0x06C6, 1); // OE
        setConnectivity(0x06C7, 1); // U
        setConnectivity(0x06C8, 1); // YU
        setConnectivity(0x06C9, 1); // KIRGHIZ YU
        setConnectivity(0x06CB, 1); // VE
        setConnectivity(0x06CC, 2); // FARSI YEH
        setConnectivity(0x06D0, 2); // E
        setConnectivity(0x06D2, 1); // YEH BARREE
        setConnectivity(0x06D3, 0); // YEH BARREE WITH HAMZA ABOVE
    }

    private static void setConnectivity(int codePoint, int type) {
        if (codePoint - ARABIC_BASE_START < CONNECTIVITY.length) {
            CONNECTIVITY[codePoint - ARABIC_BASE_START] = type;
        }
    }

    private static int getConnectivity(int codePoint) {
        if (codePoint >= ARABIC_BASE_START && codePoint < ARABIC_BASE_END) {
            int idx = codePoint - ARABIC_BASE_START;
            if (idx < CONNECTIVITY.length) return CONNECTIVITY[idx];
        }
        return 0;
    }

    // Presentation Forms-B mapping tables
    // Indexed by: [letter_index][form] where form = 0=isolated, 1=final, 2=initial, 3=medial
    private static final int[][] SHAPED_FORMS = {
        // 0621 HAMZA
        {0xFE80, 0, 0, 0},
        // 0622 ALEF WITH MADDA ABOVE
        {0xFE81, 0xFE82, 0, 0},
        // 0623 ALEF WITH HAMZA ABOVE
        {0xFE83, 0xFE84, 0, 0},
        // 0624 WAW WITH HAMZA ABOVE
        {0xFE85, 0, 0, 0},
        // 0625 ALEF WITH HAMZA BELOW
        {0xFE86, 0xFE87, 0, 0},
        // 0626 YEH WITH HAMZA ABOVE
        {0xFE88, 0xFE89, 0xFE8A, 0xFE8B},
        // 0627 ALEF
        {0xFE8D, 0xFE8E, 0, 0},
        // 0628 BEH
        {0xFE8F, 0xFE90, 0xFE91, 0xFE92},
        // 0629 TEH MARBUTA
        {0xFE93, 0, 0, 0},
        // 062A TEH
        {0xFE95, 0xFE96, 0xFE97, 0xFE98},
        // 062B THEH
        {0xFE99, 0xFE9A, 0xFE9B, 0xFE9C},
        // 062C JEEM
        {0xFE9D, 0xFE9E, 0xFE9F, 0xFEA0},
        // 062D HAH
        {0xFEA1, 0xFEA2, 0xFEA3, 0xFEA4},
        // 062E KHAH
        {0xFEA5, 0xFEA6, 0xFEA7, 0xFEA8},
        // 062F DAL
        {0xFEA9, 0xFEAA, 0, 0},
        // 0630 THAL
        {0xFEAB, 0xFEAC, 0, 0},
        // 0631 REH
        {0xFEAD, 0xFEAE, 0, 0},
        // 0632 ZAIN
        {0xFEAF, 0xFEB0, 0, 0},
        // 0633 SEEN
        {0xFEB1, 0xFEB2, 0xFEB3, 0xFEB4},
        // 0634 SHEEN
        {0xFEB5, 0xFEB6, 0xFEB7, 0xFEB8},
        // 0635 SAD
        {0xFEB9, 0xFEBA, 0xFEBB, 0xFEBC},
        // 0636 DAD
        {0xFEBD, 0xFEBE, 0xFEBF, 0xFEC0},
        // 0637 TAH
        {0xFEC1, 0xFEC2, 0xFEC3, 0xFEC4},
        // 0638 ZAH
        {0xFEC5, 0xFEC6, 0xFEC7, 0xFEC8},
        // 0639 AIN
        {0xFEC9, 0xFECA, 0xFECB, 0xFECC},
        // 063A GHAIN
        {0xFECD, 0xFECE, 0xFECF, 0xFED0},
        // 0640 TATWEEL - just return itself
        {0x0640, 0x0640, 0x0640, 0x0640},
        // 0641 FEH
        {0xFED1, 0xFED2, 0xFED3, 0xFED4},
        // 0642 QAF
        {0xFED5, 0xFED6, 0xFED7, 0xFED8},
        // 0643 KAF
        {0xFED9, 0xFEDA, 0xFEDB, 0xFEDC},
        // 0644 LAM
        {0xFEDD, 0xFEDE, 0xFEDF, 0xFEE0},
        // 0645 MEEM
        {0xFEE1, 0xFEE2, 0xFEE3, 0xFEE4},
        // 0646 NOON
        {0xFEE5, 0xFEE6, 0xFEE7, 0xFEE8},
        // 0647 HEH
        {0xFEE9, 0xFEEA, 0xFEEB, 0xFEEC},
        // 0648 WAW
        {0xFEED, 0xFEEE, 0, 0},
        // 0649 ALEF MAKSORA
        {0xFEEF, 0xFEF0, 0, 0},
        // 064A YEH
        {0xFEF1, 0xFEF2, 0xFEF3, 0xFEF4},
    };

    /**
     * Checks if text contains Arabic characters that need shaping.
     */
    public static boolean needsShaping(char[] text, int start, int length) {
        if (text == null || length == 0) return false;
        int end = Math.min(start + length, text.length);
        for (int i = start; i < end; i++) {
            char c = text[i];
            if (c >= ARABIC_BASE_START && c <= 0x06FF) return true;
            if (c >= 0xFB50 && c <= 0xFDFF) return true; // Presentation Forms-A
            if (c >= 0xFE70 && c <= 0xFEFF) return true; // Presentation Forms-B (already shaped)
        }
        return false;
    }

    /**
     * Check if a codepoint is already a shaped Arabic form (Presentation Forms-B).
     */
    private static boolean isAlreadyShaped(int codePoint) {
        return (codePoint >= 0xFE70 && codePoint <= 0xFEFF);
    }

    /**
     * Reshape Arabic text: replace base characters with their joining forms.
     * Returns a new char array with the shaped characters.
     * The original text is NOT modified.
     */
    public static char[] reshape(char[] text, int start, int length) {
        if (text == null || length <= 0) return text;

        int end = Math.min(start + length, text.length);
        int len = end - start;

        // First pass: determine the form of each character
        int[] forms = new int[len]; // 0=isolated, 1=final, 2=initial, 3=medial
        boolean[] isArabic = new boolean[len];

        for (int i = 0; i < len; i++) {
            char c = text[start + i];
            if (c >= ARABIC_BASE_START && c <= 0x06FF && !isAlreadyShaped(c)) {
                isArabic[i] = true;
                int conn = getConnectivity(c);

                // Determine connectivity to the right (previous Arabic char)
                boolean connectsRight = false;
                if (i > 0 && isArabic[i - 1]) {
                    int prevConn = getConnectivity(text[start + i - 1]);
                    connectsRight = (prevConn == 2 || prevConn == 3); // joins to left (our right)
                }

                // Determine connectivity to the left (next Arabic char)
                boolean connectsLeft = false;
                if (i < len - 1 && isArabic[i + 1]) {
                    int nextConn = getConnectivity(text[start + i + 1]);
                    connectsLeft = (conn == 2 || conn == 3); // we join to left
                }

                if (connectsRight && connectsLeft) {
                    forms[i] = 3; // medial
                } else if (connectsRight) {
                    forms[i] = 1; // final
                } else if (connectsLeft) {
                    forms[i] = 2; // initial
                } else {
                    forms[i] = 0; // isolated
                }
            } else {
                isArabic[i] = false;
                forms[i] = 0;
            }
        }

        // Second pass: build the output with shaped characters
        char[] result = new char[len];
        for (int i = 0; i < len; i++) {
            char c = text[start + i];
            if (isArabic[i]) {
                int letterIndex = c - 0x0621;
                if (letterIndex >= 0 && letterIndex < SHAPED_FORMS.length) {
                    int shaped = SHAPED_FORMS[letterIndex][forms[i]];
                    if (shaped != 0) {
                        result[i] = (char) shaped;
                        continue;
                    }
                }
            }
            result[i] = c;
        }

        return result;
    }

    /**
     * Check if a char is in the Arabic block (base form, not already shaped).
     */
    public static boolean isArabicBase(char c) {
        return c >= ARABIC_BASE_START && c <= 0x06FF && !isAlreadyShaped(c);
    }
}
