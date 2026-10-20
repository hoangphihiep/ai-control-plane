package com.example.aicontrol.controlplane.sanitizer;

public class SuspiciousInputException extends RuntimeException {

    private final String ruleId;

    public SuspiciousInputException(String ruleId) {
        super("Suspicious input detected");
        this.ruleId = ruleId;
    }

    public SuspiciousInputException(String message, String ruleId) {
        super(message);
        this.ruleId = ruleId;
    }

    public String getRuleId() {
        return ruleId;
    }
}
