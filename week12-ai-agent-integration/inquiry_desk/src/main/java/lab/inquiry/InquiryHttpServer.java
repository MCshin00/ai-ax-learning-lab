package lab.inquiry;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import lab.inquiry.intake.IntakeWire;
import lab.inquiry.status.RequestWire;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

final class InquiryHttpServer implements AutoCloseable {
    private final HttpServer server;
    private final ReviewSession session;

    InquiryHttpServer(int port, ReviewSession session) throws IOException {
        this.session = session;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    int port() { return server.getAddress().getPort(); }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getPath();
            if (!path.equals("/inquiries") && !path.equals("/requests")) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            if (!exchange.getRequestMethod().equals("POST")) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            boolean inquiry = path.equals("/inquiries");
            String first = inquiry ? "conversationId" : "reviewId";
            String second = inquiry ? "utterance" : "text";
            var input = HttpWire.input(exchange.getRequestBody(), first, second);
            if (input == null) {
                json(exchange, 400, HttpWire.invalid(first, second));
                return;
            }
            if (inquiry) {
                exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
                exchange.sendResponseHeaders(200, 0);
                var events = new Events(exchange.getResponseBody());
                var result = session.accept(input.get(first).textValue(), input.get(second).textValue(),
                        stage -> events.send("stage", Map.of("stage", stage)));
                events.send("result", HttpWire.fields(result));
            } else {
                var result = session.save(input.get(first).textValue(), input.get(second).textValue());
                json(exchange, HttpWire.status(result), RequestWire.fields(result));
            }
        }
    }

    private static void json(HttpExchange exchange, int status, Object value) throws IOException {
        byte[] bytes = IntakeWire.text(value).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private static final class Events {
        private final OutputStream output;
        private boolean disconnected;
        Events(OutputStream output) { this.output = output; }
        void send(String event, Object data) {
            if (disconnected) return;
            try {
                output.write(("event: " + event + "\ndata: " + IntakeWire.text(data) + "\n\n").getBytes(StandardCharsets.UTF_8));
                output.flush();
            } catch (IOException e) { disconnected = true; }
        }
    }

    @Override public void close() { server.stop(0); }
}
