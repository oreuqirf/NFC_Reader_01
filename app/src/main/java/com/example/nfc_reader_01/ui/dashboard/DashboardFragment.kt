package com.example.nfc_reader_01.ui.dashboard

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.nfc_reader_01.SharedNfcViewModel
import com.example.nfc_reader_01.databinding.FragmentDashboardBinding
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException

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

        sharedNfcViewModel.dashboardJsonString.observe(viewLifecycleOwner) { jsonString ->
            if (jsonString.isNullOrBlank()) {
                binding.textDashboard.text = "Aproxime una etiqueta NFC para ver los datos."
                binding.dashboardTable.visibility = View.GONE
            } else {
                displayDataInTable(jsonString)
                binding.dashboardTable.visibility = View.VISIBLE
            }
        }
    }

    private fun displayDataInTable(jsonString: String) {
        try {
            // Este es el bloque de validación. Si el JSON es inválido, se lanzará una excepción aquí.
            val dataMap = Gson().fromJson(jsonString, Map::class.java) as Map<String, Any?>

            binding.dashboardTable.removeAllViews()

            val headerRow = TableRow(context)
            val header1 = TextView(context)
            header1.text = "Campo"
            header1.setPadding(8, 8, 8, 8)
            header1.textAlignment = View.TEXT_ALIGNMENT_CENTER
            headerRow.addView(header1)

            val header2 = TextView(context)
            header2.text = "Valor"
            header2.setPadding(8, 8, 8, 8)
            header2.textAlignment = View.TEXT_ALIGNMENT_CENTER
            headerRow.addView(header2)

            binding.dashboardTable.addView(headerRow)

            for ((key, value) in dataMap) {
                val dataRow = TableRow(context)

                val keyView = TextView(context)
                keyView.text = key
                keyView.setPadding(8, 8, 8, 8)
                dataRow.addView(keyView)

                val valueView = TextView(context)
                val displayValue = if (value != null) value.toString() else "null"
                valueView.text = displayValue
                valueView.setPadding(8, 8, 8, 8)
                dataRow.addView(valueView)

                binding.dashboardTable.addView(dataRow)
            }
            binding.textDashboard.text = "Datos de la etiqueta NFC"

        } catch (e: JsonSyntaxException) {
            Log.e("DashboardFragment", "Error de sintaxis JSON", e)
            binding.dashboardTable.removeAllViews()
            binding.textDashboard.text = "Error: El contenido del tag no es un JSON válido."
        } catch (e: Exception) {
            Log.e("DashboardFragment", "Error al procesar el JSON: ${e.message}", e)
            binding.dashboardTable.removeAllViews()
            binding.textDashboard.text = "Error inesperado al procesar los datos."
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
