import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SharedNfcViewModel : ViewModel() {
    // This is the new property for passing the JSON string
    val nfcDataString = MutableLiveData<String>()

    // (Optional) You can remove the old nfcData property if you are no longer using it.
    // val nfcData = MutableLiveData<NFCData>()
}
