package lab.desk;
import com.fasterxml.jackson.databind.JsonNode;
import static lab.desk.Models.*;
public interface Operations {
    Lookup lookup(String serviceId);
    JsonNode save(JsonNode request);
}
