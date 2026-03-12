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
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import java.util.UUID

data class TeamMember(
    val memberId: String = "",
    val name: String = "",
    val roll: String = "",
    val post: String = ""
)

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
    val context = LocalContext.current
    val sharedPref = remember { context.getSharedPreferences("FacilifyPrefs", android.content.Context.MODE_PRIVATE) }
    var loggedInUser by remember { mutableStateOf<UserData?>(null) }
    var selectedEvent by remember { mutableStateOf<MainEventConfig?>(null) }
    var isInitialized by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val userName = sharedPref.getString("userName", null)
        val userRole = sharedPref.getString("userRole", null)
        val userRollNo = sharedPref.getString("userRollNo", null)
        
        if (userName != null && userRole != null && userRollNo != null) {
            loggedInUser = UserData(userName, userRollNo, userRole)
        }
        
        val eventId = sharedPref.getString("eventId", null)
        val eventName = sharedPref.getString("eventName", null)
        val eventImageUrl = sharedPref.getString("eventImageUrl", null)
        
        if (eventId != null && eventName != null) {
            selectedEvent = MainEventConfig(eventId, eventName, eventImageUrl ?: "")
        }
        isInitialized = true
    }

    if (!isInitialized) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFF2E7D32))
        }
        return
    }
    
    if (loggedInUser == null) {
        LoginScreen(onLoginSuccess = { user -> 
            loggedInUser = user
            sharedPref.edit().apply {
                putString("userName", user.name)
                putString("userRole", user.role)
                putString("userRollNo", user.rollNo)
                apply()
            }
        })
    } else if (selectedEvent == null) {
        EventSelectionScreen(
            user = loggedInUser!!, 
            onEventSelected = { event -> 
                selectedEvent = event
                sharedPref.edit().apply {
                    putString("eventId", event.id)
                    putString("eventName", event.name)
                    putString("eventImageUrl", event.imageUrl)
                    apply()
                }
            },
            onLogout = { 
                loggedInUser = null
                sharedPref.edit().clear().apply()
            }
        )
    } else {
        FacilifyApp(
            user = loggedInUser!!, 
            mainEvent = selectedEvent!!, 
            onLogout = { 
                loggedInUser = null
                selectedEvent = null
                sharedPref.edit().clear().apply()
            },
            onBackToEvents = { 
                selectedEvent = null 
                sharedPref.edit().apply {
                    remove("eventId")
                    remove("eventName")
                    remove("eventImageUrl")
                    remove("selectedTab")
                    apply()
                }
            }
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
    val sharedPref = remember { context.getSharedPreferences("FacilifyPrefs", android.content.Context.MODE_PRIVATE) }
    var selectedTab by remember { mutableStateOf(sharedPref.getInt("selectedTab", 1)) }
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
                sharedPref.edit().putInt("selectedTab", index).apply()
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
                FacilityScreen(user = user, mainEvent = mainEvent)
            } else if (selectedTab == 2) {
                TeamScreen()
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
fun FacilityScreen(user: UserData, mainEvent: MainEventConfig) {
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    
    if (selectedCategory == null) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text("Facilities", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            Spacer(modifier = Modifier.height(16.dp))
            
            val categories = listOf("Transportation", "Hostel", "Food", "Trip")
            
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(categories.size) { index ->
                    val category = categories[index]
                    val icon = when (category) {
                        "Transportation" -> Icons.Outlined.DirectionsBus
                        "Hostel" -> Icons.Outlined.Hotel
                        "Food" -> Icons.Outlined.Restaurant
                        "Trip" -> Icons.Outlined.Flight
                        else -> Icons.Outlined.Business
                    }
                    FacilityCategoryCard(title = category, icon = icon) {
                        selectedCategory = category
                    }
                }
            }
        }
    } else {
        FacilityCategoryDetailsScreen(
            category = selectedCategory!!, 
            user = user, 
            mainEvent = mainEvent, 
            onBack = { selectedCategory = null }
        )
    }
}

@Composable
fun FacilityCategoryCard(title: String, icon: ImageVector, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth().aspectRatio(1f).clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
             Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE8F5E9)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = title, tint = Color(0xFF2E7D32), modifier = Modifier.size(32.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Color.Black)
        }
    }
}

