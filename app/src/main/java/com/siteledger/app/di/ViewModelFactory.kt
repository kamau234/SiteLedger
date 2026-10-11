package com.siteledger.app.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

/** Bridges manual DI (AppContainer) into Compose `viewModel()` calls. */
inline fun <reified VM : ViewModel> viewModelFactoryFor(noinline create: () -> VM): ViewModelProvider.Factory =
    viewModelFactory { initializer { create() } }
