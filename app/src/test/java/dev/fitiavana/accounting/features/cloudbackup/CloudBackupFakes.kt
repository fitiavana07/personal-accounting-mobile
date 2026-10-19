package dev.fitiavana.accounting.features.cloudbackup

import java.io.IOException

class FakeCloudBackupStore : CloudBackupStore {
    override var folderUri: String? = null
        private set
    override var folderName: String? = null
        private set
    override var enabled = false
    override var enabledAt = 0L
    override var passphrase: String? = null
    override var lastSuccessAt = 0L
    override var lastError: CloudBackupError? = null
    override var consecutiveFailures = 0
    override var failureNotified = false

    override fun setFolder(uri: String, name: String) {
        folderUri = uri
        folderName = name
    }

    override fun clearFolder() {
        folderUri = null
        folderName = null
    }
}

/** An in-memory folder. Each created file is a little newer than the previous one. */
class FakeCloudFolder(var name: String = "Accounting-dev") : CloudFolder {
    val files = mutableListOf<CloudBackupFile>()
    val contents = mutableMapOf<String, ByteArray>()
    var failing = false
    var failCreate = false
    var failDelete = false
    private var counter = 0L

    private fun checkReachable() {
        if (failing) throw IOException("gone")
    }

    override fun displayName(): String {
        checkReachable()
        return name
    }

    override fun list(): List<CloudBackupFile> {
        checkReachable()
        return files.toList()
    }

    override fun create(name: String, bytes: ByteArray): CloudBackupFile {
        checkReachable()
        if (failCreate) throw IOException("cannot write")
        return add(name, bytes)
    }

    override fun read(id: String): ByteArray {
        checkReachable()
        return contents[id] ?: throw IOException("no such file")
    }

    override fun delete(id: String) {
        checkReachable()
        if (failDelete) throw IOException("cannot delete")
        files.removeAll { it.id == id }
        contents.remove(id)
    }

    /** Puts a file in the folder as if the provider had it already. */
    fun add(name: String, bytes: ByteArray = ByteArray(0), modifiedAt: Long = ++counter): CloudBackupFile {
        val file = CloudBackupFile(id = "id-${files.size}-$name", name = name, modifiedAt = modifiedAt, sizeBytes = bytes.size.toLong())
        files += file
        contents[file.id] = bytes
        return file
    }

    fun names() = files.map { it.name }.sorted()
}

class FakeCloudBackupScheduler : CloudBackupScheduler {
    val scheduled = mutableListOf<Long>()
    var cancelled = 0
    override fun scheduleAt(triggerAtMillis: Long) {
        scheduled += triggerAtMillis
    }

    override fun cancel() {
        cancelled++
    }
}

class FakeCloudBackupNotifier : CloudBackupNotifier {
    var failing = 0
    var cleared = 0
    override fun notifyFailing() {
        failing++
    }

    override fun clear() {
        cleared++
    }
}
