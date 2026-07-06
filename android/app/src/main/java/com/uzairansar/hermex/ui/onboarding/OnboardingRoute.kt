package com.uzairansar.hermex.ui.onboarding

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.HapticFeedbackConstants
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uzairansar.hermex.R
import com.uzairansar.hermex.data.repository.AuthRepository
import com.uzairansar.hermex.data.repository.AuthState
import com.uzairansar.hermex.ui.theme.LocalHermexHapticsEnabled
import kotlinx.coroutines.launch

private val OnboardingGold = Color(0xFFFFBD1A)
private val OnboardingGreen = Color(0xFF73EB8F)
private val OnboardingCoral = Color(0xFFFF7857)
private val OnboardingShape = RoundedCornerShape(8.dp)

@Composable
fun OnboardingRoute(
    authRepository: AuthRepository,
    onConnected: () -> Unit,
) {
    val viewModel: OnboardingViewModel = viewModel(factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return OnboardingViewModel(authRepository) as T
        }
    })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val initialPage = remember(authRepository, viewModel) {
        if (
            authRepository.state.value is AuthState.LoggedOut &&
            viewModel.state.value.serverUrl.isNotBlank()
        ) {
            OnboardingFlowPolicy.ConnectPageIndex
        } else {
            0
        }
    }
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { OnboardingFlowPolicy.PageCount },
    )
    val scope = rememberCoroutineScope()
    var lastSettledPage by remember { mutableIntStateOf(initialPage) }
    var hasCopiedAgentPrompt by remember { mutableStateOf(false) }
    var hasBypassedCopyReminder by remember { mutableStateOf(false) }
    var isShowingCopyReminder by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState, hasCopiedAgentPrompt, hasBypassedCopyReminder) {
        snapshotFlow { pagerState.settledPage }.collect { newPage ->
            val oldPage = lastSettledPage
            if (
                OnboardingFlowPolicy.shouldInterceptForwardNavigationFromAgentPrompt(
                    oldPage = oldPage,
                    newPage = newPage,
                    hasCopiedAgentPrompt = hasCopiedAgentPrompt,
                    hasBypassedCopyReminder = hasBypassedCopyReminder,
                )
            ) {
                isShowingCopyReminder = true
                pagerState.scrollToPage(oldPage)
            } else {
                lastSettledPage = newPage
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .imePadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                beyondViewportPageCount = 1,
                verticalAlignment = Alignment.Top,
            ) { page ->
                when (page) {
                    0 -> OnboardingWelcomePage()
                    1 -> OnboardingFeaturesPage()
                    2 -> OnboardingAgentPromptPage(
                        hasCopied = hasCopiedAgentPrompt,
                        onCopy = { hasCopiedAgentPrompt = true },
                    )
                    3 -> OnboardingTailscalePage()
                    else -> OnboardingConnectPage(
                        state = state,
                        onServerUrlChange = viewModel::updateServerUrl,
                        onPasswordChange = viewModel::updatePassword,
                        onCustomHeadersChange = viewModel::updateCustomHeadersText,
                    )
                }
            }

            OnboardingBottomBar(
                currentPage = pagerState.settledPage,
                isBusy = state.isBusy,
                canSubmitConnection = state.serverUrl.isNotBlank(),
                onPrimaryAction = {
                    val currentPage = pagerState.settledPage
                    if (
                        OnboardingFlowPolicy.shouldShowCopyReminder(
                            page = currentPage,
                            hasCopiedAgentPrompt = hasCopiedAgentPrompt,
                            hasBypassedCopyReminder = hasBypassedCopyReminder,
                        )
                    ) {
                        isShowingCopyReminder = true
                    } else if (currentPage < OnboardingFlowPolicy.ConnectPageIndex) {
                        scope.launch { pagerState.animateScrollToPage(currentPage + 1) }
                    }
                },
                onJumpToConnect = {
                    scope.launch {
                        pagerState.animateScrollToPage(OnboardingFlowPolicy.ConnectPageIndex)
                    }
                },
                onTestConnection = viewModel::testConnection,
                onConnect = { viewModel.connect(onConnected) },
            )
        }
    }

    if (isShowingCopyReminder) {
        AlertDialog(
            onDismissRequest = { isShowingCopyReminder = false },
            title = { Text("Copy the setup prompt first") },
            text = {
                Text(
                    "Copy the agent setup prompt on your desktop before continuing so Hermes Web UI and Tailscale are configured correctly.",
                )
            },
            dismissButton = {
                TextButton(onClick = { isShowingCopyReminder = false }) {
                    Text("Stay Here")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isShowingCopyReminder = false
                        hasBypassedCopyReminder = true
                        scope.launch {
                            pagerState.animateScrollToPage(
                                (pagerState.settledPage + 1)
                                    .coerceAtMost(OnboardingFlowPolicy.ConnectPageIndex),
                            )
                        }
                    },
                ) {
                    Text("Continue Anyway")
                }
            },
        )
    }
}

