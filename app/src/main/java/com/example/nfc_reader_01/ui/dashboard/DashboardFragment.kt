package com.example.nfc_reader_01.ui.dashboard

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.example.nfc_reader_01.SharedNfcViewModel
import com.example.nfc_reader_01.databinding.FragmentDashboardBinding
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken

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

        // Observar los cambios en la cadena de texto JSON del ViewModel
        sharedNfcViewModel.nfcDataString.observe(viewLifecycleOwner, Observer { jsonContent ->
            if (jsonContent != null) {
                updateTableWithData(jsonContent)
                Log.d("DashboardFragment", "JSON recibido: $jsonContent")
            } else {
                clearTable()
                Log.d("DashboardFragment", "Datos JSON nulos. Limpiando la tabla.")
            }
        })
    }

    private fun updateTableWithData(jsonContent: String) {
        try {
            // Convertir la cadena JSON a un mapa de forma genérica
            val gson = Gson()
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val dataMap: Map<String, Any> = gson.fromJson(jsonContent, type)

            displayJsonInTable(dataMap)

        } catch (e: JsonSyntaxException) {
            Log.e("DashboardFragment", "Error de sintaxis JSON al convertir la cadena", e)
        }
    }

    private fun clearTable() {
        binding.jsonTableLayout.removeAllViews()
    }

    private fun displayJsonInTable(data: Map<String, Any>) {
        val tableLayout: TableLayout = binding.jsonTableLayout
        tableLayout.removeAllViews()

        val headerRow = TableRow(context).apply {
            layoutParams = TableLayout.LayoutParams(
                TableLayout.LayoutParams.MATCH_PARENT,
                TableLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val headerKey = TextView(context).apply {
            text = "Clave"
            setPadding(10, 10, 10, 10)
            textSize = 16f
            setBackgroundColor(Color.LTGRAY)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        val headerValue = TextView(context).apply {
            text = "Valor"
            setPadding(10, 10, 10, 10)
            textSize = 16f
            setBackgroundColor(Color.LTGRAY)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        headerRow.addView(headerKey)
        headerRow.addView(headerValue)
        tableLayout.addView(headerRow)

        for ((key, value) in data) {
            val tableRow = TableRow(context).apply {
                layoutParams = TableLayout.LayoutParams(
                    TableLayout.LayoutParams.MATCH_PARENT,
                    TableLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val keyTextView = TextView(context).apply {
                text = key
                setPadding(10, 10, 10, 10)
                textSize = 16f
                setBackgroundColor(Color.WHITE)
            }
            tableRow.addView(keyTextView)

            val valueTextView = TextView(context).apply {
                // Maneja el caso en que el valor es nulo
                text = value?.toString() ?: "N/A"
                setPadding(10, 10, 10, 10)
                textSize = 16f
                setBackgroundColor(Color.WHITE)
            }
            tableRow.addView(valueTextView)
            tableLayout.addView(tableRow)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
