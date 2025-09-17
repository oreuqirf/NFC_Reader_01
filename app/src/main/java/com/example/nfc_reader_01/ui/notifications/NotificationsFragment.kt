package com.example.nfc_reader_01.ui.notifications

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Get the shared ViewModel
        sharedNfcViewModel = ViewModelProvider(requireActivity()).get(SharedNfcViewModel::class.java)

        // Observe the LiveData from the ViewModel
        sharedNfcViewModel.identityDataJson.observe(viewLifecycleOwner) { json ->
            updateDisplay()
        }
        sharedNfcViewModel.processDataJson.observe(viewLifecycleOwner) { json ->
            updateDisplay()
        }
        sharedNfcViewModel.configurationDataJson.observe(viewLifecycleOwner) { json ->
            updateDisplay()
        }
    }

    private fun updateDisplay() {
        // Get the current data from the ViewModel
        val identityJson = sharedNfcViewModel.identityDataJson.value
        val processJson = sharedNfcViewModel.processDataJson.value
        val configJson = sharedNfcViewModel.configurationDataJson.value

        // Build the string to display
        val displayString = StringBuilder()
        displayString.append("Estado de la Aplicación:\n\n")

        displayString.append("Estado de la etiqueta NFC:\n")
        displayString.append(" - NDEF Detectado: ${sharedNfcViewModel.isNdefTag.value ?: "N/A"}\n")
        displayString.append(" - NDEF Formateable: ${sharedNfcViewModel.isNdefFormatable.value ?: "N/A"}\n")
        displayString.append(" - NDEF Vacío: ${sharedNfcViewModel.isEmptyNdefTag.value ?: "N/A"}\n\n")

        displayString.append("Contenido de los registros NDEF:\n")
        displayString.append("---------------------------------------\n")

        displayString.append("Registro N°1 (Identidad):\n")
        displayString.append("${identityJson ?: "No hay datos de identidad"}\n\n")

        displayString.append("Registro N°2 (Proceso):\n")
        displayString.append("${processJson ?: "No hay datos de proceso"}\n\n")

        displayString.append("Registro N°3 (Configuración):\n")
        displayString.append("${configJson ?: "No hay datos de configuración"}\n")

        // Set the text of the TextView
        binding.textNotifications.text = displayString.toString()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
