package com.zachklipp.textbuffers

/**
 * Storage implementation for [TextBuffer].
 *
 * In a KMP library this would be pure common code.
 */
interface TextBufferStorage {
    val length: Int

    fun replace(
        range: TextRange = TextRange.Unspecified,
        replacement: Char,
        sourceMark: Any? = null
    )

    /**
     * Replace the characters in this buffer in [range] with the characters in [replacementRange]
     * from [replacement]. If [sourceMark] is non-null, [range] is relative to the mark.
     */
    context(GetCharsTrait<T>)
    fun <T> replace(
        range: TextRange = TextRange.Unspecified,
        replacement: T,
        replacementRange: TextRange,
        sourceMark: Any? = null
    )

    operator fun get(index: Int, sourceMark: Any? = null): Char

    /**
     * Copy the characters from the buffer from [srcBegin] to [srcEnd] to [dest] starting at
     * [destBegin]. If [sourceMark] is non-null, [srcBegin] and [srcEnd] are relative to the mark.
     */
    fun getChars(
        srcBegin: Int,
        srcEnd: Int,
        dest: CharArray,
        destBegin: Int,
        sourceMark: Any? = null
    )

    /**
     * Starts tracking the specified [range]. When the text is changed, the range associated with
     * the mark will be adjusted (e.g. text inserted before the range will increase the indices,
     * text inserted inside the range will increase the range's length). [getRangeForMark] can be
     * used to get the updated range after the edits.
     */
    fun markRange(range: TextRange, newMark: Any, sourceMark: Any? = null)

    /**
     * Removes a mark previously set by [markRange].
     */
    fun unmark(mark: Any)

    /**
     * Gets the current range for the [mark] previously passed into [markRange]. If [sourceMark] is
     * non-null, the returned range will be relative to it.
     */
    fun getRangeForMark(mark: Any, sourceMark: Any? = null): TextRange

    /**
     * Gets all marks previously passed into [markRange] whose current ranges intersect [range].
     * If [sourceMark] is non-null, [range] is relative to the mark.
     */
    fun <R : Any> getMarksIntersecting(
        range: TextRange,
        sourceMark: Any? = null,
        predicate: (Any, TextRange) -> R?
    ): List<R>

    companion object : GetCharsTrait<TextBufferStorage> {
        override fun getChars(
            src: TextBufferStorage,
            srcBegin: Int,
            srcEnd: Int,
            dest: CharArray,
            destBegin: Int
        ) = src.getChars(srcBegin, srcEnd, dest, destBegin)
    }
}

fun TextBufferStorage.contentsToString(
    range: TextRange = TextRange.Unspecified,
    mark: Any? = null
): String {
    val start = range.startInclusive
    val end = range.endExclusive
    val chars = CharArray(end - start)
    getChars(start, end, chars, 0, mark)
    return String(chars)
}