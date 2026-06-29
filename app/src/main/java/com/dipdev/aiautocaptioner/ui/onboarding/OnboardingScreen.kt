package com.dipdev.aiautocaptioner.ui.onboarding

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

import java.util.concurrent.TimeUnit
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.dipdev.aiautocaptioner.AppLinks
import com.dipdev.aiautocaptioner.R
import com.dipdev.aiautocaptioner.ui.theme.AutoCaptionerTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

data class OnboardingPage(
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    @DrawableRes val screenshotRes: Int
)

val pages = listOf(
    OnboardingPage(
        titleRes = R.string.onboarding_title_1,
        descriptionRes = R.string.onboarding_desc_1,
        screenshotRes = R.drawable.onboarding_screenshot_1
    ),
    OnboardingPage(
        titleRes = R.string.onboarding_title_2,
        descriptionRes = R.string.onboarding_desc_2,
        screenshotRes = R.drawable.onboarding_screenshot_2
    ),
    OnboardingPage(
        titleRes = R.string.onboarding_title_3,
        descriptionRes = R.string.onboarding_desc_3,
        screenshotRes = R.drawable.onboarding_screenshot_3
    )
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == pages.size - 1



    // Always render onboarding in dark mode — the ParticleWave background
    // and vignette gradients are designed for dark surfaces.
    AutoCaptionerTheme(useLightTheme = false) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Particle wave background animation
        ParticleWave(currentPage = pagerState.currentPage)

        // Bottom vignette for better text/button contrast
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .align(Alignment.BottomCenter)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.background.copy(alpha = 0.95f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { position ->
                PagerScreen(onBoardingPage = pages[position])
            }

            OnboardingBottomBar(
                pagerState = pagerState,
                isLastPage = isLastPage,
                onNextClick = {
                    if (isLastPage) {
                        viewModel.setEvent(OnboardingUiEvent.CompleteOnboarding)
                        onFinish()
                    } else {
                        scope.launch {
                            pagerState.animateScrollToPage(
                                page = pagerState.currentPage + 1,
                                animationSpec = spring(stiffness = Spring.StiffnessLow)
                            )
                        }
                    }
                }
            )
        }

        // Overlay top bar for Skip button
        OnboardingTopBar(
            isLastPage = isLastPage,
            onSkipClick = {
                viewModel.setEvent(OnboardingUiEvent.CompleteOnboarding)
                onFinish()
            },
            modifier = Modifier.align(Alignment.TopCenter)
        )


    } // end Box
    } // end AutoCaptionerTheme
}

@Composable
fun OnboardingTopBar(
    isLastPage: Boolean,
    onSkipClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 32.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)
            .height(48.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedVisibility(
            visible = !isLastPage,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            TextButton(onClick = onSkipClick) {
                Text(
                    text = stringResource(R.string.onboarding_skip),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
fun PagerScreen(
    onBoardingPage: OnboardingPage,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Image at the very top, full width, ~55% of available height
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.55f)
        ) {
            Image(
                painter = painterResource(id = onBoardingPage.screenshotRes),
                contentDescription = null,
                contentScale = ContentScale.Crop, // Stretch to whole width, crop height
                modifier = Modifier.fillMaxSize(),
                alignment = Alignment.BottomCenter
            )

            // Top shadow (swapped from bottom)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.5f)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.background,
                                MaterialTheme.colorScheme.background.copy(alpha = 0.9f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // Bottom shadow (swapped from top)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.35f)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.background.copy(alpha = 0.85f)
                            )
                        )
                    )
            )

            // Brand Logo & Name
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 40.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                BrandHeroSection()
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Text Content at the bottom
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(onBoardingPage.titleRes),
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 38.sp,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(onBoardingPage.descriptionRes),
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
fun BrandHeroSection() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_img),
            contentDescription = "AutoCaptioner logo",
            modifier = Modifier.size(46.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "AutoCaptioner",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 32.sp
            )
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingBottomBar(
    pagerState: PagerState,
    isLastPage: Boolean,
    onNextClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Animated Indicators
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(pages.size) { iteration ->
                    val isCurrent = pagerState.currentPage == iteration
                    val color by animateColorAsState(
                        targetValue = if (isCurrent) MaterialTheme.colorScheme.primary
                                      else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                        label = "color"
                    )
                    val width by animateDpAsState(
                        targetValue = if (isCurrent) 32.dp else 10.dp,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "width"
                    )
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(color)
                            .height(10.dp)
                            .width(width)
                    )
                }
            }

            // Gradient + Glow CTA Button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                            )
                        )
                    )
                    .clickable(onClick = onNextClick)
                    .padding(horizontal = 28.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = isLastPage,
                    transitionSpec = {
                        (slideInVertically { height -> height } + fadeIn() togetherWith
                                slideOutVertically { height -> -height } + fadeOut())
                    },
                    label = "buttonText"
                ) { targetIsLast ->
                    Text(
                        text = if (targetIsLast) stringResource(R.string.onboarding_get_started)
                               else stringResource(R.string.onboarding_next),
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }

        // Legal Text that only takes space on the last page
        androidx.compose.animation.AnimatedVisibility(
            visible = isLastPage,
            enter = slideInVertically(initialOffsetY = { 20 }) + fadeIn(animationSpec = tween(500)),
            exit = fadeOut(animationSpec = tween(200)),
            modifier = Modifier.fillMaxWidth()
        ) {
            val context = LocalContext.current
            val linkStyle = SpanStyle(
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            val legalPrefix = stringResource(R.string.onboarding_legal_prefix)
            val termsConditions = stringResource(R.string.onboarding_terms_conditions)
            val legalAnd = stringResource(R.string.onboarding_legal_and)
            val privacyPolicy = stringResource(R.string.onboarding_privacy_policy)

            val annotatedString = buildAnnotatedString {
                append(legalPrefix)
                pushLink(LinkAnnotation.Clickable(
                    tag = "TERMS",
                    linkInteractionListener = {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, AppLinks.TERMS_OF_SERVICE.toUri()))
                        } catch (e: ActivityNotFoundException) {
                            Toast.makeText(context, "No browser installed", Toast.LENGTH_SHORT).show()
                        }
                    }
                ))
                withStyle(linkStyle) { append(termsConditions) }
                pop()
                append(legalAnd)
                pushLink(LinkAnnotation.Clickable(
                    tag = "PRIVACY",
                    linkInteractionListener = {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, AppLinks.PRIVACY_POLICY.toUri()))
                        } catch (e: ActivityNotFoundException) {
                            Toast.makeText(context, "No browser installed", Toast.LENGTH_SHORT).show()
                        }
                    }
                ))
                withStyle(linkStyle) { append(privacyPolicy) }
                pop()
            }

            Text(
                text = annotatedString,
                style = TextStyle(
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp)
            )
        }
    }
}
