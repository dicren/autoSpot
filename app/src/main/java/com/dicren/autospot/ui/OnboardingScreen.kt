package com.dicren.autospot.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Shortcut
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dicren.autospot.R
import kotlinx.coroutines.launch

data class OnboardingPage(
    val icon: ImageVector,
    val titleRes: Int,
    val descriptionRes: Int,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pages =
        listOf(
            OnboardingPage(
                icon = Icons.Default.DirectionsCar,
                titleRes = R.string.onboarding_welcome_title,
                descriptionRes = R.string.onboarding_welcome_description,
            ),
            OnboardingPage(
                icon = Icons.Default.Bluetooth,
                titleRes = R.string.onboarding_bluetooth_title,
                descriptionRes = R.string.onboarding_bluetooth_description,
            ),
            OnboardingPage(
                icon = Icons.Default.Map,
                titleRes = R.string.onboarding_auto_save_title,
                descriptionRes = R.string.onboarding_auto_save_description,
            ),
            OnboardingPage(
                icon = Icons.Default.Navigation,
                titleRes = R.string.onboarding_navigate_title,
                descriptionRes = R.string.onboarding_navigate_description,
            ),
            OnboardingPage(
                icon = Icons.Default.History,
                titleRes = R.string.onboarding_history_title,
                descriptionRes = R.string.onboarding_history_description,
            ),
            OnboardingPage(
                icon = Icons.Default.Share,
                titleRes = R.string.onboarding_share_title,
                descriptionRes = R.string.onboarding_share_description,
            ),
            OnboardingPage(
                icon = Icons.AutoMirrored.Filled.Shortcut,
                titleRes = R.string.onboarding_shortcut_title,
                descriptionRes = R.string.onboarding_shortcut_description,
            ),
        )

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                // safeDrawing (not statusBars): with three-button navigation the Next/Start
                // button was ending up behind the navigation bar on Android 12.
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(24.dp),
    ) {
        // Skip button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            if (pagerState.currentPage < pages.size - 1) {
                TextButton(onClick = onFinish) {
                    Text(stringResource(R.string.onboarding_skip))
                }
            }
        }

        // Pager content
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
        ) { page ->
            OnboardingPageContent(
                page = pages[page],
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Page indicators
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            pages.forEachIndexed { index, _ ->
                val isSelected = pagerState.currentPage == index
                val color by animateColorAsState(
                    targetValue =
                        if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                    label = "indicator_color",
                )
                Box(
                    modifier =
                        Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (isSelected) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(color),
                )
            }
        }

        // Navigation buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (pagerState.currentPage > 0) {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                        }
                    },
                ) {
                    Text(stringResource(R.string.onboarding_back))
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            if (pagerState.currentPage < pages.size - 1) {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    },
                ) {
                    Text(stringResource(R.string.onboarding_next))
                }
            } else {
                Button(onClick = onFinish) {
                    Text(stringResource(R.string.onboarding_start))
                }
            }
        }
    }
}

@Composable
private fun OnboardingPageContent(
    page: OnboardingPage,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = page.icon,
            contentDescription = null,
            modifier = Modifier.size(120.dp),
            tint = MaterialTheme.colorScheme.primary,
        )

        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = stringResource(page.titleRes),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(page.descriptionRes),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}
