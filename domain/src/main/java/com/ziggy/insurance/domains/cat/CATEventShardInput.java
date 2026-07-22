// Input for one concurrent shard of a CAT event's claim fan-out.
// startIndex is inclusive, endIndex is exclusive.
package com.ziggy.insurance.domains.cat;

public record CATEventShardInput(
    String catEventId,
    String affectedRegion,
    int startIndex,
    int endIndex
) {}
