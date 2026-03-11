package com.example.facilify

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.util.UUID
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FacilifyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainNavigation()
                }
            }
        }
    }
}

// Basic Theme Wrapper
@Composable
fun FacilifyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF388E3C), // Green Theme color
            background = Color(0xFFFAFAFA)
        ),
        content = content
    )
}

data class MainEventConfig(
    val id: String = "",
    val name: String = "",
    val imageUrl: String = ""
)

@Composable
fun MainNavigation() {
    var loggedInUser by remember { mutableStateOf<UserData?>(null) }
    var selectedEvent by remember { mutableStateOf<MainEventConfig?>(null) }
    
    if (loggedInUser == null) {
        LoginScreen(onLoginSuccess = { user -> loggedInUser = user })
    } else if (selectedEvent == null) {
        EventSelectionScreen(
            user = loggedInUser!!, 
            onEventSelected = { selectedEvent = it },
            onLogout = { loggedInUser = null }
        )
    } else {
        FacilifyApp(
            user = loggedInUser!!, 
            mainEvent = selectedEvent!!, 
            onLogout = { 
                loggedInUser = null
                selectedEvent = null
            },
            onBackToEvents = { selectedEvent = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventSelectionScreen(user: UserData, onEventSelected: (MainEventConfig) -> Unit, onLogout: () -> Unit) {
    val db = FirebaseFirestore.getInstance()
    var events by remember { mutableStateOf(emptyList<MainEventConfig>()) }
    var showDialog by remember { mutableStateOf(false) }
    var newEventName by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var editingEvent by remember { mutableStateOf<MainEventConfig?>(null) }
    var isUploading by remember { mutableStateOf(false) }

    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        imageUri = uri
    }

    LaunchedEffect(Unit) {
        db.collection("mainEvents").addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                events = snapshot.documents.map { doc ->
                    MainEventConfig(
                        id = doc.id, 
                        name = doc.getString("name") ?: "",
                        imageUrl = doc.getString("imageUrl") ?: ""
                    )
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Select Event", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Button(onClick = onLogout, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) {
                Text("Logout")
            }
        }
        Spacer(modifier = Modifier.height(32.dp))

        // Grid for events and +Add button
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp)
        ) {
            items(events.size) { index ->
                val event = events[index]
                var menuExpanded by remember { mutableStateOf(false) }
                Column(
                    modifier = Modifier.clickable { onEventSelected(event) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(Color.LightGray)
                    ) {
                        if (event.imageUrl.isNotBlank()) {
                            AsyncImage(
                                model = event.imageUrl,
                                contentDescription = "Event Image",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(Icons.Default.Event, contentDescription = "Event", modifier = Modifier.align(Alignment.Center).size(60.dp), tint = Color.Gray)
                        }
                        if (user.role == "Admin") {
                            Box(modifier = Modifier.align(Alignment.TopEnd)) {
                                IconButton(
                                    onClick = { menuExpanded = true },
                                    modifier = Modifier.background(Color.White, CircleShape).size(28.dp)
                                ) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = Color.Black, modifier = Modifier.size(16.dp))
                                }
                                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                    DropdownMenuItem(text = { Text("Edit") }, onClick = {
                                        menuExpanded = false
                                        editingEvent = event
                                        newEventName = event.name
                                        imageUri = null
                                        showDialog = true
                                    })
                                    DropdownMenuItem(text = { Text("Delete", color = Color.Red) }, onClick = {
                                        menuExpanded = false
                                        db.collection("mainEvents").document(event.id).delete()
                                    })
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(event.name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
                }
            }

            if (user.role == "Admin") {
                item {
                    Column(
                        modifier = Modifier.clickable { 
                            editingEvent = null
                            newEventName = ""
                            imageUri = null
                            showDialog = true 
                        },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE8F5E9)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Event", tint = Color(0xFF2E7D32), modifier = Modifier.size(60.dp))
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Add Event", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF2E7D32))
                    }
                }
            }
        }
    }

    if (showDialog) {
        val titleText = if (editingEvent != null) "Edit Event Group" else "New Event Group"
        AlertDialog(
            onDismissRequest = { if (!isUploading) showDialog = false },
            title = { Text(titleText) },
            text = {
                Column {
                    OutlinedTextField(
                        value = newEventName, 
                        onValueChange = { newEventName = it }, 
                        label = { Text("Name") }, 
                        singleLine = true,
                        enabled = !isUploading
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { imageLauncher.launch("image/*") }, 
                        enabled = !isUploading,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray, contentColor = Color.Black)
                    ) {
                        Text(if (imageUri != null) "Image Selected" else "Select Profile Photo")
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !isUploading,
                    onClick = {
                        if (newEventName.isNotBlank() && !isUploading) {
                            isUploading = true
                            if (imageUri != null) {
                                val storageRef = FirebaseStorage.getInstance().reference.child("events/${UUID.randomUUID()}")
                                storageRef.putFile(imageUri!!).continueWithTask { task ->
                                    if (!task.isSuccessful) task.exception?.let { throw it }
                                    storageRef.downloadUrl
                                }.addOnCompleteListener { task ->
                                    if (task.isSuccessful) {
                                        val url = task.result.toString()
                                        if (editingEvent != null) {
                                            db.collection("mainEvents").document(editingEvent!!.id).update(mapOf("name" to newEventName, "imageUrl" to url))
                                        } else {
                                            db.collection("mainEvents").add(mapOf("name" to newEventName, "imageUrl" to url))
                                        }
                                        showDialog = false
                                        isUploading = false
                                    } else {
                                        isUploading = false
                                    }
                                }
                            } else {
                                val url = editingEvent?.imageUrl ?: ""
                                if (editingEvent != null) {
                                    db.collection("mainEvents").document(editingEvent!!.id).update(mapOf("name" to newEventName, "imageUrl" to url))
                                } else {
                                    db.collection("mainEvents").add(mapOf("name" to newEventName, "imageUrl" to url))
                                }
                                showDialog = false
                                isUploading = false
                            }
                        }
                    }
                ) { Text(if (isUploading) "Saving..." else "Save") }
            },
            dismissButton = { 
                TextButton(onClick = { if (!isUploading) showDialog = false }) { Text("Cancel") } 
            }
        )
    }
}

@Composable
fun FacilifyApp(user: UserData, mainEvent: MainEventConfig, onLogout: () -> Unit, onBackToEvents: () -> Unit) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(1) } // Default selected: Facility
    var showNotifications by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            if (!showNotifications) {
                TopBar(
                    user = user, 
                    mainEvent = mainEvent,
                    onNotificationClick = { showNotifications = true }, 
                    onLogout = onLogout,
                    onBackClick = onBackToEvents,
                    modifier = Modifier.background(Color(0xFFF5F5F7)).padding(horizontal = 16.dp)
                )
            }
        },
        bottomBar = {
            BottomNavigationBar(selectedTab = selectedTab) { index ->
                selectedTab = index
                showNotifications = false
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFF5F5F7))
        ) {
            if (showNotifications) {
                NotificationsScreen(onBack = { showNotifications = false })
            } else if (selectedTab == 0) { // Home Screen content
                HomeScreen(user = user, mainEvent = mainEvent)
            } else if (selectedTab == 1) { // Render Facility screen content
                FacilityScreen()
            } else if (selectedTab == 3) {
                EventsScreen(user = user, mainEvent = mainEvent)
            } else {
                // Placeholder for other screens
                val tabNames = listOf("Home", "Facility", "Team", "Events")
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "Navigate to Facility Tab (You are on ${tabNames[selectedTab]})", fontSize = 18.sp, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun FacilityScreen() {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        item { Spacer(modifier = Modifier.height(16.dp)) }
        item { SearchBar() }
        item { Spacer(modifier = Modifier.height(24.dp)) }
        item { Text("Explore Facilities", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black) }
        item { Spacer(modifier = Modifier.height(16.dp)) }
        item { ExploreFacilitiesGrid() }
        item { Spacer(modifier = Modifier.height(24.dp)) }
        item { Text("Book Your Space", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black) }
        item { Spacer(modifier = Modifier.height(16.dp)) }

        // Setup Dummy Data for Spaces list
        items(getDummySpaces()) { space ->
            SpaceCard(space = space)
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun TopBar(user: UserData, mainEvent: MainEventConfig, onNotificationClick: () -> Unit = {}, onLogout: () -> Unit = {}, onBackClick: () -> Unit = {}, modifier: Modifier = Modifier) {
    var profileMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left side Back Button & Text
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.Black)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(mainEvent.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                val welcomeText = if (user.role == "Admin") "Admin: ${user.name}" else "User: ${user.name}"
                Text(welcomeText, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.Gray)
            }
        }
        
        // Right side Notifications & Profile
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.clickable { onNotificationClick() }) {
                Icon(
                    imageVector = Icons.Outlined.Notifications,
                    contentDescription = "Notifications",
                    modifier = Modifier.size(28.dp),
                    tint = Color.Black
                )
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color.Red)
                        .align(Alignment.TopEnd)
                        .offset(x = (-2).dp, y = 2.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Box {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE0E0E0))
                        .clickable { profileMenuExpanded = true },
                    contentAlignment = Alignment.Center
                ) {
                    Text("😊", fontSize = 20.sp)
                }
                DropdownMenu(
                    expanded = profileMenuExpanded,
                    onDismissRequest = { profileMenuExpanded = false },
                    modifier = Modifier.background(Color.White)
                ) {
                    DropdownMenuItem(text = { Text("Hi ${user.name}", color = Color.Black) }, onClick = { profileMenuExpanded = false })
                    DropdownMenuItem(text = { Text("Roll ${user.rollNo}", color = Color.Black) }, onClick = { profileMenuExpanded = false })
                    DropdownMenuItem(text = { Text("Help", color = Color.Black) }, onClick = { profileMenuExpanded = false })
                    DropdownMenuItem(text = { Text("Logout", color = Color.Black) }, onClick = { 
                        profileMenuExpanded = false
                        onLogout()
                    })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBar() {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }

    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        modifier = Modifier
            .fillMaxWidth()
            .clickable { Toast.makeText(context, "Search clicked!", Toast.LENGTH_SHORT).show() },
        placeholder = { Text("Search rooms, equipment, booking...", color = Color.Gray) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.Gray) },
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = Color.White,
            focusedContainerColor = Color.White,
            unfocusedBorderColor = Color.LightGray,
            focusedBorderColor = Color(0xFF2E7D32)
        ),
        singleLine = true
    )
}

