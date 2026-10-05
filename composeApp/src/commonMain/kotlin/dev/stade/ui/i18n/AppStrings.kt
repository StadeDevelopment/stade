package dev.stade.ui.i18n

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

abstract class AppStrings {
    abstract val back: String
    abstract val cancel: String
    abstract val delete: String

    abstract val loading: String
    abstract val welcomeTitle: String
    abstract val welcomeDescription: String
    abstract val nicknamePlaceholder: String
    abstract val createIdentity: String
    abstract val continueAction: String

    abstract val unlockTitle: String
    abstract val unlockSubtitle: String
    abstract val pressEnterHint: String
    abstract val tooManyAttemptsSubtitle: String
    abstract val forgotPin: String
    abstract val resetPinTitle: String
    abstract val resetPinBody: String
    abstract val resetAndWipe: String
    abstract val vaultNotInitialized: String
    abstract val vaultLockFailedTitle: String
    abstract val vaultLockFailedBody: String
    abstract val databaseSchemaFailedTitle: String
    abstract val databaseSchemaFailedBody: String
    abstract fun wrongPinRemaining(remaining: Int): String
    abstract val wrongPin: String
    abstract val wiping: String
    abstract fun retryIn(formattedTime: String): String
    abstract fun formatRemainingTime(seconds: Long): String

    abstract val enterCurrentPinTitle: String
    abstract val setNewPinTitle: String
    abstract val confirmPinTitle: String
    abstract val enterCurrentPinSubtitle: String
    abstract fun setPinSubtitle(min: Int, max: Int): String
    abstract val confirmPinSubtitle: String
    abstract val wrongCurrentPin: String
    abstract val pinMismatch: String
    abstract val pinChangeFailed: String
    abstract val confirmAction: String
    abstract val backspaceAction: String
    abstract val onboardingPinTip: String
    abstract val setDuressPinTitle: String
    abstract val confirmDuressPinTitle: String
    abstract val setDuressPinSubtitle: String
    abstract val duressPinMatchesRealPin: String

    abstract val appTitle: String
    abstract val searchContactsPlaceholder: String
    abstract val closeSearch: String
    abstract val searchAction: String
    abstract val settingsAction: String
    abstract val gearAnnoyed: String
    abstract val updateAvailableTooltip: String
    abstract val updateDownloadingTooltip: String
    abstract val updateReadyTitle: String
    abstract val updateReadyBody: String
    abstract val updateInstallAction: String
    abstract val updateLaterAction: String
    abstract val updateFailedTitle: String
    abstract val updateFailedBody: String
    abstract val updateOpenPageAction: String
    abstract val updateSection: String
    abstract val updateCheckTitle: String
    abstract val updateChecking: String
    abstract val updateUpToDate: String
    abstract fun updateFoundVersion(version: String): String
    abstract val addContactAction: String
    abstract val noContactsTitle: String
    abstract val noContactsHint: String
    abstract val noSearchResults: String
    abstract val searchResultsSectionMessages: String
    abstract val searchInChatAction: String
    abstract val searchInChatPlaceholder: String
    abstract fun searchInChatCount(index: Int, total: Int): String
    abstract val searchInChatNoResults: String
    abstract val searchInChatPrevious: String
    abstract val searchInChatNext: String
    abstract val showVerificationCode: String
    abstract val viewProfileAction: String
    abstract val deleteContact: String
    abstract val pinChatAction: String
    abstract val archiveChatAction: String
    abstract val starMessageAction: String
    abstract val unstarMessageAction: String
    abstract val starredMessagesTitle: String
    abstract val starredMessagesSubtitle: String
    abstract val noStarredMessages: String
    abstract val unarchiveChatAction: String
    abstract val archivedChatsTitle: String
    abstract val archiveSettingsTitle: String
    abstract val torConnectingTitle: String
    abstract val torSetupComplete: String
    abstract val torStatusOffline: String
    abstract val torStatusConnecting: String
    abstract val torStatusConnected: String
    abstract val torStatusFailed: String
    abstract val archiveNoticeBanner: String
    abstract val archiveNoticeUnarchiveBanner: String
    abstract val keepChatsArchivedTitle: String
    abstract val keepChatsArchivedSubtitle: String
    abstract val noArchivedChats: String
    abstract val backToChatsAction: String
    abstract val unpinChatAction: String
    abstract val noMessages: String
    abstract fun deleteContactTitle(name: String): String
    abstract val deleteContactBody: String

