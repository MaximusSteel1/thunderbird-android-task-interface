package net.thunderbird.feature.taskmail.internal.ui.detail

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import net.thunderbird.feature.taskmail.internal.debug.TaskMailDebugPreviewDetailContent
import net.thunderbird.feature.taskmail.internal.preview.TaskMailPreviewData

@Composable
@Preview(name = "Question", showBackground = true)
internal fun TaskSessionDetailPreview() {
    TaskMailDebugPreviewDetailContent(detail = TaskMailPreviewData.questionSessionDetail)
}

@Composable
@Preview(name = "Plain Text Status", showBackground = true)
internal fun TaskSessionDetailPlainTextStatusPreview() {
    TaskMailDebugPreviewDetailContent(detail = TaskMailPreviewData.plainTextStatusSessionDetail)
}

@Composable
@Preview(name = "Inline PNG", showBackground = true)
internal fun TaskSessionDetailInlinePngPreview() {
    TaskMailDebugPreviewDetailContent(detail = TaskMailPreviewData.richInlinePngSessionDetail)
}

@Composable
@Preview(name = "Static SVG", showBackground = true)
internal fun TaskSessionDetailStaticSvgPreview() {
    TaskMailDebugPreviewDetailContent(detail = TaskMailPreviewData.richStaticSvgSessionDetail)
}

@Composable
@Preview(name = "Unmatched Image Fallback", showBackground = true)
internal fun TaskSessionDetailUnmatchedImageFallbackPreview() {
    TaskMailDebugPreviewDetailContent(detail = TaskMailPreviewData.richUnmatchedImageFallbackSessionDetail)
}
