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
import coil.compose.AsyncImage

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FacilifyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FacilifyApp()
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

@Composable
fun FacilifyApp() {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(1) } // Default selected: Facility

    Scaffold(
        bottomBar = {
            BottomNavigationBar(selectedTab = selectedTab) { index ->
                selectedTab = index
                val tabNames = listOf("Home", "Facility", "Team", "Events")
                Toast.makeText(context, "${tabNames[index]} clicked!", Toast.LENGTH_SHORT).show()
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFF5F5F7))
        ) {
            if (selectedTab == 1) { // Render Facility screen content
                FacilityScreen()
            } else {
                // Placeholder for other screens
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "Navigate to Facility Tab", fontSize = 18.sp, color = Color.Gray)
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
        item { TopBar() }
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
fun TopBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "FACILIFY",
            color = Color(0xFF2E7D32),
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = "https://i.pravatar.cc/100?img=11",
                contentDescription = "Profile Picture",
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Alex J.", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color.Black)
            Spacer(modifier = Modifier.width(16.dp))
            Box {
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
