@file:Suppress("unused")

package ir.mahdiparastesh.instatools.more

import androidx.documentfile.provider.DocumentFile
import java.io.IOException
import java.util.*

class DocFileTreeWalk private constructor(
    private val start: DocumentFile,
    private val direction: DocFileTreeWalkDirection = DocFileTreeWalkDirection.TOP_DOWN,
    private val onEnter: ((DocumentFile) -> Boolean)?,
    private val onLeave: ((DocumentFile) -> Unit)?,
    private val onFail: ((f: DocumentFile, e: IOException) -> Unit)?,
    private val maxDepth: Int = Int.MAX_VALUE
) : Sequence<DocumentFile> {
    internal constructor(
        start: DocumentFile,
        direction: DocFileTreeWalkDirection = DocFileTreeWalkDirection.TOP_DOWN
    ) : this(start, direction, null, null, null)

    override fun iterator(): Iterator<DocumentFile> = DocFileTreeWalkIterator()

    private abstract class WalkState(val root: DocumentFile) {
        abstract fun step(): DocumentFile?
    }

    private abstract class DirectoryState(rootDir: DocumentFile) : WalkState(rootDir) {
        init {
            if (_Assertions.ENABLED)
                assert(rootDir.isDirectory) { "rootDir must be verified to be directory beforehand." }
        }
    }

    @Suppress("KotlinConstantConditions")
    private inner class DocFileTreeWalkIterator : AbstractIterator<DocumentFile>() {
        private val state = ArrayDeque<WalkState>()

        init {
            when {
                start.isDirectory -> state.push(directoryState(start))
                start.isFile -> state.push(SingleFileState(start))
                else -> done()
            }
        }

        override fun computeNext() {
            val nextFile = gotoNext()
            if (nextFile != null) setNext(nextFile) else done()
        }

        private fun directoryState(root: DocumentFile): DirectoryState = when (direction) {
            DocFileTreeWalkDirection.TOP_DOWN -> TopDownDirectoryState(root)
            DocFileTreeWalkDirection.BOTTOM_UP -> BottomUpDirectoryState(root)
        }

        private tailrec fun gotoNext(): DocumentFile? {
            val topState = state.peek() ?: return null
            val file = topState.step()
            return if (file == null) {
                state.pop()
                gotoNext()
            } else if (file == topState.root || !file.isDirectory || state.size >= maxDepth)
                file
            else {
                state.push(directoryState(file))
                gotoNext()
            }
        }

        private inner class BottomUpDirectoryState(rootDir: DocumentFile) :
            DirectoryState(rootDir) {

            private var rootVisited = false
            private var fileList: Array<DocumentFile>? = null
            private var fileIndex = 0
            private var failed = false

            override fun step(): DocumentFile? {
                if (!failed && fileList == null) {
                    if (onEnter?.invoke(root) == false) return null

                    fileList = root.listFiles()
                    if (fileList == null) {
                        onFail?.invoke(
                            root, AccessDeniedException(
                                file = root,
                                reason = "Cannot list files in a directory"
                            )
                        )
                        failed = true
                    }
                }
                return if (fileList != null && fileIndex < fileList!!.size)
                    fileList!![fileIndex++]
                else if (!rootVisited) {
                    rootVisited = true
                    root
                } else {
                    onLeave?.invoke(root)
                    null
                }
            }
        }

        private inner class TopDownDirectoryState(rootDir: DocumentFile) : DirectoryState(rootDir) {
            private var rootVisited = false
            private var fileList: Array<DocumentFile>? = null
            private var fileIndex = 0

            override fun step(): DocumentFile? {
                if (!rootVisited) {
                    if (onEnter?.invoke(root) == false) return null
                    rootVisited = true
                    return root
                } else if (fileList == null || fileIndex < fileList!!.size) {
                    if (fileList == null) {
                        fileList = root.listFiles()
                        if (fileList == null) onFail?.invoke(
                            root, AccessDeniedException(
                                file = root,
                                reason = "Cannot list files in a directory"
                            )
                        )
                        if (fileList == null || fileList!!.isEmpty()) {
                            onLeave?.invoke(root)
                            return null
                        }
                    }
                    return fileList!![fileIndex++]
                } else {
                    onLeave?.invoke(root)
                    return null
                }
            }
        }

        private inner class SingleFileState(rootFile: DocumentFile) : WalkState(rootFile) {
            private var visited: Boolean = false

            init {
                if (_Assertions.ENABLED)
                    assert(rootFile.isFile) { "rootFile must be verified to be file beforehand." }
            }

            override fun step(): DocumentFile? {
                if (visited) return null
                visited = true
                return root
            }
        }
    }

    fun onEnter(function: (DocumentFile) -> Boolean): DocFileTreeWalk {
        return DocFileTreeWalk(
            start,
            direction,
            onEnter = function,
            onLeave = onLeave,
            onFail = onFail,
            maxDepth = maxDepth
        )
    }

    fun onLeave(function: (DocumentFile) -> Unit): DocFileTreeWalk {
        return DocFileTreeWalk(
            start,
            direction,
            onEnter = onEnter,
            onLeave = function,
            onFail = onFail,
            maxDepth = maxDepth
        )
    }

    fun onFail(function: (DocumentFile, IOException) -> Unit): DocFileTreeWalk {
        return DocFileTreeWalk(
            start,
            direction,
            onEnter = onEnter,
            onLeave = onLeave,
            onFail = function,
            maxDepth = maxDepth
        )
    }

    fun maxDepth(depth: Int): DocFileTreeWalk {
        if (depth <= 0)
            throw IllegalArgumentException("depth must be positive, but was $depth.")
        return DocFileTreeWalk(start, direction, onEnter, onLeave, onFail, depth)
    }
}

fun DocumentFile.walk(direction: DocFileTreeWalkDirection = DocFileTreeWalkDirection.TOP_DOWN): DocFileTreeWalk =
    DocFileTreeWalk(this, direction)

fun DocumentFile.walkTopDown(): DocFileTreeWalk = walk(DocFileTreeWalkDirection.TOP_DOWN)

fun DocumentFile.walkBottomUp(): DocFileTreeWalk = walk(DocFileTreeWalkDirection.BOTTOM_UP)

enum class DocFileTreeWalkDirection { TOP_DOWN, BOTTOM_UP }

@Suppress("ClassName")
internal object _Assertions {
    @JvmField
    @PublishedApi
    internal val ENABLED: Boolean = javaClass.desiredAssertionStatus()
}

class AccessDeniedException(
    val file: DocumentFile,
    private val other: DocumentFile? = null,
    val reason: String? = null
) : IOException(StringBuilder(file.toString()).apply {
    if (other != null) append(" -> $other")
    if (reason != null) append(": $reason")
}.toString())
