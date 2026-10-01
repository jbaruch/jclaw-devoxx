package jclaw

import ai.koog.rag.base.files.FileMetadata
import ai.koog.rag.base.files.FileSystemProvider
import ai.koog.rag.base.files.JVMFileSystemProvider
import kotlinx.io.Source
import java.nio.file.Path

/** Native Koog file tools, with application-enforced normalized real-path scope. */
internal class SkillFiles(root: Path) : FileSystemProvider.ReadOnly<Path> by JVMFileSystemProvider.ReadOnly {
    private val root = root.toRealPath()
    private val delegate = JVMFileSystemProvider.ReadOnly

    private fun allowed(path: Path): Path {
        val real = path.toRealPath()
        require(real.startsWith(root)) { "Skill file access outside the configured root is denied" }
        return real
    }

    override fun fromAbsolutePathString(path: String): Path {
        val parsed = Path.of(path)
        require(parsed.isAbsolute) { "Skill file path must be absolute" }
        return allowed(parsed)
    }

    override fun toAbsolutePathString(path: Path): String = allowed(path).toString()
    override fun joinPath(base: Path, vararg parts: String): Path = allowed(delegate.joinPath(allowed(base), *parts))
    override fun parent(path: Path): Path? = allowed(path).parent?.takeIf { it.startsWith(root) }
    override suspend fun metadata(path: Path): FileMetadata? = delegate.metadata(allowed(path))
    override suspend fun getFileContentType(path: Path): FileMetadata.FileContentType = delegate.getFileContentType(allowed(path))
    override suspend fun exists(path: Path): Boolean = delegate.exists(allowed(path))
    override suspend fun readBytes(path: Path): ByteArray = delegate.readBytes(allowed(path))
    override suspend fun inputStream(path: Path): Source = delegate.inputStream(allowed(path))
    override suspend fun size(path: Path): Long = delegate.size(allowed(path))
    override suspend fun list(directory: Path): List<Path> = delegate.list(allowed(directory))
        .filter { runCatching { allowed(it) }.isSuccess }
}
