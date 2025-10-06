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

                        // 3. Mostrar un Toast claro (FIX: Usamos un String explícito en lugar de 'it' que era el objeto tagInfo)
                        Toast.makeText(
                            context,
                            "¡Etiqueta NFC conectada! Analizando datos.",
                            Toast.LENGTH_SHORT
                        ).show()

                    } else {
                        // Esto se ejecuta al inicio o si el ViewModel resetea el estado
                        binding.statusIcon.setImageResource(R.drawable.ic_nfc_scan_24)
                        binding.statusMessage.text = "Aproxime una etiqueta NFC para empezar a leer."

                        // NOTA: Eliminamos el Toast aquí ya que el mensaje de la UI es suficiente
                    }
                }
            }
        }

        // Observa writeStatus (SharedFlow).
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // 'status' es un String (no nullable)
                sharedNfcViewModel.writeStatus.collect { status ->
                    // El ViewModel asegura que 'status' es un mensaje de usuario amigable.
                    // Usamos un Toast más largo para asegurar que se lea la información de estado de escritura.
                    Toast.makeText(context, status, Toast.LENGTH_LONG).show()

                    // Eliminamos: sharedNfcViewModel.setWriteStatus(null)
                    // Ya no es necesario porque SharedFlow emite el evento solo una vez.
                }
            }
        }
    }
}