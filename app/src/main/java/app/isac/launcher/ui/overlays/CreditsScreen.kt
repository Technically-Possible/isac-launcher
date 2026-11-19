package app.isac.launcher.ui.overlays

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.isac.launcher.data.Backdrop
import app.isac.launcher.ui.LauncherViewModel
import app.isac.launcher.ui.Overlay
import app.isac.launcher.ui.components.CityBackdrop
import app.isac.launcher.ui.components.Emblem
import app.isac.launcher.ui.components.Glyph
import app.isac.launcher.ui.components.Label
import app.isac.launcher.ui.components.Panel
import app.isac.launcher.ui.components.Prompt
import app.isac.launcher.ui.components.PromptBar
import app.isac.launcher.ui.components.SectionHeader
import app.isac.launcher.ui.components.Txt
import app.isac.launcher.ui.components.VGap
import app.isac.launcher.ui.components.tap
import app.isac.launcher.ui.components.topCutout
import app.isac.launcher.ui.components.topInsets
import app.isac.launcher.ui.theme.Ink
import app.isac.launcher.ui.theme.IsacIcons
import app.isac.launcher.ui.theme.Type

private const val CREATOR_SITE = "https://jenn.zip"
private const val SOURCE = "https://github.com/Technically-Possible/isac-launcher"

/** Who made the launcher, and the open-source projects and free services it's built on. */
private val BUILT_WITH = listOf(
    Triple("OpenStreetMap", "Map, landmark and accessible-place data © OpenStreetMap contributors, under the ODbL.", "https://www.openstreetmap.org/copyright"),
    Triple("OpenFreeMap", "Free map tiles, with no account or key.", "https://openfreemap.org"),
    Triple("MapLibre", "The map engine behind the fog-of-war map.", "https://maplibre.org"),
    Triple("Overpass API", "Finds nearby landmarks for caches, and accessible places.", "https://overpass-api.de"),
    Triple("Open-Meteo", "Weather and town search, under CC BY 4.0.", "https://open-meteo.com"),
    Triple("Vosk", "Offline voice commands, by Alpha Cephei, under Apache 2.0.", "https://alphacephei.com/vosk"),
    Triple("Titillium Web", "The Division typeface, under the SIL Open Font License.", "https://fonts.google.com/specimen/Titillium+Web"),
    Triple("Chakra Petch, Michroma, JetBrains Mono", "The Black Tusk typefaces, under the SIL Open Font License.", "https://fonts.google.com/specimen/Chakra+Petch"),
)

@Composable
fun CreditsScreen(vm: LauncherViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    fun open(url: String) = runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    Box(Modifier.fillMaxSize()) {
        CityBackdrop(Backdrop.CITY_BLURRED)
        val atTop = s.hideStatusBar && topCutout() != null
        Column(Modifier.fillMaxSize().then(if (atTop) Modifier else Modifier.windowInsetsPadding(topInsets()))) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = if (atTop) 10.dp else 14.dp), verticalAlignment = Alignment.Bottom) {
                Txt("CREDITS", Type.title(28.sp))
                Spacer(Modifier.weight(1f))
                Label("${app.isac.launcher.ui.theme.Lex.ai} ${app.isac.launcher.ui.components.appVersion()}", color = Ink.G2, size = 11f)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                VGap(14.dp)
                Panel(Modifier.fillMaxWidth(), padding = 18.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Emblem(40.dp)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Label("SHD ISAC · DIAMOND · CREATED BY", color = Ink.G2, size = 10.5f)
                            Txt("JENN", Type.title(40.sp))
                        }
                    }
                    VGap(14.dp)
                    LinkButton("JENN.ZIP", filled = true) { open(CREATOR_SITE) }
                    VGap(8.dp)
                    LinkButton("SOURCE CODE ON GITHUB") { open(SOURCE) }
                }
                SectionHeader("BUILT WITH")
                BUILT_WITH.forEach { (name, what, url) ->
                    Row(
                        Modifier.fillMaxWidth().background(Ink.Glass).tap({ open(url) }).padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Txt(name, Type.body(15.sp, weight = FontWeight.SemiBold))
                            Txt(what, Type.body(13.sp, Ink.G2))
                        }
                        Spacer(Modifier.width(10.dp))
                        Glyph(IsacIcons.Chevron, 14.dp, Ink.Orange)
                    }
                    Box(Modifier.fillMaxWidth().heightIn(min = 1.dp).background(Ink.Line))
                }
                SectionHeader("FAN PROJECT")
                Txt(
                    "A free, fan-made launcher inspired by the menus of Tom Clancy's The Division 2. All artwork and icons are original. " +
                        "Not affiliated with or endorsed by Ubisoft or Massive Entertainment. Tom Clancy's The Division and names from the game, such as SHD, ISAC, Black Tusk and DIAMOND, belong to Ubisoft.",
                    Type.body(13.5.sp, Ink.G2),
                )
                VGap(18.dp)
            }
            PromptBar(
                listOf(Prompt("BACK", key = "‹") { vm.overlay = Overlay.Settings }),
                Modifier.windowInsetsPadding(WindowInsets.navigationBars),
            )
        }
    }
}

@Composable
private fun LinkButton(text: String, filled: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 46.dp)
            .then(if (filled) Modifier.background(Ink.Orange) else Modifier.border(1.5.dp, Ink.Orange))
            .tap(onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Label(text, color = if (filled) Ink.OnOrange else Ink.White, size = 12.5f, modifier = Modifier.weight(1f))
        Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
            Glyph(IsacIcons.Chevron, 14.dp, if (filled) Ink.OnOrange else Ink.Orange)
        }
    }
}
