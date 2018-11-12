# Sensor

A simple project to record `sensors` data out to a file.

## Requirements

Must have `sensors` application installed for your platform, configured to generate sensor data.

## Building

The project uses the gradle application plugin to create a distributable tar/zip file.

    ./gradlew

The distribution can be found under `build/distributions/sensorapp-<VERSION>.tar`

## Help

    $ sensorapp -h
    Usage: sensorapp [OPTIONS]
    
    Options:
      -p, --period INT           Period between calls (ms)
      --showHeader / --noHeader  Print Header
      -h, --help                 Show this message and exit

The default period is 5000ms.

## Typical Output

The output is in the form:

    <TIMESTAMP epoch seconds>,[reading ...]

e.g.

    $ sensorapp -p 4000
    1542025844,54.0,66.0,50.0,50.0,54.0,51.0,66.0,61.0,2642.0,0.0,47.0,0.0
    1542025847,55.0,47.0,46.0,47.0,46.0,44.0,45.0,46.0,2642.0,0.0,47.0,0.0
    1542025851,55.0,47.0,46.0,44.0,46.0,45.0,45.0,45.0,2642.0,2623.0,48.0,0.0

Adding the --showHeader option outputs a CSV header that will be print as the first line to supply any CSV reader with headings for the data fields.

e.g.

    time,pch_temp1,coretemp_Package_id_0,coretemp_Core_0,coretemp_Core_1,coretemp_Core_2,coretemp_Core_3,coretemp_Core_4,coretemp_Core_5,s76_CPU_fan,s76_GPU_fan,s76_CPU_temperature,s76_GPU_temperature

### TODO

- Allow configurable set of chips to monitor. Currently hardcoded to my laptop's chips. Sorry.
