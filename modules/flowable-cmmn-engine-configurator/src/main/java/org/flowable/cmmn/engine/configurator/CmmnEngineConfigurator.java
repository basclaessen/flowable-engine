/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.flowable.cmmn.engine.configurator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.flowable.cmmn.api.CallbackTypes;
import org.flowable.cmmn.engine.CmmnEngine;
import org.flowable.cmmn.engine.CmmnEngineConfiguration;
import org.flowable.cmmn.engine.configurator.impl.cmmn.DefaultCaseInstanceService;
import org.flowable.cmmn.engine.configurator.impl.deployer.CmmnDeployer;
import org.flowable.cmmn.engine.configurator.impl.process.DefaultProcessInstanceService;
import org.flowable.cmmn.engine.impl.callback.ChildProcessInstanceStateChangeCallback;
import org.flowable.cmmn.engine.impl.db.EntityDependencyOrder;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.common.engine.api.scope.ScopeTypes;
import org.flowable.common.engine.impl.AbstractEngineConfiguration;
import org.flowable.common.engine.impl.AbstractEngineConfigurator;
import org.flowable.common.engine.impl.EngineDeployer;
import org.flowable.common.engine.impl.callback.RuntimeInstanceStateChangeCallback;
import org.flowable.common.engine.impl.interceptor.EngineConfigurationConstants;
import org.flowable.common.engine.impl.persistence.entity.Entity;
import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.flowable.job.service.JobServiceConfiguration;
import org.flowable.job.service.impl.asyncexecutor.AsyncExecutor;

/**
 * @author Joram Barrez
 */
public class CmmnEngineConfigurator extends AbstractEngineConfigurator<CmmnEngine> {

    protected CmmnEngineConfiguration cmmnEngineConfiguration;

    @Override
    public int getPriority() {
        return EngineConfigurationConstants.PRIORITY_ENGINE_CMMN;
    }

    @Override
    protected List<EngineDeployer> getCustomDeployers() {
        return Collections.singletonList(new CmmnDeployer());
    }

    @Override
    protected String getMybatisCfgPath() {
        return CmmnEngineConfiguration.DEFAULT_MYBATIS_MAPPING_FILE;
    }
    
    @Override
    public void beforeInit(AbstractEngineConfiguration engineConfiguration) {
        super.beforeInit(engineConfiguration);
        
        // When async history is enabled on the bpmn engine, it also gets enabled on the cmmn engine.
        // The same async history executor will be shared between the engine instances (see in the configure method),
        // which will be instantiated by the bpmn engine. However, some properties need to be set here (before instantiation)
        // to have an async history executor that works for both engines
        ProcessEngineConfigurationImpl processEngineConfiguration = getProcessEngineConfiguration(engineConfiguration);
        if (processEngineConfiguration != null && processEngineConfiguration.isAsyncHistoryEnabled()) {
            processEngineConfiguration.setHistoryJobExecutionScope(JobServiceConfiguration.JOB_EXECUTION_SCOPE_ALL);
        }
        
    }

    @Override
    public void configure(AbstractEngineConfiguration engineConfiguration) {
        if (cmmnEngineConfiguration == null) {
            cmmnEngineConfiguration = new CmmnEngineConfiguration();
        }

        initialiseCommonProperties(engineConfiguration, cmmnEngineConfiguration);

        ProcessEngineConfigurationImpl processEngineConfiguration = getProcessEngineConfiguration(engineConfiguration);
        if (processEngineConfiguration != null) {
            copyProcessEngineProperties(processEngineConfiguration);
            
        }

        initEngine();

        if (processEngineConfiguration != null) {
            cmmnEngineConfiguration.getJobServiceConfiguration().getInternalJobManager()
                    .registerScopedInternalJobManager(ScopeTypes.BPMN, processEngineConfiguration.getJobServiceConfiguration().getInternalJobManager());

            processEngineConfiguration.getJobServiceConfiguration().getInternalJobManager()
                    .registerScopedInternalJobManager(ScopeTypes.CMMN, cmmnEngineConfiguration.getJobServiceConfiguration().getInternalJobManager());
        }

        JobServiceConfiguration engineJobServiceConfiguration = getJobServiceConfiguration(engineConfiguration);
        if (engineJobServiceConfiguration != null) {
            engineJobServiceConfiguration.getInternalJobManager()
                    .registerScopedInternalJobManager(ScopeTypes.CMMN, cmmnEngineConfiguration.getJobServiceConfiguration().getInternalJobManager());
        }


        initServiceConfigurations(engineConfiguration, cmmnEngineConfiguration);
    }

