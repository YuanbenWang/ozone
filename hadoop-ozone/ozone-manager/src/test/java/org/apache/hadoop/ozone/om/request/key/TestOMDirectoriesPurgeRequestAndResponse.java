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

package org.apache.hadoop.ozone.om.request.key;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.annotation.Nonnull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.hadoop.hdds.client.BlockID;
import org.apache.hadoop.hdds.utils.TransactionInfo;
import org.apache.hadoop.hdds.utils.db.BatchOperation;
import org.apache.hadoop.hdds.utils.db.cache.CacheKey;
import org.apache.hadoop.hdds.utils.db.cache.CacheValue;
import org.apache.hadoop.ozone.ClientVersion;
import org.apache.hadoop.ozone.om.OMMetadataManager;
import org.apache.hadoop.ozone.om.OmSnapshot;
import org.apache.hadoop.ozone.om.helpers.BucketLayout;
import org.apache.hadoop.ozone.om.helpers.OmBucketInfo;
import org.apache.hadoop.ozone.om.helpers.OmKeyInfo;
import org.apache.hadoop.ozone.om.helpers.OmKeyLocationInfo;
import org.apache.hadoop.ozone.om.helpers.OmKeyLocationInfoGroup;
import org.apache.hadoop.ozone.om.helpers.SnapshotInfo;
import org.apache.hadoop.ozone.om.request.OMRequestTestUtils;
import org.apache.hadoop.ozone.om.response.key.OMDirectoriesPurgeResponseWithFSO;
import org.apache.hadoop.ozone.om.response.key.OMKeyPurgeResponse;
import org.apache.hadoop.ozone.om.snapshot.ReferenceCounted;
import org.apache.hadoop.ozone.protocol.proto.OzoneManagerProtocolProtos;
import org.apache.hadoop.ozone.protocol.proto.OzoneManagerProtocolProtos.OMRequest;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link OMKeyPurgeRequest} and {@link OMKeyPurgeResponse}.
 */
public class TestOMDirectoriesPurgeRequestAndResponse extends TestOMKeyRequest {

  private int numKeys = 10;

  /**
   * Creates volume, bucket and key entries and adds to OM DB and then
   * deletes these keys to move them to deletedKeys table.
   */
  private List<OmKeyInfo> createAndDeleteKeys(Integer trxnIndex, String bucket)
      throws Exception {
    if (bucket == null) {
      bucket = bucketName;
    }
    // Add volume, bucket and key entries to OM DB.
    OMRequestTestUtils.addVolumeAndBucketToDB(volumeName, bucket,
        omMetadataManager);

    List<OmKeyInfo> deletedKeyNames = new ArrayList<>(numKeys);
    List<String> ozoneKeyNames = new ArrayList<>(numKeys);
    for (int i = 1; i <= numKeys; i++) {
      String key = keyName + "-" + i;
      OMRequestTestUtils.addKeyToTable(false, false, volumeName, bucket,
          key, clientID, replicationConfig, trxnIndex++,
          omMetadataManager);
      String ozoneKey = omMetadataManager.getOzoneKey(
          volumeName, bucket, key);
      ozoneKeyNames.add(ozoneKey);
      OmKeyInfo omKeyInfo =
          omMetadataManager.getKeyTable(BucketLayout.DEFAULT).get(ozoneKey);
      deletedKeyNames.add(omKeyInfo);
      updateBlockInfo(omKeyInfo);
    }

    for (String ozoneKey : ozoneKeyNames) {
      OMRequestTestUtils.deleteKey(
          ozoneKey, omMetadataManager, trxnIndex++);
    }

    return deletedKeyNames;
  }

  private void updateBlockInfo(OmKeyInfo omKeyInfo) throws IOException {
    String bucketKey = omMetadataManager.getBucketKey(volumeName, bucketName);
    OmBucketInfo omBucketInfo = omMetadataManager.getBucketTable().get(
        bucketKey);
    List<OmKeyLocationInfoGroup> locationList = new ArrayList<>();
    List<OmKeyLocationInfo> locList = new ArrayList<>();
    OmKeyLocationInfo.Builder builder = new OmKeyLocationInfo.Builder();
    builder.setLength(omKeyInfo.getDataSize()).setBlockID(new BlockID(1, 1));
    locList.add(builder.build());
    locationList.add(new OmKeyLocationInfoGroup(1, locList, false));
    omKeyInfo.setKeyLocationVersions(locationList);
    omBucketInfo.incrUsedBytes(omKeyInfo.getDataSize());
    omBucketInfo.incrUsedNamespace(1L);
    omMetadataManager.getBucketTable().addCacheEntry(new CacheKey<>(bucketKey),
        CacheValue.get(1L, omBucketInfo));
    omMetadataManager.getBucketTable().put(bucketKey, omBucketInfo);
  }

