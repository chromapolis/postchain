package com.chromaway.postchain.core

interface Network {
    fun isPrimary(): Boolean
    fun broadcastTx(txData: ByteArray)
}