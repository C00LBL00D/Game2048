package com.example.game2048

import android.content.SharedPreferences
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

private val BG = Color(0xFFFAF8EF)
private val BOARD_BG = Color(0xFFBBADA0)
private val EMPTY_CELL = Color(0xFFCDC1B4)
private val TEXT_DARK = Color(0xFF776E65)
private val TEXT_MUTED = Color(0xFF9C8F82)
private val CARD_BG = Color(0xFFF3ECE1)
private const val SLIDE_MS = 90

/**
 * 道具。市面上的 2048 基本都是这几个套路：锤子/炸弹、交换、洗牌、翻倍、清屏。
 * 这里取四个：两个要点选方块，两个直接生效。
 */
private enum class Item(val icon: String, val label: String, val hint: String) {
    HAMMER("🔨", "锤子", "点掉一个方块"),
    DOUBLE("⚡", "翻倍", "选中的方块数值翻倍"),
    SHUFFLE("🎲", "重排", "随机重排所有数字"),
    PURGE("✨", "净化", "清掉所有 2 和 4"),
    ;

    val needsTarget: Boolean get() = this == HAMMER || this == DOUBLE
}

private data class Snapshot(val tiles: List<Game2048.Tile>, val score: Int)

/** 开局赠送：锤子和翻倍各 10，重排和净化各 5 */
private val INITIAL_ITEMS = mapOf(
    Item.HAMMER to 10,
    Item.DOUBLE to 10,
    Item.SHUFFLE to 5,
    Item.PURGE to 5,
)

/** 一局最多撤销 3 步 */
private const val UNDO_PER_GAME = 3

/** 会改棋盘的操作都先弹确认，防误触 */
private sealed interface Pending {
    data class UseItem(val item: Item, val target: Game2048.Tile?) : Pending
    data object Undo : Pending
    data object Restart : Pending
}

private val TIPS = listOf(
    "把最大的数字钉死在一个角落，后面所有的移动都往那个角挤。",
    "只守两条边（比如「上」和「左」）来回走，棋盘不容易被切碎。",
    "留出空格比拿分重要，堆满之前别急着把小数字合掉。",
    "大数字按顺序排成一条蛇形走廊，从角落往外递减最稳。",
    "卡住的时候先清边上一列，别让中间被孤立的方块堵死。",
    "每走一步先问自己：这一步会不会把大数字挤离角落。",
    "4 和 2 是缓冲，先保住空间，攒够了再一口气合。",
    "先稳住 512 再想 1024，手快反而更容易死。",
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("game2048", MODE_PRIVATE)
        setContent { MaterialTheme { Game2048Screen(prefs) } }
    }
}

