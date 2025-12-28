package com.example.nfc_reader_01.ui.shutdown

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.nfc_reader_01.NfcInteractionListener
import com.example.nfc_reader_01.NfcState
import com.example.nfc_reader_01.SharedNfcViewModel
import com.example.nfc_reader_01.databinding.FragmentShutdownBinding
import com.example.nfc_reader_01.R
import kotlinx.coroutines.delay // Importante para el temporizador
import kotlinx.coroutines.launch

class ShutdownFragment : Fragment() {

    companion object {
        const val COMMAND_SHUTDOWN = 0x10.toByte()
        private const val TAG = "ShutdownFragment"

        // Colores
        private const val COLOR_DEFAULT_RED = "#D32F2F"
        private const val COLOR_PROCESSING_GRAY = "#BDBDBD"
        private const val COLOR_SUCCESS_GREEN = "#4CAF50"
    }

    private val sharedNfcViewModel: SharedNfcViewModel by activityViewModels()
    private var _binding: FragmentShutdownBinding? = null
    private val binding get() = _binding!!

    private var listener: NfcInteractionListener? = null
    private var pulseAnimator: ObjectAnimator? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is NfcInteractionListener) {
            listener = context
        } else {
            Log.e(TAG, "$context debe implementar NfcInteractionListener")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentShutdownBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupLanguageButtons()
        setupShutdownLogic()
        startIconAnimation() // Inicia animación (estado inicial)
        setupObservers()
    }

    private fun setupLanguageButtons() {
        try {
            binding.btnLangEs.setOnClickListener { setAppLocale("es") }
            binding.btnLangEn.setOnClickListener { setAppLocale("en") }
            binding.btnLangZh.setOnClickListener { setAppLocale("zh-CN") }
        } catch (e: Exception) {
            Log.e(TAG, "Error configurando botones de idioma: ${e.message}")
        }
    }

    private fun setAppLocale(languageCode: String) {
        val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags(languageCode)
        AppCompatDelegate.setApplicationLocales(appLocale)
    }

    private fun setupShutdownLogic() {
        binding.btnExecuteShutdown.setOnClickListener {
            // 1. Estado PROCESANDO (Gris)
            setUiColors(COLOR_PROCESSING_GRAY)
            binding.btnExecuteShutdown.isEnabled = false

            sharedNfcViewModel.setConfigDataToWrite(null)
            listener?.requestNextCommand(COMMAND_SHUTDOWN)

            binding.tvStatus.text = getString(R.string.shutdown_status_sending)
            Toast.makeText(requireContext(), getString(R.string.toast_requesting_shutdown), Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedNfcViewModel.writeStatus.collect { state ->
                    when (state) {
                        is NfcState.Success -> {
                            // 2. Estado ÉXITO (Verde)
                            setUiColors(COLOR_SUCCESS_GREEN)
                            binding.tvStatus.text = "¡OK!"
                            binding.btnExecuteShutdown.isEnabled = false
                            stopIconAnimation()

                            // --- TEMPORIZADOR DE 3 SEGUNDOS ---
                            launch {
                                delay(3000) // Espera 3000ms (3 segundos)
                                resetUiToInitialState() // Vuelve al rojo
                            }
                        }
                        is NfcState.Error -> {
                            // Si falla, volvemos a ROJO inmediatamente
                            setUiColors(COLOR_DEFAULT_RED)
                            binding.btnExecuteShutdown.isEnabled = true
                            binding.tvStatus.text = getString(R.string.shutdown_status_ready)
                            startIconAnimation() // Aseguramos que palpite
                            Toast.makeText(requireContext(), "Error: ${state.errorMessage}", Toast.LENGTH_LONG).show()
                        }
                        is NfcState.Loading -> {
                            setUiColors(COLOR_PROCESSING_GRAY)
                        }
                        is NfcState.Idle -> { }
                    }
                }
            }
        }
    }

    /**
     * Devuelve la pantalla a su estado original: Rojo, habilitado y animado.
     */
    private fun resetUiToInitialState() {
        // Verificamos _binding por si el usuario salió de la pantalla durante los 3 seg
        if (_binding != null) {
            setUiColors(COLOR_DEFAULT_RED)
            binding.tvStatus.text = getString(R.string.shutdown_status_ready)
            binding.btnExecuteShutdown.isEnabled = true
            startIconAnimation() // Reactiva el latido
        }
    }

    private fun setUiColors(colorHex: String) {
        try {
            val color = Color.parseColor(colorHex)
            binding.ivShutdownIcon.setColorFilter(color)
            binding.btnExecuteShutdown.backgroundTintList = ColorStateList.valueOf(color)
        } catch (e: Exception) {
            Log.e(TAG, "Error cambiando colores UI: ${e.message}")
        }
    }

    private fun startIconAnimation() {
        // Si ya existe y está corriendo, no lo duplicamos
        if (pulseAnimator != null && pulseAnimator!!.isRunning) return

        val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 1.2f, 1.0f)
        val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 1.2f, 1.0f)

        pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(binding.ivShutdownIcon, scaleX, scaleY).apply {
            duration = 2000
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun stopIconAnimation() {
        pulseAnimator?.cancel()
        // Reseteamos escala por si se detuvo cuando estaba grande
        binding.ivShutdownIcon.scaleX = 1.0f
        binding.ivShutdownIcon.scaleY = 1.0f
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        pulseAnimator?.cancel()
        _binding = null
    }
}
