/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ozone.erasurecode.rawcoder;

import java.nio.ByteBuffer;
import org.apache.hadoop.hdds.annotation.InterfaceAudience;
import org.apache.hadoop.hdds.client.ECReplicationConfig;

/**
 * A raw decoder in XOR code scheme in pure Java, adapted from HDFS-RAID.
 *
 * XOR code is an important primitive code scheme in erasure coding and often
 * used in advanced codes, like HitchHiker and LRC, though itself is rarely
 * deployed independently.
 */
@InterfaceAudience.Private
public class XORRawDecoder extends RawErasureDecoder {

  public XORRawDecoder(ECReplicationConfig ecReplicationConfig) {
    super(ecReplicationConfig);
  }

  @Override
  protected void doDecode(ByteBufferDecodingState decodingState) {
    CoderUtil.resetOutputBuffers(decodingState.outputs,
        decodingState.decodeLength);
    ByteBuffer output = decodingState.outputs[0];

    int erasedIdx = decodingState.erasedIndexes[0];

    // Process the inputs.
    int iIdx, oIdx;
    for (int i = 0; i < decodingState.inputs.length; i++) {
      // Skip the erased location.
      if (i == erasedIdx) {
        continue;
      }

      for (iIdx = decodingState.inputs[i].position(), oIdx = output.position();
           iIdx < decodingState.inputs[i].limit();
           iIdx++, oIdx++) {
        output.put(oIdx, (byte) (output.get(oIdx) ^
            decodingState.inputs[i].get(iIdx)));
      }
    }
  }

  @Override
  protected void doDecode(ByteArrayDecodingState decodingState) {
    byte[] output = decodingState.outputs[0];
    int dataLen = decodingState.decodeLength;
    CoderUtil.resetOutputBuffers(decodingState.outputs,
        decodingState.outputOffsets, dataLen);
    int erasedIdx = decodingState.erasedIndexes[0];

    // Process the inputs.
    int iIdx, oIdx;
    for (int i = 0; i < decodingState.inputs.length; i++) {
      // Skip the erased location.
      if (i == erasedIdx) {
        continue;
      }

      for (iIdx = decodingState.inputOffsets[i],
               oIdx = decodingState.outputOffsets[0];
           iIdx < decodingState.inputOffsets[i] + dataLen; iIdx++, oIdx++) {
        output[oIdx] ^= decodingState.inputs[i][iIdx];
      }
    }
  }

}
