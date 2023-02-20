package com.zachklipp.textbuffers.storage

import androidx.compose.runtime.snapshots.StateObject
import androidx.compose.runtime.snapshots.StateRecord
import androidx.compose.runtime.snapshots.readable
import androidx.compose.runtime.snapshots.writable
import com.zachklipp.textbuffers.GetCharsTrait
import com.zachklipp.textbuffers.TextRange

class StringBuilderStorage : TextBufferStorage, StateObject {

    private var record = Record()
    override val firstStateRecord: StateRecord get() = record

    override val length: Int
        get() = record.readable(this).length

    override fun replace(range: TextRange, replacement: Char, sourceMark: Any?) {
        record.writable(this) {
            replace(range, replacement, sourceMark)
        }
    }

    override fun <T> replace(
        range: TextRange,
        replacement: T,
        replacementRange: TextRange,
        sourceMark: Any?,
        getCharsTrait: GetCharsTrait<T>
    ) {
        record.writable(this) {
            replace(range, replacement, replacementRange, sourceMark, getCharsTrait)
        }
    }

    @Suppress("ReplaceGetOrSet")
    override fun get(index: Int, sourceMark: Any?): Char =
        record.readable(this).get(index, sourceMark)

    override fun getChars(
        srcBegin: Int,
        srcEnd: Int,
        dest: CharArray,
        destBegin: Int,
        sourceMark: Any?
    ) {
        record.readable(this).getChars(srcBegin, srcEnd, dest, destBegin, sourceMark)
    }

    override fun markRange(range: TextRange, newMark: Any, sourceMark: Any?) {
        record.writable(this) {
            markRange(range, newMark, sourceMark)
        }
    }

    override fun unmark(mark: Any) {
        record.writable(this) {
            unmark(mark)
        }
    }

    override fun getRangeForMark(mark: Any, sourceMark: Any?): TextRange =
        record.readable(this).getRangeForMark(mark, sourceMark)

    override fun <R : Any> getMarksIntersecting(
        range: TextRange,
        sourceMark: Any?,
        predicate: (Any, TextRange) -> R?
    ): List<R> = record.writable(this) {
        getMarksIntersecting(range, sourceMark, predicate)
    }

    override fun prependStateRecord(value: StateRecord) {
        record = value as Record
    }

    override fun toString(): String = "StringBuilderStorage(\"${contentsToString()}\")"

    private class Record : StateRecord(), TextBufferStorage {
        private var builder: StringBuilder? = null
        private var hasCopiedForWrite = false

        private fun readableBuilder(): StringBuilder = builder ?: StringBuilder().also {
            builder = it
            hasCopiedForWrite = true
        }

        private fun writableBuilder(): StringBuilder = when {
            builder == null -> readableBuilder()
            hasCopiedForWrite -> builder!!
            else -> {
                StringBuilder(builder!!).also {
                    builder = it
                    hasCopiedForWrite = true
                }
            }
        }

        override fun assign(value: StateRecord) {
            builder = (value as Record).builder
            hasCopiedForWrite = false
        }

        override fun create(): StateRecord = Record()

        override val length: Int
            get() = readableBuilder().length

        override fun replace(range: TextRange, replacement: Char, sourceMark: Any?) {
            writableBuilder().replace(range, replacement, sourceMark)
        }

        override fun <T> replace(
            range: TextRange,
            replacement: T,
            replacementRange: TextRange,
            sourceMark: Any?,
            getCharsTrait: GetCharsTrait<T>
        ) {
            writableBuilder().replace(
                range,
                replacement,
                replacementRange,
                sourceMark,
                getCharsTrait
            )
        }

        override fun get(index: Int, sourceMark: Any?): Char = readableBuilder()[index]

        override fun getChars(
            srcBegin: Int,
            srcEnd: Int,
            dest: CharArray,
            destBegin: Int,
            sourceMark: Any?
        ) {
            readableBuilder().getChars(srcBegin, srcEnd, dest, destBegin)
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
}