
package com.example.nfc_reader_01.ui.notifications

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.nfc_reader_01.SharedNfcViewModel
import com.example.nfc_reader_01.databinding.FragmentNotificationsBinding

class NotificationsFragment : Fragment() {

    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!
    private lateinit var sharedNfcViewModel: SharedNfcViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        sharedNfcViewModel = ViewModelProvider(requireActivity()).get(SharedNfcViewModel::class.java)

        // The error is fixed here. 'binding.textNotifications' now correctly references the TextView from the XML.
        sharedNfcViewModel.nfcDataString.observe(viewLifecycleOwner) { data ->
            binding.textNotifications.text = data
        }

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
