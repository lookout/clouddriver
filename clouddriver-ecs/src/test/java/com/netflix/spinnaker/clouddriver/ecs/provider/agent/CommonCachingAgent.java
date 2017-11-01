/*
 * Copyright 2014 Netflix, Inc.
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
  static final String ACCOUNT = "test-account";

  static final AmazonECS ecs = mock(AmazonECS.class);
  static final AmazonClientProvider clientProvider = mock(AmazonClientProvider.class);
  final ProviderCache providerCache = mock(ProviderCache.class);
  final AWSCredentialsProvider credentialsProvider = mock(AWSCredentialsProvider.class);
  final Registry registry = mock(Registry.class);

  @BeforeClass
  public static void setUp() {
    when(clientProvider.getAmazonEcs(anyString(), any(AWSCredentialsProvider.class), anyString())).thenReturn(ecs);
  }

}
