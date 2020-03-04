package org.mifos.connector.mojaloop.zeebe;

import org.mifos.connector.mojaloop.camel.config.CamelProperties;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.response.WorkflowInstanceEvent;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;


@Component
public class ZeebeProcessStarter {

    private static Logger logger = LoggerFactory.getLogger(ZeebeProcessStarter.class);

    @Autowired
    private ZeebeClient zeebeClient;

    public void startZeebeWorkflow(String workflowId, String request, Consumer<Map<String, Object>> variablesLambda) {
        String transactionId = generateTransactionId();

        Map<String, Object> variables = new HashMap<>();
        variables.put(CamelProperties.TRANSACTION_ID, transactionId);
        variables.put(CamelProperties.TRANSACTION_REQUEST, request);
        variables.put(CamelProperties.ORIGIN_DATE, Instant.now().toEpochMilli());
        variablesLambda.accept(variables);

        WorkflowInstanceEvent instance = zeebeClient.newCreateInstanceCommand()
                .bpmnProcessId(workflowId)
                .latestVersion()
                .variables(variables)
                .send()
                .join();

        logger.debug("zeebee workflow instance {} of type {} created with transactionId {}", instance.getWorkflowInstanceKey(), workflowId, transactionId);
    }

    public static void zeebeVariablesToCamelHeaders(Map<String, Object> variables, Exchange exchange, String... names) {
        for (String name : names) {
            Object value = variables.get(name);
            if (value == null) {
                logger.error("failed to find Zeebe variable name {}", name);
            }
            exchange.getIn().setHeader(name, value);
        }
    }

    public static void camelHeadersToZeebeVariables(Exchange exchange, Map<String, Object> variables, String... names) {
        for (String name : names) {
            String header = exchange.getIn().getHeader(name, String.class);
            if (header == null) {
                logger.error("failed to find Camel Exchange header {}", name);
            }
            variables.put(name, header);
        }
    }

    // TODO generate proper cluster-safe transaction id
    private String generateTransactionId() {
        return UUID.randomUUID().toString();
    }
}