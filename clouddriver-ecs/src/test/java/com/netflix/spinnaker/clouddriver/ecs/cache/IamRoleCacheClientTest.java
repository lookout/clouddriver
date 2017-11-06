package com.netflix.spinnaker.clouddriver.ecs.cache;

import com.amazonaws.services.ecs.model.Task;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.IamRoleCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.TaskCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.IamRole;
import com.netflix.spinnaker.clouddriver.ecs.provider.agent.IamRoleCachingAgent;
import com.netflix.spinnaker.clouddriver.ecs.provider.agent.IamTrustRelationship;
import com.netflix.spinnaker.clouddriver.ecs.provider.agent.TaskCachingAgent;
import com.netflix.spinnaker.clouddriver.model.TrustRelationship;
import org.junit.Test;
import spock.lang.Subject;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Map;

import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.IAM_ROLE;
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.TASKS;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

public class IamRoleCacheClientTest extends CommonCacheClient {
  @Subject
  private final IamRoleCacheClient client = new IamRoleCacheClient(cacheView);

  @Test
  public void shouldConvert() {
    //Given
    ObjectMapper mapper = new ObjectMapper();
    String name = "iam-role-name";
    String key = Keys.getIamRoleKey(ACCOUNT, name);

    IamRole iamRole = new IamRole();
    iamRole.setAccountName("account-name");
    iamRole.setId("test-id");
    iamRole.setName(name);
    IamTrustRelationship iamTrustRelationship = new IamTrustRelationship();
    iamTrustRelationship.setType("Service");
    iamTrustRelationship.setValue("ecs-tasks.amazonaws.com");
    iamRole.setTrustRelationships(Collections.singleton(iamTrustRelationship));

    Map<String, Object> attributes = IamRoleCachingAgent.convertIamRoleToAttributes(iamRole);
    attributes.put("trustRelationships", Collections.singletonList(mapper.convertValue(iamTrustRelationship, Map.class)));

    when(cacheView.get(IAM_ROLE.toString(), key)).thenReturn(new DefaultCacheData(key, attributes, Collections.emptyMap()));

    //When
    IamRole returnedIamRole = client.get(key);

    //Then
    assertTrue("Expected the IAM Role to be " + iamRole + " but got " + returnedIamRole,
      iamRole.equals(returnedIamRole));
  }
}
