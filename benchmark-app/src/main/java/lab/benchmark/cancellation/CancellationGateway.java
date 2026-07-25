package lab.benchmark.cancellation;

public interface CancellationGateway {
    void cancel(String subscriptionId) throws TransientGatewayException, PermanentGatewayException;
}
