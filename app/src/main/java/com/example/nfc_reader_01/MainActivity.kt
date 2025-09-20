package com.example.nfc_reader_01

import android.content.Intent
import android.media.MediaPlayer
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.nfc_reader_01.databinding.ActivityMainBinding
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.*
import android.nfc.tech.NdefFormatable


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var sharedNfcViewModel: SharedNfcViewModel
    private var nfcAdapter: NfcAdapter? = null
    private lateinit var pendingIntent: android.app.PendingIntent

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedNfcViewModel = ViewModelProvider(this).get(SharedNfcViewModel::class.java)

        val navView: BottomNavigationView = binding.navView
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
        navController = navHostFragment.navController

        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_notifications, R.id.navigation_configuration
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            Toast.makeText(this, "Este dispositivo no soporta NFC.", Toast.LENGTH_LONG).show()
        } else {
            pendingIntent = android.app.PendingIntent.getActivity(
                this, 0,
                Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                android.app.PendingIntent.FLAG_MUTABLE
            )
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id == R.id.navigation_home) {
                sharedNfcViewModel.setNfcTag(null)
                sharedNfcViewModel.setNdefRecords(null, null, null)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (nfcAdapter?.isEnabled == true) {
            nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, null)
            sharedNfcViewModel.setNfcStatus(true)
        } else {
            sharedNfcViewModel.setNfcStatus(false)
        }
    }

    override fun onPause() {
        super.onPause()
        if (nfcAdapter?.isEnabled == true) {
            nfcAdapter?.disableForegroundDispatch(this)
        }
        sharedNfcViewModel.setNfcTag(null)
        sharedNfcViewModel.setNdefRecords(null, null, null)
    }


    @Suppress("DEPRECATION")
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        val tag: Tag? = intent?.getParcelableExtra(NfcAdapter.EXTRA_TAG) as Tag?

        if (tag != null) {
            sharedNfcViewModel.setNfcTag(tag)
            handleIntent(intent, tag)
        }
    }


    private fun handleIntent(intent: Intent, tag: Tag) {
        val writeMessage = sharedNfcViewModel.writeMessageRequest.value
        val formatNewTag = sharedNfcViewModel.formatNewTagRequest.value

        if (formatNewTag == true) {
            writeInitialTag(tag)
            sharedNfcViewModel.resetFormatNewTagRequest()
        } else if (writeMessage != null) {
            writeNfcTag(tag, writeMessage)
            sharedNfcViewModel.resetWriteMessageRequest()
        } else {
            readNfcTag(tag)
        }
    }


    private fun readNfcTag(tag: Tag) {
        val ndef = Ndef.get(tag)
        if (ndef == null) {
            sharedNfcViewModel.setIsNdef(false)
            sharedNfcViewModel.setIsNdefFormatable(false)
            sharedNfcViewModel.setIsEmptyNdef(false)
            return
        }

        sharedNfcViewModel.setIsNdef(true)
        sharedNfcViewModel.setIsNdefFormatable(NdefFormatable.get(tag) != null)

        try {
            ndef.connect()
            val ndefMessage = ndef.ndefMessage

            if (ndefMessage == null) {
                sharedNfcViewModel.setIsEmptyNdef(true)
                return
            }

            sharedNfcViewModel.setIsEmptyNdef(false)
            val records = ndefMessage.records.toList()

            var identityData: ByteArray? = null
            var processData: ByteArray? = null
            var configData: ByteArray? = null

            // Itera a través de todos los registros para encontrar los datos correctos.
            for (record in records) {
                when (record.toMimeType()) {
                    "application/vnd.my_app.binary_identity" -> {
                        identityData = record.payload
                    }
                    "application/vnd.my_app.binary_process" -> {
                        processData = record.payload
                    }
                    "application/vnd.my_app.binary_config" -> {
                        configData = record.payload
                    }
                }
            }

            sharedNfcViewModel.setNdefRecords(identityData, processData, configData)

            // Reproduce un sonido de éxito después de una lectura exitosa
            playSound()

        } catch (e: Exception) {
            // Maneja el error, pero no hay un Toast de error aquí, ya que se lee.
        } finally {
            try {
                ndef.close()
            } catch (e: IOException) {
                // Maneja el error al cerrar la conexión.
            }
        }
    }

    // Nuevo método para formatear una etiqueta virgen
    private fun writeInitialTag(tag: Tag) {
        // Datos de identidad (estáticos)
        val identityData = ByteBuffer.allocate(4 + 4 + 10).apply {
            putInt(12345)
            putInt(201)
            put("17-09-2025".toByteArray(StandardCharsets.UTF_8))
        }.array()

        // Datos de proceso (inicializados en 0)
        val processData = ByteBuffer.allocate(4 + 4 + 4 + 4 + 19).apply {
            putInt(0) // Volumen
            putInt(0) // Caudal
            putInt(0) // Temperatura
            putInt(0) // Estado
            val dateFormat = java.text.SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault())
            val timestamp = dateFormat.format(Date()).toByteArray(StandardCharsets.UTF_8)
            put(timestamp)
        }.array()

        // Datos de configuración (inicializados en 0.0f)
        val configData = ByteBuffer.allocate(11 * 4).apply {
            for (i in 0 until 11) {
                putFloat(0.0f)
            }
        }.array()

        val identityRecord = NdefRecord.createMime("application/vnd.my_app.binary_identity", identityData)
        val processRecord = NdefRecord.createMime("application/vnd.my_app.binary_process", processData)
        val configRecord = NdefRecord.createMime("application/vnd.my_app.binary_config", configData)

        val initialMessage = NdefMessage(arrayOf(identityRecord, processRecord, configRecord))

        writeNfcTag(tag, initialMessage)
    }


    private fun writeNfcTag(tag: Tag, message: NdefMessage) {
        val ndef = Ndef.get(tag)
        if (ndef == null) {
            sharedNfcViewModel.setWriteStatus("Este TAG no soporta NDEF o es de solo lectura.")
            return
        }

        try {
            ndef.connect()
            if (!ndef.isWritable) {
                sharedNfcViewModel.setWriteStatus("La etiqueta es de solo lectura.")
                return
            }

            val maxSize = ndef.maxSize
            if (message.toByteArray().size > maxSize) {
                sharedNfcViewModel.setWriteStatus("La etiqueta es demasiado pequeña para los datos.")
                return
            }

            ndef.writeNdefMessage(message)
            sharedNfcViewModel.setWriteStatus("Escritura exitosa.")

        } catch (e: Exception) {
            sharedNfcViewModel.setWriteStatus("Error al escribir: ${e.message}")
        } finally {
            try {
                ndef.close()
            } catch (e: IOException) {
                // Maneja el error al cerrar la conexión.
            }
        }
    }

    // Método para reproducir un sonido de éxito desde un recurso local
    private fun playSound() {
        try {
            // Reemplaza 'R.raw.beep' con el nombre de tu archivo de sonido
            // que debe estar en la carpeta res/raw/ de tu proyecto.
            val mediaPlayer = MediaPlayer.create(this, R.raw.beep)
            mediaPlayer?.start()
            mediaPlayer?.setOnCompletionListener { mp ->
                mp.release()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error al reproducir el sonido: ${e.message}")
        }
    }
}

