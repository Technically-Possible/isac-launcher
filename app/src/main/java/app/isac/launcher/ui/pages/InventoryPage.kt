package app.isac.launcher.ui.pages

import android.Manifest
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.isac.launcher.data.AppEntry
import app.isac.launcher.data.AppIcon
import app.isac.launcher.data.Badges
import app.isac.launcher.data.Category
import app.isac.launcher.data.IconStyle
import app.isac.launcher.data.Rarity
import app.isac.launcher.data.Search
import app.isac.launcher.data.SearchHit
import app.isac.launcher.ui.LauncherViewModel
import app.isac.launcher.ui.Overlay
import app.isac.launcher.ui.components.AppGlyph
import app.isac.launcher.ui.components.Chip
import app.isac.launcher.ui.components.CountBadge
import app.isac.launcher.ui.components.Gap
import app.isac.launcher.ui.components.Glyph
import app.isac.launcher.ui.components.Label
import app.isac.launcher.ui.components.Panel
import app.isac.launcher.ui.components.Tile
import app.isac.launcher.ui.components.Txt
import app.isac.launcher.ui.components.VGap
import app.isac.launcher.ui.components.tap
import app.isac.launcher.ui.theme.Ink
import app.isac.launcher.ui.theme.Lex
import app.isac.launcher.ui.theme.IsacIcons
import app.isac.launcher.ui.theme.Type

private enum class Filter(val label: String) {
    ALL("ALL"), COMMS("COMMS"), MEDIA("MEDIA"), TOOLS("TOOLS"), GAMES("GAMES"), WORK("WORK"), PRIVATE("PRIVATE"), JUNK("JUNK")
}

