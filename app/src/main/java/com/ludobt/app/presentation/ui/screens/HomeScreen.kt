package com.ludobt.app.presentation.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ludobt.app.R
import com.ludobt.app.domain.model.AiDifficulty
import com.ludobt.app.domain.model.PlayerColor
import com.ludobt.app.presentation.theme.AccentGold
import com.ludobt.app.presentation.theme.BgDark
import com.ludobt.app.presentation.theme.BorderSubtle
import com.ludobt.app.presentation.theme.LudoBlue
import com.ludobt.app.presentation.theme.LudoGreen
import com.ludobt.app.presentation.theme.LudoRed
import com.ludobt.app.presentation.theme.LudoYellow
import com.ludobt.app.presentation.theme.SurfaceDark
import com.ludobt.app.presentation.theme.SurfaceElevated
import com.ludobt.app.presentation.theme.TextPrimary
import com.ludobt.app.presentation.theme.TextSecondary
import com.ludobt.app.presentation.ui.board.GotiBitmapProvider
import com.ludobt.app.presentation.ui.board.draw3DGoti

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onStartBluetooth: () -> Unit,
    onStartPassAndPlay: (List<Pair<String, PlayerColor>>) -> Unit,
    onStartComputer: (AiDifficulty, Int, PlayerColor, String) -> Unit,
    onStartOnline: () -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        GotiBitmapProvider.preload(context)
    }

    var showPassAndPlaySheet by remember { mutableStateOf(false) }
    var showComputerSheet by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "home_anims")
    val gotiFloatOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "goti_float"
    )

    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_glow"
    )

    Scaffold(
        containerColor = BgDark,
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. TOP PROFILE & CURRENCY BAR (Modern Glassmorphic Header)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFF0F172A).copy(alpha = 0.85f))
                    .border(1.dp, BorderSubtle, RoundedCornerShape(22.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Profile Avatar & Player Tag
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(AccentGold, Color(0xFFB45309), Color(0xFF78350F))
                                )
                            )
                            .border(2.dp, AccentGold, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "👑", fontSize = 20.sp)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Champion",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(AccentGold)
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "LVL 12",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.Black
                                )
                            }
                        }
                        Text(
                            text = "Grandmaster Rank",
                            fontSize = 11.sp,
                            color = AccentGold.copy(alpha = 0.85f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Coin & Gem Capsules
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Coin Capsule
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF1E293B))
                            .border(1.dp, AccentGold.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "🪙", fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "2,500",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentGold
                            )
                        }
                    }

                    // Gem Capsule
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF1E293B))
                            .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "💎", fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "150",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF38BDF8)
                            )
                        }
                    }
                }
            }

            // 2. HERO SHOWCASE CARD (Vibrant 3D Artwork + 3D Goti Showcase matching Image 1 & 2)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .shadow(16.dp, RoundedCornerShape(24.dp))
                    .clip(RoundedCornerShape(24.dp))
                    .border(
                        width = 1.5.dp,
                        brush = Brush.linearGradient(
                            listOf(AccentGold, Color(0xFF38BDF8), Color(0xFF818CF8))
                        ),
                        shape = RoundedCornerShape(24.dp)
                    )
            ) {
                // Background Hero Artwork
                Image(
                    painter = painterResource(id = R.drawable.ludo_hero_art),
                    contentDescription = "Ludo 3D Showcase",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Dark gaming gradient overlay scrim for high readability
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0x660A0F1D),
                                    Color(0xB30A0F1D),
                                    Color(0xF00A0F1D)
                                )
                            )
                        )
                )

                // Hero Content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xCC000000))
                                .border(1.dp, AccentGold, RoundedCornerShape(12.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "👑 ROYAL 3D EDITION",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = AccentGold,
                                letterSpacing = 0.5.sp
                            )
                        }

                        Text(
                            text = "⚡ REAL-TIME SYNC",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF38BDF8)
                        )
                    }

                    // Bottom: Title & 4 Goties Showcase Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            Text(
                                text = "LUDO ROYALE",
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Classic Strategy • Modern 3D Goties",
                                fontSize = 12.sp,
                                color = TextSecondary,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // 4 Goties Visual Showcase (Image 1 tokens with float animation)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.Bottom,
                            modifier = Modifier.padding(bottom = 2.dp)
                        ) {
                            listOf(
                                PlayerColor.RED to 0f,
                                PlayerColor.YELLOW to 1.5f,
                                PlayerColor.GREEN to 3f,
                                PlayerColor.BLUE to 4.5f
                            ).forEach { (color, stagger) ->
                                val offset = ((gotiFloatOffset + stagger) % 6f)
                                Canvas(modifier = Modifier.size(28.dp, 36.dp)) {
                                    draw3DGoti(
                                        center = Offset(size.width * 0.5f, size.height * 0.52f),
                                        size = size.width * 0.95f,
                                        color = color,
                                        bounceOffset = offset
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. SECTION LABEL
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "✨", fontSize = 14.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "SELECT GAME MODE",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = AccentGold,
                    letterSpacing = 1.sp
                )
            }

            // 4. PRIMARY HERO CARD: ONLINE MULTIPLAYER (Electrifying Gradient Card)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(12.dp, RoundedCornerShape(22.dp))
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color(0xFF3730A3),
                                Color(0xFF1E1B4B),
                                Color(0xFF0F172A)
                            )
                        )
                    )
                    .border(
                        width = 1.8.dp,
                        brush = Brush.horizontalGradient(
                            listOf(Color(0xFF818CF8), Color(0xFFC084FC), Color(0xFF818CF8))
                        ),
                        shape = RoundedCornerShape(22.dp)
                    )
                    .clickable(onClick = onStartOnline)
                    .padding(18.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Live servers tag
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF10B981).copy(alpha = 0.2f))
                                .border(1.dp, Color(0xFF10B981), RoundedCornerShape(12.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF22C55E))
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "GLOBAL SERVERS ONLINE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4ADE80)
                            )
                        }

                        // Glowing Globe Icon
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF6366F1).copy(alpha = 0.25f))
                                .border(1.dp, Color(0xFF818CF8), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Public,
                                contentDescription = null,
                                tint = Color(0xFFA5B4FC),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Play Online Multiplayer",
                        fontSize = 21.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Real-time match with players worldwide. Create private 4-digit PIN rooms or jump straight into Quick Match!",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = onStartOnline,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6366F1)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Play Online (Quick Match / Room PIN)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = Color.White
                            )
                        }
                    }
                }
            }

            // 5. USP HERO CARD: BLUETOOTH NEARBY (Offline Travel Mode)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(12.dp, RoundedCornerShape(22.dp))
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color(0xFF0369A1),
                                Color(0xFF075985),
                                Color(0xFF0A1930)
                            )
                        )
                    )
                    .border(
                        width = 1.8.dp,
                        brush = Brush.horizontalGradient(
                            listOf(Color(0xFF38BDF8), AccentGold, Color(0xFF38BDF8))
                        ),
                        shape = RoundedCornerShape(22.dp)
                    )
                    .clickable(onClick = onStartBluetooth)
                    .padding(18.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(AccentGold)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "✈️ TRAVEL USP • ZERO DATA REQUIRED",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.Black
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF0284C7).copy(alpha = 0.3f))
                                .border(1.dp, Color(0xFF38BDF8), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bluetooth,
                                contentDescription = null,
                                tint = Color(0xFF7DD3FC),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Play Nearby (Bluetooth)",
                        fontSize = 21.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Direct phone-to-phone peer connectivity! No Wi-Fi or mobile data needed—play seamlessly on flights, trains, road trips, and remote areas.",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = onStartBluetooth,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0284C7)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Connect Nearby (2–4 Phones)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = Color.White
                            )
                        }
                    }
                }
            }

            // 6. TWO-COLUMN GRID: VS COMPUTER & PASS AND PLAY
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Play vs Computer Card
                ModernModeCard(
                    title = "Vs Computer",
                    subtitle = "Smart AI (4 Levels)",
                    tag = "🤖 SOLO",
                    gradient = listOf(Color(0xFF065F46), Color(0xFF064E3B), Color(0xFF022C22)),
                    borderColor = Color(0xFF34D399),
                    icon = Icons.Default.SmartToy,
                    modifier = Modifier.weight(1f),
                    onClick = { showComputerSheet = true }
                )

                // Pass & Play Card
                ModernModeCard(
                    title = "Pass & Play",
                    subtitle = "1 Phone • 2–4 Players",
                    tag = "👥 PARTY",
                    gradient = listOf(Color(0xFF92400E), Color(0xFF78350F), Color(0xFF451A03)),
                    borderColor = Color(0xFFFBBF24),
                    icon = Icons.Default.Group,
                    modifier = Modifier.weight(1f),
                    onClick = { showPassAndPlaySheet = true }
                )
            }

            // 7. CAREER STATISTICS GLASS CARD
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF0F172A).copy(alpha = 0.9f))
                    .border(1.dp, BorderSubtle, RoundedCornerShape(20.dp))
                    .padding(18.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.EmojiEvents,
                                contentDescription = null,
                                tint = AccentGold,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Career Performance",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }

                        Text(
                            text = "SEASON 4",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentGold
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        ModernStatItem(label = "Matches", value = "24", icon = "🎯")
                        ModernStatItem(label = "Victories", value = "18", icon = "🏆")
                        ModernStatItem(label = "Win Rate", value = "75%", icon = "📈")
                        ModernStatItem(label = "Streak", value = "🔥 5", icon = "⚡")
                    }
                }
            }
        }
    }

    // PASS AND PLAY BOTTOM SHEET
    if (showPassAndPlaySheet) {
        ModalBottomSheet(
            onDismissRequest = { showPassAndPlaySheet = false },
            containerColor = SurfaceDark,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            PassAndPlaySheetContent(
                onConfirm = { configs ->
                    showPassAndPlaySheet = false
                    onStartPassAndPlay(configs)
                }
            )
        }
    }

    // VS COMPUTER BOTTOM SHEET
    if (showComputerSheet) {
        ModalBottomSheet(
            onDismissRequest = { showComputerSheet = false },
            containerColor = SurfaceDark,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            ComputerModeSheetContent(
                onConfirm = { difficulty, bots, userColor, userName ->
                    showComputerSheet = false
                    onStartComputer(difficulty, bots, userColor, userName)
                }
            )
        }
    }
}

