package com.zachklipp.textbuffers.storage

import com.zachklipp.textbuffers.GetCharsTrait
import com.zachklipp.textbuffers.TextRange

class GapBufferStorage : TextBufferStorage {

    private var buffer: CharArray = CharArray(128)

    override val length: Int
        get() = TODO("Not yet implemented")

    override fun replace(range: TextRange, replacement: Char, sourceMark: Any?) {
        TODO("Not yet implemented")
    }

    context(GetCharsTrait<T>) override fun <T> replace(
        range: TextRange,
        replacement: T,
        replacementRange: TextRange,
        sourceMark: Any?
    ) {
        TODO("Not yet implemented")
    }

    override fun get(index: Int, sourceMark: Any?): Char {
        TODO("Not yet implemented")
    }

    override fun getChars(
        srcBegin: Int,
        srcEnd: Int,
        dest: CharArray,
        destBegin: Int,
        sourceMark: Any?
    ) {
        TODO("Not yet implemented")
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
}