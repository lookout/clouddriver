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

package com.netflix.spinnaker.clouddriver.ecs.provider.role;

import com.netflix.spinnaker.clouddriver.ecs.cache.client.IamRoleCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.IamRole;
import com.netflix.spinnaker.clouddriver.ecs.provider.view.EcsRoleProvider;
import org.junit.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class EcsRoleProviderTest {
  private final IamRoleCacheClient cacheClient = mock(IamRoleCacheClient.class);
  private final EcsRoleProvider provider = new EcsRoleProvider(cacheClient);

  @Test
  public void shouldReturnIamRoles() {
    //When
    int numberOfRoles = 5;
    Collection<IamRole> givenRoles = new HashSet<>();
    for (int x = 0; x < numberOfRoles; x++) {
      IamRole role = new IamRole();
      role.setId("role-id-" + x);
      role.setName("role-name-" + x);
      role.setAccountName("account-name-" + x);
      role.setTrustRelationships(Collections.emptySet());

      givenRoles.add(role);
    }
    when(cacheClient.getAll()).thenReturn(givenRoles);

    //Given
    Collection<IamRole> retrievedRoles = provider.getAll();

    //Then
    assertTrue("Expected " + numberOfRoles + " roles to be returned, but got " + retrievedRoles.size(),
      retrievedRoles.size() == numberOfRoles);
    assertTrue("Expected " + givenRoles + " roles to be returned, but got " + retrievedRoles,
      givenRoles.containsAll(retrievedRoles) && retrievedRoles.containsAll(givenRoles));
  }
}
