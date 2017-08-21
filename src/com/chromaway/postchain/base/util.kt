package com.chromaway.postchain.base

fun computeMerkleRootHash(cryptoSystem: CryptoSystem, objects: Array<ByteArray>): ByteArray {
    // TODO
    if (objects.size == 2) {
        val h1 = cryptoSystem.digest(objects[0])
        val h2 = cryptoSystem.digest(objects[1])
        return cryptoSystem.digest(h1 + h2)
    } else if (objects.size == 1) {
        return cryptoSystem.digest(objects[0])
    } else {
        val h1 = computeMerkleRootHash(cryptoSystem, objects.sliceArray(IntRange(0, 1)))
        val h2 = computeMerkleRootHash(cryptoSystem, objects.sliceArray(IntRange(2, objects.size - 1)))
        return cryptoSystem.digest(h1 + h2)
    }
}