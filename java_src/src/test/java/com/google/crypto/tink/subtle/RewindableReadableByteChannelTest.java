// Copyright 2017 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
////////////////////////////////////////////////////////////////////////////////

package com.google.crypto.tink.subtle;

import static com.google.crypto.tink.testing.TestUtil.assertExceptionContains;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.crypto.tink.testing.StreamingTestUtil.ByteBufferChannel;
import com.google.crypto.tink.testing.StreamingTestUtil.PseudorandomReadableByteChannel;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ReadableByteChannel;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for RewindableReadableByteChannel */
@RunWith(JUnit4.class)
public class RewindableReadableByteChannelTest {

  @Test
  public void testOpenClose() throws Exception {
    ReadableByteChannel baseChannel = new ByteBufferChannel("some data".getBytes(UTF_8));
    RewindableReadableByteChannel rewindableChannel =
        new RewindableReadableByteChannel(baseChannel);
    assertTrue(baseChannel.isOpen());

    rewindableChannel.close();
    assertFalse(rewindableChannel.isOpen());
    assertFalse(baseChannel.isOpen());
  }

  @Test
  public void testSingleRead() throws Exception {
    byte[] inputData = "The quick brown fox jumps over the lazy dog.".getBytes(UTF_8);
    ReadableByteChannel baseChannel = new ByteBufferChannel(inputData);
    assertTrue(baseChannel.isOpen());
    RewindableReadableByteChannel rewindableChannel =
        new RewindableReadableByteChannel(baseChannel);

    ByteBuffer buffer = ByteBuffer.allocate(20);
    assertEquals(20, rewindableChannel.read(buffer));
    assertArrayEquals(buffer.array(), Arrays.copyOf(inputData, 20));
  }

  @Test
  public void testReadTwice() throws Exception {
    byte[] inputData = "The quick brown fox jumps over the lazy dog.".getBytes(UTF_8);
    ReadableByteChannel baseChannel = new ByteBufferChannel(inputData);
    assertTrue(baseChannel.isOpen());
    RewindableReadableByteChannel rewindableChannel =
        new RewindableReadableByteChannel(baseChannel);

    ByteBuffer buffer1 = ByteBuffer.allocate(20);
    assertEquals(20, rewindableChannel.read(buffer1));
    ByteBuffer buffer2 = ByteBuffer.allocate(200);
    assertEquals(inputData.length - 20, rewindableChannel.read(buffer2));
    assertArrayEquals(buffer1.array(), Arrays.copyOf(inputData, 20));
    assertArrayEquals(
        Arrays.copyOf(buffer2.array(), buffer2.position()),
        Arrays.copyOfRange(inputData, 20, inputData.length));
  }

  @Test
  public void testReadAfterEof() throws Exception {
    byte[] inputData = "The quick brown fox jumps over the lazy dog.".getBytes(UTF_8);
    ReadableByteChannel baseChannel = new ByteBufferChannel(inputData);
    RewindableReadableByteChannel rewindableChannel =
        new RewindableReadableByteChannel(baseChannel);

    ByteBuffer buffer1 = ByteBuffer.allocate(1000);
    assertEquals(inputData.length, rewindableChannel.read(buffer1));
    ByteBuffer buffer2 = ByteBuffer.allocate(10);
    assertEquals(-1, rewindableChannel.read(buffer2));
    assertArrayEquals(Arrays.copyOf(buffer2.array(), buffer2.position()), new byte[]{});
  }

  @Test
  public void testReadRewindShorterReads() throws Exception {
    byte[] inputData = "The quick brown fox jumps over the lazy dog.".getBytes(UTF_8);
    ReadableByteChannel baseChannel = new ByteBufferChannel(inputData);
    RewindableReadableByteChannel rewindableChannel =
        new RewindableReadableByteChannel(baseChannel);

    ByteBuffer buffer = ByteBuffer.allocate(20);
    assertEquals(20, rewindableChannel.read(buffer));

    rewindableChannel.rewind();

    ByteBuffer buffer2 = ByteBuffer.allocate(15);
    assertEquals(15, rewindableChannel.read(buffer2));
    ByteBuffer buffer3 = ByteBuffer.allocate(15);
    assertEquals(15, rewindableChannel.read(buffer3));
    assertArrayEquals(buffer2.array(), Arrays.copyOf(inputData, 15));
    assertArrayEquals(buffer3.array(), Arrays.copyOfRange(inputData, 15, 30));
  }