    abstract val online: String
    abstract val offline: String
    abstract val deleteContactDialogTitle: String
    abstract fun deleteContactDialogBody(name: String): String
    abstract val noMessagesYet: String
    abstract val sendFirstMessage: String
    abstract val typeMessagePlaceholder: String
    abstract val sendButton: String
    abstract val verifyAction: String
    abstract val deleteContactIconDescription: String
    abstract val connectionFailed: String
    abstract val collapseAction: String
    abstract val expandAction: String
    abstract val viewDetailsAction: String
    abstract val closeAction: String
    abstract val scrollToBottomAction: String
    abstract val noConnectionInfo: String
    abstract val connectionChannels: String
    abstract val trying: String
    abstract val channelReadyVerifying: String
    abstract val connectedLabel: String
    abstract val unreachable: String
    abstract val handshakeFailed: String
    abstract val notYetTried: String
    abstract fun channelLabel(index: Int, maskedAddr: String): String
    abstract val connectionDelayNote: String
    abstract val newInviteCodeLabel: String
    abstract val applyInviteCode: String
    abstract val clearAddresses: String
    abstract val retryConnection: String
    abstract val retryingConnection: String
    abstract val clearAddressesConfirmTitle: String
    abstract val clearAddressesConfirmBody: String
    abstract fun handshakeRejected(reason: String): String
    abstract val contactConnected: String
    abstract val decryptFailed: String
    abstract fun sendFailed(reason: String): String
    abstract val invalidInvite: String
    abstract val inviteBelongsToDifferent: String
    abstract val noConnectionInInvite: String
    abstract val connectionInfoUpdated: String
    abstract val addressesCleared: String
    abstract fun diagnosticError(msg: String): String

    abstract val dialQueued: String
    abstract val dialTransportNotReady: String
    abstract fun dialTransportStarting(msg: String): String
    abstract fun dialConnectingVia(transport: String, attempt: Int): String
    abstract fun dialConnectFailedRetry(err: String): String
    abstract val dialHandshaking: String
    abstract val dialConnectedOk: String
    abstract val dialHandshakeFailedRetry: String
    abstract val dialOwnStaleAddress: String
    abstract val dialTransportClosed: String
    abstract val dialUnreachableTimeout: String
    abstract val dialHandshakeFailed: String

    abstract val hsKeySizeBad: String
    abstract val hsSelfConnected: String
    abstract val hsStadeIdMismatch: String
    abstract val hsTranscriptMismatch: String
    abstract val hsAuthStadeIdMismatch: String
    abstract val hsSignaturesInvalid: String
    abstract val hsEdInvalid: String
    abstract val hsMldsaInvalid: String
    abstract val hsMlkemDecapFailed: String
    abstract val unknownNickname: String
    abstract val unknownError: String
    abstract fun contactNameFallback(last4: String): String

    abstract val backgroundRunningNotice: String
    abstract val trayOpen: String
    abstract val trayExit: String

    abstract val copiedLabel: String
    abstract fun addrRemoteNetwork(first: String, last: String): String
    abstract val addrLocalNetwork: String
    abstract val addrNetwork: String
    abstract fun timeYesterday(time: String): String

    abstract val saveMediaDialogTitle: String

    abstract val vaultMetaUnreadable: String
    abstract val vaultKeyDerivationFailed: String
    abstract val vaultDekDecryptFailed: String
    abstract val vaultDatabaseDecryptFailed: String

    abstract val notifConnectionChannelName: String
    abstract val notifConnectionChannelDesc: String
    abstract val notifMessagesChannelName: String
    abstract val notifMessagesChannelDesc: String
    abstract val notifRunningTitle: String
    abstract val notifRunningText: String
    abstract fun notifNewMessages(count: Int): String
    abstract val notifNewMessageFallback: String
    abstract val notifReminderChannelName: String
    abstract val notifReminderChannelDesc: String
    abstract val notifBootReminderTitle: String
    abstract val notifBootReminderText: String