@Composable
fun TopBar(user: UserData, mainEvent: MainEventConfig, onNotificationClick: () -> Unit = {}, onLogout: () -> Unit = {}, onBackClick: () -> Unit = {}, modifier: Modifier = Modifier) {
    var profileMenuExpanded by remember { mutableStateOf(false) }
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var postInput by remember { mutableStateOf("") }

    if (showEditProfileDialog) {
        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            title = { Text("Edit Profile") },
            text = {
                OutlinedTextField(
                    value = postInput,
                    onValueChange = { postInput = it },
                    label = { Text("Post (e.g. Coordinator)") }
                )
            },
            confirmButton = {
                Button(onClick = {
                    val dbRef = FirebaseDatabase.getInstance().getReference("TeamMembers").child(user.rollNo)
                    val memberData = TeamMember(memberId = user.rollNo, name = user.name, roll = user.rollNo, post = postInput)
                    dbRef.setValue(memberData)
                    showEditProfileDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfileDialog = false }) { Text("Cancel") }
            }
        )
    }

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
                    if (user.role == "Admin") {
                        DropdownMenuItem(text = { Text("Edit Profile", color = Color.Black) }, onClick = { 
                            profileMenuExpanded = false
                            showEditProfileDialog = true
                        })
                    }
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

@Composable
fun TeamScreen() {
    var teamMembers by remember { mutableStateOf(emptyList<TeamMember>()) }

    LaunchedEffect(Unit) {
        val dbRef = FirebaseDatabase.getInstance().getReference("TeamMembers")
        dbRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val members = mutableListOf<TeamMember>()
                for (child in snapshot.children) {
                    val member = child.getValue(TeamMember::class.java)
                    if (member != null) {
                        members.add(member)
                    }
                }
                teamMembers = members
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Team Section", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(teamMembers.size) { index ->
                val member = teamMembers[index]
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Name: ${member.name}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Roll Number: ${member.roll}", fontSize = 14.sp, color = Color.DarkGray)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Post: ${member.post}", fontSize = 14.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FacilityCategoryDetailsScreen(category: String, user: UserData, mainEvent: MainEventConfig, onBack: () -> Unit) {
    val db = FirebaseFirestore.getInstance()
    var showDialog by remember { mutableStateOf(false) }
    var editingItemIndex by remember { mutableStateOf<Int?>(null) }
    var title by remember { mutableStateOf("") }
    var maxCapacity by remember { mutableStateOf("") }
    var fee by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var imageUrl by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var isUploading by remember { mutableStateOf(false) }

    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        imageUri = uri
    }
    var selectedItem by remember { mutableStateOf<FacilityItemData?>(null) }
    var itemsList by remember { mutableStateOf(emptyList<FacilityItemData>()) }

    LaunchedEffect(mainEvent.id, category) {
        db.collection("mainEvents").document(mainEvent.id).collection("facilities_${category.lowercase()}")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    itemsList = snapshot.documents.mapNotNull { doc ->
                        FacilityItemData(
                            id = doc.id,
                            title = doc.getString("title") ?: "",
                            maxCapacity = doc.getString("maxCapacity") ?: "",
                            fee = doc.getString("fee") ?: "",
                            date = doc.getString("date") ?: "",
                            time = doc.getString("time") ?: "",
                            location = doc.getString("location") ?: "",
                            description = doc.getString("description") ?: "",
                            imageUrl = doc.getString("imageUrl") ?: ""
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.Black)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(category, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            }
            if (user.role == "Admin") {
                Button(
                    onClick = { 
                        editingItemIndex = null
                        title = ""
                        maxCapacity = ""
                        fee = ""
                        date = ""
                        time = ""
                        location = ""
                        description = ""
                        imageUrl = ""
                        imageUri = null
                        showDialog = true 
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("+ Add Item", color = Color.White, fontSize = 12.sp)
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(itemsList.size) { index ->
                val item = itemsList[index]
                var expandedMenu by remember { mutableStateOf(false) }
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedItem = item }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("${item.date} at ${item.time}", fontSize = 14.sp, color = Color.Gray)
                            Text(item.location, fontSize = 14.sp, color = Color.Gray)
                        }
                        
                        if (user.role == "Admin") {
                            Box {
                                IconButton(onClick = { expandedMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "More Options", tint = Color.Gray)
                                }
                                DropdownMenu(
                                    expanded = expandedMenu,
                                    onDismissRequest = { expandedMenu = false },
                                    modifier = Modifier.background(Color.White)
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Edit Item", color = Color.Black) }, 
                                        onClick = { 
                                            expandedMenu = false
                                            editingItemIndex = index
                                            title = item.title
                                            maxCapacity = item.maxCapacity
                                            fee = item.fee
                                            date = item.date
                                            time = item.time
                                            location = item.location
                                            description = item.description
                                            imageUrl = item.imageUrl
                                            imageUri = null
                                            showDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete Item", color = Color.Red) }, 
                                        onClick = { 
                                            expandedMenu = false
                                            db.collection("mainEvents").document(mainEvent.id)
                                                .collection("facilities_${category.lowercase()}").document(item.id).delete()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { if (!isUploading) showDialog = false },
            title = { Text(if (editingItemIndex != null) "Edit Detail" else "Add New Detail") },
            text = {
                LazyColumn(modifier = Modifier.padding(8.dp)) {
                    item { OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true) }
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                    item { OutlinedTextField(value = maxCapacity, onValueChange = { maxCapacity = it }, label = { Text("Max Capacity (e.g., 50)") }, singleLine = true) }
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                    item { OutlinedTextField(value = fee, onValueChange = { fee = it }, label = { Text("Fee (e.g., Free, $50)") }, singleLine = true) }
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                    item { OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("Date") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                    item { OutlinedTextField(value = time, onValueChange = { time = it }, label = { Text("Time") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                    item { OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("Location") }, singleLine = true) }
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                    item {
                        val wordCount = description.split("\\s+".toRegex()).count { it.isNotEmpty() }
                        OutlinedTextField(
                            value = description,
                            onValueChange = { 
                                val newWordCount = it.split("\\s+".toRegex()).count { word -> word.isNotEmpty() }
                                if (newWordCount <= 500) {
                                    description = it 
                                }
                            },
                            label = { Text("Description") },
                            modifier = Modifier.fillMaxWidth().height(120.dp),
                            maxLines = 5,
                            supportingText = { Text("$wordCount/500 words") }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                    item { 
                        Button(
                            onClick = { imageLauncher.launch("image/*") }, 
                            enabled = !isUploading,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray, contentColor = Color.Black)
                        ) {
                            Text(if (imageUri != null) "Image Selected" else if (imageUrl.isNotBlank()) "Change Image" else "Select Image")
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !isUploading,
                    onClick = {
                    if (title.isNotBlank() && !isUploading) {
                        isUploading = true
                        val saveItem = { finalUrl: String ->
                            val itemMap = mapOf(
                                "title" to title,
                                "maxCapacity" to maxCapacity,
                                "fee" to fee,
                                "date" to date,
                                "time" to time,
                                "location" to location,
                                "description" to description,
                                "imageUrl" to finalUrl
                            )
                            if (editingItemIndex != null) {
                                val itemId = itemsList[editingItemIndex!!].id
                                db.collection("mainEvents").document(mainEvent.id)
                                    .collection("facilities_${category.lowercase()}").document(itemId).update(itemMap as Map<String, Any>)
                            } else {
                                db.collection("mainEvents").document(mainEvent.id)
                                    .collection("facilities_${category.lowercase()}").add(itemMap)
                            }
                            isUploading = false
                            showDialog = false
                        }

                        if (imageUri != null) {
                            val storageRef = FirebaseStorage.getInstance().reference.child("facilities_img/${UUID.randomUUID()}")
                            storageRef.putFile(imageUri!!).continueWithTask { task ->
                                if (!task.isSuccessful) task.exception?.let { throw it }
                                storageRef.downloadUrl
                            }.addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    saveItem(task.result.toString())
                                } else {
                                    isUploading = false
                                }
                            }
                        } else {
                            saveItem(imageUrl)
                        }
                    }
                }) {
                    Text(if (isUploading) "Saving..." else if (editingItemIndex != null) "Save" else "Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    if (!isUploading) {
                        showDialog = false 
                    }
                }) { Text("Cancel") }
            }
        )
    }

    if (selectedItem != null) {
        AlertDialog(
            onDismissRequest = { selectedItem = null },
            title = { Text(selectedItem!!.title, fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    item { Text("Max Capacity: ${selectedItem!!.maxCapacity}", color = Color.Black) }
                    item { Spacer(modifier = Modifier.height(4.dp)) }
                    item { Text("Fee: ${selectedItem!!.fee}", color = Color.Black) }
                    item { Spacer(modifier = Modifier.height(4.dp)) }
                    item { Text("Date: ${selectedItem!!.date}", color = Color.Black) }
                    item { Spacer(modifier = Modifier.height(4.dp)) }
                    item { Text("Time: ${selectedItem!!.time}", color = Color.Black) }
                    item { Spacer(modifier = Modifier.height(4.dp)) }
                    item { Text("Location: ${selectedItem!!.location}", color = Color.Black) }
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                    if (selectedItem!!.description.isNotBlank()) {
                        item { Text("Description:", fontWeight = FontWeight.Medium, color = Color.Black) }
                        item { Spacer(modifier = Modifier.height(4.dp)) }
                        item { Text(selectedItem!!.description, color = Color.DarkGray, fontSize = 14.sp) }
                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }
                    if (selectedItem!!.imageUrl.isNotBlank()) {
                        item { Text("Image:", fontWeight = FontWeight.Medium, color = Color.Black) }
                        item { Spacer(modifier = Modifier.height(8.dp)) }
                        item {
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                AsyncImage(
                                    model = selectedItem!!.imageUrl,
                                    contentDescription = "Item Image",
                                    modifier = Modifier.size(200.dp),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { selectedItem = null }) {
                    Text("Close")
                }
            }
        )
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
data class FacilityItemData(
    val id: String = "",
    val title: String,
    val maxCapacity: String,
    val fee: String,
    val date: String,
    val time: String,
    val location: String,
    val description: String,
    val imageUrl: String
)


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
    var isLoginAsAdmin by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var rollNo by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!isLoginAsAdmin && name.isEmpty() && rollNo.isEmpty()) {
            Text("FACILIFY", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF2E7D32), letterSpacing = 2.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Welcome! Please select your login type.", fontSize = 16.sp, color = Color.Gray, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = { isLoginAsAdmin = true },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
            ) {
                Text("Login as Admin", fontSize = 16.sp, color = Color.White)
            }
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = { /* Form will be revealed as user immediately */ name = " " ; name = "" },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Continue as User", fontSize = 16.sp, color = Color(0xFF2E7D32))
            }
        } else {
            Text("FACILIFY", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF2E7D32), letterSpacing = 2.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(if (isLoginAsAdmin) "Admin Login" else "User Login", fontSize = 16.sp, color = Color.Gray)
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
            Spacer(modifier = Modifier.height(16.dp))

            if (isLoginAsAdmin) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Admin Password") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

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

                    if (isLoginAsAdmin) {
                        if (password == "12345") {
                            onLoginSuccess(UserData(name = name, rollNo = rollNo, role = "Admin"))
                        } else {
                            errorMessage = "Wrong Password"
                            Toast.makeText(context, "Wrong Password", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        onLoginSuccess(UserData(name = name, rollNo = rollNo, role = "User"))
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
            ) {
                Text("Login", fontSize = 16.sp, color = Color.White)
            }

            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = {
                isLoginAsAdmin = false
                name = ""
                rollNo = ""
                password = ""
                errorMessage = ""
            }) {
                Text("Back to options", color = Color.Gray)
            }
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
    val id: String = "",
    val title: String,
    val maxRegistration: String,
    val registrationFee: String,
    val date: String,
    val time: String,
    val venue: String,
    val description: String,
    val qrCodeUrl: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsScreen(user: UserData, mainEvent: MainEventConfig) {
    val db = FirebaseFirestore.getInstance()
    var showDialog by remember { mutableStateOf(false) }
    var editingEventIndex by remember { mutableStateOf<Int?>(null) }
    var title by remember { mutableStateOf("") }
    var maxRegistration by remember { mutableStateOf("") }
    var registrationFee by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("") }
    var venue by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var qrCodeUrl by remember { mutableStateOf("") }
    var qrImageUri by remember { mutableStateOf<Uri?>(null) }
    var isUploading by remember { mutableStateOf(false) }

    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        qrImageUri = uri
    }
    var selectedEvent by remember { mutableStateOf<EventData?>(null) }
    var eventsList by remember { mutableStateOf(emptyList<EventData>()) }

    LaunchedEffect(mainEvent.id) {
        db.collection("mainEvents").document(mainEvent.id).collection("events")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    eventsList = snapshot.documents.mapNotNull { doc ->
                        EventData(
                            id = doc.id,
                            title = doc.getString("title") ?: "",
                            maxRegistration = doc.getString("maxRegistration") ?: "",
                            registrationFee = doc.getString("registrationFee") ?: "",
                            date = doc.getString("date") ?: "",
                            time = doc.getString("time") ?: "",
                            venue = doc.getString("venue") ?: "",
                            description = doc.getString("description") ?: "",
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
                    onClick = { 
                        editingEventIndex = null
                        title = ""
                        maxRegistration = ""
                        registrationFee = ""
                        date = ""
                        time = ""
                        venue = ""
                        description = ""
                        qrCodeUrl = ""
                        qrImageUri = null
                        showDialog = true 
                    },
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
                var expandedMenu by remember { mutableStateOf(false) }
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedEvent = event }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(event.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("${event.date} at ${event.time}", fontSize = 14.sp, color = Color.Gray)
                            Text(event.venue, fontSize = 14.sp, color = Color.Gray)
                        }
                        
                        if (user.role == "Admin") {
                            Box {
                                IconButton(onClick = { expandedMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "More Options", tint = Color.Gray)
                                }
                                DropdownMenu(
                                    expanded = expandedMenu,
                                    onDismissRequest = { expandedMenu = false },
                                    modifier = Modifier.background(Color.White)
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Edit Event", color = Color.Black) }, 
                                        onClick = { 
                                            expandedMenu = false
                                            editingEventIndex = index
                                            title = event.title
                                            maxRegistration = event.maxRegistration
                                            registrationFee = event.registrationFee
                                            date = event.date
                                            time = event.time
                                            venue = event.venue
                                            description = event.description
                                            qrCodeUrl = event.qrCodeUrl
                                            qrImageUri = null
                                            showDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete Event", color = Color.Red) }, 
                                        onClick = { 
                                            expandedMenu = false
                                            db.collection("mainEvents").document(mainEvent.id)
                                                .collection("events").document(event.id).delete()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { if (!isUploading) showDialog = false },
            title = { Text(if (editingEventIndex != null) "Edit Event" else "Add New Event") },
            text = {
                LazyColumn(modifier = Modifier.padding(8.dp)) {
                    item { OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Event Title") }, singleLine = true) }
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                    item { OutlinedTextField(value = maxRegistration, onValueChange = { maxRegistration = it }, label = { Text("Max Registration (e.g., 50)") }, singleLine = true) }
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                    item { OutlinedTextField(value = registrationFee, onValueChange = { registrationFee = it }, label = { Text("Registration Fee (e.g., Free, $50)") }, singleLine = true) }
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                    item { OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("Date") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                    item { OutlinedTextField(value = time, onValueChange = { time = it }, label = { Text("Time") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                    item { OutlinedTextField(value = venue, onValueChange = { venue = it }, label = { Text("Venue") }, singleLine = true) }
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                    item {
                        val wordCount = description.split("\\s+".toRegex()).count { it.isNotEmpty() }
                        OutlinedTextField(
                            value = description,
                            onValueChange = { 
                                val newWordCount = it.split("\\s+".toRegex()).count { word -> word.isNotEmpty() }
                                if (newWordCount <= 500) {
                                    description = it 
                                }
                            },
                            label = { Text("Description") },
                            modifier = Modifier.fillMaxWidth().height(120.dp),
                            maxLines = 5,
                            supportingText = { Text("$wordCount/500 words") }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                    item { 
                        Button(
                            onClick = { imageLauncher.launch("image/*") }, 
                            enabled = !isUploading,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray, contentColor = Color.Black)
                        ) {
                            Text(if (qrImageUri != null) "QR Image Selected" else if (qrCodeUrl.isNotBlank()) "Change QR Image" else "Select Payment QR Image")
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !isUploading,
                    onClick = {
                    if (title.isNotBlank() && !isUploading) {
                        isUploading = true
                        val saveEvent = { finalUrl: String ->
                            val eventMap = mapOf(
                                "title" to title,
                                "maxRegistration" to maxRegistration,
                                "registrationFee" to registrationFee,
                                "date" to date,
                                "time" to time,
                                "venue" to venue,
                                "description" to description,
                                "qrCodeUrl" to finalUrl
                            )
                            if (editingEventIndex != null) {
                                val eventId = eventsList[editingEventIndex!!].id
                                db.collection("mainEvents").document(mainEvent.id)
                                    .collection("events").document(eventId).update(eventMap as Map<String, Any>)
                            } else {
                                db.collection("mainEvents").document(mainEvent.id)
                                    .collection("events").add(eventMap)
                            }
                            title = ""
                            maxRegistration = ""
                            registrationFee = ""
                            date = ""
                            time = ""
                            venue = ""
                            description = ""
                            qrCodeUrl = ""
                            qrImageUri = null
                            editingEventIndex = null
                            isUploading = false
                            showDialog = false
                        }

                        if (qrImageUri != null) {
                            val storageRef = FirebaseStorage.getInstance().reference.child("events_qr/${UUID.randomUUID()}")
                            storageRef.putFile(qrImageUri!!).continueWithTask { task ->
                                if (!task.isSuccessful) task.exception?.let { throw it }
                                storageRef.downloadUrl
                            }.addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    saveEvent(task.result.toString())
                                } else {
                                    isUploading = false
                                }
                            }
                        } else {
                            saveEvent(qrCodeUrl)
                        }
                    }
                }) {
                    Text(if (isUploading) "Saving..." else if (editingEventIndex != null) "Save" else "Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    if (!isUploading) {
                        showDialog = false 
                        editingEventIndex = null
                    }
                }) { Text("Cancel") }
            }
        )
    }

    if (selectedEvent != null) {
        AlertDialog(
            onDismissRequest = { selectedEvent = null },
            title = { Text(selectedEvent!!.title, fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    item { Text("Max Registration: ${selectedEvent!!.maxRegistration}", color = Color.Black) }
                    item { Spacer(modifier = Modifier.height(4.dp)) }
                    item { Text("Registration Fee: ${selectedEvent!!.registrationFee}", color = Color.Black) }
                    item { Spacer(modifier = Modifier.height(4.dp)) }
                    item { Text("Date: ${selectedEvent!!.date}", color = Color.Black) }
                    item { Spacer(modifier = Modifier.height(4.dp)) }
                    item { Text("Time: ${selectedEvent!!.time}", color = Color.Black) }
                    item { Spacer(modifier = Modifier.height(4.dp)) }
                    item { Text("Venue: ${selectedEvent!!.venue}", color = Color.Black) }
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                    if (selectedEvent!!.description.isNotBlank()) {
                        item { Text("Description:", fontWeight = FontWeight.Medium, color = Color.Black) }
                        item { Spacer(modifier = Modifier.height(4.dp)) }
                        item { Text(selectedEvent!!.description, color = Color.DarkGray, fontSize = 14.sp) }
                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }
                    item { Text("Payment QR Code:", fontWeight = FontWeight.Medium, color = Color.Black) }
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                    item {
                        if (selectedEvent!!.qrCodeUrl.isNotBlank()) {
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                AsyncImage(
                                    model = selectedEvent!!.qrCodeUrl,
                                    contentDescription = "Payment QR Code",
                                    modifier = Modifier.size(200.dp),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        } else {
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text("No QR Code provided.", color = Color.Gray)
                            }
                        }
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
