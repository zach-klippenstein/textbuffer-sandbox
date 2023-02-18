package com.zachklipp.textbuffers

import kotlin.reflect.KClass

class StringBuilderTextBufferStorage(private val builder: StringBuilder) : TextBufferStorage {

    override val length: Int
        get() = builder.length

    override fun replace(range: TextRange, replacement: Char, mark: Any?) {
        // TODO overwrite single char and remove rest
        builder.replace(range.startInclusive, range.endExclusive, replacement.toString())
    }

    context(GetCharsTrait<T>)
    override fun <T> replace(
        range: TextRange,
        replacement: T,
        replacementRange: TextRange,
        mark: Any?
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
        // TODO delete then insert to avoid String's defensive copy?
    }

    override fun get(index: Int, mark: Any?): Char = builder[index]

    override fun getChars(srcBegin: Int, srcEnd: Int, dest: CharArray, destBegin: Int, mark: Any?) {
        builder.getChars(srcBegin, srcEnd, dest, destBegin)
    }

    override fun markRange(range: TextRange, newMark: Any, sourceMark: Any?) {
        TODO("Not yet implemented")
    }

    override fun getRangeForMark(mark: Any, sourceMark: Any?): TextRange {
        TODO("Not yet implemented")
    }

    override fun <T : Any> getMarksIntersecting(
        range: TextRange,
        type: KClass<T>,
        mark: Any?
    ): List<T> {
        TODO("Not yet implemented")
    }

    override fun toString(): String = "StringBuilderTextBufferStorage(\"${contentsToString()}\")"
}