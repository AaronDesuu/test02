package com.example.meterkenshin.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

data class TutorialStep(
    val title: String,
    val subtitle: String,
    val description: String,
    val icon: ImageVector,
    val accentColor: Color,
    val tips: List<String> = emptyList(),
    val demoType: DemoType = DemoType.ICON_PULSE
)

enum class DemoType {
    ICON_PULSE,
    SCAN_ANIMATION,
    DLMS_CARD,
    FILE_CARDS,
    PRINT_ANIMATION,
    CHECKLIST
}

private val tutorialSteps = listOf(
    TutorialStep(
        title = "Welcome to MeterKenshin",
        subtitle = "Your Meter Reading Companion",
        description = "MeterKenshin lets you read electricity meters via Bluetooth, calculate billing, and print receipts — all from your phone.",
        icon = Icons.Default.FlashOn,
        accentColor = Color(0xFF3B82F6),
        demoType = DemoType.ICON_PULSE
    ),
    TutorialStep(
        title = "Step 1: Upload Files",
        subtitle = "Import Required CSV Data",
        description = "Before reading meters, upload 3 required CSV files via the Upload Data screen.",
        icon = Icons.Default.Upload,
        accentColor = Color(0xFF10B981),
        tips = listOf(
            "meter.csv — Meter registry with serial numbers & MAC addresses",
            "rate.csv — Billing rate table (23 rate entries)",
            "printer.csv — Bluetooth printer MAC address"
        ),
        demoType = DemoType.FILE_CARDS
    ),
    TutorialStep(
        title = "Step 2: Scan for Meters",
        subtitle = "BLE Discovery",
        description = "Once files are uploaded, the app automatically scans for nearby meters via Bluetooth Low Energy.",
        icon = Icons.Default.Bluetooth,
        accentColor = Color(0xFF6366F1),
        tips = listOf(
            "Meters appear as \"Online\" when detected",
            "Signal strength (RSSI) shown for each meter",
            "Scanning runs automatically in the background"
        ),
        demoType = DemoType.SCAN_ANIMATION
    ),
    TutorialStep(
        title = "Step 3: Read Meter Data",
        subtitle = "DLMS Communication",
        description = "Tap a meter to open its detail screen, then follow the reading sequence.",
        icon = Icons.Default.Speed,
        accentColor = Color(0xFFF59E0B),
        tips = listOf(
            "1. Tap Registration to authenticate",
            "2. Tap Read Data to get current values",
            "3. Billing is calculated automatically",
            "4. Data is saved to CSV files"
        ),
        demoType = DemoType.DLMS_CARD
    ),
    TutorialStep(
        title = "Step 4: Print Receipt",
        subtitle = "Thermal Printing",
        description = "After reading a meter, print a billing receipt on a connected Woosim printer.",
        icon = Icons.Default.Print,
        accentColor = Color(0xFFEC4899),
        tips = listOf(
            "Printer auto-connects via MAC address",
            "Receipt shows all billing charges",
            "Batch print multiple receipts at once",
            "Printing can be disabled in Settings"
        ),
        demoType = DemoType.PRINT_ANIMATION
    ),
    TutorialStep(
        title = "Navigation & Settings",
        subtitle = "Find Your Way Around",
        description = "Use the side drawer menu to navigate between screens. Customize the app in Settings.",
        icon = Icons.Default.Menu,
        accentColor = Color(0xFF8B5CF6),
        tips = listOf(
            "Home — Dashboard overview & printer status",
            "Meter Reading — Browse & filter all meters",
            "Upload Data — Manage CSV files",
            "Export Data — Download billing files (Admin)",
            "Receipt Template — Preview & test printing",
            "Settings — Preferences & admin tools"
        ),
        demoType = DemoType.CHECKLIST
    ),
    TutorialStep(
        title = "You're All Set!",
        subtitle = "Ready to Start",
        description = "You now know the basics of MeterKenshin. For detailed documentation, visit Help & Documentation in Settings.",
        icon = Icons.Default.CheckCircle,
        accentColor = Color(0xFF10B981),
        tips = listOf(
            "Upload your CSV files to begin",
            "Make sure Bluetooth is enabled",
            "Check your Meters"
        ),
        demoType = DemoType.ICON_PULSE
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTutorialDialog(onDismiss: () -> Unit, onGetStarted: () -> Unit = onDismiss) {
    val pagerState = rememberPagerState(pageCount = { tutorialSteps.size })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == tutorialSteps.lastIndex
    val isFirstPage = pagerState.currentPage == 0

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "App Tutorial",
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    },
                    actions = {
                        if (!isLastPage) {
                            Text(
                                text = "Skip",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier
                                    .clickable { onDismiss() }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Pager content
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f)
                ) { page ->
                    TutorialPage(step = tutorialSteps[page])
                }

                // Bottom controls
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    // Page indicators
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        tutorialSteps.forEachIndexed { index, _ ->
                            val isActive = index == pagerState.currentPage
                            val width by animateFloatAsState(
                                targetValue = if (isActive) 24f else 8f,
                                animationSpec = tween(300),
                                label = "dot_width"
                            )
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 3.dp)
                                    .height(8.dp)
                                    .width(width.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isActive) tutorialSteps[pagerState.currentPage].accentColor
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                                    )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Navigation buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Back button
                        AnimatedVisibility(
                            visible = !isFirstPage,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Back")
                            }
                        }
                        if (isFirstPage) Spacer(modifier = Modifier.width(1.dp))

                        // Next / Get Started button
                        Button(
                            onClick = {
                                if (isLastPage) {
                                    onGetStarted()
                                } else {
                                    scope.launch {
                                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = tutorialSteps[pagerState.currentPage].accentColor
                            )
                        ) {
                            Text(if (isLastPage) "Get Started" else "Next")
                            if (!isLastPage) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TutorialPage(step: TutorialStep) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Demo area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            when (step.demoType) {
                DemoType.ICON_PULSE -> PulsingIconDemo(step.icon, step.accentColor)
                DemoType.SCAN_ANIMATION -> ScanAnimationDemo(step.accentColor)
                DemoType.DLMS_CARD -> DLMSCardDemo(step.accentColor)
                DemoType.FILE_CARDS -> FileCardsDemo(step.accentColor)
                DemoType.PRINT_ANIMATION -> PrintAnimationDemo(step.accentColor)
                DemoType.CHECKLIST -> ChecklistDemo(step.accentColor)
            }
        }

        // Title
        Text(
            text = step.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = step.subtitle,
            style = MaterialTheme.typography.titleSmall,
            color = step.accentColor,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = step.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Tips
        if (step.tips.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = step.accentColor.copy(alpha = 0.08f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    step.tips.forEach { tip ->
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                text = "\u2022",
                                color = step.accentColor,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(end = 8.dp, top = 1.dp)
                            )
                            Text(
                                text = tip,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

// ── Demo Animations ──

@Composable
private fun PulsingIconDemo(icon: ImageVector, color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring_alpha"
    )

    Box(contentAlignment = Alignment.Center) {
        // Outer ring
        Box(
            modifier = Modifier
                .size(140.dp)
                .scale(scale * 1.2f)
                .alpha(ringAlpha)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f))
        )
        // Inner circle
        Box(
            modifier = Modifier
                .size(100.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(color.copy(alpha = 0.3f), color.copy(alpha = 0.1f))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = color
            )
        }
    }
}

@Composable
private fun ScanAnimationDemo(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "scan")
    val ripple1 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)),
        label = "r1"
    )
    val ripple2 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(2000, easing = LinearEasing),
            initialStartOffset = androidx.compose.animation.core.StartOffset(666)
        ),
        label = "r2"
    )
    val ripple3 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(2000, easing = LinearEasing),
            initialStartOffset = androidx.compose.animation.core.StartOffset(1333)
        ),
        label = "r3"
    )

    Box(
        modifier = Modifier.size(180.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val maxRadius = size.minDimension / 2
            listOf(ripple1, ripple2, ripple3).forEach { progress ->
                drawCircle(
                    color = color.copy(alpha = (1f - progress) * 0.4f),
                    radius = maxRadius * progress,
                    center = center,
                    style = Stroke(width = 3.dp.toPx())
                )
            }
        }
        // Center icon
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Bluetooth,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
private fun DLMSCardDemo(color: Color) {
    // Mirrors the real DLMSFunctionsCard with animated button highlighting
    val buttons: List<Pair<ImageVector, String>> = listOf(
        Icons.Default.Person to "Registration",
        Icons.Default.Assessment to "Read data",
        Icons.Default.Storage to "Load profile",
        Icons.Default.Event to "Event log",
        Icons.Default.Payment to "Billing data",
        Icons.Default.Schedule to "Set Clock"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "dlms")
    val activeButton by infiniteTransition.animateFloat(
        initialValue = -0.5f,
        targetValue = 6.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "active_btn"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header — same as real card
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "DLMS Functions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Buttons — matching real card layout
            buttons.forEachIndexed { index, (icon, label) ->
                val isActive = activeButton.toInt() == index
                val btnScale by animateFloatAsState(
                    targetValue = if (isActive) 1.03f else 1f,
                    animationSpec = tween(200),
                    label = "btn_scale_$index"
                )

                Button(
                    onClick = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .scale(btnScale),
                    enabled = true,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isActive) color
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (isActive) Color.White
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (isActive) Color.White
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (index < buttons.lastIndex) {
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun FileCardsDemo(color: Color) {
    val files = listOf(
        Triple(Icons.Default.Description, "meter.csv", "Meter Registry"),
        Triple(Icons.Default.Receipt, "rate.csv", "Rate Table"),
        Triple(Icons.Default.Print, "printer.csv", "Printer Config")
    )

    val infiniteTransition = rememberInfiniteTransition(label = "files")
    val activeFile by infiniteTransition.animateFloat(
        initialValue = -0.5f,
        targetValue = 3.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "active_file"
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        files.forEachIndexed { index, (icon, name, desc) ->
            val isActive = activeFile.toInt() == index
            val animAlpha by animateFloatAsState(
                targetValue = if (isActive) 1f else 0.5f,
                animationSpec = tween(300),
                label = "file_alpha_$index"
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(animAlpha),
                colors = CardDefaults.cardColors(
                    containerColor = if (isActive) color.copy(alpha = 0.12f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (isActive) color.copy(alpha = 0.2f)
                                else Color.Transparent
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = if (isActive) color
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isActive) color
                            else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            desc,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    if (isActive) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = color,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PrintAnimationDemo(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "print")
    val printProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "print_prog"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Printer body
        Box(
            modifier = Modifier
                .size(width = 120.dp, height = 60.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Print,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(32.dp)
            )
        }

        // Receipt coming out
        val receiptHeight = (80 * printProgress).dp
        Box(
            modifier = Modifier
                .width(90.dp)
                .height(receiptHeight)
                .background(
                    Color.White.copy(alpha = 0.9f),
                    RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp)
                )
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            if (printProgress > 0.2f) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    // Simulated receipt lines
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(3.dp)
                            .background(Color.Gray.copy(alpha = 0.4f), CircleShape)
                    )
                    if (printProgress > 0.4f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .height(3.dp)
                                .background(Color.Gray.copy(alpha = 0.3f), CircleShape)
                        )
                    }
                    if (printProgress > 0.55f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.5f)
                                .height(3.dp)
                                .background(Color.Gray.copy(alpha = 0.3f), CircleShape)
                        )
                    }
                    if (printProgress > 0.7f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .height(3.dp)
                                .background(Color.Gray.copy(alpha = 0.25f), CircleShape)
                        )
                    }
                    if (printProgress > 0.85f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .height(3.dp)
                                .background(color.copy(alpha = 0.4f), CircleShape)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChecklistDemo(color: Color) {
    val items = listOf(
        Icons.Default.Speed to "Meter Reading",
        Icons.Default.Upload to "Upload Data",
        Icons.Default.Download to "Export Data",
        Icons.Default.Receipt to "Receipt Template",
        Icons.Default.Settings to "Settings"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "checklist")
    val activeItem by infiniteTransition.animateFloat(
        initialValue = -0.5f,
        targetValue = 5.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "active_item"
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items.forEachIndexed { index, (icon, label) ->
            val isActive = activeItem.toInt() == index
            val scale by animateFloatAsState(
                targetValue = if (isActive) 1.04f else 1f,
                animationSpec = tween(200),
                label = "check_scale_$index"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(scale)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isActive) color.copy(alpha = 0.1f) else Color.Transparent
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (isActive) color
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isActive) color
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}