    protected void copyProcessEngineProperties(ProcessEngineConfigurationImpl processEngineConfiguration) {
        initProcessInstanceService(processEngineConfiguration);
        initProcessInstanceStateChangedCallbacks(processEngineConfiguration);
        initCaseInstanceService(processEngineConfiguration);
        
        cmmnEngineConfiguration.setEnableTaskRelationshipCounts(processEngineConfiguration.getPerformanceSettings().isEnableTaskRelationshipCounts());
        
        if (processEngineConfiguration.isAsyncHistoryEnabled()) {
            AsyncExecutor asyncHistoryExecutor = processEngineConfiguration.getAsyncHistoryExecutor();
            
            // Inject the async history executor from the process engine. 
            // The job handlers will be added in the CmmnEngineConfiguration itself
            cmmnEngineConfiguration.setAsyncHistoryEnabled(true);
            cmmnEngineConfiguration.setAsyncHistoryExecutor(asyncHistoryExecutor);

            cmmnEngineConfiguration.setAsyncHistoryTaskExecutor(processEngineConfiguration.getAsyncHistoryTaskExecutor());
            
            // See the beforeInit
            cmmnEngineConfiguration.setHistoryJobExecutionScope(JobServiceConfiguration.JOB_EXECUTION_SCOPE_ALL);
        }
    }
    
    protected ProcessEngineConfigurationImpl getProcessEngineConfiguration(AbstractEngineConfiguration engineConfiguration) {
        if (engineConfiguration.getEngineConfigurations().containsKey(EngineConfigurationConstants.KEY_PROCESS_ENGINE_CONFIG)) {
            return (ProcessEngineConfigurationImpl) engineConfiguration.getEngineConfigurations()
                            .get(EngineConfigurationConstants.KEY_PROCESS_ENGINE_CONFIG);
        }
        return null;
    }

    protected JobServiceConfiguration getJobServiceConfiguration(AbstractEngineConfiguration engineConfiguration) {
        if (engineConfiguration.getServiceConfigurations().containsKey(EngineConfigurationConstants.KEY_JOB_SERVICE_CONFIG)) {
            return (JobServiceConfiguration) engineConfiguration.getServiceConfigurations().get(EngineConfigurationConstants.KEY_JOB_SERVICE_CONFIG);
        }

        return null;
    }

    protected void initProcessInstanceService(ProcessEngineConfigurationImpl processEngineConfiguration) {
        cmmnEngineConfiguration.setProcessInstanceService(new DefaultProcessInstanceService(processEngineConfiguration));
    }
    
    protected void initCaseInstanceService(ProcessEngineConfigurationImpl processEngineConfiguration) {
        processEngineConfiguration.setCaseInstanceService(new DefaultCaseInstanceService(cmmnEngineConfiguration));
    }

    protected void initProcessInstanceStateChangedCallbacks(ProcessEngineConfigurationImpl processEngineConfiguration) {
        if (processEngineConfiguration.getProcessInstanceStateChangedCallbacks() == null) {
            processEngineConfiguration.setProcessInstanceStateChangedCallbacks(new HashMap<>());
        }
        Map<String, List<RuntimeInstanceStateChangeCallback>> callbacks = processEngineConfiguration.getProcessInstanceStateChangedCallbacks();
        if (!callbacks.containsKey(CallbackTypes.PLAN_ITEM_CHILD_PROCESS)) {
            callbacks.put(CallbackTypes.PLAN_ITEM_CHILD_PROCESS, new ArrayList<>());
        }
        callbacks.get(CallbackTypes.PLAN_ITEM_CHILD_PROCESS).add(new ChildProcessInstanceStateChangeCallback(cmmnEngineConfiguration));
    }

    @Override
    protected List<Class<? extends Entity>> getEntityInsertionOrder() {
        return EntityDependencyOrder.INSERT_ORDER;
    }

    @Override
    protected List<Class<? extends Entity>> getEntityDeletionOrder() {
        return EntityDependencyOrder.DELETE_ORDER;
    }

    @Override
    protected CmmnEngine buildEngine() {
        if (cmmnEngineConfiguration == null) {
            throw new FlowableException("CmmnEngineConfiguration is required");
        }

        return cmmnEngineConfiguration.buildCmmnEngine();
    }

    public CmmnEngineConfiguration getCmmnEngineConfiguration() {
        return cmmnEngineConfiguration;
    }

    public CmmnEngineConfigurator setCmmnEngineConfiguration(CmmnEngineConfiguration cmmnEngineConfiguration) {
        this.cmmnEngineConfiguration = cmmnEngineConfiguration;
        return this;
    }
}
