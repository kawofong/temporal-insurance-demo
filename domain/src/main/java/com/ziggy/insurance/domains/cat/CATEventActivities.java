// Activity interface for filing a CAT event's property claims in concurrent shards.
package com.ziggy.insurance.domains.cat;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface CATEventActivities {

    // Starts a PropertyClaimWorkflow for each index in the shard's [startIndex, endIndex) range.
    // Returns the number of claims started.
    @ActivityMethod
    int fileClaimShard(CATEventShardInput input);
}
