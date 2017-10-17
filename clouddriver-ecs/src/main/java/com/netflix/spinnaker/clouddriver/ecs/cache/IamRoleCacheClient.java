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

package com.netflix.spinnaker.clouddriver.ecs.cache;

import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.IamRole;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

public class IamRoleCacheClient implements EcsCacheClient {

  private final Cache cache;

  @Autowired
  public IamRoleCacheClient(Cache cache) {
    this.cache = cache;
  }

  public List<IamRole> findAll() {
    return null;
  }


}
