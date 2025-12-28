package com.example.nfc_reader_01.ui.notifications

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.nfc_reader_01.databinding.FragmentNotificationsBinding
import com.example.nfc_reader_01.utils.LogManager // Importar el nuevo LogManager
import kotlinx.coroutines.launch

class NotificationsFragment : Fragment() {

    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!

    // Usaremos un ViewModel para el Fragmento (si es necesario para UI state),
    // pero el log lo obtendremos del LogManager.

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        setupLogCollector()
        setupClearButton()

        return root
    }

    /**
     * Configura el collector del Kotlin Flow para recibir los logs.
     * Esto reemplaza a la función 'setupLogObserver' que usaba LiveData.
     */
    private fun setupLogCollector() {
        // Usamos viewLifecycleOwner.lifecycleScope para garantizar que la recolección
        // se detiene automáticamente cuando la vista del fragmento se destruye.
        viewLifecycleOwner.lifecycleScope.launch {
            // El fragmento colecta (consume) el flujo de logs.
            LogManager.protocolLog.collect { logs ->
                // logs es un String que ya contiene el historial completo formateado.
                if (logs.isNotEmpty()) {
                    binding.textViewProtocolLogs.text = logs
                    // Llamamos a la función de scroll para mostrar siempre el más reciente (al inicio)
                    // NOTA: Si los logs nuevos se añaden al inicio, quizás no necesites hacer scroll a menos
                    // que quieras asegurarte de que la parte superior de la vista siempre es visible.
                    scrollToTop()
                } else {
                    binding.textViewProtocolLogs.text = "No hay eventos registrados."
                }
            }
        }
    }

    // Función de ejemplo para el botón de limpiar logs
    private fun setupClearButton() {
        binding.buttonClearLogs.setOnClickListener {
            // Lanzar la corrutina para llamar a la función suspend de LogManager
            viewLifecycleOwner.lifecycleScope.launch {
                LogManager.clearLogs()
            }
        }
    }


    /**
     * Mueve el scroll del TextView al inicio para ver el log más reciente.
     */
    private fun scrollToTop() {
        // Simplemente mueve el cursor al inicio del texto (índice 0)
        binding.textViewProtocolLogs.scrollTo(0, 0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
