package net.postchain.asserk

import assertk.Assert
import assertk.assertions.support.fail

/**
 * Asserts the ByteArray content is equal to the content of expected one.
 * @see [ByteArray.contentEquals] function
 */
fun Assert<ByteArray>.isСontentEqualTo(expected: ByteArray) {
    if (actual.contentEquals(expected)) return
    fail(expected, actual)
}

/**
 * Asserts the Array content is equal to the content of expected one.
 * @see [Array.contentEquals] function
 */
fun <T> Assert<Array<out T>>.isСontentEqualTo(expected: Array<out T>) {
    if (actual.contentEquals(expected)) return
    fail(expected, actual)
}
