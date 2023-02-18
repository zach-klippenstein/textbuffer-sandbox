package com.zachklipp.textbuffers

import kotlin.reflect.KClass

class StringBuilderTextBufferStorage(private val builder: StringBuilder) : TextBufferStorage {

    override val length: Int
        get() = builder.length

    override fun replace(range: TextRange, replacement: Char) {
        // TODO overwrite single char and remove rest
        builder.replace(range.startInclusive, range.endExclusive, replacement.toString())
    }

    context(GetCharsTrait<T>)
    override fun <T> replace(
        range: TextRange,
        replacement: T,
        replacementRange: TextRange,
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

    override fun get(index: Int): Char = builder[index]

    override fun getChars(srcBegin: Int, srcEnd: Int, dest: CharArray, destBegin: Int) {
        builder.getChars(srcBegin, srcEnd, dest, destBegin)
    }

    override fun markRange(range: TextRange, mark: Any) {
        TODO("Not yet implemented")
    }

    override fun getRangeForMark(mark: Any): TextRange {
        TODO("Not yet implemented")
    }

    override fun <T : Any> getMarksIntersecting(range: TextRange, type: KClass<T>): List<T> {
        TODO("Not yet implemented")
    }

    override fun toString(): String = "StringBuilderTextBufferStorage(\"${contentsToString()}\")"
}