@Composable
fun Game2048Screen(prefs: SharedPreferences) {
    val context = LocalContext.current
    val vibrator = remember { context.getSystemService(Vibrator::class.java) }


    var tiles by remember {
        mutableStateOf(
            prefs.getString("board", null)?.let(::decodeTiles)?.takeIf { it.isNotEmpty() }
                ?: Game2048.newGame()
        )
    }
    var score by remember { mutableIntStateOf(prefs.getInt("score", 0)) }
    var best by remember { mutableIntStateOf(prefs.getInt("best", 0)) }
    var history by remember { mutableStateOf<List<Snapshot>>(emptyList()) }
    var items by remember { mutableStateOf(loadItems(prefs)) }
    var undosLeft by remember { mutableIntStateOf(prefs.getInt("undos", UNDO_PER_GAME)) }
    var pending by remember { mutableStateOf<Pending?>(null) }
    var drops by remember { mutableIntStateOf(prefs.getInt("drops", 0)) }
    var moves by remember { mutableIntStateOf(prefs.getInt("moves", 0)) }
    var showWin by remember { mutableStateOf(false) }
    var winTile by remember { mutableIntStateOf(2048) }
    var gameOverDismissed by remember { mutableStateOf(false) }
    var armed by remember { mutableStateOf<Item?>(null) }
    var ghosts by remember { mutableStateOf<List<Game2048.Slide>>(emptyList()) }
    var drag by remember { mutableStateOf(Offset.Zero) }
    var tipIndex by remember { mutableIntStateOf(0) }
    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
    var signedIn by remember { mutableStateOf(prefs.getString("checkInDate", "") == today) }
    var showSettings by remember { mutableStateOf(false) }
    var vibrateOn by remember { mutableStateOf(prefs.getBoolean("vibrate", true)) }
    var sfxVolume by remember { mutableFloatStateOf(prefs.getInt("sfx", 80) / 100f) }
    var musicVolume by remember { mutableFloatStateOf(prefs.getInt("music", 30) / 100f) }
    // 每次状态变化都落盘：杀进程 / 切后台回来还能接着玩
    LaunchedEffect(tiles, score, moves, drops, undosLeft, items) {
        prefs.edit()
            .putString("board", encodeTiles(tiles))
            .putInt("score", score)
            .putInt("moves", moves)
            .putInt("drops", drops)
            .putInt("undos", undosLeft)
            .putString("items", Item.entries.joinToString(",") { (items[it] ?: 0).toString() })
            .apply()
    }

    val music = remember {
        MediaPlayer.create(context, R.raw.bgm)?.apply {
            isLooping = true
            setVolume(musicVolume, musicVolume)
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        music?.start()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> music?.start()
                Lifecycle.Event.ON_PAUSE -> music?.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            music?.release()
        }
    }
    LaunchedEffect(musicVolume) { music?.setVolume(musicVolume, musicVolume) }

    // ToneGenerator 没有 setVolume，音量只能在建的时候给，所以按 10% 一档重建
    val toneStep = (sfxVolume * 10).roundToInt()
    val tone = remember(toneStep) {
        ToneGenerator(AudioManager.STREAM_MUSIC, (toneStep * 10).coerceIn(0, 100))
    }
    DisposableEffect(tone) { onDispose { tone.release() } }
    val gameOver = remember(tiles) { tiles.isNotEmpty() && !Game2048.canMove(tiles) }

    LaunchedEffect(ghosts) {
        if (ghosts.isNotEmpty()) {
            delay(SLIDE_MS.toLong() + 120)
            ghosts = emptyList()
        }
    }

    fun feedback(millis: Long) {
        if (vibrateOn) {
            vibrator?.vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE))
        }
        tone.startTone(ToneGenerator.TONE_PROP_BEEP, 45)
    }

    fun saveBest(value: Int) {
        if (value > best) {
            best = value
            prefs.edit().putInt("best", value).apply()
        }
    }

    fun pushHistory() {
        history = (history + Snapshot(tiles, score)).takeLast(30)
    }

    /** 道具用完后可能把棋盘清空，补两块继续 */
    fun settle(next: List<Game2048.Tile>) {
        tiles = if (next.isEmpty()) Game2048.newGame() else next
    }

    /** 抽 slots 次：每次随机一种道具，给 1~3 个。返回「锤子×2，重排×1」这样的汇总。 */
    fun awardItems(slots: Int): String {
        val got = LinkedHashMap<Item, Int>()
        var total = 0
        repeat(slots) {
            val item = Item.entries.random()
            val n = Random.nextInt(1, 4)
            got[item] = (got[item] ?: 0) + n
            items = items + (item to (items[item] ?: 0) + n)
            total += n
        }
        drops += total
        return got.entries.joinToString("，") { "${it.key.label}×${it.value}" }
    }

    /** 每日签到：一天一次，随机给几波道具 */
    fun checkIn() {
        if (signedIn) return
        signedIn = true
        prefs.edit().putString("checkInDate", today).apply()
        val summary = awardItems(Random.nextInt(3, 6))
        feedback(25)
        Toast.makeText(context, "签到奖励：$summary", Toast.LENGTH_SHORT).show()
    }

    fun slide(dir: Int) {
        val result = Game2048.move(tiles, dir)
        if (!result.changed) return
        pushHistory()
        ghosts = result.slides.filter { it.merged }
        tiles = Game2048.spawn(result.tiles)
        score += result.score
        moves++
        saveBest(score)
        if (result.score > 0) feedback(12)
        result.created.filter { it >= 2048 }.maxOrNull()?.let {
            winTile = it
            showWin = true
        }
        result.created.forEach { value ->
            val summary = awardItems(Game2048.dropCount(value))
            Toast.makeText(context, "合成 $value！获得 $summary", Toast.LENGTH_SHORT).show()
        }
    }

    fun applyItem(item: Item, target: Game2048.Tile? = null) {
        val count = items[item] ?: 0
        if (count <= 0) return
        pushHistory()
        val next = when (item) {
            Item.SHUFFLE -> Game2048.shuffle(tiles)
            Item.PURGE -> Game2048.purge(tiles)
            Item.HAMMER -> target?.let { Game2048.hammer(tiles, it.cell) } ?: tiles
            Item.DOUBLE -> target?.let { Game2048.double(tiles, it.id) } ?: tiles
        }
        settle(next)
        items = items + (item to count - 1)
        armed = null
        feedback(25)
    }

    /** 点道具：要选方块的就进入待选状态，再点一次取消；不用选的直接弹确认 */
    fun tapItem(item: Item) {
        if ((items[item] ?: 0) <= 0) return
        if (item.needsTarget) {
            armed = if (armed == item) null else item
        } else {
            pending = Pending.UseItem(item, null)
        }
    }

    fun tapTile(tile: Game2048.Tile) {
        val item = armed ?: return
        pending = Pending.UseItem(item, tile)
    }

    fun doUndo() {
        if (undosLeft <= 0) return
        val last = history.lastOrNull() ?: return
        history = history.dropLast(1)
        tiles = last.tiles
        score = last.score
        undosLeft--
        armed = null
    }

    fun restart() {
        tiles = Game2048.newGame()
        score = 0
        moves = 0
        drops = 0
        history = emptyList()
        items = INITIAL_ITEMS
        undosLeft = UNDO_PER_GAME
        ghosts = emptyList()
        armed = null
        showWin = false
        gameOverDismissed = false
    }

    fun perform(action: Pending) {
        when (action) {
            is Pending.UseItem -> applyItem(action.item, action.target)
            Pending.Undo -> doUndo()
            Pending.Restart -> restart()
        }
    }


    val maxTile = tiles.maxOfOrNull { it.value } ?: 0

    Surface(modifier = Modifier.fillMaxSize(), color = BG) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("2048", fontSize = 60.sp, fontWeight = FontWeight.Bold, color = TEXT_DARK)
                Column(horizontalAlignment = Alignment.End) {
                    // 齿轮放在签到框外面、屏幕右上角
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .clickable { showSettings = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = "设置",
                            tint = TEXT_MUTED,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Row(
                        modifier = Modifier.height(IntrinsicSize.Min),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ScoreBox("分数", score)
                        Spacer(Modifier.width(12.dp))
                        ScoreBox("最高", best)
                        Spacer(Modifier.width(12.dp))
                        CheckInButton(
                            signedIn = signedIn,
                            modifier = Modifier.fillMaxHeight().width(52.dp),
                            onClick = { checkIn() },
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Item.entries.forEach { item ->
                    ItemChip(
                        item = item,
                        count = items[item] ?: 0,
                        armed = armed == item,
                        modifier = Modifier.weight(1f),
                        onClick = { tapItem(item) },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Board(
                tiles = tiles,
                ghosts = ghosts,
                onTileTap = { tapTile(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { drag = Offset.Zero },
                            onDragEnd = {
                                if (maxOf(abs(drag.x), abs(drag.y)) > 40f) {
                                    if (abs(drag.x) > abs(drag.y)) {
                                        slide(if (drag.x > 0) Game2048.RIGHT else Game2048.LEFT)
                                    } else {
                                        slide(if (drag.y > 0) Game2048.DOWN else Game2048.UP)
                                    }
                                }
                            },
                        ) { change, delta ->
                            change.consume()
                            drag += delta
                        }
                    },
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { pending = Pending.Undo },
                    enabled = history.isNotEmpty() && undosLeft > 0,
                    contentPadding = PaddingValues(horizontal = 14.dp),
                ) { Text("撤销 $undosLeft/$UNDO_PER_GAME") }
                OutlinedButton(
                    onClick = { pending = Pending.Restart },
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) { Text("重开") }
                Text(
                    text = when {
                        armed != null -> "点一个方块使用「${armed!!.label}」"
                        gameOver -> "没步可走了，点重开"
                        else -> "滑动合并，合出 2048 掉道具"
                    },
                    color = if (armed != null) Color(0xFFD2694B) else TEXT_MUTED,
                    fontSize = 12.sp,
                    textAlign = TextAlign.End,
                    maxLines = 2,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(12.dp))

            TipCard(
                moves = moves,
                maxTile = maxTile,
                drops = drops,
                tip = TIPS[tipIndex % TIPS.size],
                onNextTip = { tipIndex++ },
            )
        }
    }

    if (showWin) {
        AlertDialog(
            onDismissRequest = { showWin = false },
            title = { Text("合出 $winTile！") },
            text = { Text("当前 $score 分，最高 $best 分。棋盘还能继续冲更高的方块。") },
            confirmButton = {
                TextButton(onClick = { showWin = false }) { Text("继续挑战") }
            },
            dismissButton = {
                TextButton(onClick = { showWin = false; restart() }) { Text("重开一局") }
            },
        )
    }

    if (gameOver && !showWin && !gameOverDismissed) {
        AlertDialog(
            onDismissRequest = { gameOverDismissed = true },
            title = { Text("没步可走了") },
            text = {
                Text("本局 $score 分，最大方块 $maxTile。还可以用道具或撤销救一下。")
            },
            confirmButton = {
                TextButton(onClick = { gameOverDismissed = true; restart() }) { Text("再来一局") }
            },
            dismissButton = {
                TextButton(onClick = { gameOverDismissed = true }) { Text("先看看") }
            },
        )
    }

    if (showSettings) {
        SettingsDialog(
            vibrate = vibrateOn,
            sfx = sfxVolume,
            music = musicVolume,
            onVibrate = {
                vibrateOn = it
                prefs.edit().putBoolean("vibrate", it).apply()
            },
            onSfx = { sfxVolume = it },
            onMusic = { musicVolume = it },
            onClose = {
                prefs.edit()
                    .putInt("sfx", (sfxVolume * 100).roundToInt())
                    .putInt("music", (musicVolume * 100).roundToInt())
                    .apply()
                showSettings = false
            },
        )
    }

    // 改棋盘的操作都要过这一步：避免手滑点掉道具或者重开一局
    pending?.let { action ->
        val target = (action as? Pending.UseItem)?.target
        val (title, message) = when (action) {
            is Pending.UseItem -> when (action.item) {
                Item.HAMMER -> "使用锤子？" to "会敲掉这个 ${target?.value} 方块。"
                Item.DOUBLE -> "使用翻倍？" to "这个 ${target?.value} 会变成 ${(target?.value ?: 0) * 2}。"
                Item.SHUFFLE -> "使用重排？" to "场上这 ${tiles.size} 个方块会重新撒到棋盘任意位置。"
                Item.PURGE -> "使用净化？" to "会清掉场上所有 2 和 4。"
            }
            Pending.Undo -> "撤销这一步？" to "本局还剩 $undosLeft 次撤销，用完后不能再退回。"
            Pending.Restart -> "重新开始？" to "当前 $score 分会清零，道具恢复成开局数量。"
        }
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(title) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    perform(action)
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun ItemChip(item: Item, count: Int, armed: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val enabled = count > 0
    val bg = when {
        armed -> Color(0xFFE8794F)
        enabled -> Color(0xFF8F7A66)
        else -> Color(0xFFE3DCD2)
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = item.icon,
            fontSize = 20.sp,
            modifier = Modifier.graphicsLayer { alpha = if (enabled) 1f else 0.35f },
        )
        Text(
            text = item.label,
            color = if (enabled) Color.White else TEXT_MUTED,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "×$count",
            color = if (enabled) Color(0xFFF3E7DA) else TEXT_MUTED,
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun CheckInButton(signedIn: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(BOARD_BG)
            .clickable(enabled = !signedIn, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "签到",
                color = Color(0xFFEEE4DA).copy(alpha = if (signedIn) 0.35f else 1f),
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(3.dp))
            CalendarIcon(tint = Color(0xFFEEE4DA).copy(alpha = if (signedIn) 0.35f else 1f))
        }
        // 还没签到的小红点
        if (!signedIn) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 7.dp, end = 7.dp)
                    .size(7.dp)
                    .background(Color(0xFFE8794F), CircleShape),
            )
        }
    }
}

/** 画出来的小日历：顶栏 + 三个点，比 emoji 干净 */
@Composable
private fun CalendarIcon(tint: Color) {
    Column(
        Modifier
            .size(20.dp)
            .clip(RoundedCornerShape(4.dp))
            .border(2.dp, tint, RoundedCornerShape(4.dp)),
    ) {
        Box(Modifier.fillMaxWidth().height(6.dp).background(tint))
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 3.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(3) { Box(Modifier.size(3.dp).background(tint, CircleShape)) }
        }
    }
}

private fun encodeTiles(tiles: List<Game2048.Tile>): String =
    tiles.joinToString(";") { "${it.row},${it.col},${it.value}" }

private fun decodeTiles(raw: String): List<Game2048.Tile> =
    raw.split(";").filter { it.isNotBlank() }.mapIndexed { index, part ->
        val nums = part.split(",")
        Game2048.Tile(index + 1L, nums[2].toInt(), nums[0].toInt(), nums[1].toInt())
    }

private fun loadItems(prefs: SharedPreferences): Map<Item, Int> {
    val raw = prefs.getString("items", null) ?: return INITIAL_ITEMS
    val parts = raw.split(",")
    if (parts.size != Item.entries.size) return INITIAL_ITEMS
    return Item.entries.mapIndexed { index, item -> item to parts[index].toInt() }.toMap()
}

@Composable
private fun SettingsDialog(
    vibrate: Boolean,
    sfx: Float,
    music: Float,
    onVibrate: (Boolean) -> Unit,
    onSfx: (Float) -> Unit,
    onMusic: (Float) -> Unit,
    onClose: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("设置") },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("震动反馈", color = TEXT_DARK)
                    Switch(checked = vibrate, onCheckedChange = onVibrate)
                }
                Spacer(Modifier.height(14.dp))
                Text("音效音量 ${(sfx * 100).roundToInt()}%", color = TEXT_DARK, fontSize = 14.sp)
                Slider(value = sfx, onValueChange = onSfx)
                Spacer(Modifier.height(6.dp))
                Text("音乐音量 ${(music * 100).roundToInt()}%", color = TEXT_DARK, fontSize = 14.sp)
                Slider(value = music, onValueChange = onMusic)
                Spacer(Modifier.height(8.dp))
                Text(
                    "音乐：Kevin MacLeod《Easy Lemon》(incompetech.com, CC BY 4.0)",
                    color = TEXT_MUTED,
                    fontSize = 10.sp,
                )
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("完成") } },
    )
}

