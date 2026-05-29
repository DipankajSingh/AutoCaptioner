package com.dipdev.aiautocaptioner.ui.onboarding

import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import com.dipdev.aiautocaptioner.R
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.launch
import androidx.core.net.toUri

data class OnboardingPage(
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int
)

val pages = listOf(
    OnboardingPage(
        titleRes = R.string.onboarding_title_1,
        descriptionRes = R.string.onboarding_desc_1
    ),
    OnboardingPage(
        titleRes = R.string.onboarding_title_2,
        descriptionRes = R.string.onboarding_desc_2
    ),
    OnboardingPage(
        titleRes = R.string.onboarding_title_3,
        descriptionRes = R.string.onboarding_desc_3
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Skip Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.End
        ) {
            if (pagerState.currentPage < pages.size - 1) {
                TextButton(
                    onClick = {
                        viewModel.completeOnboarding()
                        onFinish()
                    }
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_skip),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(48.dp)) // Maintain height
            }
        }

        // Pager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { position ->
            PagerScreen(onBoardingPage = pages[position])
        }

        // Legal Text on last page
        if (pagerState.currentPage == pages.size - 1) {
            val context = LocalContext.current
            val linkStyle = SpanStyle(
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
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
                        context.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                com.dipdev.aiautocaptioner.AppLinks.TERMS_OF_SERVICE.toUri()
                            )
                        )
                    }
                ))
                withStyle(linkStyle) { append(termsConditions) }
                pop()
                append(legalAnd)
                pushLink(LinkAnnotation.Clickable(
                    tag = "PRIVACY",
                    linkInteractionListener = {
                        context.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                com.dipdev.aiautocaptioner.AppLinks.PRIVACY_POLICY.toUri()
                            )
                        )
                    }
                ))
                withStyle(linkStyle) { append(privacyPolicy) }
                pop()
            }

            Text(
                text = annotatedString,
                style = androidx.compose.ui.text.TextStyle(
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 16.dp)
            )
        }

        // Bottom Section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Indicators
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(pages.size) { iteration ->
                    val color = if (pagerState.currentPage == iteration) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                    val width = if (pagerState.currentPage == iteration) 24.dp else 8.dp
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(color)
                            .height(8.dp)
                            .width(width)
                    )
                }
            }

            // Next / Get Started Button
            Button(
                onClick = {
                    if (pagerState.currentPage == pages.size - 1) {
                        viewModel.completeOnboarding()
                        onFinish()
                    } else {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                },
                shape = RoundedCornerShape(24.dp), // Pill shape for premium feel
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(
                    text = if (pagerState.currentPage == pages.size - 1) stringResource(R.string.onboarding_get_started) else stringResource(R.string.onboarding_next),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun PagerScreen(onBoardingPage: OnboardingPage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(onBoardingPage.titleRes),
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 40.sp,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Start
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(onBoardingPage.descriptionRes),
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Start,
            lineHeight = 24.sp
        )
    }
}