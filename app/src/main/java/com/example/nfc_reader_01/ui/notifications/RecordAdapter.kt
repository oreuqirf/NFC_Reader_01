package com.example.nfc_reader_01.ui.notifications

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.nfc_reader_01.R
import com.example.nfc_reader_01.collection.InstrumentRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecordAdapter(
    private val onRecordClick: (InstrumentRecord) -> Unit, // NUEVO: Escucha el toque en la tarjeta
    private val onDeleteClick: (InstrumentRecord) -> Unit
) : RecyclerView.Adapter<RecordAdapter.RecordViewHolder>() {

    private var records: List<InstrumentRecord> = emptyList()

    fun submitList(newRecords: List<InstrumentRecord>) {
        records = newRecords
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_database_record, parent, false)
        return RecordViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecordViewHolder, position: Int) {
        val record = records[position]
        holder.bind(record)
    }

    override fun getItemCount(): Int = records.size

    inner class RecordViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvSerial: TextView = itemView.findViewById(R.id.tvSerialNumber)
        private val tvFirmware: TextView = itemView.findViewById(R.id.tvFirmware)
        private val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDeleteRecord)

        fun bind(record: InstrumentRecord) {
            tvSerial.text = "SN: ${record.serialNumber}"
            tvFirmware.text = "Firmware: ${record.firmwareVersion}"

            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            tvDate.text = "Guardado: ${dateFormat.format(Date(record.timestamp))}"

            // Acción para borrar
            btnDelete.setOnClickListener {
                onDeleteClick(record)
            }

            // NUEVO: Acción al tocar cualquier parte de la tarjeta
            itemView.setOnClickListener {
                onRecordClick(record)
            }
        }
    }
}
