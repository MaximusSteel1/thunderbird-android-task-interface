package net.thunderbird.feature.taskmail.internal

import com.fsck.k9.Preferences
import java.io.File
import net.thunderbird.feature.taskmail.api.TaskMailNavigation
import net.thunderbird.feature.taskmail.internal.data.DefaultTaskMailBotMailboxSettingsRepository
import net.thunderbird.feature.taskmail.internal.data.DefaultTaskMailProjectSyncDebugSettingsRepository
import net.thunderbird.feature.taskmail.internal.data.DefaultTaskMailForegroundRefreshTickerFactory
import net.thunderbird.feature.taskmail.internal.data.DefaultTaskMailProjectSyncRepository
import net.thunderbird.feature.taskmail.internal.data.DefaultTaskTransportConfigRepository
import net.thunderbird.feature.taskmail.internal.data.LegacyTaskMailAttachmentMetadataExtractor
import net.thunderbird.feature.taskmail.internal.data.LegacyTaskMailBodyExtractor
import net.thunderbird.feature.taskmail.internal.data.LegacyTaskMailMimeMessageFactory
import net.thunderbird.feature.taskmail.internal.data.LegacyTaskMailMimeMessageSender
import net.thunderbird.feature.taskmail.internal.data.LegacyTaskMailNewTaskMimeMessageFactory
import net.thunderbird.feature.taskmail.internal.data.LegacyTaskMailReplyAttachmentResolver
import net.thunderbird.feature.taskmail.internal.data.LegacyTaskMailReplySourceMessageLoader
import net.thunderbird.feature.taskmail.internal.data.LegacyTaskMailSenderAccountSource
import net.thunderbird.feature.taskmail.internal.data.LegacyTaskMailStoreChangeObserver
import net.thunderbird.feature.taskmail.internal.data.LegacyTaskMailSyncRequester
import net.thunderbird.feature.taskmail.internal.data.LegacyTaskMailTimelineAttachmentHandler
import net.thunderbird.feature.taskmail.internal.data.MailStoreBackedTaskMailProjectSyncResultReader
import net.thunderbird.feature.taskmail.internal.data.PreferencesBackedTaskMailSettingsStorage
import net.thunderbird.feature.taskmail.internal.data.SnapshotBackedTaskMailRepository
import net.thunderbird.feature.taskmail.internal.data.StorageBackedTaskMailDestinationAddressProvider
import net.thunderbird.feature.taskmail.internal.data.TaskMailDestinationAddressProvider
import net.thunderbird.feature.taskmail.internal.data.TaskMailForegroundRefreshTickerFactory
import net.thunderbird.feature.taskmail.internal.data.TaskMailMessage
import net.thunderbird.feature.taskmail.internal.data.TaskMailMessageSource
import net.thunderbird.feature.taskmail.internal.data.TaskMailMimeMessageFactory
import net.thunderbird.feature.taskmail.internal.data.TaskMailMimeMessageSender
import net.thunderbird.feature.taskmail.internal.data.TaskMailNewTaskMimeMessageFactory
import net.thunderbird.feature.taskmail.internal.data.TaskMailProjectSyncRequester
import net.thunderbird.feature.taskmail.internal.data.TaskMailProjectSyncResultReader
import net.thunderbird.feature.taskmail.internal.data.TaskMailReplyAttachmentResolver
import net.thunderbird.feature.taskmail.internal.data.TaskMailReplySourceMessageLoader
import net.thunderbird.feature.taskmail.internal.data.TaskMailSenderAccountSource
import net.thunderbird.feature.taskmail.internal.data.TaskMailSessionProjector
import net.thunderbird.feature.taskmail.internal.data.TaskMailSettingsStorage
import net.thunderbird.feature.taskmail.internal.data.TaskMailStoreChangeObserver
import net.thunderbird.feature.taskmail.internal.data.TaskMailSyncRequester
import net.thunderbird.feature.taskmail.internal.data.TaskMailTimelineAttachmentHandler
import net.thunderbird.feature.taskmail.internal.data.TaskWorkspaceSummaryProjector
import net.thunderbird.feature.taskmail.internal.data.TransportBackedTaskMailProjectSyncRequester
import net.thunderbird.feature.taskmail.internal.data.cache.CachedTaskMailMessageSource
import net.thunderbird.feature.taskmail.internal.data.cache.EmailIngressPayloadJsonCodec
import net.thunderbird.feature.taskmail.internal.data.cache.FileBackedMessageSyncStateRepository
import net.thunderbird.feature.taskmail.internal.data.cache.FileBackedTaskMailNewTaskSendRecordRepository
import net.thunderbird.feature.taskmail.internal.data.cache.FileBackedTaskMailSessionActionSendRecordRepository
import net.thunderbird.feature.taskmail.internal.data.cache.FileBackedTaskSessionDetailRepository
import net.thunderbird.feature.taskmail.internal.data.cache.FileBackedUnifiedMessageRepository
import net.thunderbird.feature.taskmail.internal.data.cache.TaskMailMessageJsonCodec
import net.thunderbird.feature.taskmail.internal.data.cache.TaskMailNewTaskSendRecordJsonCodec
import net.thunderbird.feature.taskmail.internal.data.cache.TaskMailSessionActionSendRecordJsonCodec
import net.thunderbird.feature.taskmail.internal.data.cache.TaskSessionDetailJsonCodec
import net.thunderbird.feature.taskmail.internal.data.debug.FileBackedTaskMailProjectSyncDebugRecorder
import net.thunderbird.feature.taskmail.internal.data.debug.FileBackedTaskMailFileSampleStore
import net.thunderbird.feature.taskmail.internal.data.debug.FileBackedTaskMailTransportProbeEventStore
import net.thunderbird.feature.taskmail.internal.data.debug.TaskMailFileSampleStore
import net.thunderbird.feature.taskmail.internal.data.debug.TaskMailProjectSyncDebugRecorder
import net.thunderbird.feature.taskmail.internal.data.debug.TaskMailTransportProbeEventStore
import net.thunderbird.feature.taskmail.internal.data.direct.TaskMailDirectSessionProjector
import net.thunderbird.feature.taskmail.internal.data.facade.OkHttpTaskMailCreateSessionFacadeClient
import net.thunderbird.feature.taskmail.internal.data.ingress.EmailIngress
import net.thunderbird.feature.taskmail.internal.data.ingress.EmailIngressMessage
import net.thunderbird.feature.taskmail.internal.data.ingress.MessageIngress
import net.thunderbird.feature.taskmail.internal.data.parser.EmailMessageParser
import net.thunderbird.feature.taskmail.internal.data.parser.IncomingMessageParser
import net.thunderbird.feature.taskmail.internal.data.relay.DefaultRelayBootstrapManager
import net.thunderbird.feature.taskmail.internal.data.relay.OkHttpRelayFileSurfaceClient
import net.thunderbird.feature.taskmail.internal.data.relay.OkHttpRelayConnectionClient
import net.thunderbird.feature.taskmail.internal.data.relay.OkHttpRelayHealthProbe
import net.thunderbird.feature.taskmail.internal.data.relay.RelayFileSurfaceClient
import net.thunderbird.feature.taskmail.internal.data.relay.RelayBootstrapManager
import net.thunderbird.feature.taskmail.internal.data.relay.RelayConnectionClient
import net.thunderbird.feature.taskmail.internal.data.relay.RelayTaskMailFileSampleSender
import net.thunderbird.feature.taskmail.internal.data.relay.RelayHealthProbe
import net.thunderbird.feature.taskmail.internal.data.relay.RelayTaskMailDirectNewTaskSender
import net.thunderbird.feature.taskmail.internal.data.relay.RelayTaskMailDirectProjectSyncSender
import net.thunderbird.feature.taskmail.internal.data.relay.RelayTaskMailDirectSessionActionSender
import net.thunderbird.feature.taskmail.internal.data.relay.RelayTaskMailDirectSessionDetailSubscriber
import net.thunderbird.feature.taskmail.internal.data.relay.RelayTaskMailTransportProbeSender
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayProtocolJsonCodec
import net.thunderbird.feature.taskmail.internal.data.transport.EmailTaskMailNewTaskTransport
import net.thunderbird.feature.taskmail.internal.data.transport.EmailTaskMailReplyTransport
import net.thunderbird.feature.taskmail.internal.data.transport.TaskMailNewTaskTransport
import net.thunderbird.feature.taskmail.internal.data.transport.TaskMailReplyTransport
import net.thunderbird.feature.taskmail.internal.domain.newtask.RealTaskMailNewTaskSender
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailCreateSessionClient
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailDirectNewTaskSender
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailNewTaskSender
import net.thunderbird.feature.taskmail.internal.domain.filesample.TaskMailFileSampleSender
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskMailMessageDetector
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskMailProjectSyncResultParser
import net.thunderbird.feature.taskmail.internal.domain.projectsync.TaskMailDirectProjectSyncSender
import net.thunderbird.feature.taskmail.internal.domain.reply.RealTaskMailReplySender
import net.thunderbird.feature.taskmail.internal.domain.reply.TaskMailReplySender
import net.thunderbird.feature.taskmail.internal.domain.repository.MessageSyncStateRepository
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskMailBotMailboxSettingsRepository
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskMailNewTaskSendRecordRepository
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskMailProjectSyncDebugSettingsRepository
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskMailProjectSyncRepository
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskMailRepository
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskMailSessionActionSendRecordRepository
import net.thunderbird.feature.taskmail.internal.domain.sessionaction.TaskMailDirectSessionActionSender
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskSessionDetailRepository
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskTransportConfigRepository
import net.thunderbird.feature.taskmail.internal.domain.transportprobe.TaskMailTransportProbeSender
import net.thunderbird.feature.taskmail.internal.domain.repository.UnifiedMessageRepository
import net.thunderbird.feature.taskmail.internal.domain.usecase.DefaultObserveTaskMailDirectSessionDetail
import net.thunderbird.feature.taskmail.internal.domain.usecase.CreateTaskMailSession
import net.thunderbird.feature.taskmail.internal.domain.usecase.GetLatestTaskMailNewTaskSendRecord
import net.thunderbird.feature.taskmail.internal.domain.usecase.GetLatestTaskMailProjectSyncResult
import net.thunderbird.feature.taskmail.internal.domain.usecase.GetLatestTaskMailSessionActionSendRecord
import net.thunderbird.feature.taskmail.internal.domain.usecase.GetTaskMailBotMailboxSettings
import net.thunderbird.feature.taskmail.internal.domain.usecase.GetTaskMailSenderAccounts
import net.thunderbird.feature.taskmail.internal.domain.usecase.GetTaskSessionDetail
import net.thunderbird.feature.taskmail.internal.domain.usecase.GetTaskSessionDetails
import net.thunderbird.feature.taskmail.internal.domain.usecase.ObserveTaskMailDirectSessionDetail
import net.thunderbird.feature.taskmail.internal.domain.usecase.ObserveTaskMailStoreChanges
import net.thunderbird.feature.taskmail.internal.domain.usecase.RecordTaskMailNewTaskSendRecord
import net.thunderbird.feature.taskmail.internal.domain.usecase.RecordTaskMailSessionActionSendRecord
import net.thunderbird.feature.taskmail.internal.domain.usecase.RefreshTaskMail
import net.thunderbird.feature.taskmail.internal.domain.usecase.RequestTaskMailProjectSync
import net.thunderbird.feature.taskmail.internal.domain.usecase.RunTaskMailDirectDispatch
import net.thunderbird.feature.taskmail.internal.domain.usecase.RunTaskMailDirectOrFallback
import net.thunderbird.feature.taskmail.internal.domain.usecase.SaveTaskMailBotMailboxSettings
import net.thunderbird.feature.taskmail.internal.domain.usecase.SendTaskMailDirectNewTask
import net.thunderbird.feature.taskmail.internal.domain.usecase.SendTaskMailFileSample
import net.thunderbird.feature.taskmail.internal.domain.usecase.SendTaskMailDirectSessionAction
import net.thunderbird.feature.taskmail.internal.domain.usecase.SendTaskMailNewTask
import net.thunderbird.feature.taskmail.internal.domain.usecase.SendTaskMailReply
import net.thunderbird.feature.taskmail.internal.domain.usecase.SendTaskMailTransportProbe
import net.thunderbird.feature.taskmail.internal.domain.usecase.SyncTaskMailCache
import net.thunderbird.feature.taskmail.internal.navigation.DefaultTaskMailNavigation
import net.thunderbird.feature.taskmail.internal.sync.MessageSyncCoordinator
import net.thunderbird.feature.taskmail.internal.sync.TaskMailCacheSyncCoordinator
import net.thunderbird.feature.taskmail.internal.ui.detail.TaskSessionDetailViewModel
import net.thunderbird.feature.taskmail.internal.ui.newtask.TaskNewTaskViewModel
import net.thunderbird.feature.taskmail.internal.ui.projectsync.TaskProjectSyncViewModel
import net.thunderbird.feature.taskmail.internal.ui.relaydebug.TaskMailRelayDebugViewModel
import net.thunderbird.feature.taskmail.internal.ui.settings.TaskMailSettingsViewModel
import net.thunderbird.feature.taskmail.internal.ui.workspace.TaskWorkspaceViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val taskMailModule: Module = module {
    single<TaskMailNavigation> { DefaultTaskMailNavigation() }
    single<TaskMailSettingsStorage> {
        PreferencesBackedTaskMailSettingsStorage(
            preferences = get<Preferences>(),
        )
    }
    single<TaskTransportConfigRepository> {
        DefaultTaskTransportConfigRepository(
            settingsStorage = get(),
        )
    }
    single { RelayProtocolJsonCodec() }
    single<RelayHealthProbe> {
        OkHttpRelayHealthProbe(
            logger = get(),
        )
    }
    single<RelayFileSurfaceClient> {
        OkHttpRelayFileSurfaceClient(
            logger = get(),
        )
    }
    single<RelayConnectionClient> {
        OkHttpRelayConnectionClient(
            codec = get(),
            logger = get(),
        )
    }
    single<RelayBootstrapManager> {
        DefaultRelayBootstrapManager(
            transportConfigRepository = get(),
            relayHealthProbe = get(),
            relayConnectionClient = get(),
        )
    }
    single<TaskMailDirectNewTaskSender> {
        RelayTaskMailDirectNewTaskSender(
            relayConnectionClient = get(),
        )
    }
    single<TaskMailCreateSessionClient> {
        OkHttpTaskMailCreateSessionFacadeClient(
            transportConfigRepository = get(),
            logger = get(),
        )
    }
    single<TaskMailDirectProjectSyncSender> {
        RelayTaskMailDirectProjectSyncSender(
            relayConnectionClient = get(),
            debugRecorder = get(),
        )
    }
    single<TaskMailDirectSessionActionSender> {
        RelayTaskMailDirectSessionActionSender(
            relayConnectionClient = get(),
        )
    }
    single { TaskMailDirectSessionProjector() }
    single {
        RelayTaskMailDirectSessionDetailSubscriber(
            relayConnectionClient = get(),
        )
    }
    single<ObserveTaskMailDirectSessionDetail> {
        DefaultObserveTaskMailDirectSessionDetail(
            relayBootstrapManager = get(),
            relayConnectionClient = get(),
            directSessionDetailSubscriber = get(),
            projector = get(),
            logger = get(),
        )
    }

    single { TaskMailMessageDetector() }
    single { LegacyTaskMailBodyExtractor() }
    single {
        LegacyTaskMailAttachmentMetadataExtractor(
            attachmentInfoExtractor = get(),
        )
    }
    single {
        EmailIngress(
            accountManager = get(),
            messageListRepository = get(),
            localStoreProvider = get(),
            attachmentMetadataExtractor = get(),
        )
    }
    single<MessageIngress<EmailIngressMessage>> { get<EmailIngress>() }
    single {
        EmailMessageParser(
            messageDetector = get(),
        )
    }
    single<IncomingMessageParser<EmailIngressMessage, TaskMailMessage>> { get<EmailMessageParser>() }
    single { EmailIngressPayloadJsonCodec() }
    single { TaskMailMessageJsonCodec() }
    single { TaskSessionDetailJsonCodec() }
    single { TaskMailNewTaskSendRecordJsonCodec() }
    single { TaskMailSessionActionSendRecordJsonCodec() }
    single { TaskMailSessionProjector(bodyExtractor = get()) }
    single { TaskWorkspaceSummaryProjector() }
    single<UnifiedMessageRepository> {
        FileBackedUnifiedMessageRepository(
            storageDirectory = File(androidContext().filesDir, "taskmail"),
        )
    }
    single<MessageSyncStateRepository> {
        FileBackedMessageSyncStateRepository(
            storageDirectory = File(androidContext().filesDir, "taskmail"),
        )
    }
    single<TaskSessionDetailRepository> {
        FileBackedTaskSessionDetailRepository(
            storageDirectory = File(androidContext().filesDir, "taskmail"),
            codec = get(),
        )
    }
    single<TaskMailNewTaskSendRecordRepository> {
        FileBackedTaskMailNewTaskSendRecordRepository(
            storageDirectory = File(androidContext().filesDir, "taskmail"),
            codec = get(),
        )
    }
    single<TaskMailSessionActionSendRecordRepository> {
        FileBackedTaskMailSessionActionSendRecordRepository(
            storageDirectory = File(androidContext().filesDir, "taskmail"),
            codec = get(),
        )
    }
    single<MessageSyncCoordinator> {
        TaskMailCacheSyncCoordinator(
            messageIngress = get(),
            messageParser = get(),
            unifiedMessageRepository = get(),
            messageSyncStateRepository = get(),
            payloadJsonCodec = get(),
            taskMailMessageJsonCodec = get(),
        )
    }
    single {
        SyncTaskMailCache(
            unifiedMessageRepository = get(),
            messageSyncStateRepository = get(),
            syncCoordinator = get(),
            taskMailMessageJsonCodec = get(),
            sessionProjector = get(),
            taskSessionDetailRepository = get(),
        )
    }

    single<TaskMailMessageSource> {
        CachedTaskMailMessageSource(
            unifiedMessageRepository = get(),
            taskMailMessageJsonCodec = get(),
        )
    }

    single<TaskMailRepository> {
        SnapshotBackedTaskMailRepository(
            taskSessionDetailRepository = get(),
            workspaceSummaryProjector = get(),
        )
    }

    single { TaskMailProjectSyncResultParser() }

    single<TaskMailProjectSyncRepository> {
        DefaultTaskMailProjectSyncRepository(
            requester = get(),
            resultReader = get(),
        )
    }

    single<TaskMailProjectSyncDebugSettingsRepository> {
        DefaultTaskMailProjectSyncDebugSettingsRepository(
            settingsStorage = get(),
        )
    }

    single<TaskMailProjectSyncRequester> {
        TransportBackedTaskMailProjectSyncRequester(
            transport = get(),
            directProjectSyncSender = get(),
            runTaskMailDirectOrFallback = get(),
            debugRecorder = get(),
        )
    }

    single<TaskMailProjectSyncDebugRecorder> {
        FileBackedTaskMailProjectSyncDebugRecorder(
            storageDirectory = File(
                androidContext().getExternalFilesDir(null) ?: androidContext().filesDir,
                "taskmail-debug",
            ),
            settingsRepository = get(),
            logger = get(),
        )
    }

    single<TaskMailTransportProbeEventStore> {
        FileBackedTaskMailTransportProbeEventStore(
            storageDirectory = File(
                androidContext().getExternalFilesDir(null) ?: androidContext().filesDir,
                "taskmail-debug/transport-probe",
            ),
            logger = get(),
        )
    }

    single<TaskMailFileSampleStore> {
        FileBackedTaskMailFileSampleStore(
            storageDirectory = File(
                androidContext().getExternalFilesDir(null) ?: androidContext().filesDir,
                "taskmail-debug/file-sample",
            ),
            logger = get(),
        )
    }

    single<TaskMailTransportProbeSender> {
        RelayTaskMailTransportProbeSender(
            relayConnectionClient = get(),
            eventStore = get(),
        )
    }

    single<TaskMailFileSampleSender> {
        RelayTaskMailFileSampleSender(
            fileSurfaceClient = get(),
            store = get(),
        )
    }

    single<TaskMailProjectSyncResultReader> {
        MailStoreBackedTaskMailProjectSyncResultReader(
            senderAccountSource = get(),
            messageListRepository = get(),
            localStoreProvider = get(),
            parser = get(),
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

    single<TaskMailDestinationAddressProvider> {
        StorageBackedTaskMailDestinationAddressProvider(
            settingsStorage = get(),
        )
    }

    single<TaskMailBotMailboxSettingsRepository> {
        DefaultTaskMailBotMailboxSettingsRepository(
            settingsStorage = get(),
        )
    }

    single<TaskMailSenderAccountSource> {
        LegacyTaskMailSenderAccountSource(
            accountManager = get(),
        )
    }

    single<TaskMailMimeMessageFactory> {
        LegacyTaskMailMimeMessageFactory(
            generalSettingsManager = get(),
            replyAttachmentResolver = get(),
            destinationAddressProvider = get(),
        )
    }

    single<TaskMailMimeMessageSender> {
        LegacyTaskMailMimeMessageSender(
            messagingController = get(),
        )
    }

    single<TaskMailNewTaskMimeMessageFactory> {
        LegacyTaskMailNewTaskMimeMessageFactory(
            generalSettingsManager = get(),
            destinationAddressProvider = get(),
        )
    }

    single<TaskMailReplySender> {
        RealTaskMailReplySender(
            transport = get(),
        )
    }

    single<TaskMailNewTaskSender> {
        RealTaskMailNewTaskSender(
            transport = get(),
        )
    }

    single<TaskMailReplyTransport> {
        EmailTaskMailReplyTransport(
            sourceMessageLoader = get(),
            mimeMessageFactory = get(),
            mimeMessageSender = get(),
            logger = get(),
        )
    }

    single<TaskMailNewTaskTransport> {
        EmailTaskMailNewTaskTransport(
            senderAccountSource = get(),
            mimeMessageFactory = get(),
            mimeMessageSender = get(),
            logger = get(),
        )
    }

    single<TaskMailStoreChangeObserver> {
        LegacyTaskMailStoreChangeObserver(
            messageListRepository = get(),
        )
    }

    single<TaskMailForegroundRefreshTickerFactory> {
        DefaultTaskMailForegroundRefreshTickerFactory()
    }

    single<TaskMailSyncRequester> {
        LegacyTaskMailSyncRequester(
            messagingController = get(),
            senderAccountSource = get(),
        )
    }

    factory {
        GetTaskSessionDetail(
            repository = get(),
        )
    }

    factory {
        GetTaskSessionDetails(
            repository = get(),
        )
    }

    factory {
        GetTaskMailSenderAccounts(
            senderAccountSource = get(),
        )
    }

    factory {
        GetTaskMailBotMailboxSettings(
            repository = get(),
        )
    }

    factory {
        SaveTaskMailBotMailboxSettings(
            repository = get(),
        )
    }

    factory {
        GetLatestTaskMailProjectSyncResult(
            repository = get(),
        )
    }

    factory {
        GetLatestTaskMailNewTaskSendRecord(
            repository = get(),
        )
    }

    factory {
        GetLatestTaskMailSessionActionSendRecord(
            repository = get(),
        )
    }

    factory {
        RefreshTaskMail(
            syncRequester = get(),
            syncTaskMailCache = get(),
        )
    }

    factory {
        ObserveTaskMailStoreChanges(
            observer = get(),
        )
    }

    factory {
        SendTaskMailReply(
            replySender = get(),
        )
    }

    factory {
        RunTaskMailDirectOrFallback(
            relayBootstrapManager = get(),
        )
    }

    factory {
        RunTaskMailDirectDispatch(
            relayBootstrapManager = get(),
        )
    }

    factory {
        CreateTaskMailSession(
            createSessionClient = get(),
        )
    }

    factory {
        SendTaskMailDirectNewTask(
            directNewTaskSender = get(),
        )
    }

    factory {
        SendTaskMailDirectSessionAction(
            directSessionActionSender = get(),
        )
    }

    factory {
        SendTaskMailNewTask(
            newTaskSender = get(),
        )
    }

    factory {
        SendTaskMailTransportProbe(
            sender = get(),
        )
    }

    factory {
        SendTaskMailFileSample(
            sender = get(),
        )
    }

    factory {
        RecordTaskMailNewTaskSendRecord(
            repository = get(),
        )
    }

    factory {
        RecordTaskMailSessionActionSendRecord(
            repository = get(),
        )
    }

    factory {
        RequestTaskMailProjectSync(
            repository = get(),
        )
    }

    viewModel {
        TaskWorkspaceViewModel(
            getTaskSessionDetails = get(),
            getTaskMailSenderAccounts = get(),
            refreshTaskMail = get(),
            observeTaskMailStoreChanges = get(),
            foregroundRefreshTickerFactory = get(),
            syncTaskMailCache = get(),
        )
    }

    viewModel {
        TaskSessionDetailViewModel(
            detailRepository = get(),
            getTaskSessionDetail = get(),
            refreshTaskMail = get(),
            observeTaskMailStoreChanges = get(),
            foregroundRefreshTickerFactory = get(),
            sendTaskMailDirectSessionAction = get(),
            getLatestTaskMailSessionActionSendRecord = get(),
            recordTaskMailSessionActionSendRecord = get(),
            replyAttachmentResolver = get(),
            timelineAttachmentHandler = get(),
            logger = get(),
            runTaskMailDirectDispatch = get(),
            syncTaskMailCache = get(),
            observeTaskMailDirectSessionDetail = get(),
        )
    }

    viewModel {
        TaskNewTaskViewModel(
            getTaskMailSenderAccounts = get(),
            getLatestTaskMailNewTaskSendRecord = get(),
            recordTaskMailNewTaskSendRecord = get(),
            createTaskMailSession = get(),
        )
    }

    viewModel {
        TaskProjectSyncViewModel(
            getTaskMailSenderAccounts = get(),
            getLatestTaskMailProjectSyncResult = get(),
            requestTaskMailProjectSync = get(),
            refreshTaskMail = get(),
            observeTaskMailStoreChanges = get(),
            enablePostSyncFollowUpRefresh = true,
            debugRecorder = get(),
        )
    }

    viewModel {
        TaskMailSettingsViewModel(
            getTaskMailBotMailboxSettings = get(),
            saveTaskMailBotMailboxSettings = get(),
            emailAddressValidator = get(),
        )
    }

    viewModel {
        TaskMailRelayDebugViewModel(
            relayBootstrapManager = get(),
            projectSyncDebugSettingsRepository = get(),
            sendTaskMailTransportProbe = get(),
            sendTaskMailFileSample = get(),
            taskMailStorageDirectory = File(androidContext().filesDir, "taskmail"),
        )
    }
}
