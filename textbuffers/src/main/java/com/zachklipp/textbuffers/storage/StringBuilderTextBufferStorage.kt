package com.zachklipp.textbuffers.storage

import com.zachklipp.textbuffers.GetCharsTrait
import com.zachklipp.textbuffers.TextRange

class StringBuilderTextBufferStorage(
    private val builder: StringBuilder = StringBuilder()
) : TextBufferStorage {

    override val length: Int
        get() = builder.length

    override fun replace(range: TextRange, replacement: Char, sourceMark: Any?) {
        builder.replace(range.startInclusive, range.endExclusive, replacement.toString())
    }

    context(GetCharsTrait<T>)
    override fun <T> replace(
        range: TextRange,
        replacement: T,
        replacementRange: TextRange,
        sourceMark: Any?
    ) {
        val chars = CharArray(replacementRange.length)
        getChars(
            replacement,
            replacementRange.startInclusive,
            replacementRange.endExclusive,
            chars,
            0
        )
        builder.replace(range.startInclusive, range.endExclusive, String(chars))
    }

    override fun get(index: Int, sourceMark: Any?): Char = builder[index]

    override fun getChars(
        srcBegin: Int,
        srcEnd: Int,
        dest: CharArray,
        destBegin: Int,
        sourceMark: Any?
    ) {
        builder.getChars(srcBegin, srcEnd, dest, destBegin)
    }

    override fun markRange(range: TextRange, newMark: Any, sourceMark: Any?) {
        TODO("Not yet implemented")
    }

    override fun unmark(mark: Any) {
        TODO("Not yet implemented")
    }

    override fun getRangeForMark(mark: Any, sourceMark: Any?): TextRange {
        TODO("Not yet implemented")
    }

    override fun <R : Any> getMarksIntersecting(
        range: TextRange,
        sourceMark: Any?,
        predicate: (Any, TextRange) -> R?
    ): List<R> {
        TODO("Not yet implemented")
    }

    override fun toString(): String = "StringBuilderTextBufferStorage(\"${contentsToString()}\")"
}