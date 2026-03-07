package com.vscode.mobile.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.vscode.mobile.databinding.ItemCodespaceBinding
import com.vscode.mobile.model.Codespace

/**
 * RecyclerView adapter for the Codespaces list screen.
 */
class CodespacesAdapter(
    private val onOpen: (Codespace) -> Unit
) : ListAdapter<Codespace, CodespacesAdapter.ViewHolder>(DIFF_CALLBACK) {

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Codespace>() {
            override fun areItemsTheSame(old: Codespace, new: Codespace) = old.id == new.id
            override fun areContentsTheSame(old: Codespace, new: Codespace) = old == new
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCodespaceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemCodespaceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(codespace: Codespace) {
            binding.tvName.text = codespace.label
            binding.tvStatus.text = codespace.state
            binding.tvRepository.text = codespace.repository?.fullName ?: ""
            binding.tvBranch.text = codespace.gitStatus?.ref?.let { "⎇ $it" } ?: ""
            binding.btnOpen.setOnClickListener { onOpen(codespace) }
        }
    }
}
