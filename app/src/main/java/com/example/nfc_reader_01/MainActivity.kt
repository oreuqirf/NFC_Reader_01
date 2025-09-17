package com.example.nfc_reader_01

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.nfc_reader_01.databinding.ActivityMainBinding
import java.io.IOException
import java.nio.charset.StandardCharsets
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var nfcAdapter: NfcAdapter
    private lateinit var pendingIntent: PendingIntent
    private lateinit var sharedNfcViewModel: SharedNfcViewModel
    private lateinit var navController: NavController

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

        // CÓDIGO AGREGADO: Escuchar cambios en el destino de navegación
        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id == R.id.navigation_home) {
                // Restablece el estado del ViewModel para refrescar la UI del HomeFragment
                sharedNfcViewModel.resetNfcData()
            }
        }

        // CÓDIGO AGREGADO: Observar la solicitud de escritura del ViewModel
        sharedNfcViewModel.writeConfigRequest.observe(this) { writeData ->
            if (writeData != null) {
                // No hagas nada aquí, la lógica de escritura se maneja en onNewIntent
                // cuando se detecta un TAG. Este observer solo sirve como señal.
                Log.d("MainActivity", "Write request observed. Data: $writeData")
            }
        }

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            Toast.makeText(this, "NFC no está disponible en este dispositivo.", Toast.LENGTH_LONG).show()
            return
        }

        sharedNfcViewModel.setNfcStatus(nfcAdapter.isEnabled)

        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE)
    }

    override fun onResume() {
        super.onResume()
        if (nfcAdapter.isEnabled) {
            nfcAdapter.enableForegroundDispatch(this, pendingIntent, null, null)
            sharedNfcViewModel.setNfcStatus(true)
            handleIntent(intent)
        } else {
            sharedNfcViewModel.setNfcStatus(false)
        }
    }

    override fun onPause() {
        super.onPause()
        if (nfcAdapter.isEnabled) {
            nfcAdapter.disableForegroundDispatch(this)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_TECH_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_TAG_DISCOVERED == intent.action) {

            val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)

            sharedNfcViewModel.setNfcTag(tag)

            Log.d("MainActivity", "Tag detected: ${tag?.id}")

            if (tag != null) {
                // CÓDIGO MODIFICADO: Lógica de escritura vs. lectura
                val writeData = sharedNfcViewModel.writeConfigRequest.value
                if (writeData != null) {
                    writeNfcTag(tag, writeData)
                    sharedNfcViewModel.resetWriteRequest() // This matches your ViewModel
                } else {
                    readNfcTag(tag)
                }
            }
        }
    }

    private fun readNfcTag(tag: Tag) {
        val ndef = Ndef.get(tag)
        val ndefFormatable = NdefFormatable.get(tag)

        val isNdefTag = ndef != null
        val isNdefFormatable = ndefFormatable != null
        val isEmptyNdefTag = ndef != null && ndef.cachedNdefMessage == null

        sharedNfcViewModel.setTagInfo(isNdefTag, isNdefFormatable, isEmptyNdefTag)

        if (isNdefTag && !isEmptyNdefTag) {
            try {
                ndef.connect()
                val ndefMessage = ndef.ndefMessage
                val records = ndefMessage.records

                val identityRecord = records.getOrNull(0)
                val processRecord = records.getOrNull(1)
                val configurationRecord = records.getOrNull(2)

                val identityJson = identityRecord?.let { String(it.payload, StandardCharsets.UTF_8) }
                val processJson = processRecord?.let { String(it.payload, StandardCharsets.UTF_8) }
                val configurationJson = configurationRecord?.let { String(it.payload, StandardCharsets.UTF_8) }

                sharedNfcViewModel.setNdefRecords(identityJson, processJson, configurationJson)

                Log.d("MainActivity", "Identity JSON: $identityJson")
                Log.d("MainActivity", "Process JSON: $processJson")
                Log.d("MainActivity", "Configuration JSON: $configurationJson")

            } catch (e: Exception) {
                Log.e("MainActivity", "Error reading tag", e)
                Toast.makeText(this, "Error al leer la etiqueta NFC: ${e.message}", Toast.LENGTH_LONG).show()
                sharedNfcViewModel.setNdefRecords(null, null, null)
            } finally {
                try {
                    ndef?.close()
                } catch (e: IOException) {
                    Log.e("MainActivity", "Error closing tag", e)
                }
            }
        } else {
            sharedNfcViewModel.setNdefRecords(null, null, null)
        }
    }


    /**
     * Escribe un NdefMessage con el JSON de configuración a un TAG NFC,
     * preservando los registros existentes.
     */
    private fun writeNfcTag(tag: Tag, configJson: String) {
        val ndef = Ndef.get(tag)
        if (ndef == null) {
            Toast.makeText(this, "Este TAG no soporta NDEF o es solo de lectura.", Toast.LENGTH_SHORT).show()
            Log.e("MainActivity", "TAG does not support NDEF or is read-only.")
            return
        }

        try {
            ndef.connect()
            if (!ndef.isWritable) {
                Toast.makeText(this, "El TAG no es escribible.", Toast.LENGTH_SHORT).show()
                Log.w("MainActivity", "TAG is not writable.")
                return
            }

            // 1. Leer los registros existentes del TAG
            val existingMessage = ndef.ndefMessage
            val records = existingMessage?.records?.toMutableList() ?: mutableListOf()

            // 2. Crear un nuevo registro de configuración
            val configRecord = NdefRecord.createMime("application/json", configJson.toByteArray(StandardCharsets.UTF_8))

            // 3. Reemplazar o añadir el registro de configuración (registro N°3)
            if (records.size >= 3) {
                // Si ya existen 3 o más registros, reemplazamos el tercero
                records[2] = configRecord
            } else {
                // Si no hay suficientes registros, añadimos el nuevo
                // Nota: Esto solo debería ocurrir si el TAG fue formateado previamente
                // de forma no estándar (sin los 3 registros iniciales).
                while (records.size < 2) {
                    records.add(NdefRecord.createMime("application/json", "".toByteArray()))
                }
                records.add(configRecord)
            }

            // 4. Crear un nuevo NdefMessage con todos los registros
            val newMessage = NdefMessage(records.toTypedArray())

            val maxSize = ndef.maxSize
            if (newMessage.toByteArray().size > maxSize) {
                Toast.makeText(this, "El TAG es demasiado pequeño para los datos.", Toast.LENGTH_SHORT).show()
                Log.w("MainActivity", "TAG too small.")
                return
            }

            // 5. Escribir el mensaje completo en el TAG
            ndef.writeNdefMessage(newMessage)
            Toast.makeText(this, "Configuración actualizada y escrita en el TAG.", Toast.LENGTH_SHORT).show()
            Log.d("MainActivity", "Configuration updated and written to TAG.")

        } catch (e: Exception) {
            Toast.makeText(this, "Error al escribir en el TAG: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e("MainActivity", "Error writing to TAG", e)
        } finally {
            try {
                ndef.close()
            } catch (e: IOException) {
                Log.e("MainActivity", "Error closing tag", e)
            }
        }
    }
}