  @Test
  public void testReadRewindLongerRead() throws Exception {
    byte[] inputData = "The quick brown fox jumps over the lazy dog.".getBytes(UTF_8);
    ReadableByteChannel baseChannel = new ByteBufferChannel(inputData);
    RewindableReadableByteChannel rewindableChannel =
        new RewindableReadableByteChannel(baseChannel);

    ByteBuffer buffer = ByteBuffer.allocate(20);
    assertEquals(20, rewindableChannel.read(buffer));

    rewindableChannel.rewind();

    ByteBuffer buffer2 = ByteBuffer.allocate(30);
    assertEquals(30, rewindableChannel.read(buffer2));
    assertArrayEquals(buffer2.array(), Arrays.copyOf(inputData, 30));
  }

  @Test
  public void testReadRewindReadEverything() throws Exception {
    byte[] inputData = "The quick brown fox jumps over the lazy dog.".getBytes(UTF_8);
    ReadableByteChannel baseChannel = new ByteBufferChannel(inputData);
    RewindableReadableByteChannel rewindableChannel =
        new RewindableReadableByteChannel(baseChannel);

    ByteBuffer buffer1 = ByteBuffer.allocate(20);
    assertEquals(20, rewindableChannel.read(buffer1));

    rewindableChannel.rewind();

    ByteBuffer buffer2 = ByteBuffer.allocate(300);
    assertEquals(inputData.length, rewindableChannel.read(buffer2));
    assertArrayEquals(Arrays.copyOf(buffer2.array(), buffer2.position()), inputData);
  }

  @Test
  public void testRewindAfterCloseFails() throws Exception {
    byte[] inputData = "The quick brown fox jumps over the lazy dog.".getBytes(UTF_8);
    ReadableByteChannel baseChannel = new ByteBufferChannel(inputData);
    RewindableReadableByteChannel rewindableChannel =
        new RewindableReadableByteChannel(baseChannel);
    rewindableChannel.close();
    try {
      rewindableChannel.rewind();
      fail("Should have thrown exception, as cannot rewind after closing.");
    } catch (IOException expected) {
      assertExceptionContains(expected, "Cannot rewind");
    }
  }

  @Test
  public void testReadAfterCloseFails() throws Exception {
    byte[] inputData = "The quick brown fox jumps over the lazy dog.".getBytes(UTF_8);
    ReadableByteChannel baseChannel = new ByteBufferChannel(inputData);
    RewindableReadableByteChannel rewindableChannel =
        new RewindableReadableByteChannel(baseChannel);
    rewindableChannel.close();
    ByteBuffer buffer = ByteBuffer.allocate(42);
    try {
      int unused = rewindableChannel.read(buffer);
      fail("Should have thrown exception, as cannot read after closing.");
    } catch (ClosedChannelException expected) {
    }
  }

  @Test
  public void testReadToEmptyBuffer() throws Exception {
    byte[] inputData = "The quick brown fox jumps over the lazy dog.".getBytes(UTF_8);
    ReadableByteChannel baseChannel = new ByteBufferChannel(inputData);
    RewindableReadableByteChannel rewindableChannel =
        new RewindableReadableByteChannel(baseChannel);

    ByteBuffer buffer = ByteBuffer.allocate(0);
    assertEquals(0, rewindableChannel.read(buffer));
  }

  @Test
  public void testNoData() throws Exception {
    byte[] inputData = "".getBytes(UTF_8);
    ReadableByteChannel baseChannel = new ByteBufferChannel(inputData);
    RewindableReadableByteChannel rewindableChannel =
        new RewindableReadableByteChannel(baseChannel);

    ByteBuffer buffer = ByteBuffer.allocate(10);
    assertEquals(-1, rewindableChannel.read(buffer));
  }

