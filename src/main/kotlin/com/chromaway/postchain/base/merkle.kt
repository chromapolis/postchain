package com.chromaway.postchain.base

val internalNodePrefix = byteArrayOf(0)
val leafPrefix = byteArrayOf(1)
val nonExistingNodePrefix = byteArrayOf(2)
val nonExistingNodeHash = ByteArray(32)

fun log2ceil(value: Int): Int {
    return Math.ceil(Math.log10(value.toDouble())/Math.log10(2.toDouble())).toInt()
}

fun computeMerkleRootHash(cryptoSystem: CryptoSystem, hashes: Array<ByteArray>, depth: Int = 0,
                        leafDepth: Int = log2ceil(hashes.size)): ByteArray {
    if (hashes.size == 0) {
        return ByteArray(32) // Just zeros
    }

    if (depth === leafDepth) {
        return hashes[0]
    }

    val maxLeavesPerChild = Math.pow(2.toDouble(), leafDepth.toDouble() - depth - 1).toInt()
    var prefix =  if (depth == leafDepth - 1) leafPrefix else internalNodePrefix
    if (hashes.size <= maxLeavesPerChild) {
        var left = computeMerkleRootHash(cryptoSystem, hashes, depth + 1, leafDepth)
        return cryptoSystem.digest(prefix + left + nonExistingNodeHash)
    }

    var left = computeMerkleRootHash(cryptoSystem, hashes.sliceArray(IntRange(0, maxLeavesPerChild-1)), depth + 1, leafDepth)
    var right = computeMerkleRootHash(cryptoSystem, hashes.sliceArray(IntRange(maxLeavesPerChild, hashes.lastIndex)), depth + 1, leafDepth)
    return cryptoSystem.digest(prefix + left + prefix + right)
}