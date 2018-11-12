package fish.sensor

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors
import kotlin.concurrent.scheduleAtFixedRate

val chipMaps = listOf (
    mapOf("name" to "pch_cannonlake-virtual-0", "shortName" to "pch"),
    mapOf("name" to "coretemp-isa-0000", "shortName" to "coretemp"),
    mapOf("name" to "system76-isa-0000", "shortName" to "s76")
)

fun String.runCommand(workingDir: File = File("."),
                      timeoutAmount: Long = 60,
                      timeoutUnit: TimeUnit = TimeUnit.SECONDS): String {
    return try {
        ProcessBuilder(*this.split("\\s".toRegex()).toTypedArray())
                .directory(workingDir)
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectError(ProcessBuilder.Redirect.PIPE)
                .start().apply {
                    waitFor(timeoutAmount, timeoutUnit)
                }.inputStream.bufferedReader().readText()
    } catch (e: IOException) {
        e.printStackTrace()
        ""
    }
}

data class SensorReading (
    val name: String,
    val reading: Double
)

data class Sensor (
    val name: String,
    val readings: List<SensorReading>
)

data class SensorChip (
    val name: String,
    val adapter: String,
    val sensors: List<Sensor>
)

fun splitByBlankLine(input: String) : List<String> {
    return input.split("((\\n\\r)|(\\r\\n)){2}|(\\r){2}|(\\n){2}".toRegex())
}

fun createSensorChip(block: List<String>) : SensorChip {
    val chipName = block[0]
    val adapter = block[1]
    val sensors = ArrayList<Sensor>()

    val blockIterator = block.listIterator(2)

    var newSensorName = ""
    var newSensorReadings = ArrayList<SensorReading>()
    var inBlock = false
    while (blockIterator.hasNext()) {
        val nextLine = blockIterator.next()
        if (!nextLine.startsWith(" ") && !inBlock) {
            inBlock = true
            newSensorName = nextLine.dropLast(1)
            newSensorReadings = ArrayList()
            continue
        }
        if (!nextLine.startsWith(" ") && inBlock) {
            sensors.add(Sensor(newSensorName, newSensorReadings))
            blockIterator.previous() // make it re-read the line, but now outside a block
            inBlock = false
            continue
        }
        if (nextLine.startsWith(" ") && !inBlock) {
            // error case, we can't start on a reading, need a name for this sensor
            throw IOException("Bad data block, got reading before a sensor name:\n${block.joinToString(separator = "\n")}")
        }
        if (nextLine.startsWith(" ") && inBlock) {
            // a sensor reading
            val kv = nextLine.split(":", limit = 2)
            newSensorReadings.add(SensorReading(kv[0].trim(), kv[1].trim().toDouble()))
        }
    }
    // store last sensorreadings in final block
    if (!newSensorReadings.isEmpty()) {
        sensors.add(Sensor(newSensorName, newSensorReadings))
    }
    return SensorChip(chipName, adapter, sensors)
}

fun parseSensors(sensorOutput: String) : List<SensorChip> {
    return splitByBlankLine(sensorOutput)
        .stream()
        .filter { it != "" }
        .filter {
            val name = it.lineSequence().first()
            chipMaps.map { it["name"] }.contains(name)
        }
        .map { sensorBlock ->
            createSensorChip(sensorBlock.lineSequence().toList())
        }
        .collect(Collectors.toList())
}

fun convertToInputPairs(sensorChips: List<SensorChip>) : List<Pair<String, Double>> {
    return sensorChips.flatMap { chip ->
        chip.sensors.flatMap { sensor ->
            sensor.readings
                .filter { it.name.endsWith("_input") }
                .map { reading ->
                    // val statName = reading.name.removeSuffix("_input")
                    val m = chipMaps.find { it["name"] == chip.name } ?: mapOf( "name" to chip.name, "shortName" to chip.name.replace(" ", "_").replace("-", "_"))
                    val key = (m["shortName"] + "_${sensor.name}")
                            .replace(" ", "_")
                            .replace("-", "_")
                    Pair(key, reading.reading)
                }
        }
    }

}

fun headersAsCSV(inputPairs: List<Pair<String, Double>>) : String {
    return inputPairs.map { pair ->
        pair.first
    }.joinToString(",")
}

fun readingsAsCSV(inputPairs: List<Pair<String, Double>>) : String {
    return inputPairs.map { pair ->
        pair.second
    }.joinToString(",")
}

fun job(printHeader: Boolean) {
    val sensorChips = parseSensors("sensors -u".runCommand())
    val inputPairs = convertToInputPairs(sensorChips)

    if (printHeader) {
        println("time," + headersAsCSV(inputPairs))
    }
    val epochTime = (LocalDateTime.now(ZoneOffset.UTC).atZone(ZoneOffset.UTC).toInstant().toEpochMilli() / 1000).toString()
    println("$epochTime," + readingsAsCSV(inputPairs))
}

fun main(args: Array<String>) = SensorApp().main(args)

class SensorApp : CliktCommand() {
    private val period: Int by option("-p", "--period", help = "Period between calls in milliseconds (default 5000)").int().default(5000)
    private val showHeader by option("--showHeader", help = "Output CSV Header as first line").flag("--noHeader", default = false)
    override fun run() {
        var headerControl = showHeader
        Timer().scheduleAtFixedRate(delay = 0, period = period.toLong()) {
            job(headerControl)
            headerControl = false
        }
    }
}