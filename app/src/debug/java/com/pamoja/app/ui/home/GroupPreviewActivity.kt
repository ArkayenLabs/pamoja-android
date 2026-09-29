package com.pamoja.app.ui.home

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.rememberGraphicsLayer
import com.pamoja.app.domain.model.Group
import com.pamoja.app.domain.model.GroupAccess
import com.pamoja.app.domain.model.GroupAccessState
import com.pamoja.app.domain.model.GroupFeature
import com.pamoja.app.domain.model.Membership
import com.pamoja.app.domain.model.Signal
import com.pamoja.app.domain.model.SignalKind
import com.pamoja.app.domain.model.ThemePreference
import com.pamoja.app.domain.model.User
import com.pamoja.app.domain.model.WeekWindow
import com.pamoja.app.ui.card.WeeklyCardContent
import com.pamoja.app.ui.card.WeeklyCardShare
import com.pamoja.app.ui.card.weeklyCardOf
import com.pamoja.app.ui.group.GroupDetailsContent
import com.pamoja.app.ui.group.GroupDetailsUiState
import com.pamoja.app.ui.group.GroupUiState
import com.pamoja.app.ui.group.MemberStepData
import com.pamoja.app.ui.theme.PamojaTheme
import com.pamoja.app.widgets.WidgetPreview
import com.pamoja.app.widgets.WidgetSnapshot
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.launch

/**
 * Debug-only visual review of the group home and group details (Phase 4),
 * rendering their stateless content with sample data, like the onboarding
 * preview. No navigation, no network. Release builds do not contain it.
 *
 * `adb shell am start -n com.arkayenlabs.pamoja/com.pamoja.app.ui.home.GroupPreviewActivity
 *  --ei screen 1 --ez dark false`
 *
 * 1 home, 2 home with the connect card, offline and a pending invite, 3 no
 * group, 4 loading, 5 details as admin, 6 details as a member of a Premium group,
 * 7 the weekly card. Home shows a received cheer, a sent one, day dots and a
 * five-week streak (Phase 5), and that a shield kept it last week (Phase 6).
 * 8 the paywall with a 7-day trial, 9 the weekly review of a free group whose
 * last week used a shield. Billing is not called: the plans are samples.
 * 10 the three home-screen widgets as mocked (group card, a cheer on "You
 * today", the streak), 11 their other states: no group, your steps, this
 * week's share before a streak, and a group too big for six faces with
 * everyone nudged. Both are the real RemoteViews, inflated as a launcher would.
 */
class GroupPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val screen = intent.getIntExtra(EXTRA_SCREEN, 1).coerceIn(1, 11)
        val dark = intent.getBooleanExtra(EXTRA_DARK, false)

        setContent {
            PamojaTheme(themePreference = if (dark) ThemePreference.Dark else ThemePreference.Light) {
                when (screen) {
                    1 -> Home(home(), groupState())
                    2 -> Home(
                        home().copy(needsHealthConnect = true, isOffline = true, lastSyncedAt = System.currentTimeMillis()),
                        groupState(),
                        pendingCode = "6413795d-42d0-4f2b-80df-f017a9d32817",
                    )
                    3 -> Home(
                        HomeUiState(isLoading = false, hasLoadedOnce = true, userName = "Ravi"),
                        GroupUiState(isLoading = false),
                    )
                    4 -> Home(home(), GroupUiState())
                    5 -> Details(currentUserId = ME, access = GroupAccessState.Free)
                    7 -> {
                        // The real capture, file and share sheet, so this screen
                        // checks sharing too; only the text is a stand-in.
                        val layer = rememberGraphicsLayer()
                        val scope = rememberCoroutineScope()
                        val context = LocalContext.current
                        WeeklyCardContent(
                            state = weeklyCardOf(group, memberships()),
                            layer = layer,
                            sharing = false,
                            snackbar = remember { SnackbarHostState() },
                            onBack = {},
                            onShare = {
                                scope.launch {
                                    WeeklyCardShare.share(
                                        context,
                                        layer.toImageBitmap().asAndroidBitmap(),
                                        WeeklyCardShare.appLink(),
                                    )
                                }
                            },
                        )
                    }
                    8 -> com.pamoja.app.ui.paywall.PaywallContent(
                        state = com.pamoja.app.ui.paywall.PaywallUiState(
                            group = group,
                            purchaseContext = com.pamoja.app.domain.repository.PurchaseContext(
                                groupId = GROUP, payerUid = ME, groupIsPremium = false, isGroupSponsor = false,
                                subscriptionIsActive = false, sponsoredGroupId = null, sponsoredGroupName = null,
                            ),
                            isGroupLoading = false,
                            plans = listOf(
                                com.pamoja.app.domain.repository.SubscriptionPlan(
                                    id = "monthly", storeProductId = "pamoja_premium", price = "Rs 199.00",
                                    period = com.pamoja.app.domain.model.BillingPeriod.Monthly,
                                    trial = com.pamoja.app.domain.repository.TrialPeriod(7, com.pamoja.app.domain.repository.TrialUnit.Day),
                                ),
                                com.pamoja.app.domain.repository.SubscriptionPlan(
                                    id = "annual", storeProductId = "pamoja_premium", price = "Rs 1,499.00",
                                    period = com.pamoja.app.domain.model.BillingPeriod.Annual,
                                    trial = com.pamoja.app.domain.repository.TrialPeriod(7, com.pamoja.app.domain.repository.TrialUnit.Day),
                                ),
                            ),
                            selectedPlanId = "annual",
                            members = memberships(),
                        ),
                        onDismiss = {}, onRetryContext = {}, onSelectPlan = {}, onPurchase = {}, onKeepBoth = {},
                        onMoveHere = {}, onRestore = {}, onRetryVerification = {}, onRefresh = {}, onManageInPlay = {},
                    )
                    9 -> com.pamoja.app.ui.weeklyreview.WeeklyReviewContent(
                        state = com.pamoja.app.ui.weeklyreview.WeeklyReviewUiState(
                            isLoading = false,
                            group = group,
                            access = GroupAccessState.Free,
                            lastWeek = com.pamoja.app.domain.model.GroupWeekSummary(
                                groupId = GROUP,
                                weekStart = lastWeekStart,
                                weekEnd = LocalDate.parse(lastWeekStart).plusDays(6).toString(),
                                targetSteps = 70_000,
                                totalSteps = 61_300,
                                memberCount = 6,
                                activeMemberCount = 6,
                                contributions = SAMPLE.map { com.pamoja.app.domain.model.GroupWeekContribution(it.id, it.week + 1_700, it.days ?: 0b1011011) },
                                finalizedAtEpochMillis = 1L,
                                shielded = true,
                            ),
                            lastWeekLoaded = true,
                            members = SAMPLE.associate { it.id to com.pamoja.app.ui.weeklyreview.WeeklyReviewMember(it.name, null) },
                        ),
                        onBack = {}, onOpenPremium = {}, onSelectWeek = {}, onRetry = {},
                    )
                    10 -> WidgetPreview.Board(
                        dark = dark,
                        rows = listOf(
                            WidgetPreview.Row.Group(widgetSnapshot()),
                            WidgetPreview.Row.Tiles(widgetSnapshot(), widgetSnapshot()),
                        ),
                    )
                    11 -> WidgetPreview.Board(
                        dark = dark,
                        rows = listOf(
                            WidgetPreview.Row.Group(WidgetSnapshot(userId = ME, group = null)),
                            WidgetPreview.Row.Group(bigGroupSnapshot()),
                            WidgetPreview.Row.Tiles(
                                widgetSnapshot().copy(cheersFrom = emptyList()),
                                widgetSnapshot().copy(group = group.copy(streakWeeks = 0, streakThroughWeek = "")),
                            ),
                        ),
                    )
                    else -> Details(
                        currentUserId = "priya",
                        access = GroupAccessState.Premium(
                            GroupAccess(groupId = GROUP, featureSet = setOf(GroupFeature.CircleV1), validUntilMillis = Long.MAX_VALUE),
                        ),
                    )
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun Home(home: HomeUiState, group: GroupUiState, pendingCode: String? = null) {
        HomeContent(
            home = home,
            groupState = group,
            pendingCode = pendingCode,
            snackbar = remember { SnackbarHostState() },
            onRefresh = {}, onRetryHome = {}, onRetryGroup = {}, onSelectGroup = {},
            onOpenDetails = {}, onOpenSettings = {}, onInvite = {}, onStartGroup = {},
            onJoinWithLink = {}, onOpenPendingInvite = {}, onDismissPendingInvite = {},
            onConnectSteps = {},
            signals = SignalsUiState(
                groupId = GROUP,
                received = listOf(Signal(SignalKind.CHEER, "priya", ME, LocalDate.now().toString())),
                sent = listOf(Signal(SignalKind.CHEER, ME, "sunita", LocalDate.now().toString())),
            ),
        )
    }

    @androidx.compose.runtime.Composable
    private fun Details(currentUserId: String, access: GroupAccessState) {
        GroupDetailsContent(
            state = GroupDetailsUiState(
                group = group,
                members = listOf(
                    Membership(userId = ME, displayName = "Ravi Sharma", joinedAt = 1_725_235_200_000L),
                    Membership(userId = "priya", displayName = "Priya", joinedAt = 1_725_235_300_000L),
                    Membership(userId = "sunita", displayName = "Sunita", joinedAt = 1_725_321_600_000L),
                    Membership(userId = "ramesh", displayName = "Ramesh", joinedAt = 1_725_408_000_000L),
                    Membership(userId = "meera", displayName = "Meera", joinedAt = 1_725_580_800_000L),
                    Membership(userId = "arjun", displayName = "Arjun", joinedAt = 1_725_840_000_000L),
                ),
                currentUserId = currentUserId,
                access = access,
            ),
            onBack = {}, onInvite = {}, onOpenReview = {}, onOpenPaywall = {},
            onSaveName = {}, onSaveGoal = {}, onSaveCap = {}, onSaveWeekStart = {},
            onSettingsSavedShown = {}, onChoosePhoto = {}, onRemovePhoto = {}, onRemoveMember = {},
            onLeave = {}, onDelete = {}, onRetry = {}, onActionErrorShown = {},
            subscriptionSalesEnabled = true,
        )
    }

    private val group = Group(
        groupId = GROUP,
        name = "Sharma Family",
        adminId = ME,
        weeklyTarget = 70_000,
        maxMemberCap = 10,
        memberCount = 6,
        weekStartDay = DayOfWeek.MONDAY.name,
        weeklySteps = 48_320,
        weekStart = WeekWindow.startOf(DayOfWeek.MONDAY),
        streakWeeks = 5,
        streakThroughWeek = lastWeekStart,
        streakShields = 1,
        lastShieldedWeek = lastWeekStart,
    )

    private val lastWeekStart: String
        get() = LocalDate.parse(WeekWindow.startOf(DayOfWeek.MONDAY)).minusWeeks(1).toString()

    private fun home(): HomeUiState {
        val today = LocalDate.now()
        return HomeUiState(
            isLoading = false,
            hasLoadedOnce = true,
            userId = ME,
            userName = "Ravi Sharma",
            selectedGroupId = GROUP,
            groups = listOf(
                group,
                Group(groupId = "0b7c5d3e-9f2a-4c1b-8e6d-2a4f5b6c7d8e", name = "Office Walkers", memberCount = 12, weeklyTarget = 90_000),
            ),
            personalDays = listOf(PersonalDay(today, 6_240)),
            hasLoadedPersonalSteps = true,
            usualSteps = 7_500,
        )
    }

    private fun groupState() = GroupUiState(
        groupId = GROUP,
        isLoading = false,
        group = group,
        currentUserId = ME,
        isAdmin = true,
        hasLoadedMembers = true,
        combinedWeeklySteps = 48_320,
        memberStepData = SAMPLE.map { (id, name, today, week, days) ->
            MemberStepData(
                user = User(userId = id, name = name),
                todaySteps = today,
                weeklySteps = week,
                walkedDays = days,
            )
        },
    )

    /** The same people as memberships, for the weekly card. */
    private fun memberships(): List<Membership> {
        val week = WeekWindow.startOf(DayOfWeek.MONDAY)
        return SAMPLE.map { (id, name, today, steps, days) ->
            Membership(
                userId = id, groupId = GROUP, displayName = name,
                weeklySteps = steps, weekStart = week,
                todaySteps = today, todayDate = LocalDate.now().toString(),
                walkedDays = days ?: 0, walkedWeekStart = if (days == null) "" else week,
            )
        }
    }

    private fun widgetSnapshot() = WidgetSnapshot(
        userId = ME,
        group = group,
        members = memberships(),
        signalsDate = LocalDate.now().toString(),
        cheersFrom = listOf("priya"),
        todaySteps = 6_240,
        stepsDate = LocalDate.now().toString(),
        usualSteps = 7_500,
        refreshedAt = System.currentTimeMillis(),
    )

    /** Nine people: five faces and "+4", and everyone waiting already nudged. */
    private fun bigGroupSnapshot(): WidgetSnapshot {
        val extra = listOf("Kavya", "Dev Patel", "Anil").mapIndexed { i, name ->
            Membership(userId = "extra$i", groupId = GROUP, displayName = name, todayDate = LocalDate.now().toString())
        }
        val base = widgetSnapshot()
        val members = base.members + extra
        return base.copy(
            group = group.copy(name = "Office Walkers and Friends of the Park"),
            members = members,
            nudged = members.map { it.userId }.toSet(),
        )
    }

    /** Masks cover every day of the week; the screens only use the days that have begun. */
    private data class Sample(val id: String, val name: String, val today: Long, val week: Long, val days: Int?)

    private val SAMPLE = listOf(
        Sample("sunita", "Sunita", 7_100, 6_420, 0b1111111),
        Sample("ramesh", "Ramesh", 5_300, 7_300, 0b1111110),
        Sample("priya", "Priya", 4_800, 11_900, 0b1111111),
        Sample(ME, "Ravi Sharma", 6_240, 9_800, 0b1111011),
        Sample("arjun", "Arjun", 300, 4_300, 0b0000001),
        // An older app version: no dots rather than a row of missed days.
        Sample("meera", "Meera", 0, 8_600, null),
    )

    private companion object {
        const val EXTRA_SCREEN = "screen"
        const val EXTRA_DARK = "dark"
        const val GROUP = "6413795d-42d0-4f2b-80df-f017a9d32817"
        const val ME = "me"
    }
}
