package com.zachklipp.textbuffers.storage

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.Snapshot.Companion.withMutableSnapshot
import com.google.common.truth.FailureMetadata
import com.google.common.truth.IntegerSubject
import com.google.common.truth.StringSubject
import com.google.common.truth.Subject
import com.google.common.truth.Truth.assertAbout
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.TruthJUnit.assume
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import com.zachklipp.textbuffers.TextRange
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

@Suppress("JUnitMalformedDeclaration", "unused")
@RunWith(TestParameterInjector::class)
class TextBufferStorageTest {

    enum class BufferImpl(
        val factory: () -> TextBufferStorage,
        val snapshotAware: Boolean = true,
        val supportsMarks: Boolean = true
    ) {
        StringBuilderStorageNoSnapshot(
            ::StringBuilderStorageNoSnapshot,
            snapshotAware = false,
            supportsMarks = false
        ),
        StringBuilderStorageNoPooling(
            { StringBuilderStorage(StringBuilderPool.Unpooled) },
            snapshotAware = true,
            supportsMarks = false
        ),
        StringBuilderStorageSingleItemPool(
            { StringBuilderStorage(StringBuilderPool.singleBuilder()) },
            snapshotAware = true,
            supportsMarks = false
        ),
        GapBufferStorageNoSnapshot(
            ::GapBufferStorageNoSnapshot,
            snapshotAware = false,
            supportsMarks = false
        ),
        ReplayingGapBufferStorageNoPooling(
            { ReplayingGapBufferStorage(ReplayingGapBufferStorage.GapBufferPool.Unpooled) },
            snapshotAware = true,
            supportsMarks = false
        ),
        ReplayingGapBufferStorageSingleItemPool(
            { ReplayingGapBufferStorage(ReplayingGapBufferStorage.GapBufferPool.singleBuffer()) },
            snapshotAware = true,
            supportsMarks = false
        ),
    }

    enum class ReplacementCase(
        val initial: String,
        val replacement: String,
        val expected: String,
        val range: TextRange = TextRange.Unspecified,
        val replacementRange: TextRange = TextRange(0, replacement.length),
        val sourceMark: Any? = null,
    ) {
        EmptyWithEmptyZero(initial = "", replacement = "", expected = "", range = TextRange.Zero),
        EmptyWithEmptyUnspecified(initial = "", replacement = "", expected = ""),
        EmptyZero(initial = "", replacement = "hello", expected = "hello", range = TextRange.Zero),
        EmptyUnspecified(initial = "", replacement = "hello", expected = "hello"),
        AllWithEmpty(initial = "hello", replacement = "", expected = ""),
        AllWithShorter(initial = "foo", replacement = "b", expected = "b"),
        AllWithSameLength(initial = "foo", replacement = "bar", expected = "bar"),
        AllWithLonger(initial = "foo", replacement = "barbaz", expected = "barbaz"),
        ReplacePrefix(
            initial = "foobar",
            range = TextRange(0, 3),
            replacement = "baz",
            expected = "bazbar"
        ),
        ReplaceMiddle(
            initial = "foobar",
            range = TextRange(1, 5),
            replacement = "baz",
            expected = "fbazr"
        ),
        ReplaceSuffix(
            initial = "foobar",
            range = TextRange(3, 6),
            replacement = "baz",
            expected = "foobaz"
        ),
        InsertPrefix(
            initial = "foobar",
            range = TextRange(0),
            replacement = "baz",
            expected = "bazfoobar"
        ),
        InsertMiddle(
            initial = "foobar",
            range = TextRange(3),
            replacement = "baz",
            expected = "foobazbar"
        ),
        InsertSuffix(
            initial = "foobar",
            range = TextRange(6),
            replacement = "baz",
            expected = "foobarbaz"
        ),
        // TODO replacement range
    }

    @TestParameter
    private lateinit var bufferImpl: BufferImpl
    private val buffer by lazy(LazyThreadSafetyMode.NONE) { bufferImpl.factory() }

    @Test
    fun `empty buffer`() {
        assertThat(buffer).contents.isEmpty()
        assertThat(buffer).length.isEqualTo(0)
        assertThat(buffer.toString()).isEqualTo("${buffer.javaClass.simpleName}(\"\")")
    }

    @Test
    fun `insert char to empty buffer at zero`() {
        buffer.replace(TextRange.Zero, 'a')

        assertThat(buffer).contents.isEqualTo("a")
        assertThat(buffer).length.isEqualTo(1)
    }

    @Test
    fun `insert char to empty buffer at unspecified`() {
        buffer.replace(TextRange.Unspecified, 'a')

        assertThat(buffer).contents.isEqualTo("a")
        assertThat(buffer).length.isEqualTo(1)
    }

