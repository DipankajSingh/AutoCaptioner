package com.dipdev.aiautocaptioner.ui.onboarding

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.annotation.RawRes
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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.dipdev.aiautocaptioner.AppLinks
import com.dipdev.aiautocaptioner.R
import kotlinx.coroutines.launch

data class OnboardingPage(
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    @RawRes val animationRes: Int
)

val pages = listOf(
    OnboardingPage(
        titleRes = R.string.onboarding_title_1,
        descriptionRes = R.string.onboarding_desc_1,
        animationRes = R.raw.onboarding_1
    ),
    OnboardingPage(
        titleRes = R.string.onboarding_title_2,
        descriptionRes = R.string.onboarding_desc_2,
        animationRes = R.raw.onboarding_2
    ),
    OnboardingPage(
        titleRes = R.string.onboarding_title_3,
        descriptionRes = R.string.onboarding_desc_3,
        animationRes = R.raw.onboarding_3
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Particle wave background animation
        ParticleWave(currentPage = pagerState.currentPage)

        // Vignettes for better text/button contrast
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .align(Alignment.TopCenter)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                            Color.Transparent
                        )
                    )
                )
        )
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
            OnboardingTopBar(
                isLastPage = isLastPage,
                onSkipClick = {
                    viewModel.setEvent(OnboardingUiEvent.CompleteOnboarding)
                    onFinish()
                }
            )

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
    }
}

@Composable
fun OnboardingTopBar(
    isLastPage: Boolean,
    onSkipClick: () -> Unit
) {
    Row(
        modifier = Modifier
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
fun PagerScreen(onBoardingPage: OnboardingPage) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(onBoardingPage.animationRes))
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            LottieAnimation(
                composition = composition,
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp)
            )
        }

        // Glassmorphism Card for Text
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(onBoardingPage.titleRes),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 38.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(onBoardingPage.descriptionRes),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp
                )
            }
        }
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
            .padding(bottom = 48.dp)
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

            // Animated Button
            Button(
                onClick = onNextClick,
                shape = RoundedCornerShape(50),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp, vertical = 14.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
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
                        text = if (targetIsLast) stringResource(R.string.onboarding_get_started) else stringResource(R.string.onboarding_next),
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }

        // Legal Text space allocation
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp)
                .height(48.dp)
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = isLastPage,
                enter = slideInVertically(initialOffsetY = { 20 }) + fadeIn(animationSpec = tween(500)),
                exit = fadeOut(animationSpec = tween(200)),
                modifier = Modifier.align(Alignment.TopCenter)
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
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}