@Composable
fun ExploreFacilitiesGrid() {
    val context = LocalContext.current

    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            FacilityCategoryItem(
                title = "Workspace",
                icon = Icons.Outlined.Computer,
                imageUrl = "https://images.unsplash.com/photo-1497366216548-37526070297c?auto=format&fit=crop&q=80&w=200&h=150",
                modifier = Modifier.weight(1f),
                onClick = { Toast.makeText(context, "Workspace clicked!", Toast.LENGTH_SHORT).show() }
            )
            Spacer(modifier = Modifier.width(12.dp))
            FacilityCategoryItem(
                title = "Gym & Fitness",
                icon = Icons.Outlined.FitnessCenter,
                imageUrl = "https://images.unsplash.com/photo-1534438327276-14e5300c3a48?auto=format&fit=crop&q=80&w=200&h=150",
                modifier = Modifier.weight(1f),
                onClick = { Toast.makeText(context, "Gym & Fitness clicked!", Toast.LENGTH_SHORT).show() }
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            FacilityCategoryItem(
                title = "Conference Rooms",
                icon = Icons.Outlined.Groups,
                imageUrl = "https://images.unsplash.com/photo-1497366811353-6870744d04b2?auto=format&fit=crop&q=80&w=200&h=150",
                modifier = Modifier.weight(1f),
                onClick = { Toast.makeText(context, "Conference Rooms clicked!", Toast.LENGTH_SHORT).show() }
            )
            Spacer(modifier = Modifier.width(12.dp))
            FacilityCategoryItem(
                title = "Labs & Tech",
                icon = Icons.Outlined.Science,
                imageUrl = "https://images.unsplash.com/photo-1581093458791-9f3c3900df4b?auto=format&fit=crop&q=80&w=200&h=150",
                modifier = Modifier.weight(1f),
                onClick = { Toast.makeText(context, "Labs & Tech clicked!", Toast.LENGTH_SHORT).show() }
            )
        }
    }
}