    abstract val addContactTitle: String
    abstract val step1Title: String
    abstract val step1Description: String
    abstract fun copyInviteCode(length: Int): String
    abstract fun inviteCodeCopied(length: Int): String
    abstract val yourStadeId: String
    abstract val step2Title: String
    abstract val inviteCodeLabel: String
    abstract fun charCount(n: Int): String
    abstract val contactNameLabel: String
    abstract val acceptInvite: String
    abstract val pendingInviteOpened: String
    abstract val inviteCodeIsStadeId: String
    abstract fun inviteMissingPrefix(first: String): String
    abstract fun inviteTooShort(actual: Int, expected: Int): String
    abstract fun inviteTrailingBytes(extra: Int): String
    abstract val inviteBadMagic: String
    abstract fun inviteBadVersion(version: Int): String
    abstract fun inviteBadNickname(length: Int): String
    abstract fun inviteBadAddressBlob(length: Int): String
    abstract val inviteEdVerifyFail: String
    abstract val inviteMlDsaVerifyFail: String
    abstract fun inviteDecodeError(cause: String): String
    abstract val selfInviteError: String
    abstract fun alreadyAdded(stadeId: String): String
    abstract val inviteAcceptedNoAddr: String
    abstract fun inviteAccepted(name: String, count: Int): String
    abstract fun contactAdded(name: String): String
    abstract val connectionTimeout: String
    abstract val torStartingInviteHint: String
    abstract val inviteLanOnlyWarning: String
    abstract val inviteNotReadyForRemote: String
    abstract val shareInviteFileAction: String
    abstract val saveInviteFileAction: String
    abstract val inviteFileImportHint: String
    abstract val addContactDialogTitle: String
    abstract fun addContactQuestion(name: String): String
    abstract val incomingInviteMessage: String
    abstract val confirmAddCheckbox: String
    abstract val addAction: String
    abstract val notNowAction: String
    abstract fun connectingInBackground(name: String): String
    abstract fun error(msg: String): String

    abstract val settingsTitle: String
    abstract val identitySection: String
    abstract val appearanceSection: String
    abstract val dynamicColorTitle: String
    abstract val dynamicColorSubtitle: String
    abstract val notificationsSection: String
    abstract val messageNotificationsTitle: String
    abstract val notificationsOnSubtitle: String
    abstract val notificationsOffSubtitle: String
    abstract val hideNotificationTitle: String
    abstract val hiddenNotificationSubtitle: String
    abstract val visibleNotificationSubtitle: String
    abstract val systemNotificationsTitle: String
    abstract val systemNotificationsSubtitle: String
    abstract val runInBackgroundTitle: String
    abstract val runInBackgroundOnSubtitle: String
    abstract val runInBackgroundOffSubtitle: String
    abstract val networkSection: String
    abstract val transportLayersTitle: String
    abstract val transportLayersSubtitle: String
    abstract val securitySection: String
    abstract val securitySettingsTitle: String
    abstract val securitySettingsSubtitle: String
    abstract val aboutSection: String
    abstract val aboutTitle: String
    abstract val aboutSubtitle: String
    abstract val aboutAppDescription: String
    abstract val aboutFollowUs: String
    abstract val aboutLinkComingSoon: String
    abstract val aboutVersionLabel: String
    abstract val accountSection: String
    abstract val logoutTitle: String
    abstract val logoutSubtitle: String
    abstract val localIdentity: String
    abstract val fingerprintLabel: String
    abstract val fingerprintCopied: String
    abstract val copyButton: String
    abstract val logoutDialogTitle: String
    abstract val logoutDialogBody: String
    abstract val deleteAndLogout: String
    abstract val languageSection: String
    abstract val languageTitle: String
    abstract val languageSubtitle: String

