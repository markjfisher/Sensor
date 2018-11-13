package fish.sensor

interface DataReader {
    fun headers(): List<String>
    fun values(): List<String>
}