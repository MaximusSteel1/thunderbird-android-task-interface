package net.thunderbird.feature.taskmail.internal

import net.thunderbird.feature.taskmail.api.TaskMailNavigation
import net.thunderbird.feature.taskmail.internal.data.DefaultTaskMailRepository
import net.thunderbird.feature.taskmail.internal.data.LegacyTaskMailAttachmentMetadataExtractor
import net.thunderbird.feature.taskmail.internal.data.LegacyTaskMailBodyExtractor
import net.thunderbird.feature.taskmail.internal.data.LegacyTaskMailMessageSource
import net.thunderbird.feature.taskmail.internal.data.LegacyTaskMailMimeMessageFactory
import net.thunderbird.feature.taskmail.internal.data.LegacyTaskMailMimeMessageSender
import net.thunderbird.feature.taskmail.internal.data.LegacyTaskMailReplyAttachmentResolver
import net.thunderbird.feature.taskmail.internal.data.LegacyTaskMailReplySourceMessageLoader
import net.thunderbird.feature.taskmail.internal.data.LegacyTaskMailTimelineAttachmentHandler
import net.thunderbird.feature.taskmail.internal.data.TaskMailMessageSource
import net.thunderbird.feature.taskmail.internal.data.TaskMailMimeMessageFactory
import net.thunderbird.feature.taskmail.internal.data.TaskMailMimeMessageSender
import net.thunderbird.feature.taskmail.internal.data.TaskMailReplyAttachmentResolver
import net.thunderbird.feature.taskmail.internal.data.TaskMailReplySourceMessageLoader
import net.thunderbird.feature.taskmail.internal.data.TaskMailTimelineAttachmentHandler
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskMailMessageDetector
import net.thunderbird.feature.taskmail.internal.domain.reply.RealTaskMailReplySender
import net.thunderbird.feature.taskmail.internal.domain.reply.TaskMailReplySender
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskMailRepository
import net.thunderbird.feature.taskmail.internal.domain.usecase.GetTaskSessionDetail
import net.thunderbird.feature.taskmail.internal.domain.usecase.GetTaskWorkspaceSummaries
import net.thunderbird.feature.taskmail.internal.domain.usecase.SendTaskMailReply
import net.thunderbird.feature.taskmail.internal.navigation.DefaultTaskMailNavigation
import net.thunderbird.feature.taskmail.internal.ui.detail.TaskSessionDetailViewModel
import net.thunderbird.feature.taskmail.internal.ui.workspace.TaskWorkspaceViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val taskMailModule: Module = module {
    single<TaskMailNavigation> { DefaultTaskMailNavigation() }

    single { TaskMailMessageDetector() }
    single { LegacyTaskMailBodyExtractor() }
    single {
        LegacyTaskMailAttachmentMetadataExtractor(
            attachmentInfoExtractor = get(),
        )
    }

    single<TaskMailMessageSource> {
        LegacyTaskMailMessageSource(
            accountManager = get(),
            messageListRepository = get(),
            localStoreProvider = get(),
            attachmentMetadataExtractor = get(),
            messageDetector = get(),
        )
    }

    single<TaskMailRepository> {
        DefaultTaskMailRepository(
            messageSource = get(),
            bodyExtractor = get(),
        )
    }

    single<TaskMailReplySourceMessageLoader> {
        LegacyTaskMailReplySourceMessageLoader(
            accountManager = get(),
            localStoreProvider = get(),
        )
    }

    single<TaskMailReplyAttachmentResolver> {
        LegacyTaskMailReplyAttachmentResolver(
            context = get(),
            logger = get(),
        )
    }

    single<TaskMailTimelineAttachmentHandler> {
        LegacyTaskMailTimelineAttachmentHandler(
            context = get(),
            accountManager = get(),
            localStoreProvider = get(),
            messagingController = get(),
            logger = get(),
        )
    }

    single<TaskMailMimeMessageFactory> {
        LegacyTaskMailMimeMessageFactory(
            replyToParser = get(),
            generalSettingsManager = get(),
            replyAttachmentResolver = get(),
        )
    }

    single<TaskMailMimeMessageSender> {
        LegacyTaskMailMimeMessageSender(
            messagingController = get(),
        )
    }

    single<TaskMailReplySender> {
        RealTaskMailReplySender(
            sourceMessageLoader = get(),
            mimeMessageFactory = get(),
            mimeMessageSender = get(),
            logger = get(),
        )
    }

    factory {
        GetTaskWorkspaceSummaries(
            repository = get(),
        )
    }

    factory {
        GetTaskSessionDetail(
            repository = get(),
        )
    }

    factory {
        SendTaskMailReply(
            replySender = get(),
        )
    }

    viewModel {
        TaskWorkspaceViewModel(
            repository = get(),
            getTaskWorkspaceSummaries = get(),
        )
    }

    viewModel {
        TaskSessionDetailViewModel(
            repository = get(),
            getTaskSessionDetail = get(),
            sendTaskMailReply = get(),
            replyAttachmentResolver = get(),
            timelineAttachmentHandler = get(),
        )
    }
}
