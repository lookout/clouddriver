package com.netflix.spinnaker.clouddriver.ecs.cache;

import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.TaskHealthCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.TaskHealth;
import com.netflix.spinnaker.clouddriver.ecs.provider.agent.TaskHealthCachingAgent;
import org.junit.Test;
import spock.lang.Subject;

import java.util.Collections;
import java.util.Map;

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.HEALTH;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

public class TaskHealthCacheClientTest extends CommonCacheClient {
  @Subject
  private final TaskHealthCacheClient client = new TaskHealthCacheClient(cacheView);

  @Test
  public void shouldConvert() {
    //Given
    String taskId = "1dc5c17a-422b-4dc4-b493-371970c6c4d6";
    String key = Keys.getTaskHealthKey(ACCOUNT, REGION, taskId);

    TaskHealth originalTaskHealth = new TaskHealth();
    originalTaskHealth.setTaskId("task-id");
    originalTaskHealth.setType("type");
    originalTaskHealth.setState("RUNNING");
    originalTaskHealth.setInstanceId("i-deadbeef");
    originalTaskHealth.setTaskArn("task-arn");
    originalTaskHealth.setServiceName("service-name");

    Map<String, Object> attributes = TaskHealthCachingAgent.convertTaskHealthToAttributes(originalTaskHealth);
    when(cacheView.get(HEALTH.toString(), key)).thenReturn(new DefaultCacheData(key, attributes, Collections.emptyMap()));

    //When
    TaskHealth retrievedTaskHealth = client.get(key);

    //Then
    assertTrue("Expected the task definition to be " + originalTaskHealth + " but got " + retrievedTaskHealth,
      originalTaskHealth.equals(retrievedTaskHealth));
  }
}
