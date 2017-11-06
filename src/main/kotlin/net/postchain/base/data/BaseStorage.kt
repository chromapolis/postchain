// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.base.data

import net.postchain.base.Storage
import net.postchain.core.EContext
import net.postchain.core.ProgrammerMistake
import mu.KLogging
import org.apache.commons.dbcp2.BasicDataSource
import javax.sql.DataSource

class BaseStorage(private val writeDataSource: DataSource, private val readDataSource: DataSource, private val nodeId: Int) : Storage {
    companion object: KLogging()

    private fun getConnection(chainID: Long, dataSource: DataSource): EContext {
        val connection = dataSource.connection
        return EContext(connection, chainID, nodeId)
    }

    override fun openReadConnection(chainID: Long): EContext {
        val eContext = getConnection(chainID, readDataSource)
        if (!eContext.conn.isReadOnly) {
            throw ProgrammerMistake("Connection is not read-only")
        }
        return eContext
    }

    override fun closeReadConnection(ectxt: EContext) {
        if (!ectxt.conn.isReadOnly) {
            throw ProgrammerMistake("trying to close a writable connection as a read-only connection")
        }
        ectxt.conn.close()
    }

    override fun openWriteConnection(chainID: Long): EContext {
        return getConnection(chainID, writeDataSource)
    }

    override fun closeWriteConnection(ectxt: EContext, commit: Boolean) {
        val conn = ectxt.conn
//        logger.debug("${ectxt.nodeID} BaseStorage.closeWriteConnection()")
        if (conn.isReadOnly) {
            throw ProgrammerMistake("trying to close a read-only connection as a writeable connection")
        }
        if (commit) conn.commit() else conn.rollback()
        conn.close()
    }

    override fun withSavepoint(ctxt: EContext, fn: () -> Unit) {
        val savepointName = "appendTx${System.nanoTime()}"
        val savepoint = ctxt.conn.setSavepoint(savepointName)
        try {
            fn()
            ctxt.conn.releaseSavepoint(savepoint)
        } catch (e: Exception) {
            logger.debug("Exception in savepoint $savepointName", e)
            ctxt.conn.rollback(savepoint)
        }
    }

    override fun close() {
        if (readDataSource is AutoCloseable) {
            readDataSource.close()
        }
        if (writeDataSource is AutoCloseable) {
            writeDataSource.close()
        }
    }
}

