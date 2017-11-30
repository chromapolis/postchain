package net.postchain.common

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class TimeLog {

    companion object {
        private val timers = ConcurrentHashMap<String, Timer>()
        private var enabled = false;
        private val nonce = AtomicInteger()

        fun enable(enabled: Boolean) {
            this.enabled = enabled;
        }

        fun startSum(name: String): Int {
            if (!enabled) return -1
            var timer = timers.getOrPut(name, {SumTimer()})
            timer.start(-1)
            return -1
        }

        fun startSumConc(name: String): Int {
            if (!enabled) return -1
            var timer = timers.getOrPut(name, {SumTimer()})
            val n = nonce.getAndIncrement()

            timer.start(n)
            return n
        }

        fun end(name: String, nonce: Int = -1) {
            if (!enabled) return
            // Warning: Not thread safe
            timers.get(name)!!.end(nonce)
        }

        fun getValue(name: String, nano: Boolean = false): Long {
            if (!enabled) return -1
            return if (nano) timers.get(name)!!.value() else timers.get(name)!!.value()/1000000
        }

        fun getLastValue(name: String, nano: Boolean = false): Long {
            if (!enabled) return -1
            return if (nano) timers.get(name)!!.last() else timers.get(name)!!.last()/1000000
        }

        override fun toString(): String {
            var result = ""
            timers.entries.sortedBy { it.key }.forEach {
                result += it.key + ": " + it.value.toString() + "\n"
            }
            return result
        }
    }
}

private interface Timer {
    fun start(nonce: Int)
    fun end(nonce: Int)
    fun value(): Long
    fun last(): Long
}

private class SumTimer: Timer {
    var sum = AtomicLong()
    var lastValue: Long = 0
    val current = ConcurrentHashMap<Int, Long>()
//    var current: Long? = null;
    var entryCount = AtomicInteger()

    override fun start(nonce: Int) {
        if (current.containsKey(nonce)) {
            throw RuntimeException("Starting an already started timer. Nonce=$nonce")
        }
        current.put(nonce, System.nanoTime())
    }

    override fun end(nonce: Int) {
        if (nonce != -1 && !current.containsKey(nonce)) {
            throw RuntimeException("Stopping a non-running timer. Nonce=$nonce")
        }
        // Warning lastValue only works for non-concurrent usage of this timer.
        lastValue = System.nanoTime() - current.get(nonce)!!
        sum.addAndGet(lastValue)
        entryCount.incrementAndGet()
        current.remove(nonce)
    }

    override fun value(): Long {
        return sum.toLong()
    }

    override fun last(): Long {
        return lastValue
    }

    override fun toString(): String {
        return "sum/avg: ${sum.toLong()/1000000} ms / ${sum.toLong()/entryCount.toInt()} ns"
    }
}