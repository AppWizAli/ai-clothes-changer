package com.ai.tryonclothes.photo.generator.app.presentation.settings

import android.os.Bundle
import android.text.format.Formatter
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ai.tryonclothes.photo.generator.app.BuildConfig
import com.ai.tryonclothes.photo.generator.app.R
import com.ai.tryonclothes.photo.generator.app.core.AppConfig
import com.ai.tryonclothes.photo.generator.app.databinding.FragmentSettingsBinding
import com.ai.tryonclothes.photo.generator.app.presentation.common.appViewModels
import com.ai.tryonclothes.photo.generator.app.presentation.common.launchWhenStarted
import com.ai.tryonclothes.photo.generator.app.presentation.common.toMessage
import kotlinx.coroutines.launch

class SettingsFragment : Fragment(R.layout.fragment_settings) {
    private val viewModel: SettingsViewModel by appViewModels { app, _ ->
        SettingsViewModel(
            app.storage,
            app.looks,
            backendHost = AppConfig.backendUrl.takeIf { AppConfig.isBackendConfigured }?.let { android.net.Uri.parse(it).host ?: it },
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentSettingsBinding.bind(view)
        binding.version.text = getString(R.string.settings_version, BuildConfig.VERSION_NAME)
        binding.service.text = viewModel.backendHost?.let { getString(R.string.settings_service_ready, it) }
            ?: getString(R.string.settings_service_missing)

        binding.clearCache.setOnClickListener { viewModel.clearCache() }
        binding.deleteAll.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_delete_all_confirm_title)
                .setMessage(R.string.settings_delete_all_confirm_body)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete) { _, _ -> viewModel.deleteAllLooks() }
                .show()
        }

        launchWhenStarted {
            launch {
                viewModel.usage.collect { usage ->
                    if (usage == null) return@collect
                    val context = requireContext()
                    binding.storageLooks.text = getString(
                        R.string.settings_storage_looks,
                        Formatter.formatShortFileSize(context, usage.looksBytes),
                        usage.lookCount,
                    )
                    binding.storageCache.text = getString(R.string.settings_storage_cache, Formatter.formatShortFileSize(context, usage.cacheBytes))
                    binding.deleteAll.isEnabled = usage.lookCount > 0
                }
            }
            launch {
                viewModel.events.collect { event ->
                    val message = when (event) {
                        SettingsEvent.CacheCleared -> getString(R.string.settings_clear_cache_done)
                        SettingsEvent.LooksDeleted -> getString(R.string.settings_delete_all_done)
                        is SettingsEvent.Failed -> event.error.toMessage(requireContext())
                    }
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }
}
