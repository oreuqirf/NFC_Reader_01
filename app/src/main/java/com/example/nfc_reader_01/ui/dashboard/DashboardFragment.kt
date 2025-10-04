package com.example.nfc_reader_01.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.nfc_reader_01.SharedNfcViewModel
import com.example.nfc_reader_01.databinding.FragmentDashboardBinding
// El NfcDataParser ya no es necesario aquí porque el ViewModel ahora proporciona los objetos estructurados.

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
        // Se inicializa el ViewModel compartido en el ámbito de la actividad.
        sharedNfcViewModel = ViewModelProvider(requireActivity()).get(SharedNfcViewModel::class.java)

        setupObservers()
        // Configurar los listeners, ahora incluyendo el nuevo Comando 0x05.
        setupListeners()
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Limpiar el binding para evitar pérdidas de memoria.
        _binding = null
    }

    /**
     * Configura los observadores para reaccionar a los cambios en los datos NFC.
     * Incluye observadores para 0x01, 0x02, 0x03 y el nuevo 0x05.
     */
    private fun setupObservers() {

        // --- Observa Datos de Identidad (0x81) ---
        sharedNfcViewModel.identityData.observe(viewLifecycleOwner) { identityData ->
            if (identityData != null) {

                Toast.makeText(context, "Datos Identidad Actualizados", Toast.LENGTH_SHORT).show()

                // Usar los campos ya parseados del objeto IdentityData
                binding.editTextSerialNumber.setText(identityData.deviceId.toString())
                binding.editTextFirmwareVersion.setText(identityData.firmwareVersion)
                binding.editTextLastConfigDate.setText(identityData.lastConfigurationDate)
            } else {
                // Mensaje si no hay datos de identidad disponibles.
                binding.editTextSerialNumber.setText("No hay datos")
                binding.editTextFirmwareVersion.setText("No hay datos")
                binding.editTextLastConfigDate.setText("No hay datos")
            }
        }

        // --- Observa Datos de Proceso (0x82) ---
        sharedNfcViewModel.processData.observe(viewLifecycleOwner) { processData ->
            if (processData != null) {

                Toast.makeText(context, "Datos Proceso Actualizados", Toast.LENGTH_SHORT).show()

                // Usar los campos ya parseados del objeto ProcessData para actualizar la UI
                binding.editTextVolume.setText(processData.volume.toString())
                binding.editTextFlow.setText(processData.flowRate.toString())
                binding.editTextTemperature.setText(processData.temperature.toString())
                binding.editTextBattery.setText(processData.battery.toString())
                binding.editTextStatus.setText(processData.statusFlags.toString())
                binding.editTextDirectFlowPeriod.setText(processData.directFlowPeriod.toString())
                binding.editTextReverseFlowPeriod.setText(processData.reverseFlowPeriod.toString())
                binding.editTextNoFlowPeriod.setText(processData.noFlowPeriod.toString())
                binding.editTextLeakagePeriod.setText(processData.leakageFlowPeriod.toString())
            } else {
                // Mensaje si no hay datos de proceso disponibles.
                binding.editTextVolume.setText("No hay datos")
                binding.editTextFlow.setText("No hay datos")
                binding.editTextTemperature.setText("No hay datos")
                binding.editTextBattery.setText("No hay datos")
                binding.editTextStatus.setText("No hay datos")
                binding.editTextDirectFlowPeriod.setText("No hay datos")
                binding.editTextReverseFlowPeriod.setText("No hay datos")
                binding.editTextNoFlowPeriod.setText("No hay datos")
                binding.editTextLeakagePeriod.setText("No hay datos")
            }
        }

        // --- Observa Datos de Ingenieria (0x85) ---
        sharedNfcViewModel.engineeringData.observe(viewLifecycleOwner) { engineeringData ->
            if (engineeringData != null) {

                Toast.makeText(context, "Datos Ingenieria Actualizados", Toast.LENGTH_SHORT).show()

                // Usar los campos ya parseados del objeto EngineeringData para actualizar la UI
                binding.editTextVolumeLiters.setText(engineeringData.volumeLiters.toString())
                binding.editTextVolumeLitersUncal.setText(engineeringData.volumeLitersUncal.toString())
                binding.editTextTemperatureUncal.setText(engineeringData.temperatureUncal.toString())
                binding.editTextFlowUncal.setText(engineeringData.flowUncal.toString())
                binding.editTextTtof.setText(engineeringData.ttof.toString())
                binding.editTextDtof.setText(engineeringData.dtof.toString())
                binding.editTextStdDev.setText(engineeringData.stdDev.toString())
                binding.editTextTime.setText(engineeringData.time.toString())
                binding.editTextChipTemperature.setText(engineeringData.chipTemperature.toString())
                binding.editTextLux.setText(engineeringData.lux.toString())
            } else {
                // Mensaje si no hay datos de proceso disponibles.
                binding.editTextVolumeLiters.setText("No hay datos")
                binding.editTextVolumeLitersUncal.setText("No hay datos")
                binding.editTextTemperatureUncal.setText("No hay datos")
                binding.editTextFlowUncal.setText("No hay datos")
                binding.editTextTtof.setText("No hay datos")
                binding.editTextDtof.setText("No hay datos")
                binding.editTextStdDev.setText("No hay datos")
                binding.editTextTime.setText("No hay datos")
                binding.editTextChipTemperature.setText("No hay datos")
                binding.editTextLux.setText("No hay datos")
            }
        }

        // --- Observa Datos de Configuración (0x83) ---
        // Se mantiene el observador para que el ViewModel guarde la data, aunque la UI de visualización esté en otro Fragment.
        sharedNfcViewModel.configData.observe(viewLifecycleOwner) { configData ->
            // Sin acción en la UI de Dashboard
        }

    }

    /**
     * Configura los listeners para la interacción del usuario.
     * Incluye los tres comandos de lectura existentes (0x01, 0x02, 0x03) y el nuevo (0x05).
     */
    private fun setupListeners() {

        // Comando 0x01: Solicitar bloque de Identidad
        binding.requestIdentityButton.setOnClickListener {
            val identityCommand: Byte = 0x01.toByte()
            sharedNfcViewModel.requestNextCommand(identityCommand)
            Toast.makeText(context, "Comando 0x01 (Identidad) solicitado. Acercar TAG para enviar.", Toast.LENGTH_SHORT).show()
        }

        // Comando 0x02: Solicitar bloque de Proceso
        binding.requestProcessButton.setOnClickListener {
            val processCommand: Byte = 0x02.toByte()
            sharedNfcViewModel.requestNextCommand(processCommand)
            Toast.makeText(context, "Comando 0x02 (Proceso) solicitado. Acercar TAG para enviar.", Toast.LENGTH_SHORT).show()
        }

        // Comando 0x03: Solicitar bloque de Configuración
        // ASUMIMOS que el ID del botón es 'requestConfigButton'
        binding.requestConfigButton.setOnClickListener {
            val configCommand: Byte = 0x03.toByte()
            sharedNfcViewModel.requestNextCommand(configCommand)
            Toast.makeText(context, "Comando 0x03 (Configuración) solicitado. Acercar TAG para enviar.", Toast.LENGTH_SHORT).show()
        }

        // Comando 0x05: Solicitar bloque de Datos Extra (¡NUEVO!)
        // ASUMIMOS que el ID del botón es 'requestExtraDataButton'
        binding.requestEngineeringButton.setOnClickListener {
            val extraCommand: Byte = 0x05.toByte()
            sharedNfcViewModel.requestNextCommand(extraCommand)
            Toast.makeText(context, "Comando 0x05 (Bloque Ingenieria) solicitado. Acercar TAG para enviar.", Toast.LENGTH_SHORT).show()
        }

    }
}