@Composable
fun FacilityCategoryItem(title: String, icon: ImageVector, imageUrl: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(110.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        // Dark overlay for text visibility
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFC8E6C9)), // Light green
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = title, tint = Color(0xFF2E7D32))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    }
}

@Composable
fun SpaceCard(space: SpaceData) {
    val context = LocalContext.current

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth().clickable { Toast.makeText(context, "Clicked ${space.title}", Toast.LENGTH_SHORT).show() }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = space.imageUrl,
                contentDescription = space.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(70.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = space.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)
                Text(text = space.subtitle, color = Color.Gray, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.CalendarToday, contentDescription = "Date", modifier = Modifier.size(14.dp), tint = Color.Gray)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = space.info, color = Color.Gray, fontSize = 12.sp)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Box(
                    modifier = Modifier
                        .background(
                            color = if (space.isAvailable) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (space.isAvailable) "Available" else "Booked",
                        color = if (space.isAvailable) Color(0xFF2E7D32) else Color(0xFFC62828),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                if (space.isAvailable) {
                    OutlinedButton(
                        onClick = { Toast.makeText(context, "Viewing details for ${space.title}", Toast.LENGTH_SHORT).show() },
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF1565C0)), // Blue Text
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1565C0))
                    ) {
                        Text("Details", fontSize = 12.sp)
                    }
                } else {
                    Button(
                        onClick = { Toast.makeText(context, "Booking ${space.title}", Toast.LENGTH_SHORT).show() },
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)) // Blue Button
                    ) {
                        Text("Book Now", fontSize = 12.sp, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun BottomNavigationBar(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    NavigationBar(
        containerColor = Color.White,
        contentColor = Color.Gray,
        tonalElevation = 8.dp
    ) {
        val items = listOf(
            Triple("HOME", Icons.Outlined.Home, Icons.Filled.Home),
            Triple("FACILITY", Icons.Outlined.Business, Icons.Filled.Business),
            Triple("TEAM", Icons.Outlined.Group, Icons.Filled.Group),
            Triple("EVENTS", Icons.Outlined.Event, Icons.Filled.Event)
        )
        items.forEachIndexed { index, item ->
            NavigationBarItem(
                selected = selectedTab == index,
                onClick = { onTabSelected(index) },
                icon = { Icon(if (selectedTab == index) item.third else item.second, contentDescription = item.first) },
                label = { Text(item.first, fontSize = 10.sp, fontWeight = FontWeight.Medium) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color(0xFF1565C0),
                    selectedTextColor = Color(0xFF1565C0),
                    unselectedIconColor = Color.Gray,
                    unselectedTextColor = Color.Gray,
                    indicatorColor = Color.White
                )
            )
        }
    }
}

// Data Model & Dummy Data
data class SpaceData(
    val title: String,
    val subtitle: String,
    val info: String,
    val imageUrl: String,
    val isAvailable: Boolean
)

fun getDummySpaces(): List<SpaceData> {
    return listOf(
        SpaceData(
            title = "The Innovation Lab",
            subtitle = "Workspace",
            info = "Stars • 7.51 mm",
            imageUrl = "https://images.unsplash.com/photo-1497366216548-37526070297c?w=150&h=150&fit=crop",
            isAvailable = true
        ),
        SpaceData(
            title = "Boardroom A",
            subtitle = "Co-working Room",
            info = "1 day • Ad",
            imageUrl = "https://images.unsplash.com/photo-1497366811353-6870744d04b2?w=150&h=150&fit=crop",
            isAvailable = false
        ),
        SpaceData(
            title = "Co-working Hot Desk",
            subtitle = "Vröskoping",
            info = "Stace • 7.00 mm",
            imageUrl = "https://images.unsplash.com/photo-1593642632823-8f785ba67e45?w=150&h=150&fit=crop",
            isAvailable = true
        ),
        SpaceData(
            title = "Creative Studio",
            subtitle = "Creative Studio",
            info = "Stace • 7.00 mm",
            imageUrl = "https://images.unsplash.com/photo-1517502884422-41eaead166d4?w=150&h=150&fit=crop",
            isAvailable = true
        ),
        SpaceData(
            title = "Team Suite",
            subtitle = "Team Suite",
            info = "Teams • 1 Week",
            imageUrl = "https://images.unsplash.com/photo-1522071820081-009f0129c71c?w=150&h=150&fit=crop",
            isAvailable = true
        )
    )
}

@Composable
fun NotificationsScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.Black)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("Notifications", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Text(
                text = "Hello user !",
                modifier = Modifier.padding(16.dp),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Black
            )
        }
    }
}

