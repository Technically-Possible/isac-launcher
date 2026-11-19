package app.isac.launcher.ui.overlays

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.isac.launcher.data.Backdrop
import app.isac.launcher.data.Faction
import app.isac.launcher.ui.LauncherViewModel
import app.isac.launcher.ui.Overlay
import app.isac.launcher.ui.components.ChamferShape
import app.isac.launcher.ui.components.CityBackdrop
import app.isac.launcher.ui.components.DiamondEmblem
import app.isac.launcher.ui.components.EmeraldEmblem
import app.isac.launcher.ui.components.Emblem
import app.isac.launcher.ui.components.IsacEmblem
import app.isac.launcher.ui.components.Label
import app.isac.launcher.ui.components.RogueEmblem
import app.isac.launcher.ui.components.Txt
import app.isac.launcher.ui.components.VGap
import app.isac.launcher.ui.components.tap
import app.isac.launcher.ui.theme.Ink
import app.isac.launcher.ui.theme.Type
import app.isac.launcher.ui.theme.skin

/** Each faction's own colour, used on its card whatever look is showing. */
private fun Faction.color(): Color = when (this) {
    Faction.DIVISION -> Color(0xFFFF6D10)
    Faction.BLACK_TUSK -> Color(0xFFF22D41)
    Faction.ROGUE -> Color(0xFFE53965)
    Faction.EMERALD -> Color(0xFF19C37D)
}

private fun Faction.subtitle(): String = when (this) {
    Faction.DIVISION -> "SHD · ISAC"
    Faction.BLACK_TUSK -> "BTSU · DIAMOND"
    Faction.ROGUE -> "ROGUE NETWORK"
    Faction.EMERALD -> "EMERALD NETWORK"
}

private fun Faction.blurb(): String = when (this) {
    Faction.DIVISION -> "Warm orange rings, field-worn and human. The original look."
    Faction.BLACK_TUSK -> "Crimson and ice on black. Angular, cold and machine-first."
    Faction.ROGUE -> "Off the grid. Still being built."
    Faction.EMERALD -> "Still being built."
}

@Composable
private fun FactionMark(f: Faction, size: androidx.compose.ui.unit.Dp) = when (f) {
    Faction.DIVISION -> IsacEmblem(size, f.color())
    Faction.BLACK_TUSK -> DiamondEmblem(size, f.color(), Color(0xFFF2F5F8))
    Faction.ROGUE -> RogueEmblem(size, f.color())
    Faction.EMERALD -> EmeraldEmblem(size, f.color())
}

/**
 * Choose your allegiance. Shown once on first launch, and from Settings. Picking a card previews
 * that faction's look on this screen straight away; nothing is saved until you deploy.
 */
@Composable
fun FactionScreen(vm: LauncherViewModel, firstRun: Boolean) {
    val start = remember { vm.settings.value.faction }
    var pick by remember { mutableStateOf(start) }
    LaunchedEffect(pick) { Ink.setSkin(pick.skin()) }
    // Leaving without deploying puts the saved look back.
    DisposableEffect(Unit) { onDispose { Ink.setSkin(vm.settings.value.faction.skin()) } }
    // The first time, a choice has to be made; Back does nothing.
    BackHandler(enabled = firstRun) {}

    Box(Modifier.fillMaxSize().tap({})) {
        CityBackdrop(Backdrop.CITY_BLURRED)
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).padding(horizontal = 18.dp),
        ) {
            VGap(18.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Emblem(22.dp)
                Spacer(Modifier.width(10.dp))
                Label(if (firstRun) "FIRST DEPLOYMENT" else "ALLEGIANCE", color = Ink.G2, size = 11f)
            }
            VGap(18.dp)
            Txt("CHOOSE YOUR\nALLEGIANCE", Type.title(34.sp))
            VGap(10.dp)
            Txt("This sets the whole look of your home screen. You can change it any time in Settings.", Type.body(14.sp, Ink.G2))
            VGap(16.dp)
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Faction.entries.forEach { f ->
                    FactionCard(f, selected = f == pick) {
                        if (f.ready) {
                            pick = f
                            vm.g.haptics.tick()
                        } else {
                            vm.g.haptics.tick()
                            vm.showToast("${f.label.lowercase().replaceFirstChar(Char::uppercase)} isn't ready yet.")
                        }
                    }
                }
                VGap(4.dp)
            }
            VGap(12.dp)
            val d = Ink.diamond
            Box(
                Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    .background(if (d) Ink.White else Ink.Orange, if (d) ChamferShape(12.dp) else androidx.compose.ui.graphics.RectangleShape)
                    .tap({ vm.chooseFaction(pick) })
                    .semantics { role = Role.Button },
                contentAlignment = Alignment.Center,
            ) { Label("DEPLOY AS ${pick.label}", color = Ink.OnOrange, size = 13f) }
            if (!firstRun) {
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).tap({ vm.overlay = Overlay.Settings }).semantics { role = Role.Button },
                    contentAlignment = Alignment.Center,
                ) { Label("CANCEL", color = Ink.G2, size = 11.5f) }
            } else {
                VGap(16.dp)
            }
        }
    }
}

@Composable
private fun FactionCard(f: Faction, selected: Boolean, onClick: () -> Unit) {
    val c = f.color()
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 104.dp)
            .alpha(if (f.ready) 1f else 0.55f)
            .background(Ink.Glass)
            .border(if (selected) 2.dp else 1.dp, if (selected) c else Ink.Line)
            .tap(onClick)
            .semantics {
                role = Role.RadioButton
                this.selected = selected
                if (!f.ready) stateDescription = "Not ready yet"
            }
            .padding(end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).height(104.dp).background(c))
        Spacer(Modifier.width(14.dp))
        FactionMark(f, 46.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Txt(f.label, Type.title(19.sp), maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label(f.subtitle(), color = c, size = 10.5f, modifier = Modifier.weight(1f, fill = false))
                if (!f.ready) {
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.border(1.dp, Ink.G3).padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Label("WIP · NOT READY", color = Ink.G2, size = 8.5f)
                    }
                }
            }
            Txt(f.blurb(), Type.body(13.sp, Ink.G2))
        }
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(22.dp).background(c, if (Ink.diamond) app.isac.launcher.ui.components.RhombusShape else androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
                Txt("✓", Type.title(12.sp, Color(0xFF0B0D10)))
            }
        }
    }
}