    abstract val pinSection: String
    abstract val changePinTitle: String
    abstract val changePinSubtitle: String
    abstract val scrambleKeypadTitle: String
    abstract val scrambleKeypadOnSubtitle: String
    abstract val scrambleKeypadOffSubtitle: String
    abstract val sessionSection: String
    abstract val autoLockTitle: String
    abstract val lockOnShutdownTitle: String
    abstract val lockOnShutdownSubtitle: String
    abstract fun autoLockSubtitle(label: String): String
    abstract fun sessionTimeoutLabel(seconds: Int): String
    abstract val autoLockNeverInfoTitle: String
    abstract val autoLockNeverInfoBody: String
    abstract val understood: String

    abstract val privacySection: String
    abstract val screenshotBlockingTitle: String
    abstract val screenshotBlockingOnSubtitle: String
    abstract val screenshotBlockingOffSubtitle: String
    abstract val linkPreviewsSettingTitle: String
    abstract val linkPreviewsSettingSubtitle: String
    abstract val transportsLockTitle: String
    abstract val transportsLockSubtitle: String
    abstract val conversationShortcutsTitle: String
    abstract val conversationShortcutsOnSubtitle: String
    abstract val conversationShortcutsOffSubtitle: String
    abstract val duressPinTitle: String
    abstract val duressPinSetSubtitle: String
    abstract val duressPinNotSetSubtitle: String
    abstract val duressPinInfoTitle: String
    abstract val duressPinInfoBody: String
    abstract val clearDuressPinAction: String
    abstract val duressPinManageTitle: String
    abstract val duressPinManageBody: String
    abstract val duressPinReplaceAction: String
    abstract val duressPinRemoveAction: String
    abstract val biometricUnlockTitle: String
    abstract val biometricUnlockOnSubtitle: String
    abstract val biometricUnlockOffSubtitle: String
    abstract val biometricNotEnrolledSubtitle: String
    abstract val biometricPromptTitle: String
    abstract val biometricPromptSubtitle: String
    abstract val biometricEnablePromptSubtitle: String
    abstract val biometricUsePinAction: String
    abstract val biometricUseFingerprintAction: String
    abstract val biometricConfirmPinTitle: String
    abstract val biometricConfirmPinBody: String
    abstract val biometricEnabledNotice: String
    abstract val biometricFailedNotice: String
    abstract val biometricResetNotice: String

    abstract val transportsTitle: String
    abstract val notRegistered: String
    abstract fun transportRunning(msg: String): String
    abstract val transportReady: String
    abstract fun transportUnavailable(msg: String): String
    abstract fun transportStatus(addr: String): String
    abstract fun transportChannelsReady(n: Int): String
    abstract val torBuiltinNote: String
    abstract val hiddenServiceDescription: String
    abstract val hiddenServiceId: String
    abstract val onionVirtport: String
    abstract val localPortLabel: String
    abstract val socks5Note: String
    abstract val socks5Host: String
    abstract val socks5Port: String
    abstract val saveAndRestart: String
    abstract val lanLabel: String
    abstract val torLabel: String

    abstract val useBridgesTitle: String
    abstract val useBridgesHint: String
    abstract val useBuiltInBridgesTitle: String
    abstract val customBridgesLabel: String
    abstract val customBridgesHint: String
    abstract val bridgesNotSupportedNote: String

    abstract val verifyContactTitle: String
    abstract val profileTitle: String
    abstract val safetyNumber: String
    abstract val safetyNumberNote: String
    abstract val markAsVerified: String
    abstract val alreadyVerifiedLabel: String
    abstract val verifiedLabel: String
    abstract val contactStadeId: String

    abstract val selectContactHint: String
    abstract val attachPhoto: String
    abstract val attachMediaAction: String
    abstract val selectMediaTitle: String
    abstract val photoMessage: String
    abstract val photoSendFailed: String
    abstract val photoTooBig: String
    abstract val tapToViewPhoto: String
    abstract val closePhoto: String
    abstract val removeAttachment: String
    abstract fun attachmentCount(count: Int): String
    abstract val pasteButton: String

    abstract val editImageAction: String
    abstract val cropToolAction: String
    abstract val drawToolAction: String
    abstract val undoAction: String
    abstract val resetCropAction: String
    abstract val saveEditsAction: String

