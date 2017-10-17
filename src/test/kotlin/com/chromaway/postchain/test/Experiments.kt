package com.chromaway.postchain.test

import com.chromaway.postchain.ebft.BaseBlockDatabase
import nl.komponents.kovenant.deferred
import org.junit.Test
import org.junit.Assert.*
import sun.nio.ch.ThreadPool
import java.util.concurrent.CompletableFuture
import java.util.function.Supplier
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit


class Experiments {
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