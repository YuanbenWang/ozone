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

package org.apache.hadoop.ozone.protocol;

import java.util.List;
import org.apache.hadoop.hdds.annotation.InterfaceAudience;
import org.apache.hadoop.hdds.protocol.DatanodeDetails;
import org.apache.hadoop.hdds.protocol.proto.StorageContainerDatanodeProtocolProtos.CommandQueueReportProto;
import org.apache.hadoop.hdds.protocol.proto.StorageContainerDatanodeProtocolProtos.LayoutVersionProto;
import org.apache.hadoop.hdds.protocol.proto.StorageContainerDatanodeProtocolProtos.NodeReportProto;
import org.apache.hadoop.hdds.protocol.proto.StorageContainerDatanodeProtocolProtos.PipelineReportsProto;
import org.apache.hadoop.hdds.protocol.proto.StorageContainerDatanodeProtocolProtos.SCMVersionRequestProto;
import org.apache.hadoop.ozone.protocol.commands.RegisteredCommand;
import org.apache.hadoop.ozone.protocol.commands.SCMCommand;

/**
 * The protocol spoken between datanodes and SCM.
 *
 * Please note that the full protocol spoken between a datanode and SCM is
 * separated into 2 interfaces. One interface that deals with node state and
 * another interface that deals with containers.
 *
 * This interface has functions that deals with the state of datanode.
 */
@InterfaceAudience.Private
public interface StorageContainerNodeProtocol {
  /**
   * Gets the version info from SCM.
   * @param versionRequest - version Request.
   * @return - returns SCM version info and other required information needed
   * by datanode.
   */
  VersionResponse getVersion(SCMVersionRequestProto versionRequest);

  /**
   * Register the node if the node finds that it is not registered with any SCM.
   * @param datanodeDetails DatanodeDetails
   * @param nodeReport NodeReportProto
   * @param pipelineReport PipelineReportsProto
   * @param layoutVersionInfo LayoutVersionProto
   * @return  SCMRegisteredResponseProto
   */
  RegisteredCommand register(DatanodeDetails datanodeDetails,
                             NodeReportProto nodeReport,
                             PipelineReportsProto pipelineReport,
                             LayoutVersionProto layoutVersionInfo);

  /**
   * Send heartbeat to indicate the datanode is alive and doing well.
   *
   * This method is currently used only in tests.
   * TODO: Cleanup and update tests, HDDS-9642.
   *
   * @param datanodeDetails - Datanode ID.
   * @return Commands to be sent to the datanode.
   */
  default List<SCMCommand<?>> processHeartbeat(DatanodeDetails datanodeDetails) {
    return processHeartbeat(datanodeDetails, null);
  };

  /**
   * Send heartbeat to indicate the datanode is alive and doing well.
   * @param datanodeDetails - Datanode ID.
   * @param queueReport - The CommandQueueReportProto report from the
   *                    heartbeating datanode.
   * @return Commands to be sent to the datanode.
   */
  List<SCMCommand<?>> processHeartbeat(DatanodeDetails datanodeDetails,
      CommandQueueReportProto queueReport);

  /**
   * Check if node is registered or not.
   * Return true if Node is registered and false otherwise.
   * @param datanodeDetails - Datanode ID.
   * @return true if Node is registered, false otherwise
   */
  Boolean isNodeRegistered(DatanodeDetails datanodeDetails);

}
