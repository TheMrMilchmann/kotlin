// DISABLE_NATIVE: gcType=NOOP
// FREE_COMPILER_ARGS: -opt-in=kotlin.native.internal.InternalForKotlinNative,kotlin.native.runtime.NativeRuntimeApi,kotlin.experimental.ExperimentalNativeApi,kotlinx.cinterop.ExperimentalForeignApi

import kotlin.test.*

import kotlin.native.internal.*
import kotlin.concurrent.AtomicInt
import kotlin.concurrent.AtomicReference
import kotlin.native.concurrent.*
import kotlin.native.ref.WeakReference
import kotlin.native.ref.Cleaner
import kotlin.native.ref.createCleaner
import kotlin.native.runtime.GC

class AtomicBoolean(initialValue: Boolean) {
    private val impl = AtomicInt(if (initialValue) 1 else 0)

    public var value: Boolean
        get() = impl.value != 0
        set(new) { impl.value = if (new) 1 else 0 }
}

class FunBox(private val impl: () -> Unit) {
    fun call() {
        impl()
    }
}

@Test
fun testCleanerDestroyInChild() {
    val worker = Worker.start()

    val called = AtomicBoolean(false);
    var funBoxWeak: WeakReference<FunBox>? = null
    var cleanerWeak: WeakReference<Cleaner>? = null
    worker.execute(TransferMode.SAFE, {
        val funBox = FunBox { called.value = true }
        funBoxWeak = WeakReference(funBox)
        val cleaner = createCleaner(funBox) { it.call() }
        cleanerWeak = WeakReference(cleaner)
        Pair(called, cleaner)
    }) { (called, cleaner) ->
        assertFalse(called.value)
    }.result

    GC.collect()
    worker.execute(TransferMode.SAFE, {}) { GC.collect() }.result
    GC.collect()

    assertNull(cleanerWeak!!.value)
    assertTrue(called.value)
    assertNull(funBoxWeak!!.value)

    worker.requestTermination().result
}

@Test
fun testCleanerDestroyWithChild() {
    val worker = Worker.start()

    val called = AtomicBoolean(false);
    var funBoxWeak: WeakReference<FunBox>? = null
    var cleanerWeak: WeakReference<Cleaner>? = null
    worker.execute(TransferMode.SAFE, {
        val funBox = FunBox { called.value = true }
        funBoxWeak = WeakReference(funBox)
        val cleaner = createCleaner(funBox) { it.call() }
        cleanerWeak = WeakReference(cleaner)
        Pair(called, cleaner)
    }) { (called, cleaner) ->
        assertFalse(called.value)
    }.result

    GC.collect()
    worker.requestTermination().result
    waitWorkerTermination(worker)

    GC.collect()

    assertNull(cleanerWeak!!.value)
    assertTrue(called.value)
    assertNull(funBoxWeak!!.value)
}

@Test
fun testCleanerDestroyInMain() {
    val worker = Worker.start()

    val called = AtomicBoolean(false);
    var funBoxWeak: WeakReference<FunBox>? = null
    var cleanerWeak: WeakReference<Cleaner>? = null
    {
        val result = worker.execute(TransferMode.SAFE, { called }) { called ->
            val funBox = FunBox { called.value = true }
            val cleaner = createCleaner(funBox) { it.call() }
            Triple(cleaner, WeakReference(funBox), WeakReference(cleaner))
        }.result
        val cleaner = result.first
        funBoxWeak = result.second
        cleanerWeak = result.third
        assertFalse(called.value)
    }()

    GC.collect()
    worker.execute(TransferMode.SAFE, {}) { GC.collect() }.result
    GC.collect()

    assertNull(cleanerWeak!!.value)
    assertTrue(called.value)
    assertNull(funBoxWeak!!.value)

    worker.requestTermination().result
}

@Test
fun testCleanerDestroyShared() {
    val worker = Worker.start()

    val called = AtomicBoolean(false);
    var funBoxWeak: WeakReference<FunBox>? = null
    var cleanerWeak: WeakReference<Cleaner>? = null
    val cleanerHolder: AtomicReference<Cleaner?> = AtomicReference(null);
    {
        val funBox = FunBox { called.value = true }
        funBoxWeak = WeakReference(funBox)
        val cleaner = createCleaner(funBox) { it.call() }
        cleanerWeak = WeakReference(cleaner)
        cleanerHolder.value = cleaner
        worker.execute(TransferMode.SAFE, { Pair(called, cleanerHolder) }) { (called, cleanerHolder) ->
            cleanerHolder.value = null
            assertFalse(called.value)
        }.result
    }()

    GC.collect()
    worker.execute(TransferMode.SAFE, {}) { GC.collect() }.result
    GC.collect()

    assertNull(cleanerWeak!!.value)
    assertTrue(called.value)
    assertNull(funBoxWeak!!.value)

    worker.requestTermination().result
}

@ThreadLocal
var tlsValue = 11

@Test
fun testCleanerWithTLS() {
    val worker = Worker.start()

    tlsValue = 12

    val value = AtomicInt(0)
    worker.execute(TransferMode.SAFE, {value}) {
        tlsValue = 13
        createCleaner(it) {
            it.value = tlsValue
        }
        Unit
    }.result

    worker.execute(TransferMode.SAFE, {}) { GC.collect() }.result
    GC.collect()

    assertEquals(11, value.value)

    worker.requestTermination().result
}
