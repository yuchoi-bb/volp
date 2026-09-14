package com.volp.travelbudget.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.volp.travelbudget.VolpApplication

/**
 * DI 라이브러리 없이 ViewModel에 의존성을 넘기기 위한 도우미.
 */
inline fun <reified VM : ViewModel> volpViewModelFactory(
    crossinline create: (VolpApplication) -> VM,
): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        create(volpApplication())
    }
}

fun CreationExtras.volpApplication(): VolpApplication =
    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as VolpApplication
