package com.pamoja.app.ui.kit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pamoja.app.ui.theme.PamojaIcons

/**
 * Debug-only review surface for the Lilac kit (REBUILD_PLAN.md Phase 2).
 *
 * Phase 2 is done when this renders correctly across the test matrix: a
 * 360x640 phone, 200% font size, landscape, a tablet or foldable, dark mode,
 * and Hindi. Drive it with adb, e.g.
 *
 *   adb shell am start -n com.arkayenlabs.pamoja/com.pamoja.app.ui.kit.KitGalleryActivity --ez dark true
 *
 * Copy here is sample data, not product copy, which is why it is not in
 * strings.xml. Release builds do not contain this activity.
 */
class KitGalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val forced = if (intent.hasExtra(EXTRA_DARK)) intent.getBooleanExtra(EXTRA_DARK, false) else null
        setContent {
            KitTheme(dark = forced ?: isSystemInDarkTheme()) { KitGallery() }
        }
    }

    companion object {
        const val EXTRA_DARK = "dark"
    }
}

@Composable
private fun KitGallery() {
    var name by remember { mutableStateOf("Ravi Sharma") }
    var adult by remember { mutableStateOf(true) }
    val width = currentWindowWidth()

    KitScreen(
        top = {
            TopRow(
                start = { CircleIconButton(PamojaIcons.ArrowLeft, "Back", onClick = {}) },
                end = {
                    CircleIconButton(PamojaIcons.Info, "Group details", onClick = {})
                    Avatar(name = "Ravi Sharma", photoUrl = null, size = 44.dp)
                },
            )
        },
        bottom = {
            PrimaryButton("Continue", onClick = {})
            QuietButton("I have an invite link", onClick = {})
        },
    ) {
        Text("Your people. One weekly goal.", style = Kit.type.display)
        Text("Steps count on their own. Nothing to log.", style = Kit.type.body, color = Kit.colors.muted)
        Text("आपके लोग। एक साप्ताहिक लक्ष्य।", style = Kit.type.headline)
        Text("Window: $width", style = Kit.type.caption, color = Kit.colors.muted)

        StepSegments(current = 1, total = 4, description = "Step 1 of 4")

        KitCard(tone = CardTone.Hero) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("This week", style = Kit.type.bodyStrong, modifier = Modifier.weight(1f))
                PopChip("6 weeks in a row", icon = PamojaIcons.Flame)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("48,320", style = Kit.type.bigNumber, modifier = Modifier.alignByBaseline())
                Text("of 70,000", style = Kit.type.bodyStrong, color = mutedContentColor(), modifier = Modifier.alignByBaseline())
            }
            GoalBar(progress = 0.69f, description = "48,320 of 70,000 steps", onHero = true)
            Text("3 days left, on pace for Saturday", style = Kit.type.label, color = mutedContentColor())
        }

        KitCard {
            CardHeader("Today", detail = "4 of 6 walked")
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                listOf("You" to true, "Priya" to true, "Sunita" to true, "Ramesh" to true, "Meera" to false, "Arjun" to false)
                    .forEach { (person, walked) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Avatar(name = person, photoUrl = null, size = 46.dp, walked = walked)
                            Text(person, style = Kit.type.caption, color = mutedContentColor())
                        }
                    }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Arjun and Meera haven't walked yet", style = Kit.type.body, modifier = Modifier.weight(1f))
                PrimaryButton("Nudge", onClick = {}, size = ButtonSize.Small, icon = PamojaIcons.Bell)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            KitCard(modifier = Modifier.weight(1f)) {
                Text("You today", style = Kit.type.label, color = mutedContentColor())
                Text("6,240", style = Kit.type.number)
                Text("Your usual is 7,500", style = Kit.type.caption, color = mutedContentColor())
                GoalBar(progress = 0.83f, description = "6,240 of your usual 7,500")
            }
            KitCard(modifier = Modifier.weight(1f), tone = CardTone.Pop, padding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Avatar(name = "Priya", photoUrl = null, size = 38.dp)
                    Text("Priya cheered your walk", style = Kit.type.bodyStrong)
                }
                PrimaryButton("Cheer back", onClick = {}, size = ButtonSize.Small, modifier = Modifier.fillMaxWidth())
            }
        }

        KitCard {
            CardHeader("This week", detail = "Mon to Sun")
            PersonRow(name = "Priya", photoUrl = null, detail = "Joined 2 Sep") {
                DayDots(listOf(true, true, false, true, false, false, false), description = "Walked 3 of 4 days so far", elapsed = 4)
            }
            PersonRow(name = "You", photoUrl = null, emphasised = true) {
                Text("38,110", style = Kit.type.bodyStrong)
            }
        }

        KitCard(spacing = 0.dp) {
            ListRow("Weekly goal", onClick = {}, value = "70,000 steps")
            ListRow("Member limit", onClick = {}, value = "10")
            ListRow("Week starts", onClick = {}, value = "Monday", showDivider = false)
        }

        KitTextField(
            value = name,
            onValueChange = { name = it },
            label = "Name",
            helper = "Filled in from Google. You can change it.",
            error = if (name.isBlank()) "Enter your name" else null,
        )
        CheckRow(checked = adult, onCheckedChange = { adult = it }, text = "I'm 18 or older")

        SecondaryButton("Continue with Google", onClick = {}, icon = PamojaIcons.Google)
        SecondaryButton("Continue with phone", onClick = {}, icon = PamojaIcons.Smartphone)
        PrimaryButton("Saving", onClick = {}, loading = true)
        PrimaryButton("Disabled", onClick = {}, enabled = false)
        QuietButton("Leave group", onClick = {}, danger = true)
    }
}
