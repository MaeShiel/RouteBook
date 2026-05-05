package com.routebook

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.routebook.ui.theme.RouteBookTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import com.routebook.R
import androidx.compose.ui.Alignment

// Data models
data class Stop(val name: String, val address: String, val note: String)
data class City(val name: String, val stops: List<Stop>)

class MainActivity : ComponentActivity() {
    private lateinit var importLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>
    private lateinit var exportLauncher: androidx.activity.result.ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        var citiesState: MutableState<List<City>>? = null

        importLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                    val lines = reader.readLines()
                    if (lines.isNotEmpty()) {
                        val header = lines[0].split(",")
                        val cityIdx = header.indexOf("City")
                        val stopNameIdx = header.indexOf("StopName")
                        val stopAddressIdx = header.indexOf("StopAddress")
                        val noteIdx = header.indexOf("Note")
                        val cityMap = mutableMapOf<String, MutableList<Stop>>()
                        for (line in lines.drop(1)) {
                            val cols = parseCsvLine(line)
                            if (cols.size > maxOf(cityIdx, stopNameIdx, stopAddressIdx, noteIdx)) {
                                val city = cols[cityIdx]
                                val stop = Stop(
                                    name = cols[stopNameIdx],
                                    address = cols[stopAddressIdx],
                                    note = if (noteIdx >= 0 && noteIdx < cols.size) cols[noteIdx] else ""
                                )
                                cityMap.getOrPut(city) { mutableListOf() }.add(stop)
                            }
                        }
                        val newCities = cityMap.map { City(it.key, it.value) }
                        citiesState?.value = newCities
                    }
                }
            }
        }
        exportLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            if (uri != null) {
                // TODO: Handle CSV export to uri (update to new format)
            }
        }

        setContent {
            RouteBookTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    var navState by remember { mutableStateOf<NavState>(NavState.Home) }
                    val cities = remember { mutableStateOf(sampleCities()) }
                    citiesState = cities
                    when (val state = navState) {
                        is NavState.Home -> HomeScreen(
                            cities = cities.value,
                            onCityClick = { city -> navState = NavState.Stops(city) },
                            onImportClick = { importLauncher.launch(arrayOf("text/csv", "application/csv", "text/comma-separated-values", "application/vnd.ms-excel")) },
                            onExportClick = { exportLauncher.launch("RouteBook.csv") }
                        )
                        is NavState.Stops -> StopsScreen(
                            city = state.city,
                            onBack = { navState = NavState.Home }
                        )
                    }
                }
            }
        }
    }

    // Simple CSV parser for quoted fields
    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    inQuotes = !inQuotes
                }
                c == ',' && !inQuotes -> {
                    result.add(current.toString().trim('"'))
                    current = StringBuilder()
                }
                else -> current.append(c)
            }
            i++
        }
        result.add(current.toString().trim('"'))
        return result
    }
}

sealed class NavState {
    object Home : NavState()
    data class Stops(val city: City) : NavState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    cities: List<City>,
    onCityClick: (City) -> Unit,
    onImportClick: () -> Unit = {},
    onExportClick: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "RouteBook",
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { /* TODO: Open Drawer */ }) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                actions = {
                    OutlinedButton(
                        onClick = onImportClick,
                        shape = MaterialTheme.shapes.medium
                    ) { Text("Import") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onExportClick,
                        shape = MaterialTheme.shapes.medium
                    ) { Text("Export") }
                    Spacer(modifier = Modifier.width(8.dp))
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { /* TODO: Add City/Stop */ },
                icon = { Icon(Icons.Default.Add, contentDescription = "Add") },
                text = { Text("Add") },
                containerColor = MaterialTheme.colorScheme.primary
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
                start = 8.dp,
                end = 8.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(cities) { city ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    onClick = { onCityClick(city) }
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            city.name,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        // Last update removed from UI, now auto-managed internally
                        Text(
                            "No. of stops: ${city.stops.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StopsScreen(city: City, onBack: () -> Unit) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(city.name, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {}, // No hamburger menu
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onBack, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp)) {
            Text("Stops in ${city.name}", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(12.dp))
            city.stops.forEach { stop ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = MaterialTheme.shapes.medium,
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(stop.name, style = MaterialTheme.typography.bodyLarge)
                            Text(stop.address, style = MaterialTheme.typography.bodySmall)
                            if (stop.note.isNotBlank()) {
                                Text(stop.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                val gmmIntentUri = Uri.parse("google.navigation:q=" + Uri.encode(stop.address))
                                val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                                mapIntent.setPackage("com.google.android.apps.maps")
                                context.startActivity(mapIntent)
                            },
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text("Navigate")
                        }
                    }
                }
            }
        }
    }
}

fun sampleCities(): List<City> = listOf(
    City("Grand Forks, ND", listOf(
        Stop("Stop 1", "123 Main St, Grand Forks, ND", "Sample note for Stop 1"),
        Stop("Stop 2", "456 Oak Ave, Grand Forks, ND", "Sample note for Stop 2")
    )),
    City("Fargo, ND", listOf(
        Stop("Stop 1", "789 Pine St, Fargo, ND", "Sample note for Fargo Stop 1")
    ))
)
