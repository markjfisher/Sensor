package fish.sensor

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*
import kotlin.concurrent.scheduleAtFixedRate

fun main(args: Array<String>) = App().main(args)

class App : CliktCommand() {
    private val period: Int
            by option("-p", "--period", help = "Period between calls in milliseconds (default 5000)")
                    .int()
                    .default(5000)
    private val showHeader
            by option("--showHeader", help = "Output CSV Header as first line")
                    .flag("--noHeader", default = false)

    private val readers = listOf<DataReader>(SensorReader())
    private var shouldPrintHeader = false

    override fun run() {
        shouldPrintHeader = showHeader

        Timer().scheduleAtFixedRate(delay = 0, period = period.toLong()) {
            job()
            shouldPrintHeader = false
        }
    }

    private fun job() {
        val headers= mutableListOf<String>()
        headers.add("time")

        val dataPoints = mutableListOf<String>()
        val epochTime = LocalDateTime.now(ZoneOffset.UTC).atZone(ZoneOffset.UTC).toInstant().toEpochMilli() / 1000
        dataPoints.add(epochTime.toString())

        readers.forEach { reader ->
            headers.addAll(reader.headers())
            dataPoints.addAll(reader.values())
        }

        if (shouldPrintHeader) {
            println(headers.joinToString(","))
        }
        println(dataPoints.joinToString(","))

    }

}