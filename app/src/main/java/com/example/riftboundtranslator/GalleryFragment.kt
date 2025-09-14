package com.example.riftboundtranslator

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import java.io.IOException

class GalleryFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: CardAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_gallery, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.recycler_view)

        // Load card files from assets
        val cardFiles = loadCardFiles()

        // Set up adapter
        adapter = CardAdapter(cardFiles) { cardFile ->
            // Handle card click
            Toast.makeText(context, "Clicked: $cardFile", Toast.LENGTH_SHORT).show()
        }

        recyclerView.adapter = adapter
    }

    private fun loadCardFiles(): List<String> {
        return try {
            // List all files in the english_cards folder
            requireContext().assets.list("english_cards")?.toList() ?: emptyList()
        } catch (e: IOException) {
            e.printStackTrace()
            emptyList()
        }
    }
}