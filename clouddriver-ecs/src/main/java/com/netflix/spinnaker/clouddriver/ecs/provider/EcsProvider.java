package com.netflix.spinnaker.clouddriver.ecs.provider;

import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.cats.agent.AgentSchedulerAware;
import com.netflix.spinnaker.clouddriver.cache.SearchableProvider;
import com.netflix.spinnaker.clouddriver.core.provider.agent.HealthProvidingCachingAgent;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.CONTAINER_INSTANCES;
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.ECS_CLUSTERS;
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.SERVICES;
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.TASKS;
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.TASK_DEFINITIONS;


public class EcsProvider extends AgentSchedulerAware implements SearchableProvider {
  public static final String NAME = EcsProvider.class.getName();

  private static final Set<String> defaultCaches = new HashSet<>(Arrays.asList(
    SERVICES.toString(), ECS_CLUSTERS.toString(), TASKS.toString(),
    CONTAINER_INSTANCES.toString(), TASK_DEFINITIONS.toString()));

  private static final Map<String, String> urlMappingTemplates = new HashMap<>();

  private final Collection<Agent> agents;
  private final AccountCredentialsRepository accountCredentialsRepository;
  private final Keys keys = new Keys();
  private Collection<HealthProvidingCachingAgent> healthAgents;

  public EcsProvider(AccountCredentialsRepository accountCredentialsRepository, Collection<Agent> agents) {
    this.agents = agents;
    this.accountCredentialsRepository = accountCredentialsRepository;
  }

  @Override
  public Set<String> getDefaultCaches() {
    return defaultCaches;
  }

  @Override
  public Map<String, String> getUrlMappingTemplates() {
    return urlMappingTemplates;
  }

  @Override
  public Map<SearchableResource, SearchResultHydrator> getSearchResultHydrators() {
    //TODO: Implement if needed - see InstanceSearchResultHydrator as an example.
    return Collections.emptyMap();
  }

  @Override
  public Map<String, String> parseKey(String key) {
    return keys.parseKey(key);
  }

  @Override
  public String getProviderName() {
    return NAME;
  }

  @Override
  public Collection<Agent> getAgents() {
    return agents;
  }


  public void synchronizeHealthAgents() {
    healthAgents = Collections.unmodifiableCollection(agents.stream()
      .filter(a -> a instanceof HealthProvidingCachingAgent)
      .map(a -> (HealthProvidingCachingAgent) a).collect(Collectors.toList()));
  }

  public Collection<HealthProvidingCachingAgent> getHealthAgents() {
    List<HealthProvidingCachingAgent> newHealthAgents = new LinkedList<>();
    newHealthAgents.addAll(healthAgents);
    return Collections.unmodifiableCollection(newHealthAgents);
  }

}