data class UserData(
    val name: String,
    val rollNo: String,
    val role: String // "Admin" or "User"
)

val mockAdmins = listOf(
    UserData("Pranav", "2501ME09", "Admin"),
    UserData("Sushankit", "2501MM09", "Admin")
)

@Composable
fun LoginScreen(onLoginSuccess: (UserData) -> Unit) {
    var name by remember { mutableStateOf("") }
    var rollNo by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("FACILIFY", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF2E7D32), letterSpacing = 2.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Welcome! Please login to continue.", fontSize = 16.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(32.dp))
        
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = rollNo,
            onValueChange = { rollNo = it },
            label = { Text("Roll No.") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(8.dp))
        
        if (errorMessage.isNotEmpty()) {
            Text(errorMessage, color = Color.Red, fontSize = 14.sp)
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = {
                if (name.isBlank() || rollNo.isBlank()) {
                    errorMessage = "Please enter both Name and Roll No."
                    return@Button
                }
                
                // Check against mock database
                val adminUser = mockAdmins.find { it.rollNo.equals(rollNo, ignoreCase = true) && it.name.equals(name, ignoreCase = true) }
                if (adminUser != null) {
                    onLoginSuccess(adminUser)
                } else {
                    // Sign in as a regular user
                    onLoginSuccess(UserData(name = name, rollNo = rollNo, role = "User"))
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
        ) {
            Text("Login", fontSize = 16.sp, color = Color.White)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(user: UserData, mainEvent: MainEventConfig) {
    val db = FirebaseFirestore.getInstance()
    var showDialog by remember { mutableStateOf(false) }
    var newImageUrl by remember { mutableStateOf("") }
    var fullScreenImage by remember { mutableStateOf<String?>(null) }
    var galleryImages by remember { mutableStateOf(emptyList<String>()) }

    LaunchedEffect(mainEvent.id) {
        db.collection("mainEvents").document(mainEvent.id).collection("gallery")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    galleryImages = snapshot.documents.mapNotNull { it.getString("url") }
                }
            }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Gallery", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            if (user.role == "Admin") {
                Button(
                    onClick = { showDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("+ Add Photo", color = Color.White, fontSize = 12.sp)
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        
        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(galleryImages.size) { index ->
                val imageUrl = galleryImages[index]
                AsyncImage(
                    model = imageUrl,
                    contentDescription = "Gallery Image",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .height(240.dp)
                        .width(340.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { fullScreenImage = imageUrl }
                )
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Add Photo") },
            text = {
                OutlinedTextField(
                    value = newImageUrl,
                    onValueChange = { newImageUrl = it },
                    label = { Text("Image URL") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newImageUrl.isNotBlank()) {
                        db.collection("mainEvents").document(mainEvent.id).collection("gallery")
                            .add(mapOf("url" to newImageUrl))
                        newImageUrl = ""
                        showDialog = false
                    }
                }) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Full screen image viewer
    if (fullScreenImage != null) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { fullScreenImage = null }) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { fullScreenImage = null }, // click anywhere to close
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = fullScreenImage,
                    contentDescription = "Full Screen",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

data class EventData(
    val title: String,
    val maxRegistration: String,
    val price: String,
    val dateTime: String,
    val venue: String,
    val qrCodeUrl: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsScreen(user: UserData, mainEvent: MainEventConfig) {
    val db = FirebaseFirestore.getInstance()
    var showDialog by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var maxRegistration by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var dateTime by remember { mutableStateOf("") }
    var venue by remember { mutableStateOf("") }
    var qrCodeUrl by remember { mutableStateOf("") }
    var selectedEvent by remember { mutableStateOf<EventData?>(null) }
    var eventsList by remember { mutableStateOf(emptyList<EventData>()) }

    LaunchedEffect(mainEvent.id) {
        db.collection("mainEvents").document(mainEvent.id).collection("events")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    eventsList = snapshot.documents.mapNotNull { doc ->
                        EventData(
                            title = doc.getString("title") ?: "",
                            maxRegistration = doc.getString("maxRegistration") ?: "",
                            price = doc.getString("price") ?: "",
                            dateTime = doc.getString("dateTime") ?: "",
                            venue = doc.getString("venue") ?: "",
                            qrCodeUrl = doc.getString("qrCodeUrl") ?: ""
                        )
                    }
                }
            }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Events", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            if (user.role == "Admin") {
                Button(
                    onClick = { showDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("+ Add Event", color = Color.White, fontSize = 12.sp)
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(eventsList.size) { index ->
                val event = eventsList[index]
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedEvent = event }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(event.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(event.dateTime, fontSize = 14.sp, color = Color.Gray)
                        Text(event.venue, fontSize = 14.sp, color = Color.Gray)
                    }
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Add New Event") },
            text = {
                Column(modifier = Modifier.padding(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Event Title") }, singleLine = true)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = maxRegistration, onValueChange = { maxRegistration = it }, label = { Text("Max Registration (e.g., 50)") }, singleLine = true)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text("Price (e.g., Free, $50)") }, singleLine = true)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = dateTime, onValueChange = { dateTime = it }, label = { Text("Date & Time") }, singleLine = true)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = venue, onValueChange = { venue = it }, label = { Text("Venue") }, singleLine = true)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = qrCodeUrl, onValueChange = { qrCodeUrl = it }, label = { Text("Payment QR Code URL") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (title.isNotBlank()) {
                        db.collection("mainEvents").document(mainEvent.id).collection("events").add(
                            mapOf(
                                "title" to title,
                                "maxRegistration" to maxRegistration,
                                "price" to price,
                                "dateTime" to dateTime,
                                "venue" to venue,
                                "qrCodeUrl" to qrCodeUrl
                            )
                        )
                        title = ""
                        maxRegistration = ""
                        price = ""
                        dateTime = ""
                        venue = ""
                        qrCodeUrl = ""
                        showDialog = false
                    }
                }) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (selectedEvent != null) {
        AlertDialog(
            onDismissRequest = { selectedEvent = null },
            title = { Text(selectedEvent!!.title, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Max Registration: ${selectedEvent!!.maxRegistration}", color = Color.Black)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Price: ${selectedEvent!!.price}", color = Color.Black)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Date: ${selectedEvent!!.dateTime}", color = Color.Black)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Venue: ${selectedEvent!!.venue}", color = Color.Black)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Payment QR Code:", fontWeight = FontWeight.Medium, color = Color.Black)
                    Spacer(modifier = Modifier.height(8.dp))
                    if (selectedEvent!!.qrCodeUrl.isNotBlank()) {
                        AsyncImage(
                            model = selectedEvent!!.qrCodeUrl,
                            contentDescription = "QR Code",
                            modifier = Modifier.size(200.dp).align(Alignment.CenterHorizontally),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text("No QR Code provided.", color = Color.Gray, modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                }
            },
            confirmButton = {
                Button(onClick = { selectedEvent = null }) {
                    Text("Close")
                }
            }
        )
    }
}
