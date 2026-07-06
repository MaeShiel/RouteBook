package com.routebook

import androidx.compose.foundation.layout.*

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.launch


data class DrivingTimeResult(val totalDrivingTime: String, val hoursWorked: String, val totalDeliveryTime: String)

fun formatTime(hour: Int, minute: Int): String {
    val h = if (hour == 0 || hour == 12) 12 else hour % 12
    val m = minute.toString().padStart(2, '0')
    val ampm = if (hour < 12) "AM" else "PM"
    return "$h:$m $ampm"
}

fun formatDuration(minutes: Int): String {
    if (minutes < 0) return "0 hrs 0 mins"
    val h = minutes / 60
    val m = minutes % 60
    return if (m == 0) "$h hrs" else "$h hrs $m mins"
}

fun calculateDrivingTime(
    beginHour: Int,
    beginMinute: Int,
    endHour: Int,
    endMinute: Int,
    breaks: String,
    adjustments: String
): DrivingTimeResult {
    val begin = beginHour * 60 + beginMinute
    val end = endHour * 60 + endMinute
    var total = end - begin
    if (total < 0) total += 24 * 60
    val breaksInt = breaks.toIntOrNull() ?: 0
    val adjInt = adjustments.toIntOrNull() ?: 0
    val worked = total - breaksInt
    val delivery = worked - adjInt
    return DrivingTimeResult(
        totalDrivingTime = formatDuration(total),
        hoursWorked = formatDuration(worked),
        totalDeliveryTime = formatDuration(delivery)
    )
}

fun showTimePicker(context: android.content.Context, hour: Int, minute: Int, onTimeSet: (Int, Int) -> Unit) {
    android.app.TimePickerDialog(context, { _, h, m -> onTimeSet(h, m) }, hour, minute, false).show()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrivingTimeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var beginHour by rememberSaveable { mutableStateOf(8) }
    var beginMinute by rememberSaveable { mutableStateOf(0) }
    var endHour by rememberSaveable { mutableStateOf(17) }
    var endMinute by rememberSaveable { mutableStateOf(0) }
    var breaks by rememberSaveable { mutableStateOf("30") }
    var adjustments by rememberSaveable { mutableStateOf("0") }
    var showResults by rememberSaveable { mutableStateOf(false) }
    var totalDrivingTime by rememberSaveable { mutableStateOf("--") }
    var hoursWorked by rememberSaveable { mutableStateOf("--") }
    var totalDeliveryTime by rememberSaveable { mutableStateOf("--") }

    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Driving Time", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("Begin Driving Time", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = {
                        showTimePicker(context, beginHour, beginMinute) { h, m ->
                            beginHour = h; beginMinute = m
                        }
                    }) {
                        Text(formatTime(beginHour, beginMinute))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("End Driving Time", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = {
                        showTimePicker(context, endHour, endMinute) { h, m ->
                            endHour = h; endMinute = m
                        }
                    }) {
                        Text(formatTime(endHour, endMinute))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("Breaks (in minutes)", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = breaks,
                        onValueChange = { value: String -> breaks = value.filter { c -> c.isDigit() } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("Delivery Time Adjustments (in minutes)", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = adjustments,
                        onValueChange = { value: String -> adjustments = value.filter { c -> c.isDigit() || c == '-' } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            val coroutineScope = rememberCoroutineScope()
            Button(
                onClick = {
                    showResults = true
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("DEBUG: Calculate button pressed")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Calculate", style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(16.dp))
            Text("DEBUG: showResults=$showResults", style = MaterialTheme.typography.labelLarge)
            if (showResults) {
                Spacer(Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text("Total Worked: 0", style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
    }
}
