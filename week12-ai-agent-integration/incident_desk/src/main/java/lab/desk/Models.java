package lab.desk;
import java.util.*;
public final class Models {
    private Models() {}
    public record Intake(List<String> serviceIds,String symptom,String question) {}
    public record Service(String id,String state,String detail,int revision) {}
    public record Lookup(String status,String serviceId,Service service) {}
    public record Source(String id,String serviceId,String title,String text) {}
    public record Proposal(String serviceId,String answer,List<String> sourceIds) {}
    public record Plan(List<Proposal> items) {}
    public record Item(String serviceId,String status,Service facts,List<Source> sources,String answer) {}
    public record Analysis(String conversationId,String status,String question,List<Item> items,
                           List<Object> trace,int modelCalls,String mode) {}
}