@Composable
fun InventoryPage(vm: LauncherViewModel, modifier: Modifier = Modifier) {
    val apps by vm.apps.collectAsStateWithLifecycle()
    val rarity by vm.rarity.collectAsStateWithLifecycle()
    val usage by vm.usage.collectAsStateWithLifecycle()
    val junk by vm.junk.collectAsStateWithLifecycle()
    val s by vm.settings.collectAsStateWithLifecycle()
    val badges by vm.badges.collectAsStateWithLifecycle()
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    val pinned by vm.pinned.collectAsStateWithLifecycle()
    val privateSpace by vm.privateSpace.collectAsStateWithLifecycle()
    val privateApps by vm.privateApps.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(Filter.ALL) }
    var query by rememberSaveable { mutableStateOf("") }
    var mostUsed by rememberSaveable { mutableStateOf(true) }
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val searchBox = remember { FocusRequester() }
    LaunchedEffect(Unit) { vm.searchFocus.collect { filter = Filter.ALL; runCatching { searchBox.requestFocus() } } }

    val q = query.trim()
    val shown = remember(apps, privateApps, rarity, usage, junk, filter, q, mostUsed) {
        val source = if (filter == Filter.PRIVATE) privateApps else apps
        source.asSequence()
            .filter { if (filter == Filter.JUNK) it.key in junk else it.key !in junk }
            .filter {
                when (filter) {
                    Filter.ALL, Filter.JUNK, Filter.PRIVATE -> true
                    Filter.WORK -> it.isWork
                    Filter.COMMS -> it.category == Category.COMMS
                    Filter.MEDIA -> it.category == Category.MEDIA
                    Filter.TOOLS -> it.category == Category.TOOLS
                    Filter.GAMES -> it.category == Category.GAMES
                }
            }
            .filter { q.isEmpty() || it.label.contains(q, ignoreCase = true) }
            .let { seq ->
                if (mostUsed && usage.hasAccess) seq.sortedWith(
                    compareByDescending<AppEntry> { usage.byPackage[it.pkg]?.foreground7d ?: 0L }.thenBy { it.label.lowercase() },
                ) else seq
            }
            .toList()
    }
    val shortcuts = remember(pinned, q, filter) {
        if (filter != Filter.ALL) emptyList() else pinned.filter { q.isEmpty() || it.shortLabel?.contains(q, true) == true }
    }

    // Everything else search can find: sums, conversions, settings, contacts, the web.
    var contactHits by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    var contactsAllowed by remember { mutableStateOf(Search.hasContacts(context)) }
    LaunchedEffect(q, contactsAllowed) { contactHits = if (q.length >= 2) Search.contacts(context, q) else emptyList() }
    val askContacts = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { contactsAllowed = it }
    val hits = remember(q, contactHits) {
        if (q.isEmpty() || filter != Filter.ALL) emptyList() else buildList {
            Search.calculate(q)?.let { add(SearchHit("RESULT", it, q, null)) }
            Search.convert(q)?.let { add(SearchHit("CONVERTED", it, q, null)) }
            addAll(contactHits)
            if (q.length >= 2) addAll(Search.settings(q))
            if (q.length >= 2) add(Search.web(q))
        }
    }

    val filters = Filter.entries.filter {
        when (it) {
            Filter.JUNK -> junk.isNotEmpty()
            Filter.WORK -> apps.any { a -> a.isWork }
            Filter.PRIVATE -> privateSpace != null
            else -> true
        }
    }

    Column(modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.Bottom) {
            Txt(Lex.inventory, Type.title(28.sp))
            Spacer(Modifier.weight(1f))
            Label("${apps.count { it.key !in junk }} ITEMS", color = Ink.G2, size = 11f)
        }
        Row(Modifier.padding(top = 12.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            filters.forEach { f -> Chip(if (f == Filter.JUNK) Lex.junk else f.label, f == filter) { filter = f } }
        }
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).height(40.dp).background(Ink.Glass).border(1.dp, Ink.Orange.copy(alpha = 0.45f)).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Glyph(IsacIcons.Search, 16.dp, Ink.Orange)
                Gap(8.dp)
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Txt("Search apps, contacts, settings, sums", Type.body(14.sp, Ink.G3), maxLines = 1)
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = Type.body(14.sp),
                        cursorBrush = SolidColor(Ink.Orange),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = {
                            val first = shown.firstOrNull()
                            if (first != null) { vm.launch(first); query = "" }
                            else hits.firstOrNull { it.intent != null }?.let { runCatching { context.startActivity(it.intent) }; query = "" }
                            focus.clearFocus()
                        }),
                        modifier = Modifier.fillMaxWidth().focusRequester(searchBox),
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
        if (!usage.hasAccess && s.rarityByUsage && filter == Filter.ALL && q.isEmpty()) {
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
            // Private space: locked panel, or its apps with a lock button.
            val ps = privateSpace
            if (filter == Filter.PRIVATE && ps != null) {
                wide("private-head") {
                    Panel(Modifier.fillMaxWidth().tap({
                        if (!vm.g.apps.setPrivateLocked(!ps.locked)) vm.showToast("Set SHD ISAC as your home app to lock and unlock private space from here.")
                    }), padding = 12.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Label(if (ps.locked) "PRIVATE SPACE · LOCKED" else "PRIVATE SPACE · UNLOCKED")
                                VGap(4.dp)
                                Txt(if (ps.locked) "Tap to unlock with your screen lock." else "Tap to lock and hide these apps again.", Type.body(13.sp, Ink.G2))
                            }
                            Label(if (ps.locked) "UNLOCK ›" else "LOCK ›", color = Ink.Orange, size = 11f)
                        }
                    }
                }
            }
            if (q.isEmpty() && filter == Filter.ALL && suggestions.isNotEmpty()) {
                wide("sug-head") { GridHeader("SUGGESTED NOW") }
                items(suggestions, key = { "sug:" + it.key }) { e ->
                    InventoryItem(e, rarity[e.key] ?: Rarity.STANDARD, s.iconStyle, isNew = false,
                        onClick = { vm.launch(e) }, onLongClick = { vm.overlay = Overlay.Details(e) }, badge = Badges.countFor(badges, e))
                }
                wide("all-head") { GridHeader("ALL ITEMS") }
            }
            hits.forEach { h ->
                wide("hit:${h.kind}:${h.title}") {
                    HitRow(h) { h.intent?.let { runCatching { context.startActivity(it) } } }
                }
            }
            if (q.length >= 2 && filter == Filter.ALL && !contactsAllowed) {
                wide("contacts-ask") {
                    HitRow(SearchHit("CONTACTS", "Search your contacts too", "Tap to allow", null)) { askContacts.launch(Manifest.permission.READ_CONTACTS) }
                }
            }
            if (!(filter == Filter.PRIVATE && ps?.locked == true)) {
                items(shown, key = { it.key }) { e ->
                    InventoryItem(e, rarity[e.key] ?: Rarity.STANDARD, s.iconStyle, isNew = System.currentTimeMillis() - e.firstInstall < 3 * 86_400_000L,
                        onClick = { vm.launch(e); query = "" }, onLongClick = { vm.overlay = Overlay.Details(e) },
                        badge = Badges.countFor(badges, e))
                }
            }
            if (shortcuts.isNotEmpty()) {
                wide("sc-head") { GridHeader("SHORTCUTS") }
                items(shortcuts, key = { "sc:${it.`package`}/${it.id}" }) { sc ->
                    ShortcutItem(vm, sc, onClick = { vm.g.apps.startShortcut(sc); query = "" }, onLongClick = { vm.overlay = Overlay.Shortcut(sc) })
                }
            }
        }
    }
}

