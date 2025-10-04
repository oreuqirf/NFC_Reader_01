package com.example.nfc_reader_01.ui.notifications

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.nfc_reader_01.SharedNfcViewModel
import com.example.nfc_reader_01.databinding.FragmentNotificationsBinding

/**
 * Fragmento que muestra el historial de comandos y respuestas (protocol logs) del NFC.
 */
class NotificationsFragment : Fragment() {

    private var _binding: FragmentNotificationsBinding? = null
    // Esto asegura que el binding solo se usa entre onCreateView y onDestroyView
    private val binding get() = _binding!!

    // El ViewModel se comparte a nivel de Activity para acceder a los logs de protocolo.
    private lateinit var sharedNfcViewModel: SharedNfcViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inicializa el ViewModel compartido, vinculado a la Activity.
        sharedNfcViewModel = ViewModelProvider(requireActivity()).get(SharedNfcViewModel::class.java)

        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // 1. Configurar la observación de los logs
        setupLogObserver()

        // 2. Configurar el botón de limpiar logs
        binding.buttonClearLogs.setOnClickListener {
            sharedNfcViewModel.clearProtocolLogs()
        }

        return root
    }

    private fun setupLogObserver() {
        // Usamos 'protocolLogs' (el LiveData del ViewModel)
        sharedNfcViewModel.protocolLogs.observe(viewLifecycleOwner) { logs ->

            // Muestra los logs en orden inverso para que los mensajes más nuevos aparezcan arriba.
            val reversedLogs = logs.reversed()

            // Referenciamos el TextView correctamente como 'textViewProtocolLogs'
            binding.textViewProtocolLogs.text = reversedLogs.joinToString("\n")
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Añadir un listener para asegurar que el ScrollView siempre muestra el log más reciente (al principio de la lista invertida).
        sharedNfcViewModel.protocolLogs.observe(viewLifecycleOwner) {
            // CORRECCIÓN: Accedemos al ScrollView (el elemento raíz) usando binding.root
            binding.root.post {
                // Scroll al inicio (top) del ScrollView
                binding.root.fullScroll(View.FOCUS_UP)
            }
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
