package net.thunderbird.feature.taskmail.internal.domain.parser

import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailProjectSyncProject
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailProjectSyncResult
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailProjectSyncRoot

internal class TaskMailProjectSyncResultParser {

    fun parse(
        bodyText: String,
        receivedAt: Long,
    ): TaskMailProjectSyncResult? {
        val normalizedLines = bodyText.normalizeLineEndings()
            .lineSequence()
            .map(String::trim)
            .toList()
        val scannedAt = normalizedLines.firstValueAfterPrefix("Scanned at:")
        val rootsStartIndex = normalizedLines.indexOf("Scanned roots:")
        if (rootsStartIndex == -1) return null

        val rootsByPath = linkedMapOf<String, MutableRoot>()
        val sectionStartIndex = parseRootStatuses(
            lines = normalizedLines,
            startIndex = rootsStartIndex + 1,
            rootsByPath = rootsByPath,
        )
        parseRootSections(
            lines = normalizedLines,
            startIndex = sectionStartIndex,
            rootsByPath = rootsByPath,
        )

        val roots = rootsByPath.values.map(MutableRoot::toResultRoot)
        return if (roots.isEmpty()) {
            null
        } else {
            TaskMailProjectSyncResult(
                receivedAt = receivedAt,
                scannedAt = scannedAt,
                roots = roots,
            )
        }
    }

    private fun parseRootStatuses(
        lines: List<String>,
        startIndex: Int,
        rootsByPath: MutableMap<String, MutableRoot>,
    ): Int {
        var index = startIndex
        while (index < lines.size) {
            val line = lines[index]
            if (line.isEmpty()) {
                return index + 1
            }

            parseRootStatus(line)?.let { root ->
                rootsByPath.getOrPut(root.rootPath) {
                    MutableRoot(rootPath = root.rootPath)
                }.merge(root)
            }
            index += 1
        }

        return index
    }

    private fun parseRootSections(
        lines: List<String>,
        startIndex: Int,
        rootsByPath: MutableMap<String, MutableRoot>,
    ) {
        var currentRootPath: String? = null
        for (index in startIndex until lines.size) {
            val line = lines[index]
            when {
                line.isEmpty() -> Unit
                line.startsWith(FOOTER_PREFIX) -> return
                line.startsWith("- ") -> {
                    val rootPath = currentRootPath
                    val project = parseProject(line)
                    if (rootPath != null && project != null) {
                        rootsByPath.getOrPut(rootPath) {
                            MutableRoot(rootPath = rootPath)
                        }.projects += project
                    }
                }
                else -> {
                    currentRootPath = line
                    rootsByPath.getOrPut(line) { MutableRoot(rootPath = line) }
                }
            }
        }
    }

    private fun parseRootStatus(line: String): MutableRoot? {
        val match = ROOT_STATUS_REGEX.matchEntire(line) ?: return null
        val rootPath = match.groupValues[ROOT_PATH_GROUP_INDEX]
        val availability = match.groupValues[AVAILABILITY_GROUP_INDEX]
        val details = match.groupValues.getOrNull(ROOT_DETAILS_GROUP_INDEX)
            ?.trim()
            .takeUnless { it.isNullOrEmpty() }

        return MutableRoot(
            rootPath = rootPath,
            isAvailable = availability.equals("available", ignoreCase = true),
            folderCount = details
                ?.let(FOLDER_COUNT_REGEX::find)
                ?.groupValues
                ?.getOrNull(FOLDER_COUNT_GROUP_INDEX)
                ?.toIntOrNull(),
            unavailableReason = details.takeUnless {
                availability.equals("available", ignoreCase = true)
            },
        )
    }

    private fun parseProject(line: String): TaskMailProjectSyncProject? {
        val match = PROJECT_ENTRY_REGEX.matchEntire(line) ?: return null

        return TaskMailProjectSyncProject(
            displayName = match.groupValues[PROJECT_NAME_GROUP_INDEX],
            repoPath = match.groupValues[PROJECT_PATH_GROUP_INDEX],
        )
    }
}

private class MutableRoot(
    val rootPath: String,
    var isAvailable: Boolean = true,
    var folderCount: Int? = null,
    var unavailableReason: String? = null,
    val projects: MutableList<TaskMailProjectSyncProject> = mutableListOf(),
) {
    fun merge(other: MutableRoot) {
        isAvailable = other.isAvailable
        folderCount = other.folderCount
        unavailableReason = other.unavailableReason
    }

    fun toResultRoot(): TaskMailProjectSyncRoot {
        return TaskMailProjectSyncRoot(
            rootPath = rootPath,
            isAvailable = isAvailable,
            folderCount = folderCount,
            unavailableReason = unavailableReason,
            projects = projects.toList(),
        )
    }
}

private fun String.normalizeLineEndings(): String {
    return replace("\r\n", "\n")
        .replace('\r', '\n')
}

private fun List<String>.firstValueAfterPrefix(prefix: String): String? {
    return firstNotNullOfOrNull { line ->
        line.removePrefix(prefix)
            .takeIf { line.startsWith(prefix) }
            ?.trim()
            ?.takeIf(String::isNotEmpty)
    }
}

private val ROOT_STATUS_REGEX = Regex("""^- (.+?) \| (available|unavailable)(?: \| (.+))?$""")
private val PROJECT_ENTRY_REGEX = Regex("""^- (.+?) \| (.+)$""")
private val FOLDER_COUNT_REGEX = Regex("""(\d+)""")
private const val ROOT_PATH_GROUP_INDEX = 1
private const val AVAILABILITY_GROUP_INDEX = 2
private const val ROOT_DETAILS_GROUP_INDEX = 3
private const val FOLDER_COUNT_GROUP_INDEX = 1
private const val PROJECT_NAME_GROUP_INDEX = 1
private const val PROJECT_PATH_GROUP_INDEX = 2
private const val FOOTER_PREFIX = "To start a task,"
