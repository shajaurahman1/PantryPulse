package com.pantrypulse.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pantrypulse.app.AppContainer
import com.pantrypulse.app.PantryPulseApp

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as PantryPulseApp).container

/** Creates a ViewModel that receives the app container. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    key: String? = null,
    owner: ViewModelStoreOwner = checkNotNull(LocalViewModelStoreOwner.current),
    crossinline create: (AppContainer) -> VM,
): VM {
    val container = appContainer()
    return viewModel(viewModelStoreOwner = owner, key = key, factory = viewModelFactory { initializer { create(container) } })
}