    abstract val recordVoice: String
    abstract val stopRecording: String
    abstract val voiceMessage: String
    abstract val voiceSendFailed: String
    abstract val micPermissionDenied: String
    abstract val voiceMaxDurationReached: String

    abstract val attachVideo: String
    abstract val videoMessage: String
    abstract val videoSendFailed: String
    abstract val videoTooBig: String
    abstract val videoAttached: String
    abstract val tapToPlayVideo: String
    abstract val vlcNotFoundHint: String
    abstract val videoOpenFailed: String

    abstract val emojiPickerAction: String
    abstract val showKeyboardAction: String
    abstract val stickerMessage: String
    abstract val emojiTabLabel: String
    abstract val stickersTabLabel: String
    abstract val noCustomEmojiYet: String
    abstract val noStickersYet: String
    abstract val createStickerAction: String
    abstract val stickerMakerTitle: String
    abstract val removingBackgroundLabel: String
    abstract val removeBackgroundOption: String
    abstract val keepOriginalBackgroundOption: String
    abstract val rotateLeftAction: String
    abstract val rotateRightAction: String
    abstract val saveStickerAction: String
    abstract val stickerCreationFailed: String
    abstract val stickerGifTooLarge: String
    abstract val importStickersAction: String
    abstract val importStickersHint: String
    abstract fun stickersImported(count: Int): String
    abstract val stickersImportedNone: String
    abstract fun stickersImportDuplicates(count: Int): String
    abstract fun stickersImportUnreadable(count: Int): String
    abstract val stickerPackDeleteAction: String
    abstract val stickerPackDeleteTitle: String
    abstract val stickerPackDeleteBody: String
    abstract val stickerPackLoose: String
    abstract val backupSection: String
    abstract val backupExportTitle: String
    abstract val backupExportSubtitle: String
    abstract val backupExportDialogTitle: String
    abstract val backupExportDialogBody: String
    abstract val backupExportAction: String
    abstract val backupRestoreTitle: String
    abstract val backupRestoreSubtitle: String
    abstract val backupRestoreDialogTitle: String
    abstract val backupRestoreDialogBody: String
    abstract val backupRestoreAction: String
    abstract val backupPassphraseLabel: String
    abstract val backupPassphraseRepeatLabel: String
    abstract val backupPassphraseTooShort: String
    abstract val backupPassphraseMismatch: String
    abstract val backupExported: String
    abstract val backupRestored: String
    abstract val backupRestoredNoOnion: String
    abstract val backupWrongPassphrase: String
    abstract val backupNotABackup: String
    abstract val backupDamaged: String
    abstract val backupFailed: String
    abstract val deleteStickerConfirmTitle: String
    abstract val deleteStickerConfirmBody: String
    abstract val saveStickerToPackAction: String
    abstract val stickerSavedToPack: String

    abstract val replyAction: String
    abstract fun replyingToLabel(name: String): String
    abstract val cancelReply: String
    abstract val originalMessageUnavailable: String

    abstract val createGroupTitle: String
    abstract val createGroupAction: String
    abstract val groupNameLabel: String
    abstract val selectMembersHint: String
    abstract val groupInviteTitle: String
    abstract val groupInviteBody: String
    abstract val copyInviteLink: String
    abstract val deleteGroupTitle: String
    abstract val deleteGroupBody: String
    abstract val groupGenerateInvite: String
    abstract fun groupMemberCount(count: Int): String

    abstract val addMembersTitle: String
    abstract val addMembersAction: String
    abstract val addMembersHint: String
    abstract val noContactsToAdd: String
    abstract fun membersAdded(count: Int): String
    abstract val leaveGroupAction: String
    abstract val leaveGroupTitle: String
    abstract val leaveGroupBody: String
    abstract val leaveAction: String

    abstract val viewMembersAction: String
    abstract val groupMembersTitle: String
    abstract val youLabel: String
    abstract val groupAdminBadge: String
    abstract val kickMemberAction: String
    abstract fun kickMemberTitle(name: String): String
    abstract val kickMemberBody: String
    abstract fun memberKicked(name: String): String
    abstract val kickMemberFailed: String
    abstract fun removedFromGroupNotification(groupName: String): String

