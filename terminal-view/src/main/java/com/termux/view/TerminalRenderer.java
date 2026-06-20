package com.termux.view;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Typeface;

import com.termux.terminal.TerminalBuffer;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalRow;
import com.termux.terminal.TextStyle;
import com.termux.terminal.WcWidth;
import com.termux.view.textrender.BiDiTextHelper;

import java.text.Bidi;
import java.util.WeakHashMap;

/**
 * Renderer of a {@link TerminalEmulator} into a {@link Canvas}.
 *
 * Arabic/RTL support:
 * - HarfBuzz shaping: Canvas.drawTextRun() with isRtl triggers Android's
 *   HarfBuzz engine for Arabic character joining and ligatures.
 * - Bidirectional ordering: Rows containing RTL characters are processed
 *   through the Unicode BiDi Algorithm (UAX #9) via java.text.Bidi before
 *   rendering, so mixed Arabic/English text is visually reordered correctly
 *   while the terminal buffer stays in logical (input) order.
 */
public final class TerminalRenderer {

    final int mTextSize;
    final Typeface mTypeface;
    private final Paint mTextPaint = new Paint();

    final float mFontWidth;
    final int mFontLineSpacing;
    private final int mFontAscent;
    final int mFontLineSpacingAndAscent;

    private final float[] asciiMeasures = new float[127];

    /**
     * Cached BiDi visual-run layout per terminal row, keyed by the
     * {@link TerminalRow} instance. The layout is the output of the LAYOUT
     * LAYER (logical→visual transformation via the Unicode BiDi Algorithm,
     * UAX #9) and is recomputed ONLY when the row's text content changes.
     * Cursor movement and repaints that do not alter text reuse the cached
     * layout, so BiDi never re-runs on cursor movement — guaranteeing stable
     * visual output (same text ⇒ same frame) and decoupling layout from
     * input/cursor events.
     */
    private final WeakHashMap<TerminalRow, RowLayout> mBidiLayoutCache = new WeakHashMap<>();

    /**
     * Visual-run layout for a single row — the logical→visual transformation
     * produced by the layout layer. All arrays are sized to the terminal
     * column count (small and bounded).
     */
    private static final class RowLayout {
        /** Snapshot of the row text the layout was computed from (change detection). */
        char[] textSnapshot;
        int textLen;
        /** Number of visual runs (directional × style segments). */
        int runCount;
        /** Visual runs in visual space (non-overlapping; order is logical-run order). */
        int[] frunCharStart;   // logical char start index of the run
        int[] frunCharEnd;     // logical char end index (exclusive)
        int[] frunVisualCol;   // visual start column (left-aligned in the grid)
        int[] frunWidthCols;   // run width in terminal columns
        boolean[] frunIsRtl;   // direction: true = RTL, false = LTR
        long[] frunStyle;      // cell style of the run (single style per run)
        /** logical column → visual column map (size = columns), -1 if unmapped. */
        int[] logicalToVisualCol;
        int totalCols;
        boolean baseIsLtr;
        int columns;
    }

    public TerminalRenderer(int textSize, Typeface typeface) {
        mTextSize = textSize;
        mTypeface = typeface;

        mTextPaint.setTypeface(typeface);
        mTextPaint.setAntiAlias(true);
        mTextPaint.setTextSize(textSize);

        mFontLineSpacing = (int) Math.ceil(mTextPaint.getFontSpacing());
        mFontAscent = (int) Math.ceil(mTextPaint.ascent());
        mFontLineSpacingAndAscent = mFontLineSpacing + mFontAscent;
        mFontWidth = mTextPaint.measureText("X");

        StringBuilder sb = new StringBuilder(" ");
        for (int i = 0; i < asciiMeasures.length; i++) {
            sb.setCharAt(0, (char) i);
            asciiMeasures[i] = mTextPaint.measureText(sb, 0, 1);
        }
    }