  @Test
  public void testReadEverything() throws Exception {
    byte[] inputData = "The quick brown fox jumps over the lazy dog.".getBytes(UTF_8);
    ReadableByteChannel baseChannel = new ByteBufferChannel(inputData);
    RewindableReadableByteChannel rewindableChannel =
        new RewindableReadableByteChannel(baseChannel);

    ByteBuffer buffer = ByteBuffer.allocate(1000);
    assertEquals(inputData.length, rewindableChannel.read(buffer));
    assertArrayEquals(Arrays.copyOf(buffer.array(), buffer.position()), inputData);
  }

  @Test
  public void testReadEverythingInChunks() throws Exception {
    byte[] inputData = "The quick brown fox jumps over the lazy dog.".getBytes(UTF_8);
    ReadableByteChannel baseChannel = new ByteBufferChannel(inputData, /*maxChunkSize=*/ 20);
    RewindableReadableByteChannel rewindableChannel =
        new RewindableReadableByteChannel(baseChannel);

    ByteBuffer buffer = ByteBuffer.allocate(1000);
    assertEquals(20, rewindableChannel.read(buffer));
    assertEquals(20, rewindableChannel.read(buffer));
    assertEquals(inputData.length - 40, rewindableChannel.read(buffer));
    assertEquals(-1, rewindableChannel.read(buffer));
    assertArrayEquals(Arrays.copyOf(buffer.array(), inputData.length) , inputData);
  }

  @Test
  public void testSmallChunksNoDataEveryOtherRead() throws Exception {
    byte[] inputData = "The quick brown fox jumps over the lazy dog.".getBytes(UTF_8);
    ReadableByteChannel baseChannel =
        new ByteBufferChannel(inputData, /*maxChunkSize=*/ 10, /*noDataEveryOtherRead=*/ true);
    RewindableReadableByteChannel rewindableChannel =
        new RewindableReadableByteChannel(baseChannel);

    ByteBuffer buffer1 = ByteBuffer.allocate(15);
    assertEquals(0, rewindableChannel.read(buffer1));
    assertEquals(10, rewindableChannel.read(buffer1));
    assertEquals(0, rewindableChannel.read(buffer1));
    assertEquals(5, rewindableChannel.read(buffer1));
    assertArrayEquals(buffer1.array(), Arrays.copyOf(inputData, 15));
  }

  @Test
  public void testNoDataEveryOtherReadShorterSecondRead() throws Exception {
    byte[] inputData = "The quick brown fox jumps over the lazy dog.".getBytes(UTF_8);
    ReadableByteChannel baseChannel =
        new ByteBufferChannel(inputData, /*maxChunkSize=*/ 1000, /*noDataEveryOtherRead=*/ true);
    RewindableReadableByteChannel rewindableChannel =
        new RewindableReadableByteChannel(baseChannel);

    ByteBuffer buffer1 = ByteBuffer.allocate(20);
    assertEquals(0, rewindableChannel.read(buffer1));
    assertEquals(20, rewindableChannel.read(buffer1));

    rewindableChannel.rewind();
    ByteBuffer buffer2 = ByteBuffer.allocate(10);  // read a shorter sequence
    // no read to baseChannel needed, return buffered data.
    assertEquals(10, rewindableChannel.read(buffer2));
    assertArrayEquals(buffer2.array(), Arrays.copyOf(inputData, 10));
  }

  @Test
  public void testNoDataEveryOtherReadLongerSecondRead() throws Exception {
    byte[] inputData = "The quick brown fox jumps over the lazy dog.".getBytes(UTF_8);
    ReadableByteChannel baseChannel =
        new ByteBufferChannel(inputData, /*maxChunkSize=*/ 1000, /*noDataEveryOtherRead=*/ true);
    RewindableReadableByteChannel rewindableChannel =
        new RewindableReadableByteChannel(baseChannel);

    ByteBuffer buffer1 = ByteBuffer.allocate(20);
    assertEquals(0, rewindableChannel.read(buffer1));
    assertEquals(20, rewindableChannel.read(buffer1));

    rewindableChannel.rewind();
    ByteBuffer buffer2 = ByteBuffer.allocate(30);  // read a longer sequence
    // tries to read from baseChannel, but no new data. So only data in buffer is availalbe.
    assertEquals(20, rewindableChannel.read(buffer2));
    // reads remaining bytes from baseChannel
    assertEquals(10, rewindableChannel.read(buffer2));
    assertArrayEquals(buffer2.array(), Arrays.copyOf(inputData, 30));
  }

