/*
 * Copyright 2017 Lookout, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.ecs.provider.agent;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.ecs.AmazonECS;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import org.junit.BeforeClass;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CommonCachingAgent {
  static final String REGION = "us-west-2";
  private static final String ECU_SERVICE = "arn:aws:ecs:" + REGION + ":012345678910:";
  static final String ACCOUNT = "test-account";
  static final String APP_NAME = "testapp";
  static final String ROLE_ARN = ECU_SERVICE + "role/test-role";
  static final String STATUS = "RUNNING";

  static final String SERVICE_NAME_1 = APP_NAME + "-stack-detail-v1";
  static final String SERVICE_NAME_2 = APP_NAME + "-stack-detail-v1";
  static final String SERVICE_ARN_1 = ECU_SERVICE + "service/" + SERVICE_NAME_1;
  static final String SERVICE_ARN_2 = ECU_SERVICE + "service/" + SERVICE_NAME_2;

  static final String CLUSTER_NAME_1 = "test-cluster-1";
  static final String CLUSTER_NAME_2 = "test-cluster-2";
  static final String CLUSTER_ARN_1 = ECU_SERVICE + "cluster/" + CLUSTER_NAME_1;
  static final String CLUSTER_ARN_2 = ECU_SERVICE + "cluster/" + CLUSTER_NAME_2;

  static final String TASK_ID_1 = "1dc5c17a-422b-4dc4-b493-371970c6c4d6";
  static final String TASK_ID_2 = "1dc5c17a-422b-4dc4-b493-371970c6c4d6";
  static final String TASK_ARN_1 = ECU_SERVICE + "task/" + TASK_ID_1;
  static final String TASK_ARN_2 = ECU_SERVICE + "task/" + TASK_ID_2;

  static final String CONTAINER_INSTANCE_ARN_1 = ECU_SERVICE + "container-instance/14e8cce9-0b16-4af4-bfac-a85f7587aa98";
  static final String CONTAINER_INSTANCE_ARN_2 = ECU_SERVICE + "container-instance/deadbeef-0b16-4af4-bfac-a85f7587aa98";

  static final String EC2_INSTANCE_ID_1 = "i-042f39dc";
  static final String EC2_INSTANCE_ID_2 = "i-deadbeef";

  static final String TASK_DEFINITION_ARN_1 = ECU_SERVICE + "task-definition/hello_world:10";
  static final String TASK_DEFINITION_ARN_2 = ECU_SERVICE + "task-definition/hello_world:20";

  static final AmazonECS ecs = mock(AmazonECS.class);
  static final AmazonClientProvider clientProvider = mock(AmazonClientProvider.class);
  final ProviderCache providerCache = mock(ProviderCache.class);
  final AWSCredentialsProvider credentialsProvider = mock(AWSCredentialsProvider.class);
  final Registry registry = mock(Registry.class);
  ObjectMapper mapper = new ObjectMapper();

  @BeforeClass
  public static void setUp() {
    when(clientProvider.getAmazonEcs(anyString(), any(AWSCredentialsProvider.class), anyString())).thenReturn(ecs);
  }

}
