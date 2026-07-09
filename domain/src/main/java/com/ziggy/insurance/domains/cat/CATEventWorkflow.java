// Workflow interface for a catastrophe (CAT) event: declares the event and fans out
// synthetic property claims as ABANDON child workflows, completing once all are filed.
package com.ziggy.insurance.domains.cat;

import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface CATEventWorkflow {

    @WorkflowMethod
    void run(CATEventInput input);

    @QueryMethod
    CATEventStatus getCATEventStatus();
}
