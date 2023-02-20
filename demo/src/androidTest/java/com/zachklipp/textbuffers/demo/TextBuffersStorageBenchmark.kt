package com.zachklipp.textbuffers.demo

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import com.zachklipp.textbuffers.GetCharsTrait
import com.zachklipp.textbuffers.TextRange
import com.zachklipp.textbuffers.storage.StringBuilderStorageNoSnapshot
import com.zachklipp.textbuffers.storage.TextBufferStorage
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Arrays

// TODO this is failing because unable to grant WRITE_EXTERNAL_STORAGE permission.
@RunWith(TestParameterInjector::class)
class TextBuffersStorageBenchmark {

    @Suppress("unused")
    enum class BufferImplementation(val factory: () -> TextBufferStorage) {
        StringBuilder({ StringBuilderStorageNoSnapshot(StringBuilder()) }),
    }

    @get:Rule
    val rule = BenchmarkRule()

    @TestParameter("10", "32", "64", "128", "256", "512", "1024", "4096", "1000000")
    private var bufferSize: Int = 0

    @TestParameter
    private lateinit var implementation: BufferImplementation

    @Test
    fun appendFromEmpty() {
        lateinit var buffer: TextBufferStorage
        lateinit var check: StringBuilder

        rule.measureRepeated {
            buffer = runWithTimingDisabled {
                implementation.factory()
            }
            check = runWithTimingDisabled { StringBuilder() }

            for (i in 0 until bufferSize) {
                // Don't use length because that costs a virtual method call.
                buffer.replace(TextRange(i), 'a')
                runWithTimingDisabled { check.append('a') }
            }
        }

        assertEquals(check.toString(), buffer.toString())
    }

    @Test
    fun appendOneAndRemove() {
        val buffer = implementation.factory().apply { fill('a', bufferSize) }
        val check = StringBuilder().apply { fill('a', bufferSize) }

        rule.measureRepeated {
            buffer.replace(TextRange(bufferSize), 'a')
            buffer.replace(TextRange(bufferSize, bufferSize + 1), "")

            runWithTimingDisabled {
                check.replace(bufferSize, bufferSize, "a")
                check.removeRange(bufferSize, bufferSize + 1)
            }
        }

        assertEquals(check.toString(), buffer.toString())
    }

    private fun StringBuilder.fill(char: Char, count: Int) {
        for (i in 0 until count) {
            append(char)
        }
    }

    private fun TextBufferStorage.fill(char: Char, count: Int) {
        replace(
            replacement = char,
            replacementRange = TextRange(0, count),
            getCharsTrait = { src, srcBegin, srcEnd, dest, destBegin ->
                Arrays.fill(dest, destBegin, srcEnd - srcBegin, src)
            })
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun TextBufferStorage.replace(
        range: TextRange = TextRange(0, length),
        replacement: String
    ) {
        @Suppress("UNCHECKED_CAST")
        replace(
            range, replacement, TextRange(0, replacement.length),
            getCharsTrait = java.lang.String::getChars as GetCharsTrait<String>
        )
    }
}