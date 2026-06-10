package com.gagent.entity;

public enum CiWorkflowStatus {
    PENDING,
    CLONING,
    LISTING_FILES,
    DIAGNOSING,
    FIXING,
    REMEDIATION_FAILED,
    WAIT_HUMAN,
    DONE
}
