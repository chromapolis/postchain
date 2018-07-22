package net.postchain.test

import java.io.File

/**
 * Main function, everything starts here
 *
 * TODO: Fix: @param args [ { --nodeIndex | -i } <index> ] [ { --config | -c } <configFile> ] [ {--keygen | -k } ]
 */
fun main(args: Array<String>) {
    if (args.isEmpty()) {
        usage()

    } else {
        var i = 0
        while (i < args.size) {
            when (args[i]) {
                "-h", "--help" -> {
                    usage()
                }

                "-t", "--test" -> {
                    println("gtxml file: ${args[i + 1]}")

                    TestLauncher().runXMLGTXTests(File(args[i + 1]).readText())
                }
            }

            i++
        }
    }
}

private fun usage() {
    println("Usage: java -jar postchain-devtools.jar -t <path_to_gtxml_file>")
}
