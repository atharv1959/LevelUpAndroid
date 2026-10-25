package com.levelup.workout

import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val Ink = Color(0xFF121417)
private val Panel = Color(0xFF1C2025)
private val Lime = Color(0xFFC7F36B)
private val Muted = Color(0xFF9BA3AD)
private val Paper = Color(0xFFF5F6F2)

data class Workout(val date: String, val exercise: String, val reps: Int)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LevelUpApp(this) }
    }
}

private fun todayKey() = LocalDate.now().toString()
private fun prettyDate(key: String): String = try {
    LocalDate.parse(key).format(DateTimeFormatter.ofPattern("EEE, d MMM"))
} catch (_: Exception) { key }

private class WorkoutStore(context: Context) {
    private val prefs = context.getSharedPreferences("level_up_data", Context.MODE_PRIVATE)
    fun readGoal() = prefs.getInt("goal", 20)
    fun writeGoal(value: Int) { prefs.edit().putInt("goal", value.coerceAtLeast(1)).apply() }
    fun readRestDays(): Set<String> = prefs.getString("rest_days", "")!!.split(",").filter { it.isNotBlank() }.toSet()
    fun toggleRest(day: String): Set<String> {
        val days = readRestDays().toMutableSet()
        if (!days.add(day)) days.remove(day)
        prefs.edit().putString("rest_days", days.joinToString(",")).apply()
        return days
    }
    fun readWorkouts(): List<Workout> {
        return prefs.getString("workouts", "")!!.split("\n").filter { it.isNotBlank() }.mapNotNull {
            val p = it.split("|")
            if (p.size == 3) p[2].toIntOrNull()?.let { n -> Workout(p[0], p[1], n) } else null
        }
    }
    fun addWorkout(workout: Workout) {
        val all = readWorkouts().toMutableList()
        all.add(workout)
        prefs.edit().putString("workouts", all.joinToString("\n") { "${it.date}|${it.exercise.replace("|", "")}|${it.reps}" }).apply()
    }
    fun clearAll() { prefs.edit().clear().apply() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LevelUpApp(context: Context) {
    val store = remember { WorkoutStore(context) }
    var workouts by remember { mutableStateOf(store.readWorkouts()) }
    var goal by remember { mutableIntStateOf(store.readGoal()) }
    var restDays by remember { mutableStateOf(store.readRestDays()) }
    var tab by remember { mutableIntStateOf(0) }
    var exercise by remember { mutableStateOf("Push-ups") }
    var repsText by remember { mutableStateOf("10") }
    var showLog by remember { mutableStateOf(false) }
    var showGoal by remember { mutableStateOf(false) }
    var goalText by remember { mutableStateOf(goal.toString()) }
    var toast by remember { mutableStateOf("") }

    val today = todayKey()
    val todayPushups = workouts.filter { it.date == today && it.exercise == "Push-ups" }.sumOf { it.reps }
    val totalReps = workouts.sumOf { it.reps }
    val activeDates = workouts.map { it.date }.toSet() + restDays
    val streak = calculateStreak(activeDates, today)
    val xp = totalReps + workouts.size * 5 + restDays.size * 10
    val level = xp / 100 + 1
    val todayDone = todayPushups >= goal
    val snackbar = remember { SnackbarHostState() }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Lime, onPrimary = Ink, background = Ink, onBackground = Paper,
            surface = Panel, onSurface = Paper, secondary = Lime, onSurfaceVariant = Muted
        )
    ) {
        Scaffold(
            containerColor = Ink,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("LEVEL UP", fontWeight = FontWeight.Black, letterSpacing = 2.sp, color = Lime)
                            Text("Small reps. Big momentum.", fontSize = 12.sp, color = Muted)
                        }
                    },
                    actions = {
                        Surface(shape = RoundedCornerShape(50), color = Panel, modifier = Modifier.padding(end = 12.dp)) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Bolt, contentDescription = null, tint = Lime, modifier = Modifier.size(18.dp))
                                Text(" Lv.$level", fontWeight = FontWeight.Bold, color = Paper)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Ink)
                )
            },
            bottomBar = {
                NavigationBar(containerColor = Panel, contentColor = Paper) {
                    val labels = listOf("Today", "History", "Progress", "Settings")
                    val icons = listOf(Icons.Default.Today, Icons.Default.CalendarMonth, Icons.Default.Insights, Icons.Default.Settings)
                    labels.forEachIndexed { i, label ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Icon(icons[i], contentDescription = label) },
                            label = { Text(label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Lime, selectedTextColor = Lime,
                                indicatorColor = Ink, unselectedIconColor = Muted, unselectedTextColor = Muted
                            )
                        )
                    }
                }
            }
        ) { padding ->
            when (tab) {
                0 -> TodayScreen(
                    Modifier.padding(padding), goal, todayPushups, todayDone, streak, totalReps, xp,
                    workouts.filter { it.date == today }, onLog = { showLog = true },
                    onRest = {
                        restDays = store.toggleRest(today)
                        toast = if (today in restDays) "Rest day saved" else "Rest day removed"
                    }
                )
                1 -> HistoryScreen(Modifier.padding(padding), workouts, restDays)
                2 -> ProgressScreen(Modifier.padding(padding), totalReps, workouts.size, streak, xp, level, goal, workouts)
                else -> SettingsScreen(
                    Modifier.padding(padding), goal, goalText, { goalText = it },
                    onEditGoal = { showGoal = true },
                    onReset = {
                        store.clearAll()
                        workouts = emptyList(); restDays = emptySet(); goal = 20; goalText = "20"
                        toast = "All local data reset"
                    }
                )
            }
            if (showLog) {
                AlertDialog(
                    onDismissRequest = { showLog = false },
                    title = { Text("Log a workout", fontWeight = FontWeight.Bold) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Choose exercise", color = Muted)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("Push-ups", "Squats", "Sit-ups", "Other").forEach { item ->
                                    FilterChip(selected = exercise == item, onClick = { exercise = item }, label = { Text(item) })
                                }
                            }
                            OutlinedTextField(
                                value = repsText, onValueChange = { repsText = it.filter(Char::isDigit).take(5) },
                                label = { Text(if (exercise == "Other") "Reps / count" else "Reps") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true
                            )
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            val count = repsText.toIntOrNull() ?: 0
                            if (count > 0) {
                                store.addWorkout(Workout(today, exercise, count))
                                workouts = store.readWorkouts()
                                showLog = false
                                repsText = "10"
                            }
                        }) { Text("Save workout") }
                    },
                    dismissButton = { TextButton(onClick = { showLog = false }) { Text("Cancel") }
                    },
                    containerColor = Panel
                )
            }
            if (showGoal) {
                AlertDialog(
                    onDismissRequest = { showGoal = false },
                    title = { Text("Daily push-up goal") },
                    text = {
                        OutlinedTextField(
                            value = goalText, onValueChange = { goalText = it.filter(Char::isDigit).take(4) },
                            label = { Text("Reps per day") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    },
                    confirmButton = {
                        Button(onClick = {
                            val newGoal = goalText.toIntOrNull()
                            if (newGoal != null && newGoal > 0) { goal = newGoal; store.writeGoal(goal); showGoal = false }
                        }) { Text("Save") }
                    },
                    dismissButton = { TextButton(onClick = { showGoal = false }) { Text("Cancel") } },
                    containerColor = Panel
                )
            }
            LaunchedEffect(toast) {
                if (toast.isNotBlank()) {
                    snackbar.showSnackbar(toast)
                    toast = ""
                }
            }
        }
    }
}