    public final void render(TerminalEmulator mEmulator, Canvas canvas, int topRow,
                             int selectionY1, int selectionY2, int selectionX1, int selectionX2) {
        final boolean reverseVideo = mEmulator.isReverseVideo();
        final int endRow = topRow + mEmulator.mRows;
        final int columns = mEmulator.mColumns;
        final int cursorCol = mEmulator.getCursorCol();
        final int cursorRow = mEmulator.getCursorRow();
        final boolean cursorVisible = mEmulator.shouldCursorBeVisible();
        final TerminalBuffer screen = mEmulator.getScreen();
        final int[] palette = mEmulator.mColors.mCurrentColors;
        final int cursorShape = mEmulator.getCursorStyle();

        if (reverseVideo)
            canvas.drawColor(palette[TextStyle.COLOR_INDEX_FOREGROUND], PorterDuff.Mode.SRC);

        float heightOffset = mFontLineSpacingAndAscent;
        for (int row = topRow; row < endRow; row++) {
            heightOffset += mFontLineSpacing;

            final int cursorX = (row == cursorRow && cursorVisible) ? cursorCol : -1;
            int selx1 = -1, selx2 = -1;
            if (row >= selectionY1 && row <= selectionY2) {
                if (row == selectionY1) selx1 = selectionX1;
                selx2 = (row == selectionY2) ? selectionX2 : mEmulator.mColumns;
            }

            TerminalRow lineObject = screen.allocateFullLineIfNecessary(screen.externalToInternalRow(row));
            final char[] line = lineObject.mText;
            final int charsUsedInLine = lineObject.getSpaceUsed();

            // BiDi fast-path: rows without RTL characters use the original
            // logical-order rendering (unchanged). Rows WITH RTL characters
            // are processed through the Unicode BiDi Algorithm (UAX #9) in
            // renderRowBidi, which reorders cells to visual order for display
            // while leaving the terminal buffer in logical (input) order.
            if (charsUsedInLine > 0 && BiDiTextHelper.containsRtlCharacters(line, 0, charsUsedInLine)) {
                renderRowBidi(canvas, lineObject, line, charsUsedInLine, palette, heightOffset,
                    cursorX, selx1, selx2, cursorShape, reverseVideo, columns,
                    mEmulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_CURSOR]);
                continue;
            }

            long lastRunStyle = 0;
            boolean lastRunInsideSelection = false;
            int lastRunStartColumn = -1;
            int lastRunStartIndex = 0;
            boolean lastRunFontWidthMismatch = false;
            int currentCharIndex = 0;
            float measuredWidthForRun = 0.f;

            // The cursor is rendered as a separate overlay pass after the row's
            // text runs (see drawCursorOverlay), so it never splits a text run
            // and never forces Arabic glyphs to reshape as the cursor moves.
            // We record the cursor cell here while walking the row.
            boolean cursorCellRecorded = false;
            int cursorCellCharIndex = 0;
            int cursorCellCharCount = 0;
            int cursorCellColumn = 0;
            int cursorCellColumnWidth = 0;
            long cursorCellStyle = 0;

            for (int column = 0; column < columns; ) {
                final char charAtIndex = line[currentCharIndex];
                final boolean charIsHighsurrogate = Character.isHighSurrogate(charAtIndex);
                final int charsForCodePoint = charIsHighsurrogate ? 2 : 1;
                final int codePoint = charIsHighsurrogate ? Character.toCodePoint(charAtIndex, line[currentCharIndex + 1]) : charAtIndex;
                final int codePointWcWidth = WcWidth.width(codePoint);
                final boolean insideCursor = (cursorX == column || (codePointWcWidth == 2 && cursorX == column + 1));
                final boolean insideSelection = column >= selx1 && column <= selx2;
                final long style = lineObject.getStyle(column);

                // Record the cursor cell (once) for the overlay pass. insideCursor
                // is true at the start column of the glyph under the cursor, which
                // correctly handles both single and double-width cells.
                if (insideCursor && !cursorCellRecorded) {
                    cursorCellRecorded = true;
                    cursorCellCharIndex = currentCharIndex;
                    cursorCellCharCount = charsForCodePoint;
                    cursorCellColumn = column;
                    cursorCellColumnWidth = codePointWcWidth;
                    cursorCellStyle = style;
                }

                final float measuredCodePointWidth = (codePoint < asciiMeasures.length) ? asciiMeasures[codePoint] : mTextPaint.measureText(line,
                    currentCharIndex, charsForCodePoint);
                // Arabic/RTL glyphs naturally differ in advance width from the
                // Latin base cell width (mFontWidth). The original logic split a
                // run whenever a single glyph's measured width strayed from its
                // cell width — which fragmented Arabic words into one-char
                // runs, so each letter was shaped in isolation and never joined.
                // For complex-script characters, keep them in a single
                // contiguous run (split only by style/selection) so
                // HarfBuzz receives the full joining context.
                final boolean isComplexScript = codePoint <= 0xFFFF && BiDiTextHelper.isArabicCharacter((char) codePoint);
                final boolean fontWidthMismatch = !isComplexScript &&
                    (Math.abs(measuredCodePointWidth / mFontWidth - codePointWcWidth) > 0.01);

                if (style != lastRunStyle || insideSelection != lastRunInsideSelection || fontWidthMismatch || lastRunFontWidthMismatch) {
                    if (column == 0) {
                        // Skip first column as there is nothing to draw, just record the current style.
                    } else {
                        final int columnWidthSinceLastRun = column - lastRunStartColumn;
                        final int charsSinceLastRun = currentCharIndex - lastRunStartIndex;
                        // Cursor is drawn as a separate overlay after the row's
                        // text runs, so it never splits a run and never triggers
                        // reshaping flicker. Pass cursor=0 and no cursor inversion.
                        drawTextRun(canvas, line, palette, heightOffset, lastRunStartColumn, columnWidthSinceLastRun,
                            lastRunStartIndex, charsSinceLastRun, measuredWidthForRun,
                            0, cursorShape, lastRunStyle, reverseVideo || lastRunInsideSelection, charsUsedInLine);
                    }
                    measuredWidthForRun = 0.f;
                    lastRunStyle = style;
                    lastRunInsideSelection = insideSelection;
                    lastRunStartColumn = column;
                    lastRunStartIndex = currentCharIndex;
                    lastRunFontWidthMismatch = fontWidthMismatch;
                }
                measuredWidthForRun += measuredCodePointWidth;
                column += codePointWcWidth;
                currentCharIndex += charsForCodePoint;
                while (currentCharIndex < charsUsedInLine && WcWidth.width(line, currentCharIndex) <= 0) {
                    currentCharIndex += Character.isHighSurrogate(line[currentCharIndex]) ? 2 : 1;
                }
            }

            final int columnWidthSinceLastRun = columns - lastRunStartColumn;
            final int charsSinceLastRun = currentCharIndex - lastRunStartIndex;
            // Cursor is drawn as a separate overlay after the row's text runs.
            drawTextRun(canvas, line, palette, heightOffset, lastRunStartColumn, columnWidthSinceLastRun, lastRunStartIndex, charsSinceLastRun,
                measuredWidthForRun, 0, cursorShape, lastRunStyle, reverseVideo || lastRunInsideSelection, charsUsedInLine);

            // Overlay the cursor on top of the already-rendered, stable text
            // runs. This keeps Arabic shaping intact (no run split at the
            // cursor) and prevents flicker / disappearing glyphs on cursor
            // movement and incremental input.
            if (cursorCellRecorded) {
                drawCursorOverlay(canvas, line, palette, heightOffset, cursorCellColumn, cursorCellColumnWidth,
                    cursorCellCharIndex, cursorCellCharCount, cursorCellStyle, charsUsedInLine,
                    mEmulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_CURSOR], cursorShape, reverseVideo);
            }
        }
    }

    /**
     * LAYOUT LAYER: computes (or returns the cached) BiDi visual-run layout
     * for a row.
     *
     * This is the logical→visual transformation, produced by the Unicode
     * Bidirectional Algorithm (UAX #9) via {@link java.text.Bidi} operating on
     * the FULL LINE STRING — never on individual cells or characters. BiDi
     * yields directional RUNS (each a maximal same-direction span); those runs
     * are reordered at the RUN level (not cell level) to visual order, then
     * sub-split by cell style so per-character colour/attributes are preserved.
     *
     * The result is cached per {@link TerminalRow} and recomputed ONLY when the
     * row's text content changes (snapshot mismatch). Cursor movement and
     * repaints that do not alter text reuse the cached layout, so BiDi never
     * re-runs on cursor movement — guaranteeing stable visual output
     * (same text ⇒ same frame) and decoupling layout from input/cursor events.
     *
     * The terminal buffer is NEVER mutated here — this is a display-only
     * transformation. Numbers stay LTR (UAX #9 EN handling) and neutrals/
     * punctuation inherit surrounding direction, both automatically via BiDi.
     *
     * Output ({@link RowLayout}): a flat list of VISUAL RUNS, each carrying
     * its LOGICAL char range, direction (LTR/RTL), visual start column, width
     * and style, plus a logical→visual column map for the cursor overlay.
     */
    private RowLayout computeLayout(TerminalRow lineObject, char[] line, int charsUsed, int columns) {
        RowLayout cached = mBidiLayoutCache.get(lineObject);
        if (cached != null && cached.columns == columns && cached.textLen == charsUsed
                && textEquals(cached.textSnapshot, line, charsUsed)) {
            return cached; // text unchanged → reuse layout (no BiDi re-run)
        }

        RowLayout L = new RowLayout();
        L.columns = columns;
        L.textLen = charsUsed;
        L.textSnapshot = new char[charsUsed];
        System.arraycopy(line, 0, L.textSnapshot, 0, charsUsed);

        // --- char index → column mapping, and per-column char ranges ---
        int[] colAt = new int[charsUsed + 1];          // char index → column
        int[] colToCharStart = new int[columns + 1];   // column → first char index
        int[] colToCharEnd = new int[columns + 1];     // column → char index past last mark
        int col = 0, i = 0;
        while (i < charsUsed) {
            int baseStart = i;
            char ch = line[i];
            boolean hs = Character.isHighSurrogate(ch);
            int cc = hs ? 2 : 1;
            if (hs && i + 1 >= charsUsed) { i = charsUsed; break; } // dangling surrogate
            int cp = hs ? Character.toCodePoint(ch, line[i + 1]) : ch;
            int w = WcWidth.width(cp);
            int j = i + cc;
            while (j < charsUsed && WcWidth.width(line, j) <= 0)
                j += Character.isHighSurrogate(line[j]) ? 2 : 1;
            int baseEnd = j;
            if (w > 0 && col < columns) {
                colToCharStart[col] = baseStart;
                colToCharEnd[col] = baseEnd;
                for (int k = baseStart; k < baseEnd; k++) colAt[k] = col;
                col += w;
            } else {
                for (int k = baseStart; k < baseEnd; k++) colAt[k] = col;
            }
            i = baseEnd;
        }
        int totalCols = col;
        for (int k = i; k <= charsUsed; k++) colAt[k] = totalCols; // tail incl. charsUsed
        L.totalCols = totalCols;

        // --- UAX #9 BiDi on the FULL LINE STRING ---
        // The terminal is fundamentally an LTR interface: the cursor moves
        // left-to-right, text is entered left-to-right, prompt skeletons
        // (root@host:~# , ➜ ~ , $ , >) and shell syntax (: ~ | / \ # $ ➜ >)
        // must stay in logical order at embedding level 0.
        //
        // DIRECTION_LEFT_TO_RIGHT forces an LTR paragraph base, so:
        // - Arabic words become embedded RTL runs (level 1) — HarfBuzz still
        //   shapes them correctly (isRtl=true from level parity), Arabic
        //   joining and ligatures are preserved.
        // - ALL prompt symbols, shell syntax, Latin text and numbers stay at
        //   level 0 (LTR, logical order) — they never flip or get absorbed
        //   into an RTL run.
        // - Neutrals (: ~ | / \ # $ ➜ > etc.) inherit LTR from the base, so
        //   they are effectively "locked" to LTR — they never reverse.
        // - Numbers (EN) remain LTR even inside RTL runs (UAX #9 EN rule).
        //
        // This replaces the need for unreliable semantic line classification
        // (prompt/input/output detection): with a forced LTR base, a prompt
        // line starting with an Arabic username (e.g. "عبدالله@device:~# ls")
        // keeps the Arabic name as an RTL run while "@device:~# ls" stays LTR
        // at level 0 — the prompt skeleton is never corrupted regardless of
        // the line's semantic type. Each row is already an independent BiDi
        // paragraph (no global BiDi across the screen buffer), so output,
        // input and prompt lines are naturally isolated per-row.
        String text = new String(line, 0, charsUsed);
        Bidi bidi = new Bidi(text, Bidi.DIRECTION_LEFT_TO_RIGHT);
        L.baseIsLtr = bidi.baseIsLeftToRight();
        int runCount = bidi.getRunCount();

        int[] rStart = new int[runCount];
        int[] rLimit = new int[runCount];
        byte[] rLevel = new byte[runCount];
        int[] rWidth = new int[runCount];
        for (int r = 0; r < runCount; r++) {
            rStart[r] = bidi.getRunStart(r);
            rLimit[r] = bidi.getRunLimit(r);
            rLevel[r] = (byte) bidi.getRunLevel(r);
            rWidth[r] = colAt[rLimit[r]] - colAt[rStart[r]];
        }

        // --- reorder BiDi RUNS (run-level, NOT cell-level) to visual order ---
        Integer[] runIdx = new Integer[runCount];
        for (int r = 0; r < runCount; r++) runIdx[r] = r;
        Bidi.reorderVisually(rLevel, 0, runIdx, 0, runCount);
        int[] runVisCol = new int[runCount];
        int vc = 0;
        for (int vi = 0; vi < runCount; vi++) {
            int r = runIdx[vi];
            runVisCol[r] = vc;
            vc += rWidth[r];
        }

        // --- split each BiDi run into style segments → final visual runs ---
        int maxRuns = columns + 1;
        L.frunCharStart = new int[maxRuns];
        L.frunCharEnd = new int[maxRuns];
        L.frunVisualCol = new int[maxRuns];
        L.frunWidthCols = new int[maxRuns];
        L.frunIsRtl = new boolean[maxRuns];
        L.frunStyle = new long[maxRuns];
        int frunCount = 0;

        L.logicalToVisualCol = new int[columns];
        for (int c = 0; c < columns; c++) L.logicalToVisualCol[c] = -1;

        for (int r = 0; r < runCount; r++) {
            int ls = colAt[rStart[r]];
            int le = colAt[rLimit[r]];
            if (le <= ls) continue; // zero-width run
            boolean isRtl = (rLevel[r] & 1) == 1;
            int V = runVisCol[r];

            int segStartCol = ls;
            long segStyle = lineObject.getStyle(ls);
            for (int c = ls + 1; c <= le; c++) {
                boolean boundary = (c == le);
                long st = segStyle;
                if (!boundary) st = lineObject.getStyle(c);
                if (boundary || st != segStyle) {
                    int segLs = segStartCol;
                    int segLe = c;
                    int segWidth = segLe - segLs;
                    // visual start of this segment within the BiDi run's box
                    int segVisCol = isRtl ? (V + (le - segLe)) : (V + (segLs - ls));
                    L.frunCharStart[frunCount] = colToCharStart[segLs];
                    L.frunCharEnd[frunCount] = colToCharEnd[segLe - 1];
                    L.frunVisualCol[frunCount] = segVisCol;
                    L.frunWidthCols[frunCount] = segWidth;
                    L.frunIsRtl[frunCount] = isRtl;
                    L.frunStyle[frunCount] = segStyle;
                    frunCount++;
                    // logical→visual column map within this segment
                    for (int lc = segLs; lc < segLe && lc < columns; lc++) {
                        L.logicalToVisualCol[lc] = isRtl
                                ? (segVisCol + (segLe - 1 - lc))
                                : (segVisCol + (lc - segLs));
                    }
                    if (!boundary) {
                        segStartCol = c;
                        segStyle = st;
                    }
                }
            }
        }
        L.runCount = frunCount;

        mBidiLayoutCache.put(lineObject, L);
        return L;
    }

    private static boolean textEquals(char[] a, char[] b, int len) {
        if (a == null || a.length < len || b == null || b.length < len) return false;
        for (int i = 0; i < len; i++) if (a[i] != b[i]) return false;
        return true;
    }

    /**
     * RENDERING LAYER for rows containing RTL characters.
     *
     * Uses the cached {@link RowLayout} (layout layer) and renders each visual
     * run as a single {@code drawTextRun} call with the run's FULL logical char
     * range — HarfBuzz shapes the whole run (Arabic joining, LAM-ALEF ligature,
     * diacritic positioning) and {@code drawTextRun} positions it at the run's
     * visual cell range. No pre-shaping, no cell-level fragments, no manual
     * reordering. The cursor is a pure overlay drawn last and never triggers
     * re-layout or re-shaping.
     */
    private void renderRowBidi(Canvas canvas, TerminalRow lineObject, char[] line, int charsUsed,
                               int[] palette, float heightOffset, int cursorX, int selx1, int selx2,
                               int cursorShape, boolean reverseVideo, int columns, int cursorColor) {
        if (charsUsed <= 0) return;

        RowLayout L = computeLayout(lineObject, line, charsUsed, columns);
        if (L.runCount == 0) return;

        // Selection visual extent (run-granular: a run is selected if its
        // visual columns overlap the selection's visual extent).
        int selVisMin = Integer.MAX_VALUE, selVisMax = -1;
        if (selx1 >= 0 && selx2 >= 0) {
            for (int lc = selx1; lc <= selx2 && lc < columns; lc++) {
                int v = L.logicalToVisualCol[lc];
                if (v >= 0) {
                    if (v < selVisMin) selVisMin = v;
                    if (v > selVisMax) selVisMax = v;
                }
            }
        }

        // Render each visual run: full logical run text → HarfBuzz.
        // mes = widthCols * mFontWidth ⇒ no scale-to-fit (text drawn at
        // HarfBuzz's natural shaped advance; monospace runs fill their cells).
        // cursor=0: the cursor is a separate overlay (drawn below).
        for (int i = 0; i < L.runCount; i++) {
            int charStart = L.frunCharStart[i];
            int runChars = L.frunCharEnd[i] - charStart;
            if (runChars <= 0) continue;
            int visCol = L.frunVisualCol[i];
            int widthCols = L.frunWidthCols[i];
            boolean inSel = (selVisMax >= 0 && visCol + widthCols > selVisMin && visCol <= selVisMax);
            drawTextRun(canvas, line, palette, heightOffset, visCol, widthCols,
                charStart, runChars, widthCols * mFontWidth,
                0, cursorShape, L.frunStyle[i], reverseVideo || inSel, charsUsed);
        }

        // --- CURSOR OVERLAY: pure overlay on top of stable text ---
        if (cursorX < 0 || cursorColor == 0) return;
        int vCol = (cursorX < columns) ? L.logicalToVisualCol[cursorX] : -1;
        if (vCol < 0) {
            // cursor beyond mapped text → place by paragraph base direction
            vCol = L.baseIsLtr ? cursorX : Math.max(0, L.totalCols - cursorX);
        }
        // locate the cell at logical cursorX (char range + style for block redraw)
        int cellCharStart = 0, cellCharCount = 0, cellWidth = 1, c = 0, idx = 0;
        long cellStyle = 0;
        while (idx < charsUsed) {
            char ch = line[idx];
            boolean hs = Character.isHighSurrogate(ch);
            int cc = hs ? 2 : 1;
            if (hs && idx + 1 >= charsUsed) break;
            int cp = hs ? Character.toCodePoint(ch, line[idx + 1]) : ch;
            int w = WcWidth.width(cp);
            int j = idx + cc;
            while (j < charsUsed && WcWidth.width(line, j) <= 0)
                j += Character.isHighSurrogate(line[j]) ? 2 : 1;
            if (w > 0) {
                if (c == cursorX) {
                    cellCharStart = idx;
                    cellCharCount = j - idx;
                    cellWidth = w;
                    cellStyle = lineObject.getStyle(c);
                    break;
                }
                c += w;
            }
            idx = j;
        }
        drawCursorOverlay(canvas, line, palette, heightOffset, vCol, cellWidth,
            cellCharStart, cellCharCount, cellStyle, charsUsed,
            cursorColor, cursorShape, reverseVideo);
    }

    private void drawTextRun(Canvas canvas, char[] text, int[] palette, float y, int startColumn, int runWidthColumns,
                             int startCharIndex, int runWidthChars, float mes, int cursor, int cursorStyle,
                             long textStyle, boolean reverseVideo, int contextCount) {
        int foreColor = TextStyle.decodeForeColor(textStyle);
        final int effect = TextStyle.decodeEffect(textStyle);
        int backColor = TextStyle.decodeBackColor(textStyle);
        final boolean bold = (effect & (TextStyle.CHARACTER_ATTRIBUTE_BOLD | TextStyle.CHARACTER_ATTRIBUTE_BLINK)) != 0;
        final boolean underline = (effect & TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE) != 0;
        final boolean italic = (effect & TextStyle.CHARACTER_ATTRIBUTE_ITALIC) != 0;
        final boolean strikeThrough = (effect & TextStyle.CHARACTER_ATTRIBUTE_STRIKETHROUGH) != 0;
        final boolean dim = (effect & TextStyle.CHARACTER_ATTRIBUTE_DIM) != 0;

        if ((foreColor & 0xff000000) != 0xff000000) {
            if (bold && foreColor >= 0 && foreColor < 8) foreColor += 8;
            foreColor = palette[foreColor];
        }

        if ((backColor & 0xff000000) != 0xff000000) {
            backColor = palette[backColor];
        }

        final boolean reverseVideoHere = reverseVideo ^ (effect & (TextStyle.CHARACTER_ATTRIBUTE_INVERSE)) != 0;
        if (reverseVideoHere) {
            int tmp = foreColor;
            foreColor = backColor;
            backColor = tmp;
        }

        float left = startColumn * mFontWidth;
        float right = left + runWidthColumns * mFontWidth;

        mes = mes / mFontWidth;
        boolean savedMatrix = false;
        // Scale the run's natural advance onto its cell range. Canvas.drawTextRun
        // uses `x` as the LEFT edge of the run's bounding box for both LTR and
        // RTL, so scaling around the canvas origin and compensating `left`/`right`
        // maps the box onto the cells regardless of script direction; the BiDi
        // glyph order within the box is preserved.
        if (Math.abs(mes - runWidthColumns) > 0.01) {
            canvas.save();
            canvas.scale(runWidthColumns / mes, 1.f);
            left *= mes / runWidthColumns;
            right *= mes / runWidthColumns;
            savedMatrix = true;
        }

        if (backColor != palette[TextStyle.COLOR_INDEX_BACKGROUND]) {
            mTextPaint.setColor(backColor);
            canvas.drawRect(left, y - mFontLineSpacingAndAscent + mFontAscent, right, y, mTextPaint);
        }

        if (cursor != 0) {
            mTextPaint.setColor(cursor);
            float cursorHeight = mFontLineSpacingAndAscent - mFontAscent;
            if (cursorStyle == TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE) cursorHeight /= 4.;
            else if (cursorStyle == TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR) right -= ((right - left) * 3) / 4.;
            canvas.drawRect(left, y - cursorHeight, right, y, mTextPaint);
        }

        if ((effect & TextStyle.CHARACTER_ATTRIBUTE_INVISIBLE) == 0) {
            if (dim) {
                int red = (0xFF & (foreColor >> 16));
                int green = (0xFF & (foreColor >> 8));
                int blue = (0xFF & foreColor);
                red = red * 2 / 3;
                green = green * 2 / 3;
                blue = blue * 2 / 3;
                foreColor = 0xFF000000 + (red << 16) + (green << 8) + blue;
            }

            mTextPaint.setFakeBoldText(bold);
            mTextPaint.setUnderlineText(underline);
            mTextPaint.setTextSkewX(italic ? -0.35f : 0.f);
            mTextPaint.setStrikeThruText(strikeThrough);
            mTextPaint.setColor(foreColor);

            // Let Android's HarfBuzz engine handle complex script shaping
            // (Arabic joining, ligatures like LAM-ALEF, diacritic positioning).
            // The full line is passed as the shaping context (contextIndex=0,
            // contextCount=full line) so HarfBuzz can see neighbouring
            // characters across run boundaries (style/selection splits) and
            // apply the correct joining forms. Base Arabic characters are
            // passed unmodified — no manual pre-shaping (Presentation Forms-B
            // would break HarfBuzz GSUB/GPOS and ligature generation).
            //
            // x = left edge of the run's bounding box (Canvas.drawTextRun
            // semantics, same for LTR and RTL); the scale-to-fit matrix above
            // maps the run onto its cells regardless of direction.
            boolean isRtl = BiDiTextHelper.containsRtlCharacters(text, startCharIndex, runWidthChars);
            canvas.drawTextRun(text, startCharIndex, runWidthChars, 0,
                contextCount, left, y - mFontLineSpacingAndAscent, isRtl, mTextPaint);
        }

        if (savedMatrix) canvas.restore();
    }

    /**
     * Draws the cursor as an overlay on top of an already-rendered row.
     *
     * This is separated from text-run rendering so the cursor never splits a
     * text run — splitting at the cursor would fragment Arabic shaping context
     * (HarfBuzz reshapes the boundary glyph differently depending on which side
     * of the cursor it falls), causing glyphs to flicker / change form as the
     * cursor moves and during incremental input. The row's text is drawn first
     * as stable runs; the cursor is then composited on top here.
     *
     * For a block cursor the cell's text is redrawn in the inverted colour so
     * the character stays visible on the cursor block, matching the original
     * in-run cursor behaviour. Full-line shaping context is passed so an Arabic
     * cursor cell keeps its correct joined form.
     */
    private void drawCursorOverlay(Canvas canvas, char[] text, int[] palette, float y,
                                   int cursorColumn, int cursorColumnWidth,
                                   int cursorCharIndex, int cursorCharCount, long textStyle,
                                   int contextCount, int cursorColor, int cursorShape,
                                   boolean reverseVideo) {
        if (cursorColor == 0) return;

        int foreColor = TextStyle.decodeForeColor(textStyle);
        final int effect = TextStyle.decodeEffect(textStyle);
        int backColor = TextStyle.decodeBackColor(textStyle);
        final boolean bold = (effect & (TextStyle.CHARACTER_ATTRIBUTE_BOLD | TextStyle.CHARACTER_ATTRIBUTE_BLINK)) != 0;

        if ((foreColor & 0xff000000) != 0xff000000) {
            if (bold && foreColor >= 0 && foreColor < 8) foreColor += 8;
            foreColor = palette[foreColor];
        }
        if ((backColor & 0xff000000) != 0xff000000) {
            backColor = palette[backColor];
        }

        final boolean reverseVideoHere = reverseVideo ^ (effect & (TextStyle.CHARACTER_ATTRIBUTE_INVERSE)) != 0;
        if (reverseVideoHere) {
            int tmp = foreColor;
            foreColor = backColor;
            backColor = tmp;
        }

        final float left = cursorColumn * mFontWidth;
        final float right = left + cursorColumnWidth * mFontWidth;

        // Cursor rectangle.
        mTextPaint.setColor(cursorColor);
        float cursorHeight = mFontLineSpacingAndAscent - mFontAscent;
        float cursorLeft = left;
        float cursorRight = right;
        if (cursorShape == TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE) {
            cursorHeight /= 4.;
        } else if (cursorShape == TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR) {
            cursorRight -= ((cursorRight - cursorLeft) * 3) / 4.;
        }
        canvas.drawRect(cursorLeft, y - cursorHeight, cursorRight, y, mTextPaint);

        // For a block cursor, redraw the cell's text on top in the inverted
        // colour (foreColor after the swap above == original background) so the
        // glyph remains visible on the cursor block. Skip the text redraw when
        // the cursor sits on an empty cell (beyond typed text).
        if (cursorShape == TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK && cursorCharCount > 0) {
            if ((effect & TextStyle.CHARACTER_ATTRIBUTE_DIM) != 0) {
                int red = (0xFF & (foreColor >> 16));
                int green = (0xFF & (foreColor >> 8));
                int blue = (0xFF & foreColor);
                red = red * 2 / 3;
                green = green * 2 / 3;
                blue = blue * 2 / 3;
                foreColor = 0xFF000000 + (red << 16) + (green << 8) + blue;
            }
            mTextPaint.setFakeBoldText(bold);
            mTextPaint.setUnderlineText((effect & TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE) != 0);
            mTextPaint.setTextSkewX((effect & TextStyle.CHARACTER_ATTRIBUTE_ITALIC) != 0 ? -0.35f : 0.f);
            mTextPaint.setStrikeThruText((effect & TextStyle.CHARACTER_ATTRIBUTE_STRIKETHROUGH) != 0);
            mTextPaint.setColor(foreColor);
            boolean isRtl = BiDiTextHelper.containsRtlCharacters(text, cursorCharIndex, cursorCharCount);
            canvas.drawTextRun(text, cursorCharIndex, cursorCharCount, 0,
                contextCount, left, y - mFontLineSpacingAndAscent, isRtl, mTextPaint);
        }
    }

    public float getFontWidth() {
        return mFontWidth;
    }

    public int getFontLineSpacing() {
        return mFontLineSpacing;
    }
}
