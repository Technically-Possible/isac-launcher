package app.isac.launcher.ui.pages

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.isac.launcher.data.AppEntry
import app.isac.launcher.data.Badges
import app.isac.launcher.ui.components.CountBadge
import app.isac.launcher.data.Category
import app.isac.launcher.data.IconStyle
import app.isac.launcher.data.Rarity
import app.isac.launcher.ui.LauncherViewModel
import app.isac.launcher.ui.Overlay
import app.isac.launcher.ui.components.AppGlyph
import app.isac.launcher.ui.components.Chip
import app.isac.launcher.ui.components.Gap
import app.isac.launcher.ui.components.Glyph
import app.isac.launcher.ui.components.Label
import app.isac.launcher.ui.components.Panel
import app.isac.launcher.ui.components.Tile
import app.isac.launcher.ui.components.Txt
import app.isac.launcher.ui.components.VGap
import app.isac.launcher.ui.components.tap
import app.isac.launcher.ui.theme.Ink
import app.isac.launcher.ui.theme.IsacIcons
import app.isac.launcher.ui.theme.Type

private enum class Filter(val label: String) { ALL("ALL"), COMMS("COMMS"), MEDIA("MEDIA"), TOOLS("TOOLS"), GAMES("GAMES"), WORK("WORK"), JUNK("JUNK") }

@Composable
fun InventoryPage(vm: LauncherViewModel, modifier: Modifier = Modifier) {
    val apps by vm.apps.collectAsStateWithLifecycle()
    val rarity by vm.rarity.collectAsStateWithLifecycle()
    val usage by vm.usage.collectAsStateWithLifecycle()
    val junk by vm.junk.collectAsStateWithLifecycle()
    val s by vm.settings.collectAsStateWithLifecycle()
    val badges by vm.badges.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(Filter.ALL) }
    var query by rememberSaveable { mutableStateOf("") }
    var mostUsed by rememberSaveable { mutableStateOf(true) }
    val context = LocalContext.current
    val focus = LocalFocusManager.current

    val shown = remember(apps, rarity, usage, junk, filter, query, mostUsed) {
        apps.asSequence()
            .filter { if (filter == Filter.JUNK) it.key in junk else it.key !in junk }
            .filter {
                when (filter) {
                    Filter.ALL, Filter.JUNK -> true
                    Filter.WORK -> it.isWork
                    Filter.COMMS -> it.category == Category.COMMS
                    Filter.MEDIA -> it.category == Category.MEDIA
                    Filter.TOOLS -> it.category == Category.TOOLS
                    Filter.GAMES -> it.category == Category.GAMES
                }
            }
            .filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }
            .let { seq ->
                if (mostUsed && usage.hasAccess) seq.sortedWith(
                    compareByDescending<AppEntry> { usage.byPackage[it.pkg]?.foreground7d ?: 0L }.thenBy { it.label.lowercase() },
                ) else seq
            }
            .toList()
    }
    val filters = Filter.entries.filter { it != Filter.JUNK || junk.isNotEmpty() }
        .filter { it != Filter.WORK || apps.any { a -> a.isWork } }

    Column(modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.Bottom) {
            Txt("INVENTORY", Type.title(28.sp))
            Spacer(Modifier.weight(1f))
            Label("${apps.count { it.key !in junk }} ITEMS", color = Ink.G2, size = 11f)
        }
        Row(Modifier.padding(top = 12.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            filters.forEach { f -> Chip(f.label, f == filter) { filter = f } }
        }
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).height(40.dp).background(Ink.Glass).border(1.dp, Ink.Orange.copy(alpha = 0.45f)).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Glyph(IsacIcons.Search, 16.dp, Ink.Orange)
                Gap(8.dp)
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Txt("Search", Type.body(14.sp, Ink.G3))
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = Type.body(14.sp),
                        cursorBrush = SolidColor(Ink.Orange),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = {
                            shown.firstOrNull()?.let { vm.launch(it); query = ""; focus.clearFocus() }
                        }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (query.isNotEmpty()) {
                    Box(Modifier.tap({ query = "" }).padding(4.dp)) { Label("CLEAR", color = Ink.G2, size = 9.5f) }
                }
            }
            Gap(8.dp)
            Box(
                Modifier.height(40.dp).border(1.dp, Ink.Line).tap({ mostUsed = !mostUsed }).padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Label("SORT", color = Ink.G2, size = 9.5f)
                    Gap(6.dp)
                    Label(if (mostUsed && usage.hasAccess) "MOST USED" else "A–Z", color = Ink.White, size = 9.5f)
                }
            }
        }
        if (!usage.hasAccess && s.rarityByUsage) {
            VGap(10.dp)
            Panel(Modifier.fillMaxWidth().tap({
                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }), padding = 10.dp) {
                Label("RARITY OFFLINE")
                VGap(4.dp)
                Txt("Turn on Usage Access for SHD ISAC to sort and color items by how often you use them. Tap to open the setting.", Type.body(12.5.sp, Ink.G2))
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(shown, key = { it.key }) { e ->
                InventoryItem(e, rarity[e.key] ?: Rarity.STANDARD, s.iconStyle, isNew = System.currentTimeMillis() - e.firstInstall < 3 * 86_400_000L,
                    onClick = { vm.launch(e); query = "" }, onLongClick = { vm.overlay = Overlay.Details(e) },
                    badge = Badges.countFor(badges, e))
            }
        }
    }
}

@Composable
fun InventoryItem(
    e: AppEntry, rarity: Rarity, style: IconStyle, isNew: Boolean, onClick: () -> Unit, onLongClick: () -> Unit,
    badge: Int = 0,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Tile(rarity, Modifier.fillMaxWidth().height(64.dp), onClick = onClick, onLongClick = onLongClick) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { AppGlyph(e, 30.dp, style) }
            if (isNew) {
                Box(Modifier.align(Alignment.TopStart).padding(3.dp).background(Ink.Orange).padding(horizontal = 4.dp, vertical = 1.dp)) {
                    Label("NEW", color = Ink.OnOrange, size = 8f)
                }
            }
            if (e.isWork) {
                Glyph(IsacIcons.Brief, 12.dp, Ink.White, Modifier.align(Alignment.BottomEnd).padding(start = 3.dp, end = 3.dp, bottom = 5.dp))
            }
            CountBadge(badge, Modifier.align(Alignment.TopEnd).padding(3.dp))
        }
        VGap(5.dp)
        Label(e.label, color = Ink.G2, size = 9.5f)
    }
}
