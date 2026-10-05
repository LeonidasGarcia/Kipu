package com.kipu.app.feature.auth.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kipu.app.ui.motion.rememberReducedMotionEnabled
import com.kipu.app.ui.theme.KipuEasingTokens
import com.kipu.app.ui.theme.KipuMotionTokens
import com.kipu.app.ui.theme.rememberCalmEmeraldColors
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    initialPage: Int = 0,
    onPageChanged: (Int) -> Unit = {},
    onComplete: () -> Unit,
    saving: Boolean = false,
    error: String? = null,
    modifier: Modifier = Modifier,
) {
    val pager = rememberPagerState(initialPage.coerceIn(0, 2)) { 3 }
    val scope = rememberCoroutineScope()
    val reducedMotion = rememberReducedMotionEnabled()
    val colors = rememberCalmEmeraldColors()
    val enlargedText = LocalDensity.current.fontScale > 1.3f
    fun goTo(page: Int) {
        scope.launch {
            if (reducedMotion) pager.scrollToPage(page)
            else pager.animateScrollToPage(page,
                animationSpec = tween(KipuMotionTokens.FastMillis, easing = KipuEasingTokens.Standard))
        }
    }
    BackHandler(pager.currentPage > 0) { goTo(pager.currentPage - 1) }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.distinctUntilChanged().collect(onPageChanged)
    }
    Box(modifier.fillMaxSize().background(colors.background).safeDrawingPadding(), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = AuthLayout.MaxWidth).fillMaxSize().padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (pager.currentPage > 0) {
                    IconButton({ goTo(pager.currentPage - 1) }, Modifier.width(if (enlargedText) 48.dp else 80.dp), enabled = !saving) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Página anterior", tint = colors.primaryDeep)
                    }
                }
                Text("Kipu", style = MaterialTheme.typography.titleLarge,
                    color = colors.primaryDeep, modifier = Modifier.weight(1f),
                    textAlign = if (pager.currentPage > 0) TextAlign.Center else TextAlign.Start)
                TextButton(onComplete, Modifier.width(if (enlargedText) 112.dp else 80.dp), enabled = !saving) {
                    Text("Omitir", color = colors.primaryDeep)
                }
            }
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth().testTag("auth-intro-pager"), userScrollEnabled = !saving) { page ->
                OnboardingPage(page)
            }
            OnboardingIndicator(pager.currentPage)
            AuthErrorMessage(error)
            Spacer(Modifier.height(16.dp))
            AuthPrimaryButton(if (pager.currentPage == 2) "Empezar" else "Siguiente",
                { if (pager.currentPage == 2) onComplete() else goTo(pager.currentPage + 1) },
                enabled = !saving && !pager.isScrollInProgress, loading = saving,
                icon = Icons.AutoMirrored.Outlined.ArrowForward)
            if (pager.currentPage == 2) {
                Spacer(Modifier.height(12.dp))
                TextButton(onComplete, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !saving) {
                    Text("Ya tengo una cuenta", color = colors.primaryDeep)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
internal fun OnboardingIndicator(page: Int) {
    val colors = rememberCalmEmeraldColors()
    val duration = if (rememberReducedMotionEnabled()) 0 else KipuMotionTokens.QuickMillis
    Row(Modifier.fillMaxWidth().padding(top = 16.dp).semantics {
        contentDescription = "Página ${page + 1} de 3"
    }, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            val width by animateDpAsState(if (page == index) 24.dp else 7.dp,
                tween(duration), label = "introIndicator$index")
            Box(Modifier.padding(horizontal = 4.dp).size(width, 7.dp)
                .background(if (index == page) colors.primaryDeep else colors.borderSubtle, CircleShape))
        }
    }
}

@Composable
private fun OnboardingPage(page: Int) {
    if (page == 2) {
        OnboardingPrivacyPage()
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.SpaceEvenly) {
        Spacer(Modifier.height(32.dp))
        when (page) {
            0 -> OnboardingMoneyIllustration()
            1 -> OnboardingLedgerIllustration()
        }
        Spacer(Modifier.height(32.dp))
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            if (page < 2) {
                Text(if (page == 0) "Tu dinero, en un solo lugar" else "Menos registro. Más control.",
                    style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(12.dp))
                Text(if (page == 0) "Revisa cuentas, efectivo, tarjetas y movimientos sin perder de vista cuánto tienes realmente disponible."
                    else "Registra gastos, ingresos y transferencias rápidamente. Kipu también puede ayudarte a detectar movimientos desde fuentes compatibles.",
                    style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun OnboardingPrivacyPage() {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val availableHeight = maxHeight
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .heightIn(min = availableHeight).padding(top = 24.dp, bottom = 72.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            OnboardingPrivacyIllustration()
            Spacer(Modifier.height(20.dp))
            Text("Tus finanzas son tuyas", style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp))
            Text("Kipu organiza la información que registras, pero no mueve tu dinero ni necesita tus contraseñas bancarias.",
                style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp))
        }
    }
}