  @Test
  public void testSmallChunksNoDataEveryOtherReadLongerSecondRead() throws Exception {
    byte[] inputData = "The quick brown fox jumps over the lazy dog.".getBytes(UTF_8);
    ReadableByteChannel baseChannel =
        new ByteBufferChannel(inputData, /*maxChunkSize=*/ 10, /*noDataEveryOtherRead=*/ true);
    RewindableReadableByteChannel rewindableChannel =
        new RewindableReadableByteChannel(baseChannel);

    ByteBuffer buffer1 = ByteBuffer.allocate(12);
    assertEquals(0, rewindableChannel.read(buffer1));
    assertEquals(10, rewindableChannel.read(buffer1));
    assertEquals(0, rewindableChannel.read(buffer1));
    assertEquals(2, rewindableChannel.read(buffer1));

    rewindableChannel.rewind();
    ByteBuffer buffer2 = ByteBuffer.allocate(30);  // read a longer sequence
    // tries to read from baseChannel, but no new data. So only data in buffer is availalbe.
    assertEquals(12, rewindableChannel.read(buffer2));
    // reads remaining bytes from baseChannel, in chunks
    assertEquals(10, rewindableChannel.read(buffer2));
    assertEquals(0, rewindableChannel.read(buffer2));
    assertEquals(8, rewindableChannel.read(buffer2));
    assertArrayEquals(buffer2.array(), Arrays.copyOf(inputData, 30));
  }

  @Test
  public void testNoDataEveryOtherReadEverythingOnSecondRead() throws Exception {
    byte[] inputData = "The quick brown fox jumps over the lazy dog.".getBytes(UTF_8);
    ReadableByteChannel baseChannel =
        new ByteBufferChannel(inputData, /*maxChunkSize=*/ 1000, /*noDataEveryOtherRead=*/ true);
    RewindableReadableByteChannel rewindableChannel =
        new RewindableReadableByteChannel(baseChannel);

    ByteBuffer buffer1 = ByteBuffer.allocate(20);
    assertEquals(0, rewindableChannel.read(buffer1));
    assertEquals(20, rewindableChannel.read(buffer1));

    rewindableChannel.rewind();
    ByteBuffer buffer2 = ByteBuffer.allocate(inputData.length);
    // tries to read from baseChannel, but no new data. So only data in buffer is availalbe.
    assertEquals(20, rewindableChannel.read(buffer2));
    // is able to read data from baseCannel
    assertEquals(inputData.length - 20, rewindableChannel.read(buffer2));
    assertArrayEquals(buffer2.array(), inputData);
  }

  // TODO(juerg): Refactor these tests into smaller tests.
  @Test
  public void testSubsequentReads() throws Exception {
    int inputSize = 1234;
    ReadableByteChannel baseChannel = new PseudorandomReadableByteChannel(inputSize);
    assertTrue(baseChannel.isOpen());
    RewindableReadableByteChannel rewindableChannel =
        new RewindableReadableByteChannel(baseChannel);
    assertTrue(rewindableChannel.isOpen());

    // Read some initial bytes.
    int buffer1Size = 105;
    ByteBuffer buffer1 = ByteBuffer.allocate(buffer1Size);
    int limit1 = 42;
    buffer1.limit(limit1);
    assertEquals(limit1, rewindableChannel.read(buffer1));

    // Continue reading until the buffer is full.
    buffer1.limit(buffer1.capacity());
    assertEquals(buffer1Size - limit1, rewindableChannel.read(buffer1));

    // Rewind, and read a longer sequence of initial bytes.
    rewindableChannel.rewind();
    int buffer2Size = 160;
    ByteBuffer buffer2 = ByteBuffer.allocate(buffer2Size);
    assertEquals(buffer2Size, rewindableChannel.read(buffer2));
    assertArrayEquals(buffer1.array(), Arrays.copyOfRange(buffer2.array(), 0, buffer1Size));

    // Rewind, and read a longer sequence in multiple steps.
    rewindableChannel.rewind();
    int buffer3Size = 150;
    ByteBuffer buffer3 = ByteBuffer.allocate(buffer3Size);
    int stepCount = 5;
    int blockSize = buffer3Size / stepCount;
    for (int i = 1; i <= stepCount; i++) {
      buffer3.limit(i * blockSize);
      assertEquals(blockSize, rewindableChannel.read(buffer3));
    }
    assertArrayEquals(buffer3.array(), Arrays.copyOfRange(buffer2.array(), 0, buffer3Size));

    // Read the remaining bytes and check the size;
    ByteBuffer buffer4 = ByteBuffer.allocate(inputSize);
    assertEquals(inputSize - buffer3Size, rewindableChannel.read(buffer4));
    assertEquals(-1, rewindableChannel.read(buffer4));

    // Close the channel.
    rewindableChannel.close();
    assertFalse(rewindableChannel.isOpen());
    assertFalse(baseChannel.isOpen());
  }

