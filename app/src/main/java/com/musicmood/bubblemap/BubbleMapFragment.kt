
package com.musicmood.bubblemap

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import com.musicmood.R
import kotlinx.coroutines.launch

class BubbleMapFragment : Fragment(R.layout.fragment_bubble_map) {

    private val vm: BubbleMapViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val map = view.findViewById<BubbleMapView>(R.id.bubbleMap)
        val counter = view.findViewById<TextView>(R.id.bubbleCounter)
        val hint = view.findViewById<TextView>(R.id.bubbleHint)

        map.setOnBubbleTap { b ->
            Snackbar.make(view, "${b.title} — ${b.artist} • ${b.mood}",
                Snackbar.LENGTH_SHORT).show()
        }

        vm.load()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.bubbles.collect { items ->
                    map.setBubbles(items)
                    counter.text = "${items.size} brani analizzati"
                    hint.visibility =
                        if (items.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }
}
