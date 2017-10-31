package com.netflix.spinnaker.clouddriver.ecs.cache;

import com.netflix.spinnaker.cats.cache.Cache;

import static org.mockito.Mockito.mock;

public class CommonCacheClient {
  protected static final String REGION = "us-west-2";
  protected static final String ACCOUNT = "test-account";

  protected Cache cacheView = mock(Cache.class);
}
