package com.example.riftboundtranslator

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import java.io.IOException

class CardAdapter(
    private val cardFiles: List<String>,
    private val onCardClick: (String) -> Unit
) : RecyclerView.Adapter<CardAdapter.CardViewHolder>() {

    class CardViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.card_image)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_card, parent, false)
        return CardViewHolder(view)
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        val cardFile = cardFiles[position]

        try {
            // Load image from assets
            val inputStream = holder.itemView.context.assets.open("english_cards/$cardFile")
            val bitmap = BitmapFactory.decodeStream(inputStream)
            holder.imageView.setImageBitmap(bitmap)
            inputStream.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }

        holder.itemView.setOnClickListener {
            onCardClick(cardFile)
        }
    }

    override fun getItemCount() = cardFiles.size
}