private fun calculateStreak(dates: Set<String>, today: String): Int {
    var cursor = try { LocalDate.parse(today) } catch (_: Exception) { return 0 }
    if (cursor.toString() !in dates) cursor = cursor.minusDays(1)
    var count = 0
    while (cursor.toString() in dates) { count++; cursor = cursor.minusDays(1) }
    return count
}

@Composable
private fun TodayScreen(
    modifier: Modifier, goal: Int, done: Int, completed: Boolean, streak: Int,
    total: Int, xp: Int, todayWorkouts: List<Workout>, onLog: () -> Unit, onRest: () -> Unit
) {
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(top = 16.dp, bottom = 20.dp)) {
        item {
            Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM")), color = Muted, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            Text("Show up today.", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = Paper)
            Text("Your only competition is yesterday's you.", color = Muted)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("DAILY PUSH-UPS", color = Muted, fontSize = 12.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text("$done", fontSize = 44.sp, fontWeight = FontWeight.Black, color = Paper)
                                Text(" / $goal reps", color = Muted, modifier = Modifier.padding(bottom = 8.dp))
                            }
                        }
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(86.dp)) {
                            CircularProgressIndicator(progress = { (done.toFloat() / goal).coerceIn(0f, 1f) }, strokeWidth = 8.dp, color = Lime, trackColor = Ink, modifier = Modifier.fillMaxSize())
                            Icon(if (completed) Icons.Default.Check else Icons.Default.FitnessCenter, contentDescription = null, tint = Lime, modifier = Modifier.size(30.dp))
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(progress = { (done.toFloat() / goal).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(7.dp), color = Lime, trackColor = Ink)
                    Spacer(Modifier.height(16.dp))
                    Text(if (completed) "Goal complete. Nice work!" else "${(goal - done).coerceAtLeast(0)} reps to hit today's goal.", color = if (completed) Lime else Muted)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onLog, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Log workout", fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onRest, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Default.Bedtime, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp)); Text("Mark today as rest day")
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("STREAK", "$streak days", Icons.Default.LocalFireDepartment, Modifier.weight(1f))
                StatCard("TOTAL REPS", "$total", Icons.Default.FitnessCenter, Modifier.weight(1f))
                StatCard("XP", "$xp", Icons.Default.Bolt, Modifier.weight(1f))
            }
        }
        item { Text("TODAY'S ACTIVITY", color = Muted, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.3.sp) }
        if (todayWorkouts.isEmpty()) {
            item { EmptyCard("No workouts logged yet. Start with what you can do.") }
        } else {
            items(todayWorkouts.reversed()) { w -> WorkoutRow(w) }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(12.dp)) {
            Icon(icon, contentDescription = null, tint = Lime, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(10.dp))
            Text(value, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Paper, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, fontSize = 10.sp, color = Muted, letterSpacing = 0.5.sp)
        }
    }
}

