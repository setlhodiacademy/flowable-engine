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
package org.flowable.dmn.engine.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.flowable.common.engine.api.FlowableException;
import org.flowable.common.engine.impl.service.CommonEngineServiceImpl;
import org.flowable.dmn.api.DecisionExecutionAuditContainer;
import org.flowable.dmn.api.DecisionServiceExecutionAuditContainer;
import org.flowable.dmn.api.DmnDecisionService;
import org.flowable.dmn.api.ExecuteDecisionBuilder;
import org.flowable.dmn.api.ExecuteDecisionContext;
import org.flowable.dmn.engine.DmnEngineConfiguration;
import org.flowable.dmn.engine.impl.cmd.EvaluateDecisionCmd;
import org.flowable.dmn.engine.impl.cmd.ExecuteDecisionCmd;
import org.flowable.dmn.engine.impl.cmd.ExecuteDecisionServiceCmd;
import org.flowable.dmn.engine.impl.cmd.ExecuteDecisionWithAuditTrailCmd;
import org.flowable.dmn.engine.impl.cmd.PersistHistoricDecisionExecutionCmd;
import org.flowable.dmn.model.Decision;
import org.flowable.dmn.model.DecisionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Yvo Swillens
 */
public class DmnDecisionServiceImpl extends CommonEngineServiceImpl<DmnEngineConfiguration> implements DmnDecisionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DmnDecisionServiceImpl.class);

    public DmnDecisionServiceImpl(DmnEngineConfiguration engineConfiguration) {
        super(engineConfiguration);
    }

    @Override
    public ExecuteDecisionBuilder createExecuteDecisionBuilder() {
        return new ExecuteDecisionBuilderImpl(this);
    }

    @Override
    public Map<String, List<Map<String, Object>>> evaluateDecision(ExecuteDecisionBuilder executeDecisionBuilder) {
        ExecuteDecisionContext executeDecisionContext = executeDecisionBuilder.buildExecuteDecisionContext();

        commandExecutor.execute(new EvaluateDecisionCmd(executeDecisionContext));

        Map<String, List<Map<String, Object>>> decisionResult = composeEvaluateDecisionResult(executeDecisionContext);

        persistDecisionServiceAudit(executeDecisionContext);

        return decisionResult;
    }

    @Override
    public DecisionExecutionAuditContainer evaluateDecisionWithAuditTrail(ExecuteDecisionBuilder executeDecisionBuilder) {
        ExecuteDecisionContext executeDecisionContext = executeDecisionBuilder.buildExecuteDecisionContext();

        commandExecutor.execute(new EvaluateDecisionCmd(executeDecisionContext));

        composeDecisionResult(executeDecisionContext);

        DecisionExecutionAuditContainer decisionExecution = persistDecisionAudit(executeDecisionContext);

        return decisionExecution;
    }

    @Override
    public List<Map<String, Object>> executeDecision(ExecuteDecisionBuilder executeDecisionBuilder) {
        ExecuteDecisionContext executeDecisionContext = executeDecisionBuilder.buildExecuteDecisionContext();

        commandExecutor.execute(new ExecuteDecisionCmd(executeDecisionContext));

        List<Map<String, Object>> decisionResult = composeDecisionResult(executeDecisionContext);

        persistDecisionAudit(executeDecisionContext);

        return decisionResult;
    }

    @Override
    public Map<String, Object> executeDecisionWithSingleResult(ExecuteDecisionBuilder executeDecisionBuilder) {
        ExecuteDecisionContext executeDecisionContext = executeDecisionBuilder.buildExecuteDecisionContext();

        commandExecutor.execute(new ExecuteDecisionCmd(executeDecisionContext));

        Map<String, Object> singleDecisionResult = null;
        List<Map<String, Object>> decisionResult = composeDecisionResult(executeDecisionContext);

        persistDecisionAudit(executeDecisionContext);

        if (decisionResult != null && !decisionResult.isEmpty()) {
            if (decisionResult.size() > 1) {
                throw new FlowableException("more than one result");
            }
            singleDecisionResult = decisionResult.get(0);
        }

        return singleDecisionResult;
    }

    @Override
    public DecisionExecutionAuditContainer executeDecisionWithAuditTrail(ExecuteDecisionBuilder executeDecisionBuilder) {
        ExecuteDecisionContext executeDecisionContext = executeDecisionBuilder.buildExecuteDecisionContext();

        commandExecutor.execute(new ExecuteDecisionWithAuditTrailCmd(executeDecisionContext));

        composeDecisionResult(executeDecisionContext);

        DecisionExecutionAuditContainer decisionExecution = persistDecisionAudit(executeDecisionContext);

        return decisionExecution;
    }

    @Override
    public Map<String, List<Map<String, Object>>> executeDecisionService(ExecuteDecisionBuilder executeDecisionBuilder) {
        ExecuteDecisionContext executeDecisionContext = executeDecisionBuilder.buildExecuteDecisionContext();

        commandExecutor.execute(new ExecuteDecisionServiceCmd(executeDecisionContext));

        Map<String, List<Map<String, Object>>> decisionResult = composeDecisionServiceResult(executeDecisionContext);

        persistDecisionServiceAudit(executeDecisionContext);

        return decisionResult;
    }

    @Override
    public DecisionServiceExecutionAuditContainer executeDecisionServiceWithAuditTrail(ExecuteDecisionBuilder executeDecisionBuilder) {
        ExecuteDecisionContext executeDecisionContext = executeDecisionBuilder.buildExecuteDecisionContext();

        commandExecutor.execute(new ExecuteDecisionServiceCmd(executeDecisionContext));

        composeDecisionServiceResult(executeDecisionContext);

        DecisionServiceExecutionAuditContainer decisionExecution = persistDecisionServiceAudit(executeDecisionContext);

        return decisionExecution;
    }

    protected Map<String, List<Map<String, Object>>> composeEvaluateDecisionResult(ExecuteDecisionContext executeDecisionContext) {
        Map<String, List<Map<String, Object>>> result;

        // check if execution was Decision or DecisionService
        if (executeDecisionContext.getDmnElement() instanceof DecisionService) {
            result = composeDecisionServiceResult(executeDecisionContext);
        } else if (executeDecisionContext.getDmnElement() instanceof Decision) {
            result = new HashMap<>();
            result.put(executeDecisionContext.getDecisionId(), executeDecisionContext.getDecisionExecution().getDecisionResult());
        } else {
            LOGGER.error("Execution was not a decision or decision service");
            throw new FlowableException("Execution was not a decision or decision service");
        }

        return result;
    }

    protected List<Map<String, Object>> composeDecisionResult(ExecuteDecisionContext executeDecisionContext) {
        return executeDecisionContext.getDecisionExecution().getDecisionResult();
    }

    protected Map<String, List<Map<String, Object>>> composeDecisionServiceResult(ExecuteDecisionContext executeDecisionContext) {
        // check if execution was Decision or DecisionService
        if (executeDecisionContext.getDmnElement() instanceof DecisionService) {
            Map<String, List<Map<String, Object>>> decisionServiceResult = new HashMap<>();
            DecisionService decisionService = (DecisionService) executeDecisionContext.getDmnElement();
            DecisionServiceExecutionAuditContainer decisionServiceExecutionAuditContainer = (DecisionServiceExecutionAuditContainer) executeDecisionContext.getDecisionExecution();

            decisionService.getOutputDecisions()
                .forEach(elementReference ->
                    decisionServiceResult.put(
                        elementReference.getParsedId(),
                        executeDecisionContext.getDecisionExecution().getChildDecisionExecution(elementReference.getParsedId()).getDecisionResult()
                    )
                );

            decisionServiceExecutionAuditContainer.setDecisionServiceResult(decisionServiceResult);
            return decisionServiceResult;
        } else {
            throw new FlowableException("Main execution was a not a decision service");
        }
    }

    protected DecisionExecutionAuditContainer persistDecisionAudit(ExecuteDecisionContext executeDecisionContext) {
        DecisionExecutionAuditContainer decisionExecution = executeDecisionContext.getDecisionExecution();

        decisionExecution.stopAudit();

        commandExecutor.execute(new PersistHistoricDecisionExecutionCmd(executeDecisionContext));

        return decisionExecution;
    }

    protected DecisionServiceExecutionAuditContainer persistDecisionServiceAudit(ExecuteDecisionContext executeDecisionContext) {
        DecisionServiceExecutionAuditContainer decisionServiceExecution = (DecisionServiceExecutionAuditContainer) executeDecisionContext.getDecisionExecution();

        decisionServiceExecution.stopAudit();

        commandExecutor.execute(new PersistHistoricDecisionExecutionCmd(executeDecisionContext));

        return decisionServiceExecution;
    }
}