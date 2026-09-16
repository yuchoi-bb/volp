package com.volp.travelbudget.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

/**
 * 태블릿처럼 넓은 화면에서 화면을 어떻게 놓을지.
 *
 * 폰에 맞춘 화면을 태블릿에서 그대로 늘리면 한 줄이 손가락 한 뼘을 넘어간다. 눈이 줄 끝에서
 * 다음 줄 앞으로 돌아오지 못해 읽기 어려워지고, 가계부처럼 왼쪽에 이름 오른쪽에 금액을 두는
 * 줄은 둘이 너무 멀어져 짝이 안 보인다. 그래서 내용은 읽을 만한 폭으로 모으고, 남는 자리는
 * 비워 둔다.
 */

/** 이 너비부터 태블릿으로 본다. 폰을 가로로 눕힌 것도 여기 든다. */
private const val WIDE_DP = 600

/** 한 줄이 이보다 길어지면 읽기 어렵다. */
private val READABLE_MAX = 720.dp

@Composable
fun isWideScreen(): Boolean = LocalConfiguration.current.screenWidthDp >= WIDE_DP

/** 넓은 화면에서 내용을 읽을 만한 폭으로 모아 가운데 둔다. 좁은 화면에서는 아무 일도 하지 않는다. */
@Composable
fun ReadableContent(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = READABLE_MAX).fillMaxHeight()) { content() }
    }
}
