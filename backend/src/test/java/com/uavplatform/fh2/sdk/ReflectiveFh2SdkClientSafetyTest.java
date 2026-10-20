package com.uavplatform.fh2.sdk;

import com.dji.flighthubsdk.FlightHubCallback;
import com.dji.flighthubsdk.bean.FlightAreaInfoItem;
import com.dji.flighthubsdk.bean.FlightAreaPagination;
import com.dji.flighthubsdk.bean.FlightHubError;
import com.dji.flighthubsdk.bean.GetCustomizedFlightAreasParams;
import com.dji.flighthubsdk.bean.GetFlightAreasResults;
import com.dji.flighthubsdk.generated.FlightHubSdkErrorCode;
import com.uavplatform.fh2.config.Fh2Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReflectiveFh2SdkClientSafetyTest {
    private static final String AREA_ID = "0d584cf1-257a-4988-82d2-338d30b97cf5";
    private ReflectiveFh2SdkClient client;
    private SafetyModuleStub module;

    @BeforeEach
    void setUp() {
        module = new SafetyModuleStub();
        client = new ReflectiveFh2SdkClient(new Fh2Properties(), null);
        ReflectionTestUtils.setField(client, "ready", true);
        ReflectionTestUtils.setField(client, "workspace", new WorkspaceStub(module));
    }

    @Test
    void missingAreaAfterMigrationCanBeDeleted() {
        module.result = new GetFlightAreasResults(List.of(), new FlightAreaPagination(1, 1, 0));
        assertDoesNotThrow(() -> client.deleteFlightArea(AREA_ID));
        assertEquals(List.of(AREA_ID), module.query.getIds());
        assertTrue(module.query.getType() == null || module.query.getType().isBlank());
        assertTrue(module.query.getStatus() == null || module.query.getStatus().isBlank());
    }

    @Test
    void existingAreaStillReportsDeleteFailure() {
        FlightAreaInfoItem area = new FlightAreaInfoItem();
        area.setId(AREA_ID);
        module.result = new GetFlightAreasResults(List.of(area), new FlightAreaPagination(1, 1, 1));
        assertDeleteFailed();
    }

    @Test
    void serverOmittedTotalWithEmptyFirstPageCanBeDeleted() {
        // 实机响应省略 total 时，SDK 会返回非确定值。
        module.result = new GetFlightAreasResults(List.of(), new FlightAreaPagination(1, 1, 1946328456));
        assertDoesNotThrow(() -> client.deleteFlightArea(AREA_ID));
    }

    @Test
    void defaultPaginationCannotBeTreatedAsMissing() {
        module.result = new GetFlightAreasResults(List.of(), new FlightAreaPagination(0, 0, 0));
        assertDeleteFailed();
    }

    @Test
    void failedLookupCannotBeTreatedAsMissing() {
        module.queryError = error(230001, 500);
        module.result = new GetFlightAreasResults(List.of(), new FlightAreaPagination(1, 1, 0));
        assertEquals(1, assertDeleteFailed().getSuppressed().length);
    }

    @Test
    void nullLookupResultCannotBeTreatedAsMissing() {
        assertDeleteFailed();
    }

    @Test
    void missingPaginationCannotBeTreatedAsMissing() {
        module.result = new GetFlightAreasResults(List.of(), null);
        assertDeleteFailed();
    }

    @Test
    void laterEmptyPageCannotBeTreatedAsMissing() {
        module.result = new GetFlightAreasResults(List.of(), new FlightAreaPagination(2, 1, 1));
        assertDeleteFailed();
    }

    @Test
    void unrelatedErrorsAreNotIgnored() {
        module.deleteError = error(230001, 403);
        assertThrows(IllegalStateException.class, () -> client.deleteFlightArea(AREA_ID));
        assertNull(module.query);
    }

    @Test
    void successfulDeleteDoesNotNeedVerification() {
        module.deleteError = null;
        assertDoesNotThrow(() -> client.deleteFlightArea(AREA_ID));
        assertNull(module.query);
    }

    private IllegalStateException assertDeleteFailed() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> client.deleteFlightArea(AREA_ID));
        assertTrue(error.getMessage().contains("server=230007/http=400"));
        return error;
    }

    private static FlightHubError error(int serverCode, int httpCode) {
        FlightHubError error = new FlightHubError();
        error.setErrorCode(FlightHubSdkErrorCode.ERROR_OPERATION_FAILED);
        error.setServerCode(serverCode);
        error.setHttpCode(httpCode);
        error.setServerMessage("delete area failed");
        return error;
    }

    public static class WorkspaceStub {
        private final SafetyModuleStub module;

        public WorkspaceStub(SafetyModuleStub module) {
            this.module = module;
        }

        public SafetyModuleStub getSafetyServiceModule() {
            return module;
        }
    }

    public static class SafetyModuleStub {
        FlightHubError deleteError = error(230007, 400);
        FlightHubError queryError;
        GetFlightAreasResults result;
        GetCustomizedFlightAreasParams query;

        public void deleteCustomizedFlightAreas(String id, FlightHubCallback.ResultCallbackWithError callback) {
            callback.onResult(deleteError);
        }

        public void getCustomizedFlightAreas(GetCustomizedFlightAreasParams params,
                                             FlightHubCallback.GetCustomizedFlightAreasCallback callback) {
            query = params;
            callback.onResult(queryError, result);
        }
    }
}
