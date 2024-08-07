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
package org.flowable.spring.test.expression.callactivity;

import static org.assertj.core.api.Assertions.assertThat;

import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.test.Deployment;
import org.flowable.spring.impl.test.SpringFlowableTestCase;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;

/**
 * The CallActivityBasedOnSpringBeansExpressionTest is isUsed to test dynamically wiring in the calledElement in the callActivity task. This test case helps verify that we do not have to hard code the
 * sub process definition key within the process.
 * 
 * @author Sang Venkatraman
 */
@ContextConfiguration("classpath:org/flowable/spring/test/expression/callactivity/testCallActivityByExpression-context.xml")
@DirtiesContext
public class CallActivityBasedOnSpringBeansExpressionTest extends SpringFlowableTestCase {

    @Test
    @Deployment(resources = { "org/flowable/spring/test/expression/callactivity/CallActivityBasedOnSpringBeansExpressionTest.testCallActivityByExpression.bpmn20.xml",
            "org/flowable/spring/test/expression/callactivity/simpleSubProcess.bpmn20.xml" })
    public void testCallActivityByExpression() throws Exception {
        // Start process (main)
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("testCallActivityByExpression");

        // one task in the subprocess should be active after starting the
        // process instance
        TaskQuery taskQuery = taskService.createTaskQuery();
        Task taskBeforeSubProcess = taskQuery.singleResult();
        assertThat(taskBeforeSubProcess.getName()).isEqualTo("Task before subprocess");

        // Completing the task continues the process which leads to calling the
        // subprocess. The sub process we want to
        // call is passed in as a variable into this task
        taskService.complete(taskBeforeSubProcess.getId());
        Task taskInSubProcess = taskQuery.singleResult();
        assertThat(taskInSubProcess.getName()).isEqualTo("Task in subprocess");

        // Completing the task in the subprocess, finishes the subprocess
        taskService.complete(taskInSubProcess.getId());
        Task taskAfterSubProcess = taskQuery.singleResult();
        assertThat(taskAfterSubProcess.getName()).isEqualTo("Task after subprocess");

        // Completing this task end the process instance
        taskService.complete(taskAfterSubProcess.getId());
        assertProcessEnded(processInstance.getId());
    }

}
