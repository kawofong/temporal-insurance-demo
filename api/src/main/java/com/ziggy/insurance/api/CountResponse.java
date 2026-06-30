// Response body returned by update endpoints that add items to a policy.
// Contains the current count of items after the operation.
package com.ziggy.insurance.api;

public record CountResponse(int count) {}
