package com.example.nfc_reader_01.ui.home

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
// IMPORTANTE: Agregar estas importaciones para el idioma
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.nfc_reader_01.NfcInteractionListener
import com.example.nfc_reader_01.R
import com.example.nfc_reader_01.SharedNfcViewModel
import com.example.nfc_reader_01.databinding.FragmentHomeBinding
import com.example.nfc_reader_01.NfcState
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val sharedNfcViewModel: SharedNfcViewModel by activityViewModels()
    private var listener: NfcInteractionListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is NfcInteractionListener) {
            listener = context
        } else {
            Log.wtf("HomeFragment", "$context debe implementar NfcInteractionListener")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        // SetupObservers se mantiene aquí o en onViewCreated, es igual
        setupObservers()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupLanguageButtons()

        // --- AGREGAR ESTO ---
        // Asignamos la versión real definida en el build.gradle
        try {
            binding.textAppVersion.text = com.example.nfc_reader_01.BuildConfig.VERSION_NAME
        } catch (e: Exception) {
            Log.e("HomeFragment", "Error mostrando versión: ${e.message}")
        }
    }


    // --- NUEVO: Función para configurar los clicks de idioma ---
    private fun setupLanguageButtons() {
        try {
            binding.btnLangEs.setOnClickListener { setAppLocale("es") }
            binding.btnLangEn.setOnClickListener { setAppLocale("en") }
            binding.btnLangZh.setOnClickListener { setAppLocale("zh-CN") }
        } catch (e: Exception) {
            Log.e("HomeFragment", "Error configurando botones de idioma: ${e.message}")
        }
    }

    // --- NUEVO: Función para cambiar el locale ---
    private fun setAppLocale(languageCode: String) {
        val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags(languageCode)
        AppCompatDelegate.setApplicationLocales(appLocale)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                // ... dentro de setupObservers() ...

                sharedNfcViewModel.nfcTagInfo.collect { tagInfo ->
                    if (tagInfo != null) {
                        listener?.navigateToDashboard()

                        binding.statusIcon.setImageResource(R.drawable.ic_nfc_connected_24)

                        // CAMBIO 1: Usar referencia R.string
                        binding.statusMessage.setText(R.string.status_nfc_detected)

                        // CAMBIO 2: Usar getString()
                        Toast.makeText(
                            context,
                            getString(R.string.toast_nfc_connected),
                            Toast.LENGTH_SHORT
                        ).show()

                    } else {
                        binding.statusIcon.setImageResource(R.drawable.ic_nfc_scan_24)

                        // CAMBIO 3: Usar referencia R.string
                        binding.statusMessage.setText(R.string.status_nfc_idle)
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedNfcViewModel.writeStatus.collect { state ->
                    when (state) {
                        is NfcState.Success -> { }
                        is NfcState.Error -> {
                            Toast.makeText(context, state.errorMessage, Toast.LENGTH_LONG).show()
                        }
                        is NfcState.Loading -> { }
                        is NfcState.Idle -> { }
                    }
                }
            }
        }
    }
}
