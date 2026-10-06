package pl.nawyki.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private enum class HabitType { GOOD, BAD }
private enum class DailyStatus { SUCCESS, FAILURE }

private data class Habit(
    val id: Long,
    val name: String,
    val type: HabitType,
    val createdAt: String
)

private data class StatusKey(val habitId: Long, val date: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                HabitApp(applicationContext)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HabitApp(context: Context) {
    val repository = remember { HabitRepository(context) }
    val habits: SnapshotStateList<Habit> = remember {
        mutableStateListOf<Habit>().apply { addAll(repository.loadHabits()) }
    }
    val statuses: SnapshotStateMap<StatusKey, DailyStatus> = remember {
        mutableStateMapOf<StatusKey, DailyStatus>().apply { putAll(repository.loadStatuses()) }
    }

    var filter by remember { mutableStateOf<HabitType?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    val today = LocalDate.now()
    val todayKey = today.toString()
    val todayMarked = habits.count { statuses[StatusKey(it.id, todayKey)] != null }
    val todaySuccess = habits.count { statuses[StatusKey(it.id, todayKey)] == DailyStatus.SUCCESS }

    fun persist() {
        repository.saveHabits(habits)
        repository.saveStatuses(statuses)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Nawyki", fontWeight = FontWeight.Bold)
                        Text(
                            text = "Dzisiaj: $todaySuccess/$todayMarked sukcesów",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Text("+", style = MaterialTheme.typography.headlineMedium)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            FilterBar(filter = filter, onFilterChange = { filter = it })

            val visibleHabits = habits.filter { filter == null || it.type == filter }
            if (visibleHabits.isEmpty()) {
                EmptyState(
                    hasAnyHabits = habits.isNotEmpty(),
                    onAdd = { showAddDialog = true }
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(visibleHabits, key = { it.id }) { habit ->
                        HabitCard(
                            habit = habit,
                            statuses = statuses,
                            today = today,
                            onStatus = { status ->
                                val key = StatusKey(habit.id, todayKey)
                                if (statuses[key] == status) {
                                    statuses.remove(key)
                                } else {
                                    statuses[key] = status
                                }
                                persist()
                            },
                            onDelete = {
                                habits.removeAll { it.id == habit.id }
                                statuses.keys
                                    .filter { it.habitId == habit.id }
                                    .toList()
                                    .forEach { statuses.remove(it) }
                                persist()
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddHabitDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, type ->
                val nextId = (habits.maxOfOrNull { it.id } ?: 0L) + 1L
                habits.add(
                    Habit(
                        id = nextId,
                        name = name.trim(),
                        type = type,
                        createdAt = todayKey
                    )
                )
                persist()
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun FilterBar(filter: HabitType?, onFilterChange: (HabitType?) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = filter == null,
            onClick = { onFilterChange(null) },
            label = { Text("Wszystkie") }
        )
        FilterChip(
            selected = filter == HabitType.GOOD,
            onClick = { onFilterChange(HabitType.GOOD) },
            label = { Text("Dobre") }
        )
        FilterChip(
            selected = filter == HabitType.BAD,
            onClick = { onFilterChange(HabitType.BAD) },
            label = { Text("Złe") }
        )
    }
}

@Composable
private fun EmptyState(hasAnyHabits: Boolean, onAdd: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                if (hasAnyHabits) "Brak nawyków w tej kategorii."
                else "Dodaj pierwszy nawyk i zacznij go kontrolować."
            )
            Button(onClick = onAdd) { Text("Dodaj nawyk") }
        }
    }
}

@Composable
private fun HabitCard(
    habit: Habit,
    statuses: Map<StatusKey, DailyStatus>,
    today: LocalDate,
    onStatus: (DailyStatus) -> Unit,
    onDelete: () -> Unit
) {
    val todayStatus = statuses[StatusKey(habit.id, today.toString())]
    val streak = currentStreak(habit.id, statuses, today)
    val last7 = (6 downTo 0).map { daysAgo -> today.minusDays(daysAgo.toLong()) }
    val marked7 = last7.count { statuses[StatusKey(habit.id, it.toString())] != null }
    val success7 = last7.count { statuses[StatusKey(habit.id, it.toString())] == DailyStatus.SUCCESS }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(habit.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (habit.type == HabitType.GOOD) "Dobry nawyk" else "Zły nawyk",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                TextButton(onClick = onDelete) { Text("Usuń") }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Metric("Seria", "$streak dni")
                Metric("7 dni", "$success7/$marked7 sukcesów")
            }

            SevenDayStrip(habit.id, statuses, last7)

            Text(
                text = if (habit.type == HabitType.GOOD)
                    "Czy wykonałeś ten nawyk dzisiaj?"
                else
                    "Czy udało Ci się uniknąć tego nawyku dzisiaj?",
                style = MaterialTheme.typography.bodyMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (todayStatus == DailyStatus.SUCCESS) {
                    Button(onClick = { onStatus(DailyStatus.SUCCESS) }, modifier = Modifier.weight(1f)) {
                        Text("✓ Sukces")
                    }
                } else {
                    OutlinedButton(onClick = { onStatus(DailyStatus.SUCCESS) }, modifier = Modifier.weight(1f)) {
                        Text("✓ Sukces")
                    }
                }

                if (todayStatus == DailyStatus.FAILURE) {
                    Button(onClick = { onStatus(DailyStatus.FAILURE) }, modifier = Modifier.weight(1f)) {
                        Text("✕ Porażka")
                    }
                } else {
                    OutlinedButton(onClick = { onStatus(DailyStatus.FAILURE) }, modifier = Modifier.weight(1f)) {
                        Text("✕ Porażka")
                    }
                }
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SevenDayStrip(
    habitId: Long,
    statuses: Map<StatusKey, DailyStatus>,
    dates: List<LocalDate>
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        dates.forEach { date ->
            val status = statuses[StatusKey(habitId, date.toString())]
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("pl"))
                        .take(2)
                        .replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(Modifier.height(5.dp))
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .background(
                            color = when (status) {
                                DailyStatus.SUCCESS -> Color(0xFF2E7D32)
                                DailyStatus.FAILURE -> Color(0xFFC62828)
                                null -> Color(0xFFE0E0E0)
                            },
                            shape = CircleShape
                        )
                )
            }
        }
    }
}

@Composable
private fun AddHabitDialog(
    onDismiss: () -> Unit,
    onAdd: (String, HabitType) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(HabitType.GOOD) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nowy nawyk") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nazwa, np. 20 min spaceru") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = { if (name.isNotBlank()) onAdd(name, type) }
                    )
                )

                Text("Rodzaj")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = type == HabitType.GOOD, onClick = { type = HabitType.GOOD })
                    Text("Dobry")
                    Spacer(Modifier.width(18.dp))
                    RadioButton(selected = type == HabitType.BAD, onClick = { type = HabitType.BAD })
                    Text("Zły")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(name, type) },
                enabled = name.isNotBlank()
            ) { Text("Dodaj") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Anuluj") }
        }
    )
}

