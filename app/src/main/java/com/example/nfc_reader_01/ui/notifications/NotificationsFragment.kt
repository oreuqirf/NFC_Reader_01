package com.example.nfc_reader_01.ui.notifications

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import com.example.nfc_reader_01.SharedNfcViewModel
import com.example.nfc_reader_01.databinding.FragmentNotificationsBinding
import kotlinx.coroutines.launch

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
            // CORRECCIÓN: Usar clearProtocolLog() en lugar de clearProtocolLogs()
            sharedNfcViewModel.clearProtocolLog()
        }

        return root
    }

    /**
     * Configura el observador para el StateFlow de los logs del protocolo.
     */
    private fun setupLogObserver() {
        // CORRECCIÓN: Usar lifecycleScope.launch con repeatOnLifecycle para recolectar StateFlow
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // CORRECCIÓN: Usar protocolLog en lugar de protocolLogs
                sharedNfcViewModel.protocolLog.collect { logs ->

                    // Muestra los logs en orden inverso para que los mensajes más nuevos aparezcan arriba.
                    // Esto simula el comportamiento típico de una consola de logs.
                    val reversedLogs = logs.reversed()

                    // Referenciamos el TextView correctamente como 'textViewProtocolLogs'
                    binding.textViewProtocolLogs.text = reversedLogs.joinToString("\n")

                    // Llamamos a la función de scroll para mostrar siempre el más reciente
                    scrollToTop()
                }
            }
        }
    }

    /**
     * Asegura que el ScrollView siempre muestra el log más reciente (al principio de la lista invertida).
     */
    private fun scrollToTop() {
        // Esto asegura que el log más nuevo (que está en la parte superior) sea visible.
        // Se usa post para garantizar que el scroll ocurra después de que el texto haya sido dibujado.
        binding.root.post {
            // Scroll al inicio (top) del ScrollView
            binding.root.fullScroll(View.FOCUS_UP)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
