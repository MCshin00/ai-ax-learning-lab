package lab.inquiry.intake;

import java.util.List;

record Intake(List<String> services, String symptom, String errorMessage) {}