@Composable
private fun ScoreBox(label: String, value: Int) {
    Column(
        modifier = Modifier
            .background(BOARD_BG, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, color = Color(0xFFEEE4DA), fontSize = 11.sp, lineHeight = 16.sp)
        Text(
            text = "$value",
            color = Color.White,
            fontSize = 18.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun TipCard(moves: Int, maxTile: Int, drops: Int, tip: String, onNextTip: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CARD_BG),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Stat("步数", "$moves")
                Stat("最大方块", "$maxTile")
                Stat("道具掉落", "$drops")
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = Color(0xFFE2D8CA),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onNextTip)
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text("💡", fontSize = 14.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = tip,
                    color = TEXT_DARK,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text("换一条", color = TEXT_MUTED, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = TEXT_MUTED, fontSize = 11.sp)
        Text(value, color = TEXT_DARK, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Board(
    tiles: List<Game2048.Tile>,
    ghosts: List<Game2048.Slide>,
    onTileTap: (Game2048.Tile) -> Unit,
    modifier: Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(1f)
            .background(BOARD_BG, RoundedCornerShape(12.dp))
            .padding(8.dp),
    ) {
        val gap = 8.dp
        val cell = (maxWidth - gap * 3) / 4

        for (r in 0 until Game2048.SIZE) {
            for (c in 0 until Game2048.SIZE) {
                Box(
                    Modifier
                        .offset(x = (cell + gap) * c, y = (cell + gap) * r)
                        .size(cell)
                        .background(EMPTY_CELL, RoundedCornerShape(8.dp)),
                )
            }
        }

        tiles.forEach { tile ->
            key(tile.id) { TileView(tile, cell, gap, onTap = { onTileTap(tile) }) }
        }

        ghosts.forEach { slide ->
            key("ghost-${slide.tile.id}") { GhostView(slide, cell, gap) }
        }
    }
}

@Composable
private fun TileView(tile: Game2048.Tile, cell: Dp, gap: Dp, onTap: () -> Unit) {
    val x by animateDpAsState((cell + gap) * tile.col, tween(SLIDE_MS), label = "x")
    val y by animateDpAsState((cell + gap) * tile.row, tween(SLIDE_MS), label = "y")
    val pop = remember { Animatable(0.6f) }
    LaunchedEffect(tile.id) { pop.animateTo(1f, tween(140)) }

    Box(
        modifier = Modifier
            .offset(x, y)
            .size(cell)
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            }
            .background(tileColor(tile.value), RoundedCornerShape(8.dp))
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "${tile.value}",
            color = if (tile.value <= 4) TEXT_DARK else Color.White,
            fontSize = when {
                tile.value >= 1000 -> 20.sp
                tile.value >= 100 -> 26.sp
                else -> 32.sp
            },
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun GhostView(slide: Game2048.Slide, cell: Dp, gap: Dp) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(SLIDE_MS)) }
    val from = slide.tile
    val x = lerp((cell + gap) * from.col, (cell + gap) * slide.col, progress.value)
    val y = lerp((cell + gap) * from.row, (cell + gap) * slide.row, progress.value)

    Box(
        modifier = Modifier
            .offset(x, y)
            .size(cell)
            .graphicsLayer { alpha = 1f - progress.value }
            .background(tileColor(from.value), RoundedCornerShape(8.dp)),
    )
}

private fun tileColor(value: Int) = when (value) {
    2 -> Color(0xFFEEE4DA)
    4 -> Color(0xFFEDE0C8)
    8 -> Color(0xFFF2B179)
    16 -> Color(0xFFF59563)
    32 -> Color(0xFFF67C5F)
    64 -> Color(0xFFF65E3B)
    128 -> Color(0xFFEDCF72)
    256 -> Color(0xFFEDCC61)
    512 -> Color(0xFFEDC850)
    1024 -> Color(0xFFEDC53F)
    2048 -> Color(0xFFEDC22E)
    else -> Color(0xFF3C3A32)
}