@Composable
private fun OnboardingWelcomePage() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(248.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                OnboardingGold.copy(alpha = 0.36f),
                                Color(0xFFFF9E14).copy(alpha = 0.14f),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )
            Image(
                painter = painterResource(R.drawable.hermex_app_icon),
                contentDescription = "Hermex",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(124.dp)
                    .clip(RoundedCornerShape(27.dp))
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            listOf(Color.White.copy(alpha = 0.25f), Color.White.copy(alpha = 0.04f)),
                        ),
                        shape = RoundedCornerShape(27.dp),
                    ),
            )
        }
        Spacer(Modifier.weight(1f))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Control your Hermes agent from Android.",
                color = Color.White,
                fontSize = 31.sp,
                lineHeight = 37.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Connect to your self-hosted Web UI over Tailscale.",
                color = Color.White.copy(alpha = 0.58f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeroBadge(
                    icon = R.drawable.ic_hermex_exclamation_triangle,
                    title = "Password protected",
                )
                HeroBadge(
                    icon = R.drawable.ic_hermex_git_branch,
                    title = "Tailscale ready",
                )
            }
        }
    }
}

@Composable
private fun HeroBadge(
    @DrawableRes icon: Int,
    title: String,
) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.68f)),
            modifier = Modifier.size(13.dp),
        )
        Text(
            text = title,
            color = Color.White.copy(alpha = 0.68f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun OnboardingFeaturesPage() {
    val features = remember {
        listOf(
            OnboardingFeature(
                R.drawable.ic_hermex_chat_bubbles,
                OnboardingGold,
                "Chat with your Hermes agent from Android",
                "Drive conversations from anywhere on your tailnet.",
            ),
            OnboardingFeature(
                R.drawable.ic_lucide_calendar_clock,
                Color(0xFF34C759),
                "Manage sessions, tasks, and files remotely",
                "Browse workspaces and stay on top of agent work.",
            ),
            OnboardingFeature(
                R.drawable.ic_hermex_waveform,
                Color(0xFFBF5AF2),
                "Voice input and mobile-friendly composer controls",
                "Compose naturally with touch-first controls.",
            ),
            OnboardingFeature(
                R.drawable.ic_hermex_exclamation_triangle,
                Color(0xFF64D2FF),
                "Review approvals and clarifications inline",
                "Respond to agent prompts without switching apps.",
            ),
            OnboardingFeature(
                R.drawable.ic_lucide_folder,
                Color(0xFFFF9F0A),
                "Self-hosted: your machine, your tailnet",
                "Your Hermes Web UI stays on hardware you control.",
            ),
        )
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp)
            .padding(top = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(36.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "What you get",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Your Hermes agent, reachable from Android over Tailscale.",
                color = Color.White.copy(alpha = 0.45f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            features.forEach { feature ->
                OnboardingFeatureRow(feature)
            }
        }
    }
}

private data class OnboardingFeature(
    @param:DrawableRes val icon: Int,
    val color: Color,
    val title: String,
    val subtitle: String,
)

@Composable
private fun OnboardingFeatureRow(feature: OnboardingFeature) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(feature.color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(feature.icon),
                contentDescription = null,
                colorFilter = ColorFilter.tint(feature.color),
                modifier = Modifier.size(16.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                feature.title,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                feature.subtitle,
                color = Color.White.copy(alpha = 0.4f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun OnboardingAgentPromptPage(
    hasCopied: Boolean,
    onCopy: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp)
            .padding(top = 24.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        OnboardingStepHeader(
            stepNumber = 1,
            icon = R.drawable.ic_hermex_ellipsis,
            title = "Set up Hermes Web UI",
            description = "Send this prompt to your Hermes Agent. It installs Hermes Web UI, enables password auth, and configures Tailscale access.",
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.055f))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = OnboardingFlowPolicy.AgentSetupPrompt,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .verticalScroll(rememberScrollState()),
                color = Color.White.copy(alpha = 0.82f),
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
            OnboardingActionButton(
                label = if (hasCopied) "Copied" else "Copy prompt",
                icon = if (hasCopied) R.drawable.ic_hermex_check_circle else R.drawable.ic_hermex_copy,
                primary = true,
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(
                        ClipData.newPlainText("Hermex agent setup", OnboardingFlowPolicy.AgentSetupPrompt),
                    )
                    onCopy()
                },
            )
        }
    }
}

@Composable
private fun OnboardingTailscalePage() {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp)
            .padding(top = 24.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        OnboardingStepHeader(
            stepNumber = 2,
            icon = R.drawable.ic_hermex_arrow_up,
            title = "Install Tailscale on Android",
            description = "Install Tailscale on your Android phone and sign into the same tailnet as your server. Your agent will reply with the exact URL to use on the next screen.",
        )
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            TailscaleStep("1", "Install Tailscale from Google Play.")
            TailscaleStep("2", "Sign in with the same account you used on your server.")
            TailscaleStep("3", "Keep Tailscale connected while using Hermex.")
            OnboardingActionButton(
                label = "Get Tailscale on Google Play",
                icon = R.drawable.ic_hermex_external_link,
                primary = false,
                contentColor = OnboardingGold,
                alignStart = true,
                onClick = { openTailscale(context) },
            )
        }
    }
}

