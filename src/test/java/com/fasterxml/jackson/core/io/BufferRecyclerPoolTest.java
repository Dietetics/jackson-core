package com.fasterxml.jackson.core.io;

import com.fasterxml.jackson.core.BaseTest;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.json.JsonGeneratorImpl;
import com.fasterxml.jackson.core.util.BufferRecycler;
import com.fasterxml.jackson.core.util.BufferRecyclerPool;

import java.io.IOException;
import java.io.OutputStream;

public class BufferRecyclerPoolTest extends BaseTest
{
    public void testNoOp() {
        // no-op pool doesn't actually pool anything, so avoid checking it
        checkBufferRecyclerPoolImpl(BufferRecyclerPool.NonRecyclingPool.shared(), false);
    }

    public void testThreadLocal() {
        checkBufferRecyclerPoolImpl(BufferRecyclerPool.ThreadLocalPool.shared(), true);
    }

    public void testLockFree() {
        checkBufferRecyclerPoolImpl(BufferRecyclerPool.LockFreePool.nonShared(), true);
    }

    public void testConcurrentDequeue() {
        checkBufferRecyclerPoolImpl(BufferRecyclerPool.ConcurrentDequePool.nonShared(), true);
    }

    public void testBounded() {
        checkBufferRecyclerPoolImpl(BufferRecyclerPool.BoundedPool.nonShared(1), true);
    }

    private void checkBufferRecyclerPoolImpl(BufferRecyclerPool pool, boolean checkPooledResource) {
        JsonFactory jsonFactory = JsonFactory.builder()
                .bufferRecyclerPool(pool)
                .build();
        BufferRecycler usedBufferRecycler = write("test", jsonFactory, 6);

        if (checkPooledResource) {
            // acquire the pooled BufferRecycler again and check if it is the same instance used before
            BufferRecycler pooledBufferRecycler = pool.acquireBufferRecycler();
            try {
                assertSame(usedBufferRecycler, pooledBufferRecycler);
            } finally {
                pooledBufferRecycler.release();
            }
        }
    }

    protected final BufferRecycler write(Object value, JsonFactory jsonFactory, int expectedSize) {
        BufferRecycler bufferRecycler;
        NopOutputStream out = new NopOutputStream();
        try (JsonGenerator gen = jsonFactory.createGenerator(out)) {
            bufferRecycler = ((JsonGeneratorImpl) gen).ioContext()._bufferRecycler;
            gen.writeObject(value);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        assertEquals(expectedSize, out.size);
        return bufferRecycler;
    }

    public class NopOutputStream extends OutputStream {
        protected int size = 0;

        public NopOutputStream() { }

        @Override
        public void write(int b) throws IOException { ++size; }

        @Override
        public void write(byte[] b) throws IOException { size += b.length; }

        @Override
        public void write(byte[] b, int offset, int len) throws IOException { size += len; }

        public NopOutputStream reset() {
            size = 0;
            return this;
        }
        public int size() { return size; }
    }
}