    @Test
    fun replace(@TestParameter case: ReplacementCase) {
        if (case.sourceMark != null) assume().that(bufferImpl.supportsMarks).isTrue()
        if (case.initial.isNotEmpty()) {
            buffer.replace(case.initial)
        }
        assertThat(buffer).contents.isEqualTo(case.initial)

        buffer.replace(
            range = case.range,
            replacement = case.replacement,
            replacementRange = case.replacementRange
        )

        assertThat(buffer).contents.isEqualTo(case.expected)
        assertThat(buffer).length.isEqualTo(case.expected.length)
    }

    @Test
    fun `replace inside snapshot`(@TestParameter case: ReplacementCase) {
        assume().that(bufferImpl.snapshotAware).isTrue()
        if (case.sourceMark != null) assume().that(bufferImpl.supportsMarks).isTrue()
        if (case.initial.isNotEmpty()) {
            buffer.replace(case.initial)
        }
        assertThat(buffer).contents.isEqualTo(case.initial)

        withMutableSnapshot {
            buffer.replace(
                range = case.range,
                replacement = case.replacement,
                replacementRange = case.replacementRange
            )
            assertThat(buffer).contents.isEqualTo(case.expected)
            assertThat(buffer).length.isEqualTo(case.expected.length)
        }

        assertThat(buffer).contents.isEqualTo(case.expected)
        assertThat(buffer).length.isEqualTo(case.expected.length)
    }

    @Test
    fun `change inside snapshot discarded`(@TestParameter case: ReplacementCase) {
        assume().that(bufferImpl.snapshotAware).isTrue()
        if (case.sourceMark != null) assume().that(bufferImpl.supportsMarks).isTrue()
        if (case.initial.isNotEmpty()) {
            buffer.replace(case.initial)
        }
        assertThat(buffer).contents.isEqualTo(case.initial)

        val snapshot = Snapshot.takeMutableSnapshot()
        try {
            snapshot.enter {
                buffer.replace(
                    range = case.range,
                    replacement = case.replacement,
                    replacementRange = case.replacementRange
                )
                assertThat(buffer).contents.isEqualTo(case.expected)
                assertThat(buffer).length.isEqualTo(case.expected.length)
            }

            assertThat(buffer).contents.isEqualTo(case.initial)
            assertThat(buffer).length.isEqualTo(case.initial.length)
        } finally {
            snapshot.dispose()
        }
    }

    enum class MultiOpProvider(
        val buildOperations: () -> MutableList<(TextBufferStorage, StringBuilder) -> Unit>
    ) {
        PrependOps({
            ('a'..'z').mapTo(mutableListOf()) { char ->
                fun(buffer: TextBufferStorage, checker: StringBuilder) {
                    buffer.replace(TextRange.Zero, char)
                    checker.insert(0, char)
                }
            }
        }),
        RemovePrefixOps({
            val list = mutableListOf(fun(buffer: TextBufferStorage, checker: StringBuilder) {
                buffer.replace(replacement = alphabet)
                checker.insert(0, alphabet)
            })
            repeat(alphabet.length) {
                list += fun(buffer: TextBufferStorage, checker: StringBuilder) {
                    buffer.replace("", TextRange(0, 1))
                    checker.replace(0, 1, "")
                }
            }
            list
        }),
        AppendOps({
            ('a'..'z').mapTo(mutableListOf()) { char ->
                fun(buffer: TextBufferStorage, checker: StringBuilder) {
                    buffer.replace(TextRange(buffer.length), char)
                    checker.append(char)
                }
            }
        }),
        RemoveSuffixOps({
            val list = mutableListOf(fun(buffer: TextBufferStorage, checker: StringBuilder) {
                buffer.replace(replacement = alphabet)
                checker.insert(0, alphabet)
            })
            repeat(alphabet.length) {
                list += fun(buffer: TextBufferStorage, checker: StringBuilder) {
                    buffer.replace("", TextRange(buffer.length - 1, buffer.length))
                    checker.replace(checker.length - 1, checker.length, "")
                }
            }
            list
        }),
        RandomOps(::buildRandomOperations)
    }

    @Test
    fun `multiple operations in no snapshot`(@TestParameter multiOpProvider: MultiOpProvider) {
        val checker = StringBuilder()
        val operations = multiOpProvider.buildOperations()

        operations.forEach {
            it(buffer, checker)
        }

        assertThat(buffer).contents.isEqualTo(checker.toString())
    }

    @Test
    fun `multiple operations in individual serial snapshots`(@TestParameter multiOpProvider: MultiOpProvider) {
        val checker = StringBuilder()
        val operations = multiOpProvider.buildOperations()

        operations.forEach {
            withMutableSnapshot {
                it(buffer, checker)
            }
        }

        assertThat(buffer).contents.isEqualTo(checker.toString())
    }

