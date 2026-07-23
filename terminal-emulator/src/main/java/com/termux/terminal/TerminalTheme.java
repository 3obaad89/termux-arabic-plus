package com.termux.terminal;

/**
 * Collection of beautiful terminal color schemes.
 * Each scheme has: name, background, foreground, cursor, and 16 ANSI colors.
 */
public final class TerminalTheme {

    public static final int COLOR_BACKGROUND = 0;
    public static final int COLOR_FOREGROUND = 1;
    public static final int COLOR_CURSOR = 2;
    public static final int COLORS_PER_THEME = 19; // bg + fg + cursor + 16 ANSI

    public static final String[] THEME_NAMES = {
        "Dracula",
        "Catppuccin Mocha",
        "Tokyo Night",
        "Nord",
        "Gruvbox Dark",
        "One Dark",
        "GitHub Dark",
        "Ayu Dark",
        "Monokai Pro",
        "Solarized Dark"
    };

    /**
     * Each theme: [background, foreground, cursor,
     *   black, red, green, yellow, blue, magenta, cyan, white,
     *   bright_black, bright_red, bright_green, bright_yellow, bright_blue, bright_magenta, bright_cyan, bright_white]
     * All colors are ARGB ints (0xFF prefix for fully opaque).
     */
    private static final int[][] THEMES = {
        // ── Dracula ──────────────────────────────
        // bg=#282a36, fg=#f8f8f2, cursor=#f8f8f2
        // ANSI: black=#21222c, red=#ff5555, green=#50fa7b, yellow=#f1fa8c, blue=#bd93f9, magenta=#ff79c6, cyan=#8be9fd, white=#f8f8f2
        // bri: black=#6272a4, red=#ff6e6e, green=#69ff94, yellow=#ffffa5, blue=#d6acff, magenta=#ff92df, cyan=#a4ffff, white=#ffffff
        new int[]{
            0xFF282a36, 0xFFf8f8f2, 0xFFf8f8f2,
            0xFF21222c, 0xFFff5555, 0xFF50fa7b, 0xFFf1fa8c, 0xFFbd93f9, 0xFFff79c6, 0xFF8be9fd, 0xFFf8f8f2,
            0xFF6272a4, 0xFFff6e6e, 0xFF69ff94, 0xFFffffa5, 0xFFd6acff, 0xFFff92df, 0xFFa4ffff, 0xFFffffff
        },
        // ── Catppuccin Mocha ─────────────────────
        // bg=#1e1e2e, fg=#cdd6f4, cursor=#f5e0dc
        new int[]{
            0xFF1e1e2e, 0xFFcdd6f4, 0xFFf5e0dc,
            0xFF45475a, 0xFFf38ba8, 0xFFa6e3a1, 0xFFf9e2af, 0xFF89b4fa, 0xFFf5c2e7, 0xFF94e2d5, 0xFFbac2de,
            0xFF585b70, 0xFFff8095, 0xFFb8ebc0, 0xFFfae3b0, 0xFF74c7ec, 0xFFf5c2e7, 0xFF89ebce, 0xFFa6adc8
        },
        // ── Tokyo Night ──────────────────────────
        // bg=#1a1b26, fg=#a9b1d6, cursor=#c0caf5
        new int[]{
            0xFF1a1b26, 0xFFa9b1d6, 0xFFc0caf5,
            0xFF32344a, 0xFFf7768e, 0xFF9ece6a, 0xFFe0af68, 0xFF7aa2f7, 0xFFbb9af7, 0xFF7dcfff, 0xFFa9b1d6,
            0xFF444b6a, 0xFFff7a93, 0xFFb3f1a8, 0xFFffd787, 0xFF7dcfff, 0xFFbb9af7, 0xFF89ddff, 0xFFc0caf5
        },
        // ── Nord ─────────────────────────────────
        // bg=#2e3440, fg=#d8dee9, cursor=#d8dee9
        new int[]{
            0xFF2e3440, 0xFFd8dee9, 0xFFd8dee9,
            0xFF3b4252, 0xFFbf616a, 0xFFa3be8c, 0xFFebcb8b, 0xFF81a1c1, 0xFFb48ead, 0xFF88c0d0, 0xFFe5e9f0,
            0xFF4c566a, 0xFFd06f79, 0xFFb0cf9a, 0xFFf0d399, 0xFF8fbcbb, 0xFFc190c0, 0xFF8de0e0, 0xFFeceff4
        },
        // ── Gruvbox Dark ─────────────────────────
        // bg=#282828, fg=#ebdbb2, cursor=#ebdbb2
        new int[]{
            0xFF282828, 0xFFebdbb2, 0xFFebdbb2,
            0xFF3c3836, 0xFFcc241d, 0xFF98971a, 0xFFd79921, 0xFF458588, 0xFFb16286, 0xFF689d6a, 0xFFa89984,
            0xFF7c6f64, 0xFFfb4934, 0xFFb8bb26, 0xFFfabd2f, 0xFF83a598, 0xFFd3869b, 0xFF8ec07c, 0xFFebdbb2
        },
        // ── One Dark ─────────────────────────────
        // bg=#282c34, fg=#abb2bf, cursor=#528bff
        new int[]{
            0xFF282c34, 0xFFabb2bf, 0xFF528bff,
            0xFF3e4452, 0xFFe06c75, 0xFF98c379, 0xFFe5c07b, 0xFF61afef, 0xFFc678dd, 0xFF56b6c2, 0xFFabb2bf,
            0xFF5c6370, 0xFFe06c75, 0xFF98c379, 0xFFe5c07b, 0xFF61afef, 0xFFc678dd, 0xFF56b6c2, 0xFFabb2bf
        },
        // ── GitHub Dark ──────────────────────────
        // bg=#0d1117, fg=#e6edf3, cursor=#e6edf3
        new int[]{
            0xFF0d1117, 0xFFe6edf3, 0xFFe6edf3,
            0xFF21262d, 0xFFff6b6b, 0xFF3fb950, 0xFFd29922, 0xFF58a6ff, 0xFFbc8cff, 0xFF39c5cf, 0xFFe6edf3,
            0xFF484f58, 0xFFff6b6b, 0xFF56d364, 0xFFe3b341, 0xFF79c0ff, 0xFFd2a8ff, 0xFF56d4dd, 0xFFf0f6fc
        },
        // ── Ayu Dark ─────────────────────────────
        // bg=#0f1419, fg=#e6e1cf, cursor=#e6e1cf
        new int[]{
            0xFF0f1419, 0xFFe6e1cf, 0xFFe6e1cf,
            0xFF242936, 0xFFf07178, 0xFF86b300, 0xFFf2ae49, 0xFF6fa2fc, 0xFFf296a9, 0xFF66bfff, 0xFFe6e1cf,
            0xFF4c5266, 0xFFf2878d, 0xFFa6cc33, 0xFFf4bf66, 0xFF80b0fc, 0xFFf4a6b9, 0xFF77ccff, 0xFFf2e6d9
        },
        // ── Monokai Pro ──────────────────────────
        // bg=#2d2a2e, fg=#fcfcfa, cursor=#fcfcfa
        new int[]{
            0xFF2d2a2e, 0xFFfcfcfa, 0xFFfcfcfa,
            0xFF403e41, 0xFFff6188, 0xFFa9dc76, 0xFFffd866, 0xFFfc9867, 0xFFab9df2, 0xFF78dce8, 0xFFfcfcfa,
            0xFF727072, 0xFFff6188, 0xFFa9dc76, 0xFFffd866, 0xFFfc9867, 0xFFab9df2, 0xFF78dce8, 0xFFfcfcfa
        },
        // ── Solarized Dark ───────────────────────
        // bg=#002b36, fg=#839496, cursor=#93a1a1
        new int[]{
            0xFF002b36, 0xFF839496, 0xFF93a1a1,
            0xFF073642, 0xFFdc322f, 0xFF859900, 0xFFb58900, 0xFF268bd2, 0xFFd33682, 0xFF2aa198, 0xFFeee8d5,
            0xFF586e75, 0xFFdc322f, 0xFF859900, 0xFFb58900, 0xFF268bd2, 0xFFd33682, 0xFF2aa198, 0xFFfdf6e3
        }
    };