@Composable
private fun HistoryScreen(modifier: Modifier, workouts: List<Workout>, restDays: Set<String>) {
    val grouped = workouts.groupBy { it.date }.toSortedMap(compareByDescending { it })
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(top = 18.dp, bottom = 20.dp)) {
        item { Text("Your history", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Paper); Text("Every logged rep counts.", color = Muted) }
        if (grouped.isEmpty() && restDays.isEmpty()) item { EmptyCard("Your workout history will appear here.") }
        items(grouped.keys.toList()) { day ->
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(prettyDate(day), color = Lime, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    grouped[day]!!.forEach { w ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(w.exercise, color = Paper); Text("${w.reps} reps", color = Muted)
                        }
                    }
                    Text("Total: ${grouped[day]!!.sumOf { it.reps }} reps", color = Muted, fontSize = 12.sp)
                }
            }
        }
        items(restDays.sortedDescending().toList()) { day ->
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Bedtime, contentDescription = null, tint = Lime)
                    Spacer(Modifier.width(10.dp)); Text("${prettyDate(day)} · Rest day", color = Paper)
                }
            }
        }
    }
}

@Composable
private fun ProgressScreen(modifier: Modifier, total: Int, sessions: Int, streak: Int, xp: Int, level: Int, goal: Int, workouts: List<Workout>) {
    val best = workouts.groupBy { it.exercise }.mapValues { (_, list) -> list.maxOfOrNull { it.reps } ?: 0 }
    val nextLevel = level * 100
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(top = 18.dp, bottom = 20.dp)) {
        item { Text("Progress", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Paper); Text("Consistency beats intensity.", color = Muted) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(60.dp).background(Lime, CircleShape), contentAlignment = Alignment.Center) {
                            Text("$level", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Black)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("LEVEL $level", color = Lime, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                            Text("$xp XP earned", color = Muted)
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    LinearProgressIndicator(progress = { ((xp % 100) / 100f) }, modifier = Modifier.fillMaxWidth().height(8.dp), color = Lime, trackColor = Ink)
                    Spacer(Modifier.height(6.dp))
                    Text("${xp % 100}/100 XP to next level", color = Muted, fontSize = 12.sp)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("TOTAL REPS", "$total", Icons.Default.FitnessCenter, Modifier.weight(1f))
                StatCard("SESSIONS", "$sessions", Icons.Default.CheckCircle, Modifier.weight(1f))
                StatCard("BEST STREAK", "$streak d", Icons.Default.LocalFireDepartment, Modifier.weight(1f))
            }
        }
        item { Text("PERSONAL RECORDS", color = Muted, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.3.sp) }
        if (best.isEmpty()) item { EmptyCard("Log a workout to start building your personal records.") }
        items(best.toList().sortedBy { it.first }) { (name, value) ->
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column { Text(name, color = Paper, fontWeight = FontWeight.Bold); Text("Best single log", color = Muted, fontSize = 12.sp) }
                    Text("$value reps", color = Lime, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Your daily target", color = Muted)
                    Text("$goal push-ups", color = Paper, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(modifier: Modifier, goal: Int, goalText: String, onGoalText: (String) -> Unit, onEditGoal: () -> Unit, onReset: () -> Unit) {
    var confirmReset by remember { mutableStateOf(false) }
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(top = 18.dp, bottom = 20.dp)) {
        item { Text("Settings", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Paper); Text("Make the system work for you.", color = Muted) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.fillMaxWidth().clickable(onClick = onEditGoal).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Flag, contentDescription = null, tint = Lime)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) { Text("Daily push-up goal", color = Paper, fontWeight = FontWeight.Bold); Text("$goal reps per day", color = Muted, fontSize = 13.sp) }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Muted)
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("About Level Up", color = Paper, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text("A simple habit tracker for workouts, streaks and progress. Your data is stored locally on this device.", color = Muted)
                }
            }
        }
        item {
            OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF8585))) {
                Icon(Icons.Default.DeleteOutline, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Reset all app data")
            }
        }
        item { Text("Version 1.0 · Made for steady progress", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset all data?") },
            text = { Text("This permanently clears workout history and rest days from this device.") },
            confirmButton = { Button(onClick = { confirmReset = false; onReset() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB93838))) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
            containerColor = Panel
        )
    }
}

@Composable
private fun WorkoutRow(w: Workout) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(Ink, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.FitnessCenter, contentDescription = null, tint = Lime)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { Text(w.exercise, color = Paper, fontWeight = FontWeight.Bold); Text("Logged today", color = Muted, fontSize = 12.sp) }
            Text("+${w.reps}", color = Lime, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun EmptyCard(message: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Lime)
            Spacer(Modifier.width(12.dp)); Text(message, color = Muted)
        }
    }
}