  /**
   * Create OMRequest which encapsulates DeleteKeyRequest.
   * @return OMRequest
   */
  private OMRequest createPurgeKeysRequest(String fromSnapshot, String purgeDeletedDir,
      List<OmKeyInfo> keyList, OmBucketInfo bucketInfo) throws IOException {
    List<OzoneManagerProtocolProtos.PurgePathRequest> purgePathRequestList
        = new ArrayList<>();
    List<OmKeyInfo> subFiles = new ArrayList<>();
    for (OmKeyInfo key : keyList) {
      subFiles.add(key);
    }
    List<OmKeyInfo> subDirs = new ArrayList<>();
    Long volumeId = 1L;
    Long bucketId = bucketInfo.getObjectID();
    OzoneManagerProtocolProtos.PurgePathRequest request = wrapPurgeRequest(
        volumeId, bucketId, purgeDeletedDir, subFiles, subDirs);
    purgePathRequestList.add(request);
    
    OzoneManagerProtocolProtos.PurgeDirectoriesRequest.Builder purgeDirRequest =
        OzoneManagerProtocolProtos.PurgeDirectoriesRequest.newBuilder();
    purgeDirRequest.addAllDeletedPath(purgePathRequestList);
    if (fromSnapshot != null) {
      purgeDirRequest.setSnapshotTableKey(fromSnapshot);
    }
    OzoneManagerProtocolProtos.OMRequest omRequest =
        OzoneManagerProtocolProtos.OMRequest.newBuilder()
            .setCmdType(OzoneManagerProtocolProtos.Type.PurgeDirectories)
            .setPurgeDirectoriesRequest(purgeDirRequest)
            .setClientId(UUID.randomUUID().toString())
            .build();
    return omRequest;
  }
  private OzoneManagerProtocolProtos.PurgePathRequest wrapPurgeRequest(
      final long volumeId, final long bucketId, final String purgeDeletedDir,
      final List<OmKeyInfo> purgeDeletedFiles, final List<OmKeyInfo> markDirsAsDeleted) {
    // Put all keys to be purged in a list
    OzoneManagerProtocolProtos.PurgePathRequest.Builder purgePathsRequest
        = OzoneManagerProtocolProtos.PurgePathRequest.newBuilder();
    purgePathsRequest.setVolumeId(volumeId);
    purgePathsRequest.setBucketId(bucketId);

    if (purgeDeletedDir != null) {
      purgePathsRequest.setDeletedDir(purgeDeletedDir);
    }

    for (OmKeyInfo purgeFile : purgeDeletedFiles) {
      purgePathsRequest.addDeletedSubFiles(
          purgeFile.getProtobuf(true, ClientVersion.CURRENT_VERSION));
    }

    // Add these directories to deletedDirTable, so that its sub-paths will be
    // traversed in next iteration to ensure cleanup all sub-children.
    for (OmKeyInfo dir : markDirsAsDeleted) {
      purgePathsRequest.addMarkDeletedSubDirs(
          dir.getProtobuf(ClientVersion.CURRENT_VERSION));
    }

    return purgePathsRequest.build();
  }
  
  private OMRequest preExecute(OMRequest originalOmRequest) throws IOException {
    OMKeyPurgeRequest omKeyPurgeRequest =
        new OMKeyPurgeRequest(originalOmRequest);

    OMRequest modifiedOmRequest = omKeyPurgeRequest.preExecute(ozoneManager);

    // Will not be equal, as UserInfo will be set.
    assertNotEquals(originalOmRequest, modifiedOmRequest);

    return modifiedOmRequest;
  }

