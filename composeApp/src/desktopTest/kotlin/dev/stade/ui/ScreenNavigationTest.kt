package dev.stade.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScreenNavigationTest {

    private fun goesForward(from: Screen, to: Screen) = screenDepth(to) >= screenDepth(from)

    @Test
    fun settingsSubScreensAnimateBackToSettings() {
        val children = listOf(
            Screen.About,
            Screen.Security,
            Screen.Transports,
            Screen.Theme,
            Screen.Language,
            Screen.Starred
        )
        for (child in children) {
            assertTrue(goesForward(Screen.Settings, child), "Settings -> $child should go forward")
            assertTrue(!goesForward(child, Screen.Settings), "$child -> Settings should go back")
        }
    }

    @Test
    fun chatOpensForwardAndClosesBackward() {
        val chat = Screen.Chat("abc")
        assertTrue(goesForward(Screen.Contacts, chat), "opening a chat should go forward")
        assertTrue(!goesForward(chat, Screen.Contacts), "leaving a chat should go back")
    }

    @Test
    fun groupMembersSitsDeeperThanItsGroupChat() {
        val group = Screen.GroupChat("g1")
        val members = Screen.GroupMembers("g1")
        assertTrue(goesForward(group, members))
        assertTrue(!goesForward(members, group))
    }

    @Test
    fun manageStadiumSitsDeeperThanItsStadium() {
        val stadium = Screen.Stadium("s1")
        val manage = Screen.ManageStadium("s1")
        assertTrue(goesForward(stadium, manage))
        assertTrue(!goesForward(manage, stadium))
    }

    @Test
    fun verifySitsDeeperThanTheChatItOpensFrom() {
        val chat = Screen.Chat("abc")
        val verify = Screen.Verify("abc", fromScreen = chat)
        assertTrue(goesForward(chat, verify))
        assertTrue(!goesForward(verify, chat))
    }

    @Test
    fun contactsIsShallowerThanEveryOtherMainScreen() {
        val mains = listOf(
            Screen.Settings, Screen.Stadey, Screen.AddContact, Screen.Radar,
            Screen.CreateGroup, Screen.CreateStadium, Screen.JoinStadium,
            Screen.Chat("a"), Screen.GroupChat("g"), Screen.Stadium("s")
        )
        for (s in mains) {
            assertTrue(screenDepth(s) > screenDepth(Screen.Contacts), "$s should be deeper than Contacts")
        }
    }

    @Test
    fun onboardingIsTheShallowestScreen() {
        assertEquals(0, screenDepth(Screen.Onboarding))
        assertTrue(screenDepth(Screen.Contacts) > screenDepth(Screen.Onboarding))
    }

    @Test
    fun highlightingAMessageDoesNotChangeTheScreenIdentity() {
        assertEquals(screenKey(Screen.Chat("a")), screenKey(Screen.Chat("a", highlightMessageId = "m1")))
        assertEquals(screenKey(Screen.GroupChat("g")), screenKey(Screen.GroupChat("g", highlightMessageId = "m1")))
        assertEquals(screenKey(Screen.Stadium("s")), screenKey(Screen.Stadium("s", highlightMessageId = "m1")))
    }

    @Test
    fun differentConversationsHaveDifferentKeys() {
        assertTrue(screenKey(Screen.Chat("a")) != screenKey(Screen.Chat("b")))
        assertTrue(screenKey(Screen.Chat("a")) != screenKey(Screen.GroupChat("a")))
        assertTrue(screenKey(Screen.Stadium("a")) != screenKey(Screen.ManageStadium("a")))
    }

    @Test
    fun aChatOpenedFromStarredIsDeeperThanStarred() {
        val fromStarred = Screen.Chat("abc", "m1", Screen.Starred)
        assertTrue(goesForward(Screen.Starred, fromStarred), "Starred -> chat should go forward")
        assertTrue(!goesForward(fromStarred, Screen.Starred), "chat -> Starred should go back")
    }

    @Test
    fun returnToAppliesToEveryChannel() {
        val cases = listOf(
            Screen.Chat("a", "m", Screen.Starred),
            Screen.GroupChat("g", "m", Screen.Starred),
            Screen.Stadium("s", "m", Screen.Starred)
        )
        for (target in cases) {
            assertTrue(goesForward(Screen.Starred, target), "Starred -> $target should go forward")
            assertTrue(!goesForward(target, Screen.Starred), "$target -> Starred should go back")
        }
    }

    @Test
    fun aChatWithoutReturnToKeepsItsNormalDepth() {
        assertEquals(screenDepth(Screen.Chat("a")), screenDepth(Screen.Chat("a", "m1")))
        assertTrue(goesForward(Screen.Contacts, Screen.Chat("a")))
        assertTrue(!goesForward(Screen.Chat("a"), Screen.Contacts))
    }

    @Test
    fun archiveSettingsSitsDeeperThanTheChatList() {
        assertTrue(goesForward(Screen.Contacts, Screen.ArchiveSettings))
        assertTrue(!goesForward(Screen.ArchiveSettings, Screen.Contacts))
    }

    @Test
    fun returnToDoesNotChangeScreenIdentity() {
        assertEquals(screenKey(Screen.Chat("a")), screenKey(Screen.Chat("a", "m1", Screen.Starred)))
    }

    @Test
    fun archivedIsASiblingOfChatsAndDeeperThanTheList() {
        assertTrue(goesForward(Screen.Contacts, Screen.Archived), "Contacts -> Archived should go forward")
        assertTrue(!goesForward(Screen.Archived, Screen.Contacts), "Archived -> Contacts should go back")
    }

    @Test
    fun archiveSettingsSitsDeeperThanArchived() {
        assertTrue(goesForward(Screen.Archived, Screen.ArchiveSettings))
        assertTrue(!goesForward(Screen.ArchiveSettings, Screen.Archived))
    }

    @Test
    fun stadiumInfoSitsDeeperThanItsStadium() {
        val stadium = Screen.Stadium("s1")
        val info = Screen.StadiumInfo("s1", stadium)
        assertTrue(goesForward(stadium, info), "Stadium -> info should go forward")
        assertTrue(!goesForward(info, stadium), "info -> Stadium should go back")
    }

    @Test
    fun stadiumInfoOpenedFromTheChatListStillGoesBackToIt() {
        val info = Screen.StadiumInfo("s1", Screen.Contacts)
        assertTrue(goesForward(Screen.Contacts, info))
        assertTrue(!goesForward(info, Screen.Contacts))
    }

    @Test
    fun stadiumInfoIsItsOwnDestination() {
        assertTrue(screenKey(Screen.StadiumInfo("s1")) != screenKey(Screen.Stadium("s1")))
        assertTrue(screenKey(Screen.StadiumInfo("s1")) != screenKey(Screen.ManageStadium("s1")))
        assertEquals(
            screenKey(Screen.StadiumInfo("s1")),
            screenKey(Screen.StadiumInfo("s1", Screen.Contacts))
        )
    }

    @Test
    fun archivedAndContactsAreDistinctDestinations() {
        assertTrue(screenKey(Screen.Archived) != screenKey(Screen.Contacts))
    }
}
