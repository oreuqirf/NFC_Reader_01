package com.example.nfc_reader_01

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.tech.Ndef
import android.nfc.tech.NfcA
import android.nfc.tech.NfcB
import android.nfc.tech.NfcF
import android.nfc.tech.NfcV
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.nfc_reader_01.databinding.ActivityMainBinding
import androidx.navigation.NavOptions
// ... (imports)
import androidx.lifecycle.ViewModelProvider // Importa la clase
// ... (resto del código)
import com.example.nfc_reader_01.SharedNfcViewModel

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var nfcAdapter: NfcAdapter? = null
    private var nfcPendingIntent: PendingIntent? = null
    // Declara el ViewModel
    private lateinit var sharedNfcViewModel: SharedNfcViewModel


    // Declara las variables para los filtros de intent y las tecnologías
    private lateinit var intentFilters: Array<IntentFilter>
    private lateinit var techList: Array<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)


        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
        val navController = navHostFragment.navController
        val appBarConfiguration = AppBarConfiguration(setOf(
            R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_notifications))
        setupActionBarWithNavController(navController, appBarConfiguration)
        binding.navView.setupWithNavController(navController)

        // Inicializar el adaptador NFC
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        // Configurar el PendingIntent para redirigir intents de NFC a esta Activity
        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        nfcPendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE)

        // Crear un array de filtros de intent para las acciones de NFC
        intentFilters = arrayOf(
            IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED),
            IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED),
            IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)
        )

        // Crear un array de tecnologías para las etiquetas
        techList = arrayOf(
            arrayOf(Ndef::class.java.name),
            arrayOf(NfcA::class.java.name),
            arrayOf(NfcB::class.java.name),
            arrayOf(NfcV::class.java.name),
            arrayOf(NfcF::class.java.name)
        )

        // Inicializa el ViewModel
        sharedNfcViewModel = ViewModelProvider(this).get(SharedNfcViewModel::class.java)

    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.enableForegroundDispatch(this, nfcPendingIntent, intentFilters, techList)
        Log.d("MainActivity", "Foreground dispatch habilitado")
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
        Log.d("MainActivity", "Foreground dispatch deshabilitado")
    }



    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Log.d("MainActivity", "onNewIntent called with action: ${intent?.action}")

        if (intent != null && (NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action ||
                    NfcAdapter.ACTION_TECH_DISCOVERED == intent.action ||
                    NfcAdapter.ACTION_TAG_DISCOVERED == intent.action)) {

            val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
            val navController = navHostFragment.navController

            val args = Bundle().apply {
                putParcelable("nfc_intent", intent)
            }

            // Use NavOptions to navigate and pop the back stack
            val navOptions = NavOptions.Builder()
                .setPopUpTo(R.id.mobile_navigation, true) // Clear the back stack up to the start destination
                .setLaunchSingleTop(true) // If already on NotificationsFragment, don't create a new one
                .build()

            // Navigate to the NotificationsFragment with the NFC data
            navController.navigate(R.id.navigation_notifications, args, navOptions)

            Log.d("MainActivity", "Intent de NFC pasado al NavController. Navegando al NotificationsFragment.")
        }

    }
}
