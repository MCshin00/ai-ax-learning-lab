package lab.desk;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import static java.nio.file.StandardOpenOption.*;
import static lab.desk.Models.*;

/** 두 앱이 공유하는 서비스 조회와 작업 요청 저장. 모델을 호출하지 않습니다. */
public final class OperationsStore implements Operations {
    private final Path data, requests;
    public OperationsStore(Path data,Path requests) {this.data=data;this.requests=requests;}
    public Lookup lookup(String id) {
        try {
            for(var row:Json.read(Files.readString(data.resolve("services.json")))) {
                var service=Json.as(row,Service.class);
                if(service.id().equals(id))return new Lookup("found",id,service);
            }
            return new Lookup("not_found",id,null);
        } catch(java.io.IOException e){throw new IllegalStateException("서비스 상태를 읽지 못했습니다.");}
    }
    public synchronized JsonNode save(JsonNode input) {
        if(!input.isObject() || !input.path("requestId").isTextual() || input.path("requestId").asText().isBlank()
            || input.path("requestId").asText().length()>100 || !input.path("text").isTextual()
            || input.path("text").asText().isBlank() || input.path("text").asText().length()>8000
            || !input.path("facts").isArray() || input.path("facts").isEmpty()
            || !input.path("sources").isArray() || input.path("sources").isEmpty())
            throw new IllegalArgumentException("요청 ID·본문·확인한 상태·출처가 필요합니다.");
        try {
            Files.createDirectories(requests);
            String id=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(input.path("requestId").asText().getBytes(StandardCharsets.UTF_8)));
            Path file=requests.resolve(id+".json");
            // 독립 MCP 프로세스들의 중복 저장 검사도 같은 잠금으로 보호합니다.
            try(var channel=FileChannel.open(requests.resolve(".lock"),CREATE,WRITE);var lock=channel.lock()) {
                if(Files.exists(file)) {
                    var saved=Json.read(Files.readString(file));
                    return Json.tree(Map.of("status",saved.path("payload").equals(input)?"already_saved":"conflict","id",id));
                }
                var ids=new HashSet<String>();
                for(var fact:input.path("facts")) {
                    String serviceId=fact.path("id").asText();var now=lookup(serviceId);
                    if(!ids.add(serviceId) || !now.status().equals("found") || !Json.tree(now.service()).equals(fact))
                        return Json.tree(Map.of("status","stale_facts","serviceId",serviceId));
                }
                var sources=EvidenceSearch.load(data.resolve("runbooks.json"));
                var covered=new HashSet<String>();
                for(var source:input.path("sources")) {
                    var s=Json.as(source,Source.class);
                    if(!ids.contains(s.serviceId()) || !sources.contains(s))
                        return Json.tree(Map.of("status","stale_source"));
                    covered.add(s.serviceId());
                }
                if(!covered.equals(ids))throw new IllegalArgumentException("서비스별 근거가 필요합니다.");
                var record=Json.tree(Map.of("id",id,"status","saved","payload",input));
                Path temp=Files.createTempFile(requests,"request-",".tmp");
                try {
                    Files.writeString(temp,Json.write(record));
                    Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE);
                }finally{Files.deleteIfExists(temp);}
                return Json.tree(Map.of("status","saved","id",id));
            }
        }catch(java.io.IOException|java.security.NoSuchAlgorithmException e){throw new IllegalStateException("작업 요청을 저장하지 못했습니다.");}
    }
    public JsonNode read(String id) {
        if(id==null||!id.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("저장 ID를 확인하세요.");
        try {
            var file=requests.resolve(id+".json");
            return Files.exists(file)?Json.read(Files.readString(file)):Json.tree(Map.of("status","not_found"));
        }catch(java.io.IOException e){throw new IllegalStateException("작업 요청을 읽지 못했습니다.");}
    }
}