@Composable
private fun ModernModeCard(
    title: String,
    subtitle: String,
    tag: String,
    gradient: List<Color>,
    borderColor: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .shadow(10.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(gradient))
            .border(1.5.dp, borderColor.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.25f))
                        .border(1.dp, borderColor.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = borderColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(borderColor.copy(alpha = 0.2f))
                        .border(1.dp, borderColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = tag,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = borderColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = TextSecondary,
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
private fun ModernStatItem(label: String, value: String, icon: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1E293B).copy(alpha = 0.6f))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(text = value, fontSize = 16.sp, fontWeight = FontWeight.Black, color = AccentGold)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = label, fontSize = 10.sp, color = TextSecondary)
    }
}

/**
 * Authentic "CHOOSE COLOR AND NAME" setup matching reference Image 3 & 4.
 * Allows 2, 3, or 4 players to each customize their goti color and name.
 */
@Composable
private fun PassAndPlaySheetContent(
    onConfirm: (List<Pair<String, PlayerColor>>) -> Unit
) {
    var playerCount by remember { mutableIntStateOf(2) } // 2P, 3P, 4P

    // 2P state (Matching Image 4)
    var selectedPairIndex by remember { mutableIntStateOf(1) } // 0: Blue & Green, 1: Red & Yellow
    var p2Name1 by remember { mutableStateOf("Player 1") }
    var p2Name2 by remember { mutableStateOf("Player 2") }

    // 3P state (Matching Image 3)
    var p3Colors by remember {
        mutableStateOf(listOf(PlayerColor.RED, PlayerColor.GREEN, PlayerColor.YELLOW))
    }
    var p3Names by remember {
        mutableStateOf(listOf("Player 1", "Player 2", "Player 3"))
    }

    // 4P state
    var p4Colors by remember {
        mutableStateOf(listOf(PlayerColor.RED, PlayerColor.GREEN, PlayerColor.YELLOW, PlayerColor.BLUE))
    }
    var p4Names by remember {
        mutableStateOf(listOf("Player 1", "Player 2", "Player 3", "Player 4"))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Title Header (Matching Image 3 & 4)
        Text(
            text = "CHOOSE COLOR AND NAME",
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            color = AccentGold,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Content based on Player Count
        if (playerCount == 2) {
            // Option 1: Blue & Green (Diagonally Opposite)
            TwoPlayerPairOption(
                pairIndex = 0,
                color1 = PlayerColor.BLUE,
                color2 = PlayerColor.GREEN,
                name1 = p2Name1,
                name2 = p2Name2,
                isSelected = selectedPairIndex == 0,
                onSelect = { selectedPairIndex = 0 },
                onName1Change = { p2Name1 = it },
                onName2Change = { p2Name2 = it }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Option 2: Red & Yellow (Diagonally Opposite - Checked in Image 4)
            TwoPlayerPairOption(
                pairIndex = 1,
                color1 = PlayerColor.RED,
                color2 = PlayerColor.YELLOW,
                name1 = p2Name1,
                name2 = p2Name2,
                isSelected = selectedPairIndex == 1,
                onSelect = { selectedPairIndex = 1 },
                onName1Change = { p2Name1 = it },
                onName2Change = { p2Name2 = it }
            )
        } else if (playerCount == 3) {
            // 3 Player Rows with 4 Goti options and green circular selection (Image 3)
            (0..2).forEach { index ->
                PlayerRowConfig(
                    playerNumber = index + 1,
                    selectedColor = p3Colors[index],
                    name = p3Names[index],
                    onNameChange = { newName ->
                        p3Names = p3Names.toMutableList().also { it[index] = newName }
                    },
                    onColorSelect = { newColor ->
                        // Swap with whoever had newColor to guarantee distinct colors
                        val existingIndex = p3Colors.indexOf(newColor)
                        val updated = p3Colors.toMutableList()
                        if (existingIndex != -1 && existingIndex != index) {
                            updated[existingIndex] = p3Colors[index]
                        }
                        updated[index] = newColor
                        p3Colors = updated
                    }
                )
                if (index < 2) Spacer(modifier = Modifier.height(10.dp))
            }
        } else {
            // 4 Player Rows with 4 Goti options and green circular selection
            (0..3).forEach { index ->
                PlayerRowConfig(
                    playerNumber = index + 1,
                    selectedColor = p4Colors[index],
                    name = p4Names[index],
                    onNameChange = { newName ->
                        p4Names = p4Names.toMutableList().also { it[index] = newName }
                    },
                    onColorSelect = { newColor ->
                        val existingIndex = p4Colors.indexOf(newColor)
                        val updated = p4Colors.toMutableList()
                        if (existingIndex != -1 && existingIndex != index) {
                            updated[existingIndex] = p4Colors[index]
                        }
                        updated[index] = newColor
                        p4Colors = updated
                    }
                )
                if (index < 3) Spacer(modifier = Modifier.height(10.dp))
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Player Count Selector Pills: 2P, 3P, 4P (Image 3 & 4)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                listOf(2, 3, 4).forEach { count ->
                    PlayerCountPill(
                        label = "${count}P",
                        isSelected = playerCount == count,
                        onClick = { playerCount = count }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Play Button (Image 3 & 4)
        LudoPlayButton(
            onClick = {
                val configs = when (playerCount) {
                    2 -> {
                        val (c1, c2) = if (selectedPairIndex == 0) {
                            PlayerColor.BLUE to PlayerColor.GREEN
                        } else {
                            PlayerColor.RED to PlayerColor.YELLOW
                        }
                        listOf(p2Name1 to c1, p2Name2 to c2)
                    }
                    3 -> {
                        (0..2).map { p3Names[it] to p3Colors[it] }
                    }
                    else -> {
                        (0..3).map { p4Names[it] to p4Colors[it] }
                    }
                }
                onConfirm(configs)
            }
        )
    }
}

/**
 * Vs Computer setup sheet:
 * Allows user to pick their goti color and guarantees AI is placed in the diagonally opposite yard in 2P.
 */
@Composable
private fun ComputerModeSheetContent(
    onConfirm: (AiDifficulty, Int, PlayerColor, String) -> Unit
) {
    var botCount by remember { mutableIntStateOf(1) } // 1 AI (2P), 2 AI (3P), 3 AI (4P)
    var selectedDifficulty by remember { mutableStateOf(AiDifficulty.MEDIUM) }
    var userColor by remember { mutableStateOf(PlayerColor.BLUE) }
    var userName by remember { mutableStateOf("You") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Title Header
        Text(
            text = "PLAY VS COMPUTER",
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            color = AccentGold,
            letterSpacing = 1.sp
        )
        Text(
            text = "CHOOSE YOUR COLOR AND OPPONENTS",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = TextSecondary,
            letterSpacing = 0.5.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // User Color & Name Row (Matching Image 3)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF0F2656))
                .border(1.5.dp, BorderSubtle, RoundedCornerShape(14.dp))
                .padding(12.dp)
        ) {
            Column {
                Text(
                    text = "Select Your Color:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentGold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 4 Gotis with Green selection circle
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(PlayerColor.BLUE, PlayerColor.RED, PlayerColor.GREEN, PlayerColor.YELLOW).forEach { col ->
                            SelectableGotiItem(
                                color = col,
                                isSelected = userColor == col,
                                onClick = { userColor = col }
                            )
                        }
                    }

                    // User name field
                    NameInputBox(
                        value = userName,
                        onValueChange = { userName = it },
                        modifier = Modifier.width(110.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Court layout preview (Shows that in 2P, AI is in the diagonally opposite yard!)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceDark)
                .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val oppositeColor = when (userColor) {
                    PlayerColor.RED -> PlayerColor.YELLOW
                    PlayerColor.YELLOW -> PlayerColor.RED
                    PlayerColor.BLUE -> PlayerColor.GREEN
                    PlayerColor.GREEN -> PlayerColor.BLUE
                }

                if (botCount == 1) {
                    Text(
                        text = "2P Match: Diagonally Opposite Courts",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Text(
                        text = "AI: ${oppositeColor.name}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentGold
                    )
                } else {
                    Text(
                        text = "Multi-Player AI Match",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Text(
                        text = "${botCount} Computer Bots",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentGold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // AI Difficulty selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AiDifficulty.entries.forEach { diff ->
                val isSel = selectedDifficulty == diff
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSel) AccentGold else SurfaceElevated)
                        .clickable { selectedDifficulty = diff }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = diff.label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSel) Color.Black else TextPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Player Count Selector Pills: 2P (1 AI), 3P (2 AI), 4P (3 AI)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                listOf(1, 2, 3).forEach { count ->
                    PlayerCountPill(
                        label = "${count + 1}P",
                        isSelected = botCount == count,
                        onClick = { botCount = count }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Play Button
        LudoPlayButton(
            onClick = {
                onConfirm(selectedDifficulty, botCount, userColor, userName)
            }
        )
    }
}

/**
 * 2-Player Pair Option Card matching reference Image 4.
 */
@Composable
private fun TwoPlayerPairOption(
    pairIndex: Int,
    color1: PlayerColor,
    color2: PlayerColor,
    name1: String,
    name2: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onName1Change: (String) -> Unit,
    onName2Change: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) Color(0xFF0D2C68) else Color(0xFF091B40))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) Color(0xFF22C55E) else BorderSubtle,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onSelect)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Radio Check circle matching Image 4
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) Color(0xFF22C55E) else Color.Transparent)
                    .border(2.5.dp, Color(0xFF22C55E), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Two gotis vertical column
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Canvas(modifier = Modifier.size(26.dp)) {
                    draw3DGoti(
                        center = Offset(size.width / 2f, size.height * 0.44f),
                        size = size.width * 0.95f,
                        color = color1
                    )
                }
                Canvas(modifier = Modifier.size(26.dp)) {
                    draw3DGoti(
                        center = Offset(size.width / 2f, size.height * 0.44f),
                        size = size.width * 0.95f,
                        color = color2
                    )
                }
            }

            // Names inputs vertical column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NameInputBox(value = name1, onValueChange = onName1Change)
                NameInputBox(value = name2, onValueChange = onName2Change)
            }

            // Subtle dice icons column matching Image 4
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("🎲", fontSize = 18.sp)
                Text("🎲", fontSize = 18.sp)
            }
        }
    }
}

/**
 * Player Row Config with 4 gotis and name input matching reference Image 3.
 */
@Composable
private fun PlayerRowConfig(
    playerNumber: Int,
    selectedColor: PlayerColor,
    name: String,
    onNameChange: (String) -> Unit,
    onColorSelect: (PlayerColor) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0D2554))
            .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 4 Gotis with Green selection circle matching Image 3
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(PlayerColor.BLUE, PlayerColor.RED, PlayerColor.GREEN, PlayerColor.YELLOW).forEach { color ->
                    SelectableGotiItem(
                        color = color,
                        isSelected = selectedColor == color,
                        onClick = { onColorSelect(color) }
                    )
                }
            }

            // Name field
            NameInputBox(
                value = name,
                onValueChange = onNameChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            )

            // Right side dice
            Text("🎲", fontSize = 18.sp)
        }
    }
}

