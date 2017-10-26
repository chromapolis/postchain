package com.chromaway.postchain.test

import com.chromaway.postchain.base.IntegrationTest
import org.apache.commons.configuration2.builder.fluent.Configurations
import org.junit.Test
import java.io.File


class Experiments: IntegrationTest() {

    @Test
    fun testSavepoint() {
        val configs = Configurations()
        val conf = configs.properties(File("config.properties"))
        val storage = baseStorage(conf, 0)
        val ctx = storage.openWriteConnection(1);

        val savepoint = ctx.conn.setSavepoint("A")
        try {
            var statement = ctx.conn.prepareStatement("SELECT ft_register_account($1, $2, $3, $4, $5, $6)")
                    statement.setInt(1, 1)
            //        statement.setInt(2, 2)
            //        statement.setInt(3, 3)
            //        statement.setInt(4, 4)
            //        statement.setInt(5, 5)
            //        statement.setInt(6, 6)
            statement.execute()
        } catch (e: Exception) {
            e.printStackTrace()
            ctx.conn.rollback(savepoint)
            storage.closeWriteConnection(ctx, false)
        }
//        ctx.conn.rollback(savepoint)
    }
//    @Test
//    fun testLineBreaks() {
//
////        val count = 1
////
////        "hello"
////        3
////        +2
////        assertEquals(3, count)
//    }
//
//    @Test
//    fun shouldReturnWithRecoveryAsync() {
//        val executor = ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, SynchronousQueue<Runnable>())
//        val deferred = deferred<Int, Exception>()
//        executor.execute({
//            try {
//                val res = {slowStuff(1)}()
//                deferred.resolve(res)
//            } catch (e: Exception) {
//                deferred.reject(e)
//            }
//        })
//        deferred.promise.success { println("success ${it}") }
//        slowStuff(2)
//    }
//
//    @Test
//    fun shouldReturnWithRecoveryAsync2() {
//        val executor = ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, SynchronousQueue<Runnable>())
//        val future = executor.submit<Int>( { slowStuff(1) })
//
//        CompletableFuture.supplyAsync<Int>({ future.get() })
//                .thenApply { value -> println("Completed async task with result $value") }
//        println("This should come early")
//        println("Completed sync task with result ${slowStuff(2)}")
//    }
//
//    fun slowStuff(c: Int): Int {
//        println("Doing slow stuff $c")
//        Thread.sleep(1000)
//        println("Done doing slow stuff $c")
//        return c+100
//    }

}