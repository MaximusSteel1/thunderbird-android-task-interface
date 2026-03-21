package net.thunderbird.feature.taskmail.internal.data

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import net.thunderbird.feature.taskmail.internal.domain.model.TaskRichTextBlock
import net.thunderbird.feature.taskmail.internal.domain.model.TaskRichTextInline
import org.junit.Test

internal class TaskMailRichTextRepresentativeSampleTest {
    private val testSubject = TaskMailRichTextProjector()
    private val representativeFailedStackFrame =
        "at async main (file:///E:/projects/mail_based_task_manager/" +
            "scripts/codex_sdk_sidecar/dist/index.js:151:22)"

    @Test
    fun `project representative external delivery and notice sample`() {
        val result = testSubject.project(
            html = """
                <html>
                  <body style="margin:0; padding:12px; background:#efe6d4;">
                    <article class="task-mail">
                      <section class="task-artifacts">
                        <h2>Artifacts</h2>
                        <ul>
                          <li>thunderbird-taskmail-foss-debug.apk</li>
                        </ul>
                      </section>
                      <section class="task-external-deliveries">
                        <h2>External Deliveries</h2>
                        <ul>
                          <li>
                            <a href="https://mailbot-1412015279.cos.ap-shanghai.myqcloud.com/mail-runner/thread_072/20260320_001950_88c5/thunderbird-taskmail-foss-debug.apk.bin">
                              thunderbird-taskmail-foss-debug.apk
                            </a>
                            : Delivered via COS (46.7 MB, expires 2026-03-26T16:25:17+00:00)
                          </li>
                        </ul>
                      </section>
                      <section class="task-attachment-notices">
                        <h2>Attachment Notices</h2>
                        <ul>
                          <li>
                            COS default domain blocks direct APK distribution. The external download uses a .bin object
                            name; rename the downloaded file back to thunderbird-taskmail-foss-debug.apk if needed.
                          </li>
                        </ul>
                      </section>
                    </article>
                  </body>
                </html>
            """.trimIndent(),
            attachments = emptyList(),
        )

        assertThat(result).isNotNull()
        assertRepresentativeSectionBlocks(result!!.blocks)
    }

    @Test
    fun `project representative failed sample as code block`() {
        val result = testSubject.project(
            html = """
                <html>
                  <body>
                    <article class="task-mail">
                      <section class="task-reply">
                        <h2>Failure Output</h2>
                        <p>Representative failed-mail content stays scannable without collapsing the detail view.</p>
                        <pre>
Error: Codex Exec exited with code 1: 2026-03-18T12:23:54.044393Z ERROR codex_core::models_manager::manager: failed to refresh available models: timeout waiting for child process to exit
Reading prompt from stdin...
2026-03-18T12:23:59.102450Z ERROR codex_core::models_manager::manager: failed to refresh available models: timeout waiting for child process to exit

    at CodexExec.run (file:///E:/projects/mail_based_task_manager/scripts/codex_sdk_sidecar/node_modules/@openai/codex-sdk/dist/index.js:280:15)
    at process.processTicksAndRejections (node:internal/process/task_queues:103:5)
    at async Thread.runStreamedInternal (file:///E:/projects/mail_based_task_manager/scripts/codex_sdk_sidecar/node_modules/@openai/codex-sdk/dist/index.js:78:24)
    at async main (file:///E:/projects/mail_based_task_manager/scripts/codex_sdk_sidecar/dist/index.js:151:22)
                        </pre>
                      </section>
                    </article>
                  </body>
                </html>
            """.trimIndent(),
            attachments = emptyList(),
        )

        assertThat(result).isNotNull()
        val blocks = result!!.blocks
        assertThat(blocks.size).isEqualTo(3)
        assertThat(blocks[0]).isEqualTo(
            TaskRichTextBlock.Heading(
                level = 2,
                inlines = listOf(TaskRichTextInline.Text("Failure Output")),
            ),
        )
        assertThat(blocks[1]).isEqualTo(
            TaskRichTextBlock.Paragraph(
                inlines = listOf(
                    TaskRichTextInline.Text(
                        "Representative failed-mail content stays scannable without collapsing the detail view.",
                    ),
                ),
            ),
        )
        val codeBlock = blocks[2] as TaskRichTextBlock.CodeBlock
        assertThat(codeBlock.languageHint).isEqualTo(null)
        assertThat(codeBlock.text.contains("Codex Exec exited with code 1")).isEqualTo(true)
        assertThat(
            codeBlock.text.contains(representativeFailedStackFrame),
        ).isEqualTo(true)
    }

    private fun assertRepresentativeSectionBlocks(blocks: List<TaskRichTextBlock>) {
        assertThat(blocks.size).isEqualTo(6)
        assertThat(blocks[0]).isEqualTo(
            TaskRichTextBlock.Heading(
                level = 2,
                inlines = listOf(TaskRichTextInline.Text("Artifacts")),
            ),
        )
        assertThat(blocks[2]).isEqualTo(
            TaskRichTextBlock.Heading(
                level = 2,
                inlines = listOf(TaskRichTextInline.Text("External Deliveries")),
            ),
        )
        assertThat(blocks[4]).isEqualTo(
            TaskRichTextBlock.Heading(
                level = 2,
                inlines = listOf(TaskRichTextInline.Text("Attachment Notices")),
            ),
        )
        assertThat(
            bulletListText(blocks[1]).contains("thunderbird-taskmail-foss-debug.apk"),
        ).isEqualTo(true)
        assertThat(
            bulletListText(blocks[3]).contains("Delivered via COS (46.7 MB"),
        ).isEqualTo(true)
        assertThat(
            bulletListText(blocks[5]).contains("COS default domain blocks direct APK distribution."),
        ).isEqualTo(true)
    }

    private fun bulletListText(block: TaskRichTextBlock): String {
        val list = block as TaskRichTextBlock.BulletList
        return list.items.flatten().joinToString(separator = " ") { item ->
            item.toReadableText()
        }
    }

    private fun TaskRichTextBlock.toReadableText(): String {
        return when (this) {
            is TaskRichTextBlock.Paragraph -> inlines.joinToString(separator = "") { inline ->
                inline.textValue()
            }

            is TaskRichTextBlock.Heading -> inlines.joinToString(separator = "") { inline ->
                inline.textValue()
            }

            is TaskRichTextBlock.CodeBlock -> text
            is TaskRichTextBlock.BulletList -> items.flatten().joinToString(separator = " ") { item ->
                item.toReadableText()
            }

            is TaskRichTextBlock.OrderedList -> items.flatten().joinToString(separator = " ") { item ->
                item.toReadableText()
            }

            is TaskRichTextBlock.Quote -> blocks.joinToString(separator = " ") { block ->
                block.toReadableText()
            }

            is TaskRichTextBlock.Table -> rows.flatten().flatten().joinToString(separator = " ") { inline ->
                inline.textValue()
            }

            is TaskRichTextBlock.InlineImage -> altText ?: caption ?: contentId.orEmpty()
            is TaskRichTextBlock.UnsupportedHtml -> fallbackText
            TaskRichTextBlock.Divider -> ""
        }
    }

    private fun TaskRichTextInline.textValue(): String {
        return when (this) {
            is TaskRichTextInline.Text -> text
            is TaskRichTextInline.Strong -> text
            is TaskRichTextInline.Emphasis -> text
            is TaskRichTextInline.Code -> text
            is TaskRichTextInline.Link -> text
        }
    }
}