/**
 * Selectable 3D goti token item with green selection circle matching Image 3.
 */
@Composable
internal fun SelectableGotiItem(
    color: PlayerColor,
    isSelected: Boolean,
    isEnabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .then(
                if (!isEnabled) {
                    Modifier.alpha(0.28f)
                } else if (isSelected) {
                    Modifier.border(2.5.dp, Color(0xFF22C55E), CircleShape)
                } else {
                    Modifier
                }
            )
            .clickable(enabled = isEnabled, onClick = onClick)
            .padding(3.dp)
    ) {
        Canvas(modifier = Modifier.size(26.dp)) {
            draw3DGoti(
                center = Offset(size.width / 2f, size.height * 0.44f),
                size = size.width * 0.95f,
                color = color
            )
        }
    }
}

/**
 * Name text input box matching reference Image 3 & 4.
 */
@Composable
internal fun NameInputBox(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(
            color = Color.Black,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        ),
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .border(1.5.dp, Color(0xFF8A2B12), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp)
    )
}

/**
 * Player Count selector pill matching reference Image 3 & 4.
 */
@Composable
internal fun PlayerCountPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(width = 64.dp, height = 44.dp)
            .shadow(if (isSelected) 6.dp else 2.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = if (isSelected) {
                        listOf(Color(0xFF0F3276), Color(0xFF081B45))
                    } else {
                        listOf(Color(0xFF131D33), Color(0xFF0D1424))
                    }
                )
            )
            .border(
                width = if (isSelected) 2.5.dp else 1.5.dp,
                color = if (isSelected) AccentGold else Color(0x66F59E0B),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = FontWeight.Black,
            color = if (isSelected) Color.White else Color(0xFFB0C4DE)
        )
    }
}

/**
 * Large Play button matching reference Image 3 & 4.
 */
@Composable
internal fun LudoPlayButton(
    text: String = "Play",
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(width = 180.dp, height = 50.dp)
            .shadow(8.dp, RoundedCornerShape(25.dp))
            .clip(RoundedCornerShape(25.dp))
            .background(
                brush = Brush.verticalGradient(
                    listOf(
                        Color(0xFF2563EB),
                        Color(0xFF1D4ED8),
                        Color(0xFF1E3A8A)
                    )
                )
            )
            .border(2.5.dp, AccentGold, RoundedCornerShape(25.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
            letterSpacing = 1.sp
        )
    }
}
