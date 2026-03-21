package net.thunderbird.feature.taskmail.internal.data.cache

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import java.nio.file.Files
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import net.thunderbird.feature.taskmail.internal.preview.TaskMailPreviewData

class FileBackedTaskSessionDetailRepositoryTest {

    @Test
    fun `load should ignore old storage version and persist current version`() = runTest {
        val tempDirectory = Files.createTempDirectory("taskmail-session-detail-repo").toFile()
        val detail = TaskMailPreviewData.sessionDetails.first()
        val codec = TaskSessionDetailJsonCodec()
        val storageFile = tempDirectory.resolve("taskmail_session_details.json")
        storageFile.writeText(
            buildJsonObject {
                put("version", JsonPrimitive(1))
                put(
                    "sessionDetails",
                    JsonArray(
                        listOf(JsonPrimitive(codec.encode(detail))),
                    ),
                )
            }.toString(),
        )

        val staleRepository = FileBackedTaskSessionDetailRepository(
            storageDirectory = tempDirectory,
            codec = codec,
        )
        assertThat(staleRepository.getTaskSessionDetails()).isEmpty()

        staleRepository.replaceAllSessionDetails(listOf(detail))

        val reloadedRepository = FileBackedTaskSessionDetailRepository(
            storageDirectory = tempDirectory,
            codec = codec,
        )
        assertThat(reloadedRepository.getTaskSessionDetails()).containsExactly(detail)
    }
}
