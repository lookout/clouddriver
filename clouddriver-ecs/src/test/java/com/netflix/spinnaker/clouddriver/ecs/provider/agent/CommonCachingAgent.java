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
  protected static final String REGION = "us-west-2";
  protected static final String ACCOUNT = "test-account";

  protected static AmazonECS ecs = mock(AmazonECS.class);
  protected static AmazonClientProvider clientProvider = mock(AmazonClientProvider.class);
  protected ProviderCache providerCache = mock(ProviderCache.class);
  protected AWSCredentialsProvider credentialsProvider = mock(AWSCredentialsProvider.class);
  protected Registry registry = mock(Registry.class);

  @BeforeClass
  public static void setUp() {
    when(clientProvider.getAmazonEcs(anyString(), any(AWSCredentialsProvider.class), anyString())).thenReturn(ecs);
  }

}