    abstract val copyMessage: String
    abstract val deleteMessageForMe: String
    abstract val deleteMessagesForMe: String
    abstract val deleteMessageAction: String
    abstract fun selectedCount(count: Int): String
    abstract val messageCopied: String
    abstract val cancelSelection: String

    abstract val saveImageAction: String
    abstract val copyImageAction: String
    abstract val imageSaved: String
    abstract val imageSaveFailed: String
    abstract val imageCopied: String
    abstract val imageCopyFailed: String

    abstract val saveAction: String
    abstract val editAliasTitle: String
    abstract val editAliasBody: String
    abstract val editAliasLabel: String
    abstract val createStadiumTitle: String
    abstract val createStadiumAction: String
    abstract val createStadiumHint: String
    abstract val stadiumNameLabel: String
    abstract fun stadiumSubscriberCount(count: Long): String
    abstract val manageStadiumTitle: String
    abstract val stadiumInviteLabel: String
    abstract val stadiumInviteHint: String
    abstract val copyStadiumInviteAction: String
    abstract val stadiumInviteDialogTitle: String
    abstract val shareInviteAction: String
    abstract val sendInviteToContactAction: String
    abstract val sendAction: String
    abstract val inviteAction: String
    abstract val stadiumRenamed: String
    abstract val deleteStadiumAction: String
    abstract val deleteStadiumConfirmTitle: String
    abstract val deleteStadiumConfirmBody: String
    abstract val leaveStadiumAction: String
    abstract val leaveStadiumConfirmTitle: String
    abstract val leaveStadiumConfirmBody: String
    abstract val leftStadium: String
    abstract val stadiumConnectionLost: String
    abstract val stadiumReconnectAction: String
    abstract val joinStadiumTitle: String
    abstract val joinStadiumAction: String
    abstract val joinStadiumHint: String
    abstract val notAStadiumInvite: String
    abstract fun stadiumJoinDialing(name: String): String
    abstract fun stadiumJoined(name: String): String
    abstract val muteStadiumAction: String
    abstract val unmuteStadiumAction: String
    abstract val muteChatAction: String
    abstract val unmuteChatAction: String
    abstract val vanishSwipeUpPrompt: String
    abstract val vanishDurationSheetTitle: String
    abstract val vanishDuration30Min: String
    abstract val vanishDuration1Hour: String
    abstract val vanishDuration6Hours: String
    abstract val vanishDuration12Hours: String
    abstract val vanishDuration1Day: String
    abstract fun vanishActiveBannerLabel(remaining: String): String
    abstract val vanishTurnOffConfirmTitle: String
    abstract val vanishTurnOffConfirmBody: String
    abstract val vanishTurnOffAction: String
    abstract val vanishStateOff: String
    abstract fun vanishStateOn(remaining: String): String

    abstract val stadeyRowSubtitle: String
    abstract val stadeyIntro: String
    abstract val stadeySupportLabel: String
    abstract val stadeySupportAnswer: String
    abstract val stadeyFaqAddFriendsQuestion: String
    abstract val stadeyFaqAddFriendsAnswer: String
    abstract val stadeyFaqSecurityQuestion: String
    abstract val stadeyFaqSecurityAnswer: String
    abstract val stadeyFaqGroupsStadiumsQuestion: String
    abstract val stadeyFaqGroupsStadiumsAnswer: String
    abstract val stadeyFaqMediaQuestion: String
    abstract val stadeyFaqMediaAnswer: String
    abstract val stadeyFaqNetworkingQuestion: String
    abstract val stadeyFaqNetworkingAnswer: String
    abstract val stadeyFaqLockdownQuestion: String
    abstract val stadeyFaqLockdownAnswer: String

