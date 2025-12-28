package com.example.nfc_reader_01.ui.home

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.nfc_reader_01.NfcInteractionListener
import com.example.nfc_reader_01.R
import com.example.nfc_reader_01.SharedNfcViewModel
import com.example.nfc_reader_01.databinding.FragmentHomeBinding
import com.example.nfc_reader_01.NfcState // IMPORTANTE: Importar la clase de estado
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    // Usar by activityViewModels() para inyectar y obtener la instancia compartida
    private val sharedNfcViewModel: SharedNfcViewModel by activityViewModels()

    private var listener: NfcInteractionListener? = null // Referencia a la Activity (Listener)

    override fun onAttach(context: Context) {
        super.onAttach(context)
        // 1. Asegurarse de que la Activity implemente la interfaz
        if (context is NfcInteractionListener) {
            listener = context
        } else {
            // Se usa Log.wtf para errores críticos de configuración
            Log.wtf("HomeFragment", "$context debe implementar NfcInteractionListener")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        setupObservers()
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDetach() {
        super.onDetach()
        listener = null // Limpiar la referencia para evitar pérdidas de memoria
    }

    private fun setupObservers() {
        // --- Observa nfcTagInfo (usando collect) ---
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedNfcViewModel.nfcTagInfo.collect { tagInfo ->
                    if (tagInfo != null) {
                        // 1. Notificar a la Activity para navegar
                        listener?.navigateToDashboard()

                        // 2. Actualizar la UI
                        binding.statusIcon.setImageResource(R.drawable.ic_nfc_connected_24)
                        binding.statusMessage.text = "¡TAG detectado! Navegando a la información..."

                        // 3. Mostrar un Toast claro
                        Toast.makeText(
                            context,
                            "¡Etiqueta NFC conectada! Analizando datos.",
                            Toast.LENGTH_SHORT
                        ).show()

                    } else {
                        // Esto se ejecuta al inicio o si el ViewModel resetea el estado
                        binding.statusIcon.setImageResource(R.drawable.ic_nfc_scan_24)
                        binding.statusMessage.text = "Aproxime una etiqueta NFC para empezar a leer."
                    }
                }
            }
        }

        // --- CORRECCIÓN AQUÍ: Observa writeStatus como NfcState ---
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedNfcViewModel.writeStatus.collect { state ->
                    // Usamos 'when' para manejar el objeto NfcState
                    when (state) {
                        is NfcState.Success -> {
                            // Opcional: Mostrar mensaje de éxito si es relevante en el Home
                            // Toast.makeText(context, "Operación Exitosa", Toast.LENGTH_SHORT).show()
                        }
                        is NfcState.Error -> {
                            // Extraemos el mensaje de error del objeto
                            Toast.makeText(context, state.errorMessage, Toast.LENGTH_LONG).show()
                        }
                        is NfcState.Loading -> {
                            // Opcional: UI de carga
                        }
                        is NfcState.Idle -> {
                            // Nada que hacer
                        }
                    }
                }
            }
        }
    }
}
