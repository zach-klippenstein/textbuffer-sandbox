package com.zachklipp.textbuffers

import com.zachklipp.textbuffers.storage.TextBufferStorage
import java.text.CharacterIterator

/**
 * An inefficient implementation of [CharacterIterator] that uses storage marks to track the
 * iterator's range and current index. Unlike a standard [CharacterIterator], this one must be
 * [dispose]d after use.
 */
@PublishedApi
internal class TextBufferCharacterIterator(
    private val storage: TextBufferStorage,
    private val sourceMark: Any?,
    initialRange: TextRange,
    initialIndex: Int = -1
) : CharacterIterator {

    private class IndexMark

    private val indexMark = IndexMark()
    private val range: TextRange get() = storage.getRangeForMark(this, sourceMark)

    init {
        storage.markRange(initialRange, newMark = this, sourceMark = sourceMark)
        index = if (initialIndex == -1) beginIndex else initialIndex
    }

    // TODO return DONE sentinel.
    override fun first(): Char = setIndex(beginIndex)
    override fun last(): Char = setIndex(endIndex)
    override fun next(): Char = setIndex(index + 1)
    override fun previous(): Char = setIndex(index - 1)

    override fun setIndex(position: Int): Char {
        storage.markRange(TextRange(position), newMark = indexMark, sourceMark)
        return current()
    }
    override fun getIndex(): Int = storage.getRangeForMark(indexMark, sourceMark).startInclusive

    @Suppress("ReplaceGetOrSet")
    override fun current(): Char = storage.get(index, sourceMark)
    override fun getBeginIndex(): Int = range.startInclusive
    override fun getEndIndex(): Int = range.endExclusive

    override fun clone(): Any =
        TextBufferCharacterIterator(storage, sourceMark, range, initialIndex = index)

    fun dispose() {
        storage.unmark(this)
        storage.unmark(indexMark)
    }

    companion object {
        fun create(
            storage: TextBufferStorage,
            sourceMark: Any?,
            range: TextRange,
        ): CharacterIterator = TextBufferCharacterIterator(storage, sourceMark, range)
    }
}

@PublishedApi
internal inline fun <R> CharacterIterator.use(block: (CharacterIterator) -> R): R {
    try {
        return block(this)
    } finally {
        (this as? TextBufferCharacterIterator)?.dispose()
    }
}