# Module duration-parser

Parses durations from timer markers and free text, for example `⏱ 1h, 30 Min.`, and converts them to and from ISO 8601 durations such as `PT1H30M`.

`IsoDuration.parse` reads the ISO 8601 durations of recipe times (`PT1H5M`, `PT01H05M00S`, `P1DT2H`) and returns `null` for anything else; `IsoDuration.format` writes the shortest form in hours and minutes. Timer markers in step text follow with `cooking-mode`.

This module is pure Kotlin/JVM and has no Android dependency.
