
package com.example.nfc_reader_01

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NfcA
import android.nfc.tech.NfcB
import android.nfc.tech.NfcF
import android.nfc.tech.NfcV
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.nfc_reader_01.databinding.ActivityMainBinding
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var nfcAdapter: NfcAdapter? = null
    private var nfcPendingIntent: PendingIntent? = null
    private lateinit var sharedNfcViewModel: SharedNfcViewModel
    private lateinit var intentFilters: Array<IntentFilter>
    private lateinit var techList: Array<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedNfcViewModel = ViewModelProvider(this).get(SharedNfcViewModel::class.java)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
        val navController = navHostFragment.navController
        val appBarConfiguration = AppBarConfiguration(setOf(
            R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_notifications))
        setupActionBarWithNavController(navController, appBarConfiguration)
        binding.navView.setupWithNavController(navController)

        sharedNfcViewModel.isTagDetected.observe(this) { isDetected ->
            binding.navView.menu.findItem(R.id.navigation_dashboard).isEnabled = isDetected
            binding.navView.menu.findItem(R.id.navigation_notifications).isEnabled = isDetected

            if (!isDetected && navController.currentDestination?.id != R.id.navigation_home) {
                navController.navigate(R.id.navigation_home)
            }
        }

        binding.navView.menu.findItem(R.id.navigation_dashboard).isEnabled = false
        binding.navView.menu.findItem(R.id.navigation_notifications).isEnabled = false

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        // Comprueba si el hardware NFC existe
        if (nfcAdapter == null) {
            Toast.makeText(this, "Este dispositivo no soporta NFC.", Toast.LENGTH_LONG).show()
            sharedNfcViewModel.isNfcEnabled.value = false // Hardware no disponible
        }
        // La comprobación de si el NFC está habilitado se hará en onResume
    }

    override fun onResume() {
        super.onResume()
        if (nfcAdapter != null) {
            // Actualiza el estado de habilitación de NFC en el ViewModel
            sharedNfcViewModel.isNfcEnabled.value = nfcAdapter!!.isEnabled

            if (nfcAdapter!!.isEnabled) {
                val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                nfcPendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE)

                intentFilters = arrayOf(
                    IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED),
                    IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED),
                    IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)
                )

                techList = arrayOf(
                    arrayOf(Ndef::class.java.name),
                    arrayOf(NfcA::class.java.name),
                    arrayOf(NfcB::class.java.name),
                    arrayOf(NfcV::class.java.name),
                    arrayOf(NfcF::class.java.name)
                )

                nfcAdapter?.enableForegroundDispatch(this, nfcPendingIntent, intentFilters, techList)
                Log.d("MainActivity", "Foreground dispatch habilitado")
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (nfcAdapter != null && nfcAdapter!!.isEnabled) {
            nfcAdapter?.disableForegroundDispatch(this)
            Log.d("MainActivity", "Foreground dispatch deshabilitado")
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        if (intent != null && (NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action ||
                    NfcAdapter.ACTION_TECH_DISCOVERED == intent.action ||
                    NfcAdapter.ACTION_TAG_DISCOVERED == intent.action)) {

            // Llama a la nueva función del ViewModel para que se encargue del procesamiento
            sharedNfcViewModel.processNfcIntent(intent)
            Log.d("MainActivity", "Intent de NFC enviado al ViewModel para su procesamiento.")
        }
    }

}