  @Test
  public void testValidateAndUpdateCacheCheckQuota() throws Exception {
    // Create and Delete keys. The keys should be moved to DeletedKeys table
    List<OmKeyInfo> deletedKeyInfos = createAndDeleteKeys(1, null);
    // The keys should be present in the DeletedKeys table before purging
    List<String> deletedKeyNames = validateDeletedKeysTable(omMetadataManager, deletedKeyInfos, true);

    // Create PurgeKeysRequest to purge the deleted keys
    String bucketKey = omMetadataManager.getBucketKey(volumeName, bucketName);
    OmBucketInfo omBucketInfo = omMetadataManager.getBucketTable().get(
        bucketKey);
    OMRequest omRequest = createPurgeKeysRequest(null,
        null, deletedKeyInfos, omBucketInfo);
    OMRequest preExecutedRequest = preExecute(omRequest);
    OMDirectoriesPurgeRequestWithFSO omKeyPurgeRequest =
        new OMDirectoriesPurgeRequestWithFSO(preExecutedRequest);

    assertEquals(1000L * deletedKeyNames.size(), omBucketInfo.getUsedBytes());
    OMDirectoriesPurgeResponseWithFSO omClientResponse
        = (OMDirectoriesPurgeResponseWithFSO) omKeyPurgeRequest
        .validateAndUpdateCache(ozoneManager, 100L);
    omBucketInfo = omMetadataManager.getBucketTable().get(
        bucketKey);
    assertEquals(0L * deletedKeyNames.size(), omBucketInfo.getUsedBytes());

    performBatchOperationCommit(omClientResponse);

    // The keys should exist in the DeletedKeys table after dir delete
    validateDeletedKeys(omMetadataManager, deletedKeyNames);
  }

  @Test
  public void testValidateAndUpdateCacheSnapshotLastTransactionInfoUpdated() throws Exception {
    // Create and Delete keys. The keys should be moved to DeletedKeys table
    List<OmKeyInfo> deletedKeyInfos = createAndDeleteKeys(1, null);
    // The keys should be present in the DeletedKeys table before purging
    List<String> deletedKeyNames = validateDeletedKeysTable(omMetadataManager, deletedKeyInfos, true);

    String snapshotName = "snap1";
    SnapshotInfo snapshotInfo = createSnapshot(snapshotName);
    ReferenceCounted<OmSnapshot> rcOmSnapshot = ozoneManager.getOmSnapshotManager()
        .getSnapshot(snapshotInfo.getVolumeName(), snapshotInfo.getBucketName(), snapshotInfo.getName());
    // Keys should be present in snapshot
    validateDeletedKeysTable(rcOmSnapshot.get().getMetadataManager(), deletedKeyInfos, true);
    // keys should have been moved from AOS
    validateDeletedKeysTable(omMetadataManager, deletedKeyInfos, false);

    // Create PurgeKeysRequest to purge the deleted keys
    assertEquals(snapshotInfo.getLastTransactionInfo(),
        TransactionInfo.valueOf(TransactionInfo.getTermIndex(1L)).toByteString());
    String bucketKey = omMetadataManager.getBucketKey(volumeName, bucketName);
    OmBucketInfo omBucketInfo = omMetadataManager.getBucketTable().get(
        bucketKey);
    OMRequest omRequest = createPurgeKeysRequest(snapshotInfo.getTableKey(),
        null, deletedKeyInfos, omBucketInfo);
    OMRequest preExecutedRequest = preExecute(omRequest);
    OMDirectoriesPurgeRequestWithFSO omKeyPurgeRequest =
        new OMDirectoriesPurgeRequestWithFSO(preExecutedRequest);

    assertEquals(1000L * deletedKeyNames.size(), omBucketInfo.getUsedBytes());
    OMDirectoriesPurgeResponseWithFSO omClientResponse
        = (OMDirectoriesPurgeResponseWithFSO) omKeyPurgeRequest
        .validateAndUpdateCache(ozoneManager, 100L);

    SnapshotInfo snapshotInfoOnDisk = omMetadataManager.getSnapshotInfoTable().getSkipCache(snapshotInfo.getTableKey());
    SnapshotInfo updatedSnapshotInfo = omMetadataManager.getSnapshotInfoTable().get(snapshotInfo.getTableKey());

    assertEquals(snapshotInfoOnDisk, snapshotInfo);
    snapshotInfo.setLastTransactionInfo(TransactionInfo.valueOf(TransactionInfo.getTermIndex(100L))
        .toByteString());
    assertEquals(snapshotInfo, updatedSnapshotInfo);
    omBucketInfo = omMetadataManager.getBucketTable().get(bucketKey);
    assertEquals(0L * deletedKeyNames.size(), omBucketInfo.getUsedBytes());

    performBatchOperationCommit(omClientResponse);

    // The keys should exist in the DeletedKeys table after dir delete
    validateDeletedKeys(rcOmSnapshot.get().getMetadataManager(), deletedKeyNames);
    snapshotInfoOnDisk = omMetadataManager.getSnapshotInfoTable().getSkipCache(snapshotInfo.getTableKey());
    assertEquals(snapshotInfo, snapshotInfoOnDisk);
    rcOmSnapshot.close();
  }

