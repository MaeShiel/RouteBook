
package com.routebook
import androidx.compose.material.icons.filled.Search
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

import androidx.compose.foundation.clickable
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import kotlinx.coroutines.launch

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

        private val jsonFileName = "routebook_data.json"
        private val gson = Gson()

        private fun getJsonFile(): File = File(filesDir, jsonFileName)

        private fun saveCitiesToJson(cities: List<City>) {
            try {
                val json = gson.toJson(cities)
                getJsonFile().writeText(json)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        private fun loadCitiesFromJson(): List<City>? {
            return try {
                val file = getJsonFile()
                if (file.exists()) {
                    val json = file.readText()
                    val type = object : TypeToken<List<City>>() {}.type
                    gson.fromJson<List<City>>(json, type)
                } else null
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    private lateinit var importLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>
    private lateinit var exportLauncher: androidx.activity.result.ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        var citiesState: MutableState<List<City>>? = null
        val context = this

        importLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                try {
                    contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                        val lines = reader.readLines()
                        if (lines.isNotEmpty()) {
                            val header = lines[0].split(",")
                            val cityIdx = header.indexOf("City")
                            val stopNameIdx = header.indexOf("StopName")
                            val stopAddressIdx = header.indexOf("StopAddress")
                            val noteIdx = header.indexOf("Note")
                            // Check for required columns
                            if (cityIdx == -1 || stopNameIdx == -1 || stopAddressIdx == -1) {
                                android.widget.Toast.makeText(context, "CSV missing required columns.", android.widget.Toast.LENGTH_LONG).show()
                                return@use
                            }
                            val cityMap = mutableMapOf<String, MutableList<Stop>>()
                            for (line in lines.drop(1)) {
                                val cols = parseCsvLine(line)
                                if (cols.size > maxOf(cityIdx, stopNameIdx, stopAddressIdx)) {
                                    val city = cols[cityIdx]
                                    val stop = Stop(
                                        name = cols[stopNameIdx],
                                        address = cols[stopAddressIdx],
                                        note = if (noteIdx >= 0 && noteIdx < cols.size) cols[noteIdx] else ""
                                    )
                                    cityMap.getOrPut(city) { mutableListOf() }.add(stop)
                                } // else skip malformed row
                            }
                            val newCities = cityMap.map { City(it.key, it.value) }
                            citiesState?.value = newCities
                            saveCitiesToJson(newCities)
                        } else {
                            android.widget.Toast.makeText(context, "CSV file is empty.", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, "Failed to import CSV: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
        exportLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            if (uri != null) {
                try {
                    val cities = citiesState?.value ?: return@registerForActivityResult
                    val csvHeader = "City,StopName,StopAddress,Note\n"
                    val csvRows = cities.flatMap { city ->
                        city.stops.map { stop ->
                            "${city.name},${stop.name},${stop.address},${stop.note}"
                        }
                    }
                    val csvContent = buildString {
                        append(csvHeader)
                        csvRows.forEach {
                            append(it)
                            append("\n")
                        }
                    }
                    contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                        writer.write(csvContent)
                    }
                    android.widget.Toast.makeText(this, "Export successful!", android.widget.Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    android.widget.Toast.makeText(this, "Export failed: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }

        setContent {
            RouteBookTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    var navState by remember { mutableStateOf<NavState>(NavState.Home) }
                    val loadedCities = loadCitiesFromJson() ?: sampleCities()
                    val cities = remember { mutableStateOf(loadedCities) }
                    citiesState = cities
                    fun updateCities(newCities: List<City>) {
                        cities.value = newCities
                        saveCitiesToJson(newCities)
                    }
                    when (val state = navState) {
                        is NavState.Home -> HomeScreen(
                            cities = cities.value,
                            onCityClick = { city -> navState = NavState.Stops(city) },
                            onImportClick = { importLauncher.launch(arrayOf("text/csv", "application/csv", "text/comma-separated-values", "application/vnd.ms-excel")) },
                            onExportClick = { exportLauncher.launch("RouteBook.csv") }
                        )
                        is NavState.Stops -> StopsScreen(
                            city = state.city,
                            onBack = { navState = NavState.Home },
                            cities = cities.value,
                            setCities = { updateCities(it) }
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
    var showAbout by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    "Menu",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp)
                )
                Divider()
                // Future menu items go here
                Text(
                    "About",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAbout = true; scope.launch { drawerState.close() } }
                        .padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "RouteBook",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(0.7f)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
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
                            Text(
                                "No. of stops: ${city.stops.size}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            if (showAbout) {
                AboutDialog(onDismiss = { showAbout = false })
            }
        }
    }
}

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("About RouteBook", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("RouteBook is a professional delivery route management app.", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(12.dp))
                Text("Version: v0.1.0", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                Text("Author: Michael Tomambiling", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                Text("© 2026 RouteBook. All rights reserved.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StopsScreen(
    city: City,
    onBack: () -> Unit,
    cities: List<City>,
    setCities: (List<City>) -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    val cityIndex = cities.indexOfFirst { it.name == city.name }
    var stops by remember { mutableStateOf(city.stops.toMutableList()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var newStopName by remember { mutableStateOf("") }
    var newStopAddress by remember { mutableStateOf("") }
    var newStopNote by remember { mutableStateOf("") }
    val filteredStops = stops.filter {
        it.name.contains(searchQuery, ignoreCase = true) ||
        it.address.contains(searchQuery, ignoreCase = true) ||
        it.note.contains(searchQuery, ignoreCase = true)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(city.name, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    Row {
                        IconButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add Stop")
                        }
                        IconButton(onClick = onBack) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                placeholder = { Text("Search stops...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredStops.size) { index ->
                    val stop = filteredStops[index]
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = MaterialTheme.shapes.medium,
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(stop.name, style = MaterialTheme.typography.bodyLarge)
                            Text(stop.address, style = MaterialTheme.typography.bodySmall)
                            if (stop.note.isNotBlank()) {
                                Text(stop.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
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
                                OutlinedButton(
                                    onClick = {
                                        // Remove stop from list and update state
                                        val stopIndex = stops.indexOf(stop)
                                        if (stopIndex >= 0) {
                                            val newStops = stops.toMutableList().apply { removeAt(stopIndex) }
                                            stops = newStops
                                            if (cityIndex >= 0) {
                                                val updatedCities = cities.toMutableList()
                                                val updatedCity = city.copy(stops = newStops)
                                                updatedCities[cityIndex] = updatedCity
                                                setCities(updatedCities)
                                            }
                                        }
                                    },
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text("Delete")
                                }
                            }
                        }
                    }
                }
            }
            if (showAddDialog) {
                AlertDialog(
                    onDismissRequest = { showAddDialog = false },
                    title = { Text("Add Stop") },
                    text = {
                        Column {
                            OutlinedTextField(
                                value = newStopName,
                                onValueChange = { newStopName = it },
                                label = { Text("Stop Name") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = newStopAddress,
                                onValueChange = { newStopAddress = it },
                                label = { Text("Stop Address") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = newStopNote,
                                onValueChange = { newStopNote = it },
                                label = { Text("Notes") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            if (newStopName.isNotBlank() && newStopAddress.isNotBlank()) {
                                val newStop = Stop(newStopName, newStopAddress, newStopNote)
                                stops = (stops + newStop).toMutableList()
                                // Update global state
                                if (cityIndex >= 0) {
                                    val updatedCities = cities.toMutableList()
                                    val updatedCity = city.copy(stops = stops)
                                    updatedCities[cityIndex] = updatedCity
                                    setCities(updatedCities)
                                }
                                newStopName = ""
                                newStopAddress = ""
                                newStopNote = ""
                                showAddDialog = false
                            }
                        }) { Text("Add") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
                    }
                )
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
