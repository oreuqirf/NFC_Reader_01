package com.example.nfc_reader_01.ui.configuration

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.nfc_reader_01.SharedNfcViewModel
import com.example.nfc_reader_01.databinding.FragmentConfigurationBinding
import org.json.JSONException
import org.json.JSONObject

class ConfigurationFragment : Fragment() {

    private var _binding: FragmentConfigurationBinding? = null
    private val binding get() = _binding!!

    private lateinit var sharedNfcViewModel: SharedNfcViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConfigurationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sharedNfcViewModel = ViewModelProvider(requireActivity()).get(SharedNfcViewModel::class.java)

        setupObservers()
        setupListeners()
    }

    private fun setupObservers() {
        // Observar datos del registro 3 (Configuración) y rellenar los EditTexts
        sharedNfcViewModel.configurationDataJson.observe(viewLifecycleOwner) { jsonString ->
            try {
                if (jsonString != null) {
                    val jsonObject = JSONObject(jsonString)
                    binding.editTextKMeter.setText(jsonObject.optDouble("K_meter", 0.0).toString())
                    binding.editTextTempRawLow.setText(jsonObject.optDouble("temp_raw_low", 0.0).toString())
                    binding.editTextTempRawHigh.setText(jsonObject.optDouble("temp_raw_high", 0.0).toString())
                    binding.editTextTempCalLow.setText(jsonObject.optDouble("temp_cal_low", 0.0).toString())
                    binding.editTextTempCalHigh.setText(jsonObject.optDouble("temp_cal_high", 0.0).toString())
                    // Referencias corregidas
                    binding.editTextFcQQ1.setText(jsonObject.optDouble("fc_q_q1", 0.0).toString())
                    binding.editTextFcQQ2.setText(jsonObject.optDouble("fc_q_q2", 0.0).toString())
                    binding.editTextFcQ035.setText(jsonObject.optDouble("fc_q_0_35", 0.0).toString())
                    binding.editTextFcQ100.setText(jsonObject.optDouble("fc_q_1_00", 0.0).toString())
                    binding.editTextFcQ10Lm.setText(jsonObject.optDouble("fc_q_10_0", 0.0).toString())
                    binding.editTextFcQQ3.setText(jsonObject.optDouble("fc_q_q3", 0.0).toString())
                }
            } catch (e: JSONException) {
                Log.e("ConfigurationFragment", "Error al parsear el JSON de configuración", e)
                Toast.makeText(context, "Error en el formato del JSON de configuración.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupListeners() {
        binding.saveConfigButton.setOnClickListener {
            val configData = JSONObject().apply {
                try {
                    put("K_meter", binding.editTextKMeter.text.toString().toDouble())
                    put("temp_raw_low", binding.editTextTempRawLow.text.toString().toDouble())
                    put("temp_raw_high", binding.editTextTempRawHigh.text.toString().toDouble())
                    put("temp_cal_low", binding.editTextTempCalLow.text.toString().toDouble())
                    put("temp_cal_high", binding.editTextTempCalHigh.text.toString().toDouble())
                    // Referencias corregidas
                    put("fc_q_q1", binding.editTextFcQQ1.text.toString().toDouble())
                    put("fc_q_q2", binding.editTextFcQQ2.text.toString().toDouble())
                    put("fc_q_0_35", binding.editTextFcQ035.text.toString().toDouble())
                    put("fc_q_1_00", binding.editTextFcQ100.text.toString().toDouble())
                    put("fc_q_10_0", binding.editTextFcQ10Lm.text.toString().toDouble())
                    put("fc_q_q3", binding.editTextFcQQ3.text.toString().toDouble())
                } catch (e: NumberFormatException) {
                    Toast.makeText(context, "Error: Ingrese valores numéricos válidos.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }
            val jsonString = configData.toString()
            sharedNfcViewModel.setWriteConfigRequest(jsonString)
            Toast.makeText(context, "Configuración guardada en memoria. Aproxime el TAG para escribirla.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