    @Test
    fun `multiple operations in grouped serial snapshots`(
        @TestParameter multiOpProvider: MultiOpProvider
    ) {
        val checker = StringBuilder()
        val operations = multiOpProvider.buildOperations()
        var opCount = 2

        while (operations.isNotEmpty()) {
            val group = List(opCount.coerceAtMost(operations.size)) {
                operations[it]
            }
            operations.subList(0, group.size).clear()
            opCount++

            withMutableSnapshot {
                group.forEach {
                    it(buffer, checker)
                }
            }
        }

        assertThat(buffer).contents.isEqualTo(checker.toString())
    }

    @Test
    fun `multiple operations in nested snapshots`(@TestParameter multiOpProvider: MultiOpProvider) {
        val checker = StringBuilder()
        val operations = multiOpProvider.buildOperations()

        fun step() {
            val op = operations.removeFirstOrNull() ?: return
            withMutableSnapshot {
                op(buffer, checker)
                step()
            }
        }

        assertThat(buffer).contents.isEqualTo(checker.toString())
    }

    @Test
    fun `mark tracks changes before`() {
        assume().that(bufferImpl.supportsMarks).isTrue()
        TODO()
    }

    @Test
    fun `mark tracks changes inside`() {
        assume().that(bufferImpl.supportsMarks).isTrue()
        TODO()
    }

    @Test
    fun `mark tracks changes after`() {
        assume().that(bufferImpl.supportsMarks).isTrue()
        TODO()
    }

    @Test
    fun `mark tracks changes intersecting start`() {
        assume().that(bufferImpl.supportsMarks).isTrue()
        TODO()
    }

    @Test
    fun `mark tracks changes intersecting end`() {
        assume().that(bufferImpl.supportsMarks).isTrue()
        TODO()
    }

    @Test
    fun `mark tracks changes intersecting all`() {
        assume().that(bufferImpl.supportsMarks).isTrue()
        TODO()
    }

    private class TextBufferStorageSubject(
        metadata: FailureMetadata,
        private val actual: TextBufferStorage
    ) : Subject(metadata, actual) {
        val length: IntegerSubject get() = check("length").that(actual.length)
        val contents: StringSubject get() = check("contents").that(actual.contentsToString())
    }

    private companion object {

        private const val alphabet = "abcdefghijklmnopqrstuvwxyz"

        fun assertThat(buffer: TextBufferStorage): TextBufferStorageSubject =
            assertAbout(::TextBufferStorageSubject).that(buffer)

        /**
         * Creates a large list of pseudo-random operations to perform on both a [TextBufferStorage] and
         * a [StringBuilder]. The latter is assumed to be correct and used to check that the result of
         * the operations on the former is correct.
         */
        fun buildRandomOperations(): MutableList<(TextBufferStorage, StringBuilder) -> Unit> {
            val chunkSize = 10
            val chunks = ('a'..'z').map { char ->
                buildString(chunkSize) {
                    repeat(chunkSize) {
                        append(char)
                    }
                }
            }
            val list = mutableListOf<(TextBufferStorage, StringBuilder) -> Unit>()
            val random = Random(0)
            chunks.forEach { chunk ->
                list += { buffer, checker ->
                    assertThat(buffer).contents.isEqualTo(checker.toString())

                    val insertLocation =
                        if (buffer.length == 0) 0 else random.nextInt(buffer.length)
                    buffer.replace(chunk, TextRange(insertLocation))
                    checker.insert(insertLocation, chunk)

                    assertThat(buffer).contents.isEqualTo(checker.toString())
                    if (buffer.length > 0) {
                        val removeSize = random.nextInt(1, buffer.length)
                        val removeLocation = if (removeSize == buffer.length) {
                            0
                        } else {
                            random.nextInt(buffer.length - removeSize)
                        }
                        val removeRange = TextRange(removeLocation, removeLocation + removeSize)
                        buffer.replace("", removeRange)
                        checker.replace(removeRange.startInclusive, removeRange.endExclusive, "")
                        assertThat(buffer).contents.isEqualTo(checker.toString())
                    }
                }
            }
            return list
        }

        private fun TextBufferStorage.replace(
            replacement: String,
            range: TextRange = TextRange.Unspecified,
            replacementRange: TextRange = TextRange(0, replacement.length)
        ) {
            replace(
                range,
                replacement,
                replacementRange,
                getCharsTrait = { src, srcBegin, srcEnd, dest, destBegin ->
                    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN", "KotlinConstantConditions")
                    (src as java.lang.String).getChars(srcBegin, srcEnd, dest, destBegin)
                }
            )
        }
    }
}
