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

package org.apache.hadoop.ozone.om.response;

import org.apache.hadoop.ozone.om.helpers.OmBucketInfo;
import org.apache.hadoop.util.Time;

/**
 * Helper class to test OMClientResponse classes.
 */
public final class TestOMResponseUtils {

  // No one can instantiate, this is just utility class with all static methods.
  private TestOMResponseUtils() {
  }

  public static  OmBucketInfo createBucket(String volume, String bucket) {
    return OmBucketInfo.newBuilder().setVolumeName(volume).setBucketName(bucket)
        .setCreationTime(Time.now()).setIsVersionEnabled(true).addMetadata(
            "key1", "value1").build();

  }

}