    /** Get a theme by index. Returns null if index is out of bounds. */
    public static int[] getTheme(int index) {
        if (index < 0 || index >= THEMES.length) return null;
        return THEMES[index];
    }

    /** Get the number of available themes. */
    public static int getThemeCount() {
        return THEMES.length;
    }

    /** Get the default theme index (Dracula). */
    public static int getDefaultThemeIndex() {
        return 0; // Dracula
    }

    /** Apply a theme's colors into the destination array (16 ANSI + default fg/bg/cursor). */
    public static void applyTheme(int[] dest, int themeIndex) {
        int[] theme = getTheme(themeIndex);
        if (theme == null) return;

        // dest[0..15] = ANSI colors
        // dest[COLOR_INDEX_DEFAULT_FOREGROUND=256] = foreground
        // dest[COLOR_INDEX_DEFAULT_BACKGROUND=257] = background
        // dest[COLOR_INDEX_DEFAULT_CURSOR=258] = cursor

        // Copy ANSI colors (indices 3..18 in theme → 0..15 in dest)
        System.arraycopy(theme, 3, dest, 0, 16);

        // Set default foreground, background, cursor
        dest[TextStyle.COLOR_INDEX_FOREGROUND] = theme[COLOR_FOREGROUND];
        dest[TextStyle.COLOR_INDEX_BACKGROUND] = theme[COLOR_BACKGROUND];
        dest[TextStyle.COLOR_INDEX_CURSOR] = theme[COLOR_CURSOR];
    }

}
