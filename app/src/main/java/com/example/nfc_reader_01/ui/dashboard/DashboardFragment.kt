package com.example.nfc_reader_01.ui.dashboard

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.nfc_reader_01.SharedNfcViewModel
import com.example.nfc_reader_01.databinding.FragmentDashboardBinding
import org.json.JSONException
import org.json.JSONObject

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private lateinit var sharedNfcViewModel: SharedNfcViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sharedNfcViewModel = ViewModelProvider(requireActivity()).get(SharedNfcViewModel::class.java)
        setupObservers()
    }

    private fun setupObservers() {
        // Observar datos del registro 1 (Identidad)
        sharedNfcViewModel.identityDataJson.observe(viewLifecycleOwner) { jsonString ->
            try {
                if (jsonString != null) {
                    val jsonObject = JSONObject(jsonString)
                    val serialNumber = jsonObject.optString("serialNumber", "N/A")
                    val firmwareVersion = jsonObject.optString("firmwareVersion", "N/A")
                    val lastConfigDate = jsonObject.optString("lastConfigurationDate", "N/A")
                    // Rellenar los nuevos TextInputEditText
                    binding.editTextSerialNumber.setText(serialNumber)
                    binding.editTextFirmwareVersion.setText(firmwareVersion)
                    binding.editTextLastConfigDate.setText(lastConfigDate)
                } else {
                    // Limpiar los campos si no hay datos
                    binding.editTextSerialNumber.setText("")
                    binding.editTextFirmwareVersion.setText("")
                    binding.editTextLastConfigDate.setText("")
                }
            } catch (e: JSONException) {
                Log.e("DashboardFragment", "Error al parsear el JSON de identidad", e)
                Toast.makeText(context, "Error en el formato del JSON de identidad.", Toast.LENGTH_SHORT).show()
            }
        }

        // Observar datos del registro 2 (Proceso)
        sharedNfcViewModel.processDataJson.observe(viewLifecycleOwner) { jsonString ->
            try {
                if (jsonString != null) {
                    val jsonObject = JSONObject(jsonString)
                    val volumen = jsonObject.optInt("volumen", -1).toString()
                    val caudal = jsonObject.optInt("caudal", -1).toString()
                    val temperatura = jsonObject.optInt("temperatura", -1).toString()
                    val status = jsonObject.optString("status", "N/A")
                    val timestamp = jsonObject.optString("timestamp", "N/A")
                    // Rellenar los nuevos TextInputEditText
                    binding.editTextVolumen.setText(volumen)
                    binding.editTextCaudal.setText(caudal)
                    binding.editTextTemperatura.setText(temperatura)
                    binding.editTextStatus.setText(status)
                    binding.editTextTimestamp.setText(timestamp)
                } else {
                    // Limpiar los campos si no hay datos
                    binding.editTextVolumen.setText("")
                    binding.editTextCaudal.setText("")
                    binding.editTextTemperatura.setText("")
                    binding.editTextStatus.setText("")
                    binding.editTextTimestamp.setText("")
                }
            } catch (e: JSONException) {
                Log.e("DashboardFragment", "Error al parsear el JSON de proceso", e)
                Toast.makeText(context, "Error en el formato del JSON de proceso.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
