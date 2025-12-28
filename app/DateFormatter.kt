import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Convierte un timestamp Epoch (segundos desde 1970) en una cadena de fecha formateada.
 *
 * @param configTimestamp El timestamp Epoch en segundos (Int), obtenido típicamente de buffer.getInt().
 * @return La cadena de fecha formateada (ej: "2024-06-15 10:30:00").
 */
fun formatEpochTimestamp(configTimestamp: Int): String {
    // 1. Convertir el timestamp de segundos (Int) a milisegundos (Long).
    // La clase Date requiere el tiempo en milisegundos.
    val dateMillis = configTimestamp.toLong() * 1000

    // 2. Crear el objeto Date.
    val date = Date(dateMillis)

    // 3. Definir el formato deseado.
    // Usamos Locale.getDefault() para respetar la configuración regional del dispositivo.
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    // 4. Formatear y devolver la cadena.
    return formatter.format(date)
}