private fun currentStreak(
    habitId: Long,
    statuses: Map<StatusKey, DailyStatus>,
    today: LocalDate
): Int {
    var date = today
    if (statuses[StatusKey(habitId, date.toString())] == null) {
        date = date.minusDays(1)
    }

    var streak = 0
    while (statuses[StatusKey(habitId, date.toString())] == DailyStatus.SUCCESS) {
        streak++
        date = date.minusDays(1)
    }
    return streak
}

private class HabitRepository(context: Context) {
    private val prefs = context.getSharedPreferences("nawyki_data", Context.MODE_PRIVATE)

    fun loadHabits(): List<Habit> = runCatching {
        val raw = prefs.getString("habits", "[]") ?: "[]"
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                add(
                    Habit(
                        id = obj.getLong("id"),
                        name = obj.getString("name"),
                        type = HabitType.valueOf(obj.getString("type")),
                        createdAt = obj.optString("createdAt", LocalDate.now().toString())
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    fun saveHabits(habits: List<Habit>) {
        val array = JSONArray()
        habits.forEach { habit ->
            array.put(
                JSONObject()
                    .put("id", habit.id)
                    .put("name", habit.name)
                    .put("type", habit.type.name)
                    .put("createdAt", habit.createdAt)
            )
        }
        prefs.edit().putString("habits", array.toString()).apply()
    }

    fun loadStatuses(): Map<StatusKey, DailyStatus> = runCatching {
        val raw = prefs.getString("statuses", "[]") ?: "[]"
        val array = JSONArray(raw)
        buildMap {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                put(
                    StatusKey(
                        habitId = obj.getLong("habitId"),
                        date = obj.getString("date")
                    ),
                    DailyStatus.valueOf(obj.getString("status"))
                )
            }
        }
    }.getOrDefault(emptyMap())

    fun saveStatuses(statuses: Map<StatusKey, DailyStatus>) {
        val array = JSONArray()
        statuses.forEach { (key, status) ->
            array.put(
                JSONObject()
                    .put("habitId", key.habitId)
                    .put("date", key.date)
                    .put("status", status.name)
            )
        }
        prefs.edit().putString("statuses", array.toString()).apply()
    }
}
