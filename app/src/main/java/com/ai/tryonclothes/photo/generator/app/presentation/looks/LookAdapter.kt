package com.ai.tryonclothes.photo.generator.app.presentation.looks

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.LayoutRes
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import coil3.request.crossfade
import com.ai.tryonclothes.photo.generator.app.R
import com.ai.tryonclothes.photo.generator.app.domain.model.SavedLook
import java.io.File

/**
 * Shared by My Looks (grid) and Home (horizontal strip); only the item layout differs.
 * Images are decoded lazily as rows scroll into view, so a large collection stays cheap.
 */
class LookAdapter(
    @LayoutRes private val itemLayout: Int,
    private val onClick: (SavedLook) -> Unit,
) : ListAdapter<SavedLook, LookAdapter.Holder>(Diff) {

    class Holder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.image)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(LayoutInflater.from(parent.context).inflate(itemLayout, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val look = getItem(position)
        holder.image.load(File(look.imagePath)) { crossfade(true) }
        holder.itemView.setOnClickListener { onClick(look) }
    }

    private object Diff : DiffUtil.ItemCallback<SavedLook>() {
        override fun areItemsTheSame(old: SavedLook, new: SavedLook) = old.id == new.id
        override fun areContentsTheSame(old: SavedLook, new: SavedLook) = old == new
    }
}
