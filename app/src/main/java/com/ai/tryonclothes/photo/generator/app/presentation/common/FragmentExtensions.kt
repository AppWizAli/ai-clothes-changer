package com.ai.tryonclothes.photo.generator.app.presentation.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ai.tryonclothes.photo.generator.app.AppContainer
import com.ai.tryonclothes.photo.generator.app.TryOnApp
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

val Fragment.appContainer: AppContainer get() = (requireActivity().application as TryOnApp).container

/** Creates a ViewModel wired with the app container and a [SavedStateHandle] (which also carries fragment arguments). */
inline fun <reified VM : ViewModel> Fragment.appViewModels(
    crossinline create: CreationExtras.(AppContainer, SavedStateHandle) -> VM,
) = viewModels<VM> {
    viewModelFactory { initializer { create(appContainer, createSavedStateHandle()) } }
}

/** Runs [block] only while the view is at least STARTED, so collection stops in the background. */
fun Fragment.launchWhenStarted(block: suspend CoroutineScope.() -> Unit) {
    viewLifecycleOwner.lifecycleScope.launch {
        viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED, block)
    }
}

fun View.show(visible: Boolean) {
    visibility = if (visible) View.VISIBLE else View.GONE
}

/** Opens the system share sheet for an image that lives in this app's FileProvider-exposed folders. */
fun Context.shareImage(file: File, chooserTitle: String) {
    val uri: Uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
}