  @Test
  public void testValidateAndUpdateCacheQuotaBucketRecreated()
      throws Exception {
    // Create and Delete keys. The keys should be moved to DeletedKeys table
    List<OmKeyInfo> deletedKeyInfos = createAndDeleteKeys(1, null);
    // The keys should be present in the DeletedKeys table before purging
    List<String> deletedKeyNames = validateDeletedKeysTable(omMetadataManager, deletedKeyInfos, true);

    // Create PurgeKeysRequest to purge the deleted keys
    String bucketKey = omMetadataManager.getBucketKey(volumeName, bucketName);
    OmBucketInfo omBucketInfo = omMetadataManager.getBucketTable().get(
        bucketKey);
    OMRequest omRequest = createPurgeKeysRequest(null,
        null, deletedKeyInfos, omBucketInfo);
    OMRequest preExecutedRequest = preExecute(omRequest);
    OMDirectoriesPurgeRequestWithFSO omKeyPurgeRequest =
        new OMDirectoriesPurgeRequestWithFSO(preExecutedRequest);

    // recreate bucket
    omMetadataManager.getBucketTable().delete(bucketKey);
    OMRequestTestUtils.addBucketToDB(volumeName, bucketName,
        omMetadataManager);
    omBucketInfo = omMetadataManager.getBucketTable().get(
        bucketKey);
    final long bucketInitialUsedBytes = omBucketInfo.getUsedBytes();

    omBucketInfo.incrUsedBytes(1000L);
    omBucketInfo.incrUsedNamespace(100L);
    omMetadataManager.getBucketTable().addCacheEntry(new CacheKey<>(bucketKey),
        CacheValue.get(1L, omBucketInfo));
    omMetadataManager.getBucketTable().put(bucketKey, omBucketInfo);

    // prevalidate bucket
    omBucketInfo = omMetadataManager.getBucketTable().get(bucketKey);
    final long bucketExpectedUsedBytes = bucketInitialUsedBytes + 1000L;
    assertEquals(bucketExpectedUsedBytes, omBucketInfo.getUsedBytes());
    
    // perform delete
    OMDirectoriesPurgeResponseWithFSO omClientResponse
        = (OMDirectoriesPurgeResponseWithFSO) omKeyPurgeRequest
        .validateAndUpdateCache(ozoneManager, 100L);
    
    // validate bucket info, no change expected
    omBucketInfo = omMetadataManager.getBucketTable().get(
        bucketKey);
    assertEquals(bucketExpectedUsedBytes, omBucketInfo.getUsedBytes());

    performBatchOperationCommit(omClientResponse);

    // The keys should exist in the DeletedKeys table after dir delete
    validateDeletedKeys(omMetadataManager, deletedKeyNames);
  }

  private void performBatchOperationCommit(OMDirectoriesPurgeResponseWithFSO omClientResponse) throws IOException {
    try (BatchOperation batchOperation =
             omMetadataManager.getStore().initBatchOperation()) {
      omClientResponse.addToDBBatch(omMetadataManager, batchOperation);
      // Do manual commit and see whether addToBatch is successful or not.
      omMetadataManager.getStore().commitBatchOperation(batchOperation);
    }
  }

  @Nonnull
  private List<String> validateDeletedKeysTable(OMMetadataManager omMetadataManager,
      List<OmKeyInfo> deletedKeyInfos, boolean keyExists) throws IOException {
    List<String> deletedKeyNames = new ArrayList<>();
    for (OmKeyInfo deletedKey : deletedKeyInfos) {
      String keyName = omMetadataManager.getOzoneKey(deletedKey.getVolumeName(),
          deletedKey.getBucketName(), deletedKey.getKeyName());
      assertEquals(omMetadataManager.getDeletedTable().isExist(keyName), keyExists);
      deletedKeyNames.add(keyName);
    }
    return deletedKeyNames;
  }

  private void validateDeletedKeys(OMMetadataManager omMetadataManager,
      List<String> deletedKeyNames) throws IOException {
    for (String deletedKey : deletedKeyNames) {
      assertTrue(omMetadataManager.getDeletedTable().isExist(
          deletedKey));
    }
  }
}
