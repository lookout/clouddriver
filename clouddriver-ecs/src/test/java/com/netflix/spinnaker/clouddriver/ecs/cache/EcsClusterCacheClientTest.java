package com.netflix.spinnaker.clouddriver.ecs.cache;

import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.EcsClusterCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.EcsCluster;
import org.junit.Test;
import spock.lang.Subject;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.ECS_CLUSTERS;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

public class EcsClusterCacheClientTest extends CommonCacheClient {
  @Subject
  private EcsClusterCacheClient client = new EcsClusterCacheClient(cacheView);

  @Test
  public void shouldConvert() {
    //Given
    String clusterName = "test-cluster";
    String clusterArn = "arn:aws:ecs:" + REGION + ":012345678910:cluster/" + clusterName;
    String key = Keys.getClusterKey(ACCOUNT, REGION, clusterName);

    Map<String, Object> attributes = new HashMap<>();
    attributes.put("account", ACCOUNT);
    attributes.put("region", REGION);
    attributes.put("clusterName", clusterName);
    attributes.put("clusterArn", clusterArn);

    when(cacheView.get(ECS_CLUSTERS.toString(), key)).thenReturn(new DefaultCacheData(key, attributes, Collections.emptyMap()));

    //When
    EcsCluster ecsCluster = client.get(key);

    //Then
    assertTrue("Expected cluster name to be " + clusterName + " but got " + ecsCluster.getName(), clusterName.equals(ecsCluster.getName()));
    assertTrue("Expected cluster ARN to be " + clusterArn + " but got " + ecsCluster.getArn(), clusterArn.equals(ecsCluster.getArn()));
    assertTrue("Expected cluster account to be " + ACCOUNT + " but got " + ecsCluster.getAccount(), ACCOUNT.equals(ecsCluster.getAccount()));
    assertTrue("Expected cluster region to be " + REGION + " but got " + ecsCluster.getRegion(), REGION.equals(ecsCluster.getRegion()));
  }
}