@Composable
private fun OnboardingStepHeader(
    stepNumber: Int,
    @DrawableRes icon: Int,
    title: String,
    description: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .border(1.dp, OnboardingGold.copy(alpha = 0.35f), RoundedCornerShape(22.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                colorFilter = ColorFilter.tint(Color.White),
                modifier = Modifier.size(30.dp),
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "STEP $stepNumber",
                color = OnboardingGold.copy(alpha = 0.8f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                title,
                color = Color.White,
                fontSize = 28.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                description,
                color = Color.White.copy(alpha = 0.45f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun TailscaleStep(
    number: String,
    text: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(23.dp)
                .clip(CircleShape)
                .background(OnboardingGold),
            contentAlignment = Alignment.Center,
        ) {
            Text(number, color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            text,
            modifier = Modifier.weight(1f),
            color = Color.White.copy(alpha = 0.72f),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun OnboardingConnectPage(
    state: OnboardingUiState,
    onServerUrlChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onCustomHeadersChange: (String) -> Unit,
) {
    var isShowingAdvanced by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
            .padding(top = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Connect",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Enter the Tailscale URL your agent returned, for example http://<tailnet-ip>:8787.",
                color = Color.White.copy(alpha = 0.5f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OnboardingTextField(
                label = "Server URL",
                icon = R.drawable.ic_hermex_external_link,
                value = state.serverUrl,
                onValueChange = onServerUrlChange,
                placeholder = "http://100.64.0.1:8787",
                keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri),
            )
            if (state.isPasswordRequired) {
                OnboardingTextField(
                    label = "Password",
                    icon = R.drawable.ic_hermex_lock,
                    value = state.password,
                    onValueChange = onPasswordChange,
                    placeholder = "Server password",
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                    ),
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(OnboardingShape)
                    .clickable { isShowingAdvanced = !isShowingAdvanced }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_hermex_ellipsis),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.85f)),
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    "Advanced",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Image(
                    painter = painterResource(R.drawable.ic_hermex_chevron_down),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .size(14.dp)
                        .rotate(if (isShowingAdvanced) 180f else 0f),
                )
            }
            if (isShowingAdvanced) {
                Spacer(Modifier.height(10.dp))
                OnboardingTextField(
                    label = "Custom Headers",
                    icon = R.drawable.ic_hermex_git_branch,
                    value = state.customHeadersText,
                    onValueChange = onCustomHeadersChange,
                    placeholder = "CF-Access-Client-Id: ...",
                    singleLine = false,
                    minHeight = 92.dp,
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                    ),
                )
            }
        }
        if (state.isBusy) {
            OnboardingStatusBanner(
                text = "Checking server...",
                tint = Color.White.copy(alpha = 0.7f),
                showsProgress = true,
            )
        } else {
            state.message?.let { message ->
                OnboardingStatusBanner(
                    text = message,
                    tint = if (state.messageIsError) OnboardingCoral else OnboardingGreen,
                    icon = if (state.messageIsError) {
                        R.drawable.ic_hermex_exclamation_triangle
                    } else {
                        R.drawable.ic_hermex_check_circle
                    },
                )
            }
        }
    }
}

@Composable
private fun OnboardingTextField(
    label: String,
    @DrawableRes icon: Int,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minHeight: androidx.compose.ui.unit.Dp = 0.dp,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .clip(OnboardingShape)
            .background(Color.Black.copy(alpha = 0.24f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), OnboardingShape)
            .padding(horizontal = 13.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(OnboardingGold),
            modifier = Modifier.size(18.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                label,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = label },
                textStyle = textStyle,
                cursorBrush = SolidColor(OnboardingGold),
                singleLine = singleLine,
                visualTransformation = visualTransformation,
                keyboardOptions = keyboardOptions,
                decorationBox = { innerTextField ->
                    Box {
                        if (value.isEmpty()) {
                            Text(
                                placeholder,
                                color = Color.White.copy(alpha = 0.38f),
                                style = textStyle,
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }
    }
}

@Composable
private fun OnboardingStatusBanner(
    text: String,
    tint: Color,
    showsProgress: Boolean = false,
    @DrawableRes icon: Int = R.drawable.ic_hermex_refresh,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(OnboardingShape)
            .background(tint.copy(alpha = 0.12f))
            .border(1.dp, tint.copy(alpha = 0.18f), OnboardingShape)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (showsProgress) {
            CircularProgressIndicator(
                color = tint,
                strokeWidth = 2.dp,
                modifier = Modifier.size(16.dp),
            )
        } else {
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                colorFilter = ColorFilter.tint(tint),
                modifier = Modifier.size(17.dp),
            )
        }
        Text(
            text,
            color = Color.White.copy(alpha = 0.76f),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun OnboardingBottomBar(
    currentPage: Int,
    isBusy: Boolean,
    canSubmitConnection: Boolean,
    onPrimaryAction: () -> Unit,
    onJumpToConnect: () -> Unit,
    onTestConnection: () -> Unit,
    onConnect: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f), Color.Black),
                ),
            )
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(top = 12.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OnboardingPageIndicator(
            pageCount = OnboardingFlowPolicy.PageCount,
            currentPage = currentPage,
        )
        if (currentPage == OnboardingFlowPolicy.ConnectPageIndex) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OnboardingActionButton(
                    label = "Test Connection",
                    icon = R.drawable.ic_hermex_refresh,
                    primary = false,
                    enabled = !isBusy && canSubmitConnection,
                    modifier = Modifier.weight(1f),
                    onClick = onTestConnection,
                )
                OnboardingActionButton(
                    label = "Connect",
                    icon = R.drawable.ic_hermex_check_circle,
                    primary = true,
                    enabled = !isBusy && canSubmitConnection,
                    modifier = Modifier.weight(1f),
                    onClick = onConnect,
                )
            }
        } else {
            OnboardingActionButton(
                label = OnboardingFlowPolicy.primaryButtonTitle(currentPage),
                primary = true,
                onClick = onPrimaryAction,
            )
            if (OnboardingFlowPolicy.showsServerShortcut(currentPage)) {
                Text(
                    text = "Already have a server?",
                    color = Color.White.copy(alpha = 0.55f),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(OnboardingShape)
                        .clickable(onClick = onJumpToConnect)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun OnboardingPageIndicator(
    pageCount: Int,
    currentPage: Int,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.semantics {
            contentDescription = "Page ${currentPage + 1} of $pageCount"
        },
    ) {
        repeat(pageCount) { index ->
            Box(
                modifier = Modifier
                    .width(if (index == currentPage) 24.dp else 8.dp)
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (index == currentPage) Color.White else Color.White.copy(alpha = 0.18f),
                    )
                    .animateContentSize(),
            )
        }
    }
}

@Composable
private fun OnboardingActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    primary: Boolean,
    enabled: Boolean = true,
    contentColor: Color? = null,
    alignStart: Boolean = false,
) {
    val view = LocalView.current
    val hapticsEnabled = LocalHermexHapticsEnabled.current
    val enabledContent = contentColor ?: if (primary) Color.Black else Color.White.copy(alpha = 0.84f)
    Button(
        onClick = {
            if (hapticsEnabled) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            onClick()
        },
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .semantics { contentDescription = label },
        shape = OnboardingShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) OnboardingGold else Color.White.copy(alpha = 0.065f),
            contentColor = enabledContent,
            disabledContainerColor = if (primary) OnboardingGold.copy(alpha = 0.36f) else Color.White.copy(alpha = 0.035f),
            disabledContentColor = enabledContent.copy(alpha = 0.4f),
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (alignStart) Arrangement.Start else Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Image(
                    painter = painterResource(icon),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(
                        if (enabled) enabledContent else enabledContent.copy(alpha = 0.4f),
                    ),
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun openTailscale(context: Context) {
    val playStoreIntent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse(OnboardingFlowPolicy.TailscalePlayStoreUri),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(playStoreIntent)
    } catch (_: ActivityNotFoundException) {
        context.startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse(OnboardingFlowPolicy.TailscalePlayStoreFallbackUrl),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
