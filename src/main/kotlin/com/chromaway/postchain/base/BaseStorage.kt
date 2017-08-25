package com.chromaway.postchain.base

import com.chromaway.postchain.core.EContext

class BaseStorage : Storage {
    override fun openReadConnection(chainID: Int): EContext {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun closeReadConnection(ectxt: EContext) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun openWriteConnection(chainID: Int): EContext {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun closeWriteConnection(ectxt: EContext, commit: Boolean) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun withSavepoint(ctxt: EContext, fn: () -> Unit) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}