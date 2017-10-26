/*
 * * Copyright 2017 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.security;

import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.security.config.CredentialsConfig;
import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class EcsAccountMakerTest {

  @Test
  public void shouldMakeAccount() {
    //Given
    String accountName = "ecs-test-account";
    String accountType = "ecs";
    NetflixAmazonCredentials netflixAmazonCredentials = mock(NetflixAmazonCredentials.class);

    //When
    CredentialsConfig.Account account = EcsAccountMaker.makeAccount(netflixAmazonCredentials, accountName, accountType);

    //Then
    assertTrue("The new account should not be of the same type as the old one (" + netflixAmazonCredentials.getAccountType() + ").",
      !account.getAccountType().equals(netflixAmazonCredentials.getAccountType()));
  }
}
