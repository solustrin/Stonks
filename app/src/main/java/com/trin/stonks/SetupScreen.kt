package com.trin.stonks

import androidx.compose.animation.core.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri

// ============================================================
// STONKS ANIMATED LOADING SCREEN
// ============================================================
@Composable
fun StonksLoadingAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "loading")

    // Animate 3 bars to look like a stock graph moving
    val bar1 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar1"
    )
    val bar2 by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar2"
    )
    val bar3 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar3"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .height(64.dp)
                .width(80.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            Box(
                modifier = Modifier
                    .width(16.dp)
                    .fillMaxHeight(bar1)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
            )
            Box(
                modifier = Modifier
                    .width(16.dp)
                    .fillMaxHeight(bar2)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
            )
            Box(
                modifier = Modifier
                    .width(16.dp)
                    .fillMaxHeight(bar3)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "Connecting to Stonks...",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

// ============================================================
// CLEANER SETUP SCREEN
// ============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    onSetupComplete: () -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var imgbbKey by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var fileName by remember { mutableStateOf<String?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            selectedFileUri = it
            fileName = it.path?.substringAfterLast("/")
        }
    }

    fun processSetup() {
        if (selectedFileUri == null) {
            onError("Please select google-services.json")
            return
        }
        if (imgbbKey.isBlank()) {
            onError("Please enter your ImgBB API key")
            return
        }

        isProcessing = true
        try {
            val json = context.contentResolver.openInputStream(selectedFileUri!!)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: throw Exception("Could not read file")

            val config = com.trin.stonks.utils.JsonParser.parseGoogleServicesJson(json)
                ?: throw Exception("Invalid google-services.json format")

            val firestore = com.trin.stonks.utils.FirebaseInitializer.initializeFirebase(context, config)
            com.trin.stonks.utils.ConfigManager.saveConfig(context, config, imgbbKey)

            firestore.collection("test").limit(1).get()
                .addOnSuccessListener {
                    isProcessing = false
                    onSetupComplete()
                }
                .addOnFailureListener { e ->
                    isProcessing = false
                    onError("Firebase connection failed: ${e.message}")
                }
        } catch (e: Exception) {
            isProcessing = false
            onError("Setup failed: ${e.message}")
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Setup Form (Fades out when processing)
            AnimatedVisibility(
                visible = !isProcessing,
                enter = fadeIn(tween(500)),
                exit = fadeOut(tween(300))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "STONKS Setup",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Connect your own Firebase project and ImgBB account to get started.",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(48.dp))

                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp)
                            .clickable { filePicker.launch("application/json") },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = if (fileName != null) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) else Color.Transparent
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.UploadFile,
                                contentDescription = "Upload",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = fileName ?: "Select google-services.json",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (fileName != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    OutlinedTextField(
                        value = imgbbKey,
                        onValueChange = { imgbbKey = it },
                        label = { Text("ImgBB API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        supportingText = { Text("Get your key from https://api.imgbb.com/") }
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = { processSetup() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Connect", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "You need a Firebase project with Firestore enabled and an ImgBB API key.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Cool Graph Loading Screen (Fades in when processing)
            AnimatedVisibility(
                visible = isProcessing,
                enter = fadeIn(tween(500)),
                exit = fadeOut(tween(300)),
                modifier = Modifier.align(Alignment.Center)
            ) {
                StonksLoadingAnimation()
            }
        }
    }
}