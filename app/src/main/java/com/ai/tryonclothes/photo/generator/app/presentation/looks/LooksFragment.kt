package com.ai.tryonclothes.photo.generator.app.presentation.looks

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.ai.tryonclothes.photo.generator.app.R
import com.ai.tryonclothes.photo.generator.app.databinding.FragmentLooksBinding
import com.ai.tryonclothes.photo.generator.app.presentation.common.appViewModels
import com.ai.tryonclothes.photo.generator.app.presentation.common.launchWhenStarted
import com.ai.tryonclothes.photo.generator.app.presentation.common.show

class LooksFragment : Fragment(R.layout.fragment_looks) {
    private val viewModel: LooksViewModel by appViewModels { app, _ -> LooksViewModel(app.looks) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentLooksBinding.bind(view)
        val adapter = LookAdapter(R.layout.item_look) { look -> findNavController().navigate(R.id.resultFragment, bundleOf("lookId" to look.id)) }
        binding.list.layoutManager = GridLayoutManager(requireContext(), resources.getInteger(R.integer.looks_columns))
        binding.list.adapter = adapter
        binding.list.setHasFixedSize(true)

        launchWhenStarted {
            viewModel.looks.collect { looks ->
                adapter.submitList(looks.orEmpty())
                binding.empty.show(looks != null && looks.isEmpty())
                binding.list.show(!looks.isNullOrEmpty())
            }
        }
    }
}
