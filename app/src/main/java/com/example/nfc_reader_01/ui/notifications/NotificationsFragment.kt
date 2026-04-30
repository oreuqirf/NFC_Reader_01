package com.example.nfc_reader_01.ui.notifications

import android.app.AlertDialog
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
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.nfc_reader_01.SharedNfcViewModel
import com.example.nfc_reader_01.databinding.FragmentNotificationsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationsFragment : Fragment() {

    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!

    // Usamos activityViewModels para compartir la instancia exacta con los otros fragmentos
    private val sharedNfcViewModel: SharedNfcViewModel by activityViewModels()

    private lateinit var adapter: RecordAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupListeners()
        observeViewModel()

        // Cada vez que se abre la pantalla, le pedimos a la BD que cargue los últimos datos
        sharedNfcViewModel.loadAllRecords(requireContext())
    }

    private fun setupRecyclerView() {
        adapter = RecordAdapter(
            onRecordClick = { recordToView ->
                // Acción al tocar la tarjeta: Mostrar los detalles
                showRecordDetails(recordToView)
            },
            onDeleteClick = { recordToDelete ->
                // Acción al tocar el basurero: Confirmar borrado
                confirmDeleteRecord(recordToDelete)
            }
        )
        binding.recyclerViewRecords.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewRecords.adapter = adapter
    }

    private fun setupListeners() {
        // Botón para exportar
        binding.btnExportCsv.setOnClickListener {
            exportDatabaseToCSV()
        }

        // Botón para vaciar todo
        binding.btnClearDatabase.setOnClickListener {
            confirmClearDatabase()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                // Observar la lista de registros
                launch {
                    sharedNfcViewModel.databaseRecords.collect { records ->
                        adapter.submitList(records)

                        // Si la lista está vacía, desactivamos el botón de exportar
                        binding.btnExportCsv.isEnabled = records.isNotEmpty()
                        binding.btnClearDatabase.isEnabled = records.isNotEmpty()
                    }
                }

                // Observar el contador
                launch {
                    sharedNfcViewModel.batchCount.collect { count ->
                        binding.textViewRecordCount.text = "$count equipos registrados"
                    }
                }
            }
        }
    }


    private fun showRecordDetails(record: com.example.nfc_reader_01.collection.InstrumentRecord) {
        val dateFormat = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault())
        val deviceDate = dateFormat.format(java.util.Date(record.deviceLastConfigDate))
        val saveDate = dateFormat.format(java.util.Date(record.timestamp))

        // Formateamos todos los datos en un texto limpio y ordenado
        val detailsText = """
            IDENTIDAD
            • Serial: ${record.serialNumber}
            • Firmware: ${record.firmwareVersion}

            CONFIGURACIÓN GENERAL
            • K Meter: ${record.kMeter}
            • Estabilidad: ${record.low_stability}
            • Tiempo Parada: ${record.high_stability}

            TEMPERATURAS (Raw / Calibrada)
            • Temp Baja: ${record.tempRawLow} / ${record.tempCalLow}
            • Temp Alta: ${record.tempRawHigh} / ${record.tempCalHigh}

            ERRORES DE FLUJO
            • Factor Q1: ${record.fcQ1Error}
            • Factor Q2: ${record.fcQ2Error}
            • Factor 0.35L: ${record.fcQ035Error}
            • Factor 1.00L: ${record.fcQ100Error}
            • Factor 10.0L: ${record.fcQ10LmError}
            • Factor Q3: ${record.fcQ3Error}

            FECHAS DE REGISTRO
            • Fecha interna equipo: $deviceDate
            • Momento de guardado: $saveDate
        """.trimIndent()

        // Usamos el diálogo nativo de Material Design
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Detalles de Configuración")
            .setMessage(detailsText)
            .setPositiveButton("Cerrar", null)
            .show()
    }

    // --- DIÁLOGOS DE CONFIRMACIÓN ---

    private fun confirmDeleteRecord(record: com.example.nfc_reader_01.collection.InstrumentRecord) {
        AlertDialog.Builder(requireContext())
            .setTitle("Eliminar Registro")
            .setMessage("¿Estás seguro de que deseas borrar el registro del equipo ${record.serialNumber}?")
            .setPositiveButton("Borrar") { _, _ ->
                sharedNfcViewModel.deleteSingleRecord(requireContext(), record)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun confirmClearDatabase() {
        AlertDialog.Builder(requireContext())
            .setTitle("Vaciar Lote")
            .setMessage("¡CUIDADO! Esto borrará todos los registros de la base de datos de tu teléfono. ¿Estás seguro?")
            .setPositiveButton("Sí, Vaciar Todo") { _, _ ->
                sharedNfcViewModel.clearDatabase(requireContext())
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // --- FUNCIÓN DE EXPORTACIÓN (Movida aquí) ---
    private fun exportDatabaseToCSV() {
        binding.btnExportCsv.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val database = com.example.nfc_reader_01.collection.AppDatabase.getDatabase(requireContext())
                val records = database.recordDao().getAllRecords()

                if (records.isEmpty()) return@launch

                val exportDir = java.io.File(requireContext().cacheDir, "exports")
                if (!exportDir.exists()) exportDir.mkdirs()

                val file = java.io.File(exportDir, "Lote_Configuraciones_${System.currentTimeMillis()}.csv")
                val writer = java.io.FileWriter(file)

                // Encabezados
                writer.append("ID,Serial_Number,Firmware,K_Meter,Stability,StopTime,Temp_Raw_Low,Temp_Raw_High,Temp_Cal_Low,Temp_Cal_High,Q1_Error,Q2_Error,Q0.35_Error,Q1.00_Error,Q10.0L_Error,Q3_Error,Device_Config_Date,Timestamp\n")

                // Datos
                for (record in records) {
                    writer.append("${record.id},${record.serialNumber},${record.firmwareVersion},${record.kMeter},${record.low_stability},,${record.high_stability},${record.tempRawLow},${record.tempRawHigh},${record.tempCalLow},${record.tempCalHigh},${record.fcQ1Error},${record.fcQ2Error},${record.fcQ035Error},${record.fcQ100Error},${record.fcQ10LmError},${record.fcQ3Error},")

                    val deviceDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(record.deviceLastConfigDate))
                    val exportDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(record.timestamp))
                    writer.append("$deviceDate,$exportDate\n")
                }
                writer.flush()
                writer.close()

                withContext(Dispatchers.Main) {
                    shareCsvFile(file)
                    binding.btnExportCsv.isEnabled = true
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error exportando CSV", Toast.LENGTH_SHORT).show()
                    binding.btnExportCsv.isEnabled = true
                }
            }
        }
    }

    private fun shareCsvFile(file: java.io.File) {
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", file)
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(android.content.Intent.createChooser(intent, "Exportar Lote de Configuraciones"))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Falta configurar FileProvider en el Manifest", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}