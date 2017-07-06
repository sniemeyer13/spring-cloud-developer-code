package test.pivotal.pal.tracker;


import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import test.pivotal.pal.tracker.support.ApplicationServer;
import test.pivotal.pal.tracker.support.HttpClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;
import static test.pivotal.pal.tracker.support.MapBuilder.jsonMapBuilder;

public class FlowTest {

    private final HttpClient httpClient = new HttpClient();
    private final String workingDir = System.getProperty("user.dir");

    private ApplicationServer registrationServer = new ApplicationServer(workingDir + "/../applications/registration-server/build/libs/registration-server.jar", "8883");
    private ApplicationServer backlogServer = new ApplicationServer(workingDir + "/../applications/backlog-server/build/libs/backlog-server.jar", "8882");
    private ApplicationServer timesheetsServer = new ApplicationServer(workingDir + "/../applications/timesheets-server/build/libs/timesheets-server.jar", "8884");

    private String registrationServerUrl(String path) {
        return "http://localhost:8883" + path;
    }

    private String backlogServerUrl(String path) {
        return "http://localhost:8882" + path;
    }

    private String timesheetsServerUrl(String path) {
        return "http://localhost:8884" + path;
    }

    private long findResponseId(HttpClient.Response response) {
        try {
            return JsonPath.parse(response.body).read("$.id", Long.class);
        } catch (PathNotFoundException e) {
            try {
                return JsonPath.parse(response.body).read("$[0].id", Long.class);
            } catch (PathNotFoundException e1) {
                fail("Could not find id in response body. Response was: \n" + response);
                return -1;
            }
        }
    }


    @Before
    public void setup() throws Exception {
        registrationServer.start();
        backlogServer.start();
        timesheetsServer.start();
        ApplicationServer.waitOnPorts("8882", "8883", "8884");
    }

    @After
    public void tearDown() {
        registrationServer.stop();
        backlogServer.stop();
        timesheetsServer.stop();
    }

    @Test
    public void testBasicFlow() throws Exception {
        HttpClient.Response response;

        response = httpClient.post(registrationServerUrl("/registration"), jsonMapBuilder()
            .put("name", "aUser")
            .build()
        );
        long createdUserId = findResponseId(response);
        assertThat(createdUserId).isGreaterThan(0);

        response = httpClient.get(registrationServerUrl("/users/" + createdUserId));
        assertThat(response.body).isNotNull().isNotEmpty();

        response = httpClient.get(registrationServerUrl("/accounts?ownerId=" + createdUserId));
        long createdAccountId = findResponseId(response);
        assertThat(createdAccountId).isGreaterThan(0);

        response = httpClient.post(registrationServerUrl("/projects"), jsonMapBuilder()
            .put("accountId", createdAccountId)
            .put("name", "aProject")
            .build()
        );
        long createdProjectId = findResponseId(response);
        assertThat(createdProjectId).isGreaterThan(0);

        response = httpClient.get(registrationServerUrl("/projects?accountId=" + createdAccountId));
        assertThat(response.body).isNotNull().isNotEmpty();

        response = httpClient.post(backlogServerUrl("/stories"), jsonMapBuilder()
            .put("projectId", createdProjectId)
            .put("name", "A story")
            .build()
        );
        long createdStoryId = findResponseId(response);
        assertThat(createdStoryId).isGreaterThan(0);

        response = httpClient.get(backlogServerUrl("/stories?projectId" + createdProjectId));
        assertThat(response.body).isNotNull().isNotEmpty();

        response = httpClient.post(timesheetsServerUrl("/time-entries"), jsonMapBuilder()
            .put("projectId", createdProjectId)
            .put("userId", createdUserId)
            .put("date", "2015-12-17")
            .put("hours", 8)
            .build()
        );
        long createdTimeEntryId = findResponseId(response);
        assertThat(createdTimeEntryId).isGreaterThan(0);

        response = httpClient.get(timesheetsServerUrl("/time-entries?projectId" + createdProjectId));
        assertThat(response.body).isNotNull().isNotEmpty();
    }
}