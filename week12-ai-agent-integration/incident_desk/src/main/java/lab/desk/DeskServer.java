package lab.desk;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.*;
import static java.nio.charset.StandardCharsets.UTF_8;

/** 담당자 화면이 쓰는 HTTP API. 문의 분석은 처리 단계를 SSE로 보내고, 저장은 별도 요청으로만 받습니다. */
public final class DeskServer implements AutoCloseable {
    private final HttpServer server;private final ReviewDesk desk;
    public DeskServer(ReviewDesk desk,int port)throws IOException {
        this.desk=desk;server=HttpServer.create(new InetSocketAddress("127.0.0.1",port),0);
        server.createContext("/inquiries",this::inquiry);
        server.createContext("/inquiries/stream",this::stream);
        server.createContext("/requests",this::save);
        server.start();
    }
    public int port(){return server.getAddress().getPort();}
    @Override public void close(){server.stop(0);}

    /** 한 번에 결과를 받습니다. 업무 상태(needs_input·partial·review 등)는 200 응답의 status로 전달합니다. */
    private void inquiry(HttpExchange ex)throws IOException {
        if(!accept(ex,"/inquiries"))return;
        try{var in=body(ex);send(ex,200,desk.analyze(text(in,"conversationId"),text(in,"text")));}
        catch(IllegalArgumentException e){send(ex,400,Map.of("status","invalid_request","message","conversationId와 text가 필요합니다."));}
    }
    /** 접수·조회·검색 단계를 stage 이벤트로, 검사를 마친 검토본을 result 이벤트로 보냅니다. */
    private void stream(HttpExchange ex)throws IOException {
        if(!accept(ex,"/inquiries/stream"))return;
        String id,text;
        try{var in=body(ex);id=text(in,"conversationId");text=text(in,"text");}catch(IllegalArgumentException e){id=null;text=null;}
        if(id==null||id.isBlank()||text==null||text.isBlank()) {
            send(ex,400,Map.of("status","invalid_request","message","conversationId와 text가 필요합니다."));return;
        }
        ex.getResponseHeaders().set("Content-Type","text/event-stream; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control","no-cache");
        ex.sendResponseHeaders(200,0);
        try(var out=new EventStream(ex.getResponseBody())) {
            var preview=desk.analyze(id,text,event->out.send("stage",event));
            out.send("result",preview);
        }
    }
    private void save(HttpExchange ex)throws IOException {
        if(!accept(ex,"/requests"))return;
        try{var in=body(ex);send(ex,200,desk.save(text(in,"conversationId"),text(in,"previewId"),text(in,"requestId"),text(in,"text")));}
        catch(IllegalArgumentException e){send(ex,400,Map.of("status","invalid_request","message","conversationId·previewId·requestId·text가 필요합니다."));}
    }

    private static boolean accept(HttpExchange ex,String path)throws IOException {
        if(!ex.getRequestURI().getPath().equals(path)){send(ex,404,Map.of("status","not_found"));return false;}
        if(!ex.getRequestMethod().equals("POST")) {
            ex.getResponseHeaders().set("Allow","POST");send(ex,405,Map.of("status","method_not_allowed"));return false;
        }
        return true;
    }
    private static JsonNode body(HttpExchange ex)throws IOException {return Json.read(new String(ex.getRequestBody().readAllBytes(),UTF_8));}
    private static String text(JsonNode in,String field) {var v=in.path(field);return v.isTextual()?v.asText():null;}
    private static void send(HttpExchange ex,int code,Object body)throws IOException {
        byte[] bytes=Json.write(body).getBytes(UTF_8);
        ex.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");
        ex.sendResponseHeaders(code,bytes.length);
        try(var out=ex.getResponseBody()){out.write(bytes);}
    }
    /** 화면이 연결을 끊어도 분석은 끝까지 진행하고 검토본을 남깁니다. 이후 이벤트만 보내지 않습니다. */
    private static final class EventStream implements AutoCloseable {
        private final OutputStream out;private boolean open=true;
        EventStream(OutputStream out){this.out=out;}
        void send(String name,Object data) {
            if(!open)return;
            try{out.write(("event: "+name+"\ndata: "+Json.write(data)+"\n\n").getBytes(UTF_8));out.flush();}
            catch(IOException e){open=false;}
        }
        @Override public void close(){try{out.close();}catch(IOException ignored){}}
    }

    public static void main(String[] args)throws Exception {
        var options=new ArrayList<>(List.of(args));int port=8080;
        int at=options.indexOf("--port");
        if(at>=0) {
            if(at+1>=options.size())throw new IllegalArgumentException("--port 뒤에 번호가 필요합니다.");
            port=Integer.parseInt(options.get(at+1));options.subList(at,at+2).clear();
        }
        if(!Set.of("--live","--fixed").containsAll(options))throw new IllegalArgumentException("--live, --fixed, --port <번호>를 사용하세요.");
        var sources=EvidenceSearch.load(Path.of("data/runbooks.json"));
        var ai=options.contains("--live")?ModelSetup.live(System::getenv,sources):ModelSetup.scripted(sources);
        try(var operations=new OperationsClient(Path.of("data"),Path.of(".local/requests"));
            var server=new DeskServer(new ReviewDesk(new IncidentFlow(operations,ai,!options.contains("--fixed")),operations),port)) {
            System.out.println("모드: "+ai.mode+". http://127.0.0.1:"+server.port()+" 에서 요청을 받습니다. 종료: Enter");
            System.in.read();
        }
    }
}
