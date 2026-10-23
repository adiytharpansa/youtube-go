package com.app.playtubelite.presentation.navigation

import androidx.compose.runtime.compositionLocalOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Sinyal "tab yang sedang aktif diketuk lagi" (nilai: route tab). Layar tab mendengarkannya untuk
 * menggulir ke paling atas, seperti perilaku aplikasi profesional.
 */
val LocalTabReselect = compositionLocalOf<SharedFlow<String>> { MutableSharedFlow() }
