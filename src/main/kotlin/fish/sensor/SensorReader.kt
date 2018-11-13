package fish.sensor

import java.io.IOException
import java.util.stream.Collectors

val chipMaps = listOf (
    mapOf("name" to "pch_cannonlake-virtual-0", "shortName" to "pch"),
    mapOf("name" to "coretemp-isa-0000", "shortName" to "coretemp"),
    mapOf("name" to "system76-isa-0000", "shortName" to "s76")
)

data class SensorReading (
    val name: String,
    val reading: String
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

class SensorReader : DataReader {
    private val inputPairs = mutableListOf<Pair<String, String>>()

    private fun splitByBlankLine(input: String): List<String> {
        return input.split("((\\n\\r)|(\\r\\n)){2}|(\\r){2}|(\\n){2}".toRegex())
    }

    private fun createSensorChip(block: List<String>): SensorChip {
        val chipName = block[0]
        val adapter = block[1]
        val sensors = mutableListOf<Sensor>()

        val blockIterator = block.listIterator(2)

        var newSensorName = ""
        val newSensorReadings = mutableListOf<SensorReading>()
        var inBlock = false
        while (blockIterator.hasNext()) {
            val nextLine = blockIterator.next()
            if (!nextLine.startsWith(" ") && !inBlock) {
                inBlock = true
                newSensorName = nextLine.dropLast(1)
                newSensorReadings.clear()
                continue
            }
            if (!nextLine.startsWith(" ") && inBlock) {
                sensors.add(Sensor(newSensorName, newSensorReadings.toList())) // need to clone the list, else gets wiped next value
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
                newSensorReadings.add(SensorReading(kv[0].trim(), kv[1].trim()))
            }
        }
        // store last sensorreadings in final block
        if (!newSensorReadings.isEmpty()) {
            sensors.add(Sensor(newSensorName, newSensorReadings))
        }
        return SensorChip(chipName, adapter, sensors)
    }

    private fun parseSensors(sensorOutput: String): List<SensorChip> {
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

    private fun convertToInputPairs(sensorChips: List<SensorChip>): List<Pair<String, String>> {
        return sensorChips.flatMap { chip ->
            chip.sensors.flatMap { sensor ->
                sensor.readings
                        .filter { it.name.endsWith("_input") }
                        .map { reading ->
                            // val statName = reading.name.removeSuffix("_input")
                            val m = chipMaps.find { it["name"] == chip.name }
                                    ?: mapOf("name" to chip.name, "shortName" to chip.name.replace(" ", "_").replace("-", "_"))
                            val key = (m["shortName"] + "_${sensor.name}")
                                    .replace(" ", "_")
                                    .replace("-", "_")
                            Pair(key, reading.reading)
                        }
            }
        }

    }

    private fun fetch() {
        inputPairs.clear()
        val rawData = "sensors -u".runCommand()
        inputPairs.addAll(convertToInputPairs(parseSensors(rawData)))
    }

    override fun values(): List<String> {
        fetch()
        return inputPairs.map { it.second }
    }

    override fun headers(): List<String> {
        fetch()
        return inputPairs.map{ it.first }
    }
}