private fun LazyGridScope.wide(key: String, content: @Composable () -> Unit) =
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }

@Composable
private fun GridHeader(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).background(Ink.Orange))
        Gap(8.dp)
        Label(text, size = 10.5f)
        Gap(10.dp)
        Box(Modifier.weight(1f).height(1.dp).background(Ink.Orange.copy(alpha = 0.35f)))
    }
}

@Composable
private fun HitRow(h: SearchHit, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(50.dp).background(Ink.Glass).border(1.dp, Ink.Line).tap(onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(84.dp)) {
            Box(Modifier.border(1.dp, Ink.Orange).padding(horizontal = 5.dp, vertical = 2.dp)) { Label(h.kind, size = 8.5f) }
        }
        Column(Modifier.weight(1f)) {
            Txt(h.title, Type.body(if (h.intent == null) 18.sp else 14.5.sp, weight = FontWeight.SemiBold), maxLines = 1)
            Label(h.detail, color = Ink.G3, size = 9f)
        }
        if (h.intent != null || h.kind == "CONTACTS") Glyph(IsacIcons.Chevron, 14.dp, Ink.Orange)
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

/** A pinned shortcut's own icon (for example a web app added from the browser). */
@Composable
fun ShortcutGlyph(vm: LauncherViewModel, sc: ShortcutInfo, size: Dp, modifier: Modifier = Modifier) {
    var icon by remember(sc.id, sc.`package`) { mutableStateOf<AppIcon?>(null) }
    LaunchedEffect(sc.id, sc.`package`) { icon = vm.g.icons.loadShortcut(sc, vm.g.apps) }
    val i = icon
    if (i == null) Glyph(IsacIcons.Chevron, size, Ink.G2, modifier) else Image(i.bitmap, null, modifier.size(size))
}

@Composable
private fun ShortcutItem(vm: LauncherViewModel, sc: ShortcutInfo, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Tile(Rarity.SPECIALIZED, Modifier.fillMaxWidth().height(64.dp), onClick = onClick, onLongClick = onLongClick) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ShortcutGlyph(vm, sc, 32.dp) }
        }
        VGap(5.dp)
        Label(sc.shortLabel?.toString() ?: "Shortcut", color = Ink.G2, size = 9.5f)
    }
}