    abstract val hideStadeyAction: String
    abstract val hideStadeyConfirm: String
    abstract val hideStadeyDialogTitle: String
    abstract val hideStadeyDialogBody: String
    abstract val activateStadeyTitle: String
    abstract val activateStadeySubtitle: String
    abstract val activateStadeyConfirm: String
    abstract val activateStadeyDialogTitle: String
    abstract val activateStadeyDialogBody: String
    abstract val changeAvatarAction: String
    abstract val removeAvatarAction: String
    abstract val removeAvatarConfirmTitle: String
    abstract val removeAvatarConfirmBody: String

    abstract val moneroPaymentLabel: String
    abstract fun moneroAmountLabel(amount: String): String
    abstract val openInWalletAction: String
    abstract val copyAddressAction: String
    abstract val addressCopied: String
    abstract val noMoneroWalletFound: String

    abstract val typingIndicator: String
    abstract val voiceTooShort: String
    abstract val voiceCancelRecording: String
    abstract val voiceSlideToCancel: String
    abstract val voiceRecordingCancelled: String

    abstract val scheduleMessagePickDate: String
    abstract val scheduleMessagePickTime: String
    abstract val scheduleNextAction: String
    abstract val scheduleConfirmAction: String
    abstract val scheduleTimeInPast: String
    abstract fun scheduleDeliveryAt(time: String): String
    abstract val scheduleTextOnly: String
    abstract fun messageScheduled(time: String): String
    abstract fun scheduledMessagesBanner(count: Int): String
    abstract val scheduledMessagesSheetTitle: String
    abstract val noScheduledMessages: String
    abstract val deleteScheduledMessage: String

    abstract val radarTitle: String
    abstract val radarScanning: String
    abstract fun radarNearbyCount(count: Int): String
    abstract val radarEmptyTitle: String
    abstract val radarEmptyBody: String
    abstract val radarDiscoverable: String
    abstract val radarNotDiscoverable: String
    abstract val radarPermissionTitle: String
    abstract val radarPermissionBody: String
    abstract val radarGrantAction: String
    abstract val radarBluetoothOffTitle: String
    abstract val radarBluetoothOffBody: String
    abstract val radarEnableBluetoothAction: String
    abstract val radarUnsupportedTitle: String
    abstract val radarUnsupportedBody: String
    abstract fun radarConnectingTo(name: String): String
    abstract val radarExchangeFailed: String
    abstract val radarUnknownPeer: String
    abstract val radarConfirmTitle: String
    abstract fun radarConfirmBody(name: String): String
    abstract val radarProximityNear: String
    abstract val radarProximityMedium: String
    abstract val radarProximityFar: String
    abstract val radarIntroTitle: String
    abstract val radarIntroBody: String
    abstract val radarIntroStepOne: String
    abstract val radarIntroStepTwo: String
    abstract val radarIntroStepThree: String
    abstract val radarIntroDontShowAgain: String
    abstract val radarIntroAction: String
    abstract val radarSettingsTitle: String
    abstract val radarAnonymousTitle: String
    abstract val radarAnonymousBody: String
    abstract val radarAnonymousLockedBody: String
    abstract val radarAnonymousActive: String
    abstract val radarGhostTitle: String
    abstract val radarGhostBody: String
    abstract val radarGhostActive: String
    abstract val navChats: String
    abstract val navCreateAction: String
    abstract val navRadar: String
    abstract val previewYouPrefix: String
    abstract val updateRequiredByYou: String
    abstract val updateRequiredByPeer: String
    abstract val updateAction: String
    abstract val padPlusAction: String
    abstract val padAttachMedia: String
    abstract val padPaddyTitle: String
    abstract val padPaddySubtitle: String
    abstract val padUnavailable: String
    abstract val padSoundUnsupported: String
    abstract val padDownloadFailed: String
    abstract val padRefresh: String
    abstract val padEmpty: String
    abstract val padRetry: String
    abstract val padDownloading: String
    abstract val padSendFailed: String
    abstract val padSoundTag: String
    abstract val padTapToPlay: String
    abstract val padTorNotReady: String
    abstract fun padSentSound(sender: String?, isSelf: Boolean): String
    abstract val unsupportedMessage: String
}

val LocalStrings = compositionLocalOf<AppStrings> { EnglishStrings }

object I18n {
    var current: AppStrings by mutableStateOf(EnglishStrings)
}

