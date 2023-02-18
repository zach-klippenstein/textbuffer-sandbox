package com.zachklipp.textbuffers

import kotlin.reflect.KClass

/**
 * Storage implementation for [TextBuffer].
 *
 * In a KMP library this would be pure common code.
 */
interface TextBufferStorage {
    val length: Int

    fun replace(
        range: TextRange = this.range,
        replacement: Char
    )

    /**
     * Replace the characters in this buffer in [range] with the characters in [replacementRange]
     * from [replacement].
     */
    context(GetCharsTrait<T>)
    fun <T> replace(
        range: TextRange = this.range,
        replacement: T,
        replacementRange: TextRange
    )

    operator fun get(index: Int): Char
    fun getChars(srcBegin: Int, srcEnd: Int, dest: CharArray, destBegin: Int)

    /**
     * Starts tracking the specified [range]. When the text is changed, the range associated with
     * the mark will be adjusted (e.g. text inserted before the range will increase the indices,
     * text inserted inside the range will increase the range's length). [getRangeForMark] can be
     * used to get the updated range after the edits.
     */
    fun markRange(range: TextRange, mark: Any)

    /**
     * Gets the current range for the [mark] previously passed into [markRange].
     */
    fun getRangeForMark(mark: Any): TextRange

    /**
     * Gets all marks previously passed into [markRange] whose current ranges intersect [range].
     */
    fun <T : Any> getMarksIntersecting(range: TextRange, type: KClass<T>): List<T>

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

val TextBufferStorage.range: TextRange get() = TextRange(0, length)

fun TextBufferStorage.contentsToString(range: TextRange = this.range): String {
    val start = range.startInclusive
    val end = range.endExclusive
    val chars = CharArray(end - start)
    getChars(start, end, chars, 0)
    return String(chars)
}