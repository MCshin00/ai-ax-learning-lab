package lab.inquiry.intake;

import java.util.List;

public record Intake(List<String> services, String symptom, String errorMessage) {}