  @Test
  public void testDisableRewind() throws Exception {
    int blockSize = PseudorandomReadableByteChannel.BLOCK_SIZE;
    int extraSize = 123;
    int blockCount = 5;
    int inputSize = blockSize * blockCount + extraSize;
    ReadableByteChannel baseChannel = new PseudorandomReadableByteChannel(inputSize);
    assertTrue(baseChannel.isOpen());
    RewindableReadableByteChannel rewindableChannel =
        new RewindableReadableByteChannel(baseChannel);
    assertTrue(rewindableChannel.isOpen());

    // Read two blocks.
    ByteBuffer twoBlocksBuffer = ByteBuffer.allocate(2 * blockSize);
    assertEquals(2 * blockSize, rewindableChannel.read(twoBlocksBuffer));
    // Verify that the read bytes are not all the same.
    assertFalse(Arrays.equals(Arrays.copyOfRange(twoBlocksBuffer.array(), 0, 42),
            Arrays.copyOfRange(twoBlocksBuffer.array(), 42, 2 * 42)));

    // Rewind and read 1 block + extraSize;
    rewindableChannel.rewind();
    ByteBuffer blockAndExtraBuffer = ByteBuffer.allocate(blockSize + extraSize);
    assertEquals(blockSize + extraSize, rewindableChannel.read(blockAndExtraBuffer));
    assertArrayEquals(blockAndExtraBuffer.array(),
        Arrays.copyOfRange(twoBlocksBuffer.array(), 0, blockSize + extraSize));

    // Disable the rewinding feature, and continue reading.
    rewindableChannel.disableRewinding();
    try {
      rewindableChannel.rewind();
      fail("Should have thrown exception, as rewinding has been dropped");
    } catch (IOException expected) {
      assertExceptionContains(expected, "Cannot rewind");
    }
    ByteBuffer oneBlockBuffer = ByteBuffer.allocate(blockSize);
    assertEquals(blockSize, rewindableChannel.read(oneBlockBuffer));
    assertArrayEquals(oneBlockBuffer.array(),
        Arrays.copyOfRange(twoBlocksBuffer.array(), extraSize, blockSize + extraSize));

    int remainingSize = (blockCount - 2) * blockSize;
    ByteBuffer remainingBuffer = ByteBuffer.allocate(remainingSize);
    assertEquals(remainingSize, rewindableChannel.read(remainingBuffer));
    assertArrayEquals(blockAndExtraBuffer.array(),
        Arrays.copyOfRange(remainingBuffer.array(),
            remainingSize - blockSize - extraSize, remainingSize));

    // Check EOF.
    ByteBuffer buffer = ByteBuffer.allocate(42);
    assertEquals(-1, rewindableChannel.read(buffer));

    // Close the channel.
    rewindableChannel.close();
    assertFalse(rewindableChannel.isOpen());
    assertFalse(baseChannel.isOpen());
  }

  @Test
  public void testExceptions() throws Exception {
    int inputSize = 1234;
    ReadableByteChannel baseChannel = new PseudorandomReadableByteChannel(inputSize);
    baseChannel.close();
    assertFalse(baseChannel.isOpen());
    RewindableReadableByteChannel rewindableChannel =
        new RewindableReadableByteChannel(baseChannel);
    assertFalse(rewindableChannel.isOpen());

    ByteBuffer buffer = ByteBuffer.allocate(42);
    try {
      int unused = rewindableChannel.read(buffer);
      fail("Should have thrown exception, as cannot read after closing.");
    } catch (ClosedChannelException expected) {
    }
  }
}
