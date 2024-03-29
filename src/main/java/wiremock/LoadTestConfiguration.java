package wiremock;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.Slf4jNotifier;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer;
import com.github.tomakehurst.wiremock.http.UniformDistribution;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.commons.lang3.RandomStringUtils.randomAscii;

public class LoadTestConfiguration {

    private WireMockServer wireMockServer;
    private WireMock wm;

    private String scheme;
    private String host;
    private Integer port;
    private int durationSeconds;
    private int rate;
    private int rampSeconds;

    private Integer stubCount = 6000;

    public static LoadTestConfiguration fromEnvironment() {
        String scheme = System.getenv("SCHEME");
        if (scheme == null) {
            scheme = "http";
        }
        String host = System.getenv("HOST");
        Integer port = envInt("PORT", 0);
        int durationSeconds = envInt("DURATION_SECONDS", 30);
        int rate = envInt("RATE", 200);
        int rampSeconds = envInt("RAMP_SECONDS", 10);

        return new LoadTestConfiguration(scheme, host, port, durationSeconds, rate);
    }

    private static Integer envInt(String key, Integer defaultValue) {
        String valString = System.getenv(key);
        return valString != null ? Integer.parseInt(valString) : defaultValue;
    }

    public LoadTestConfiguration() {
        this(null, null, null, 10, 200);
    }

    public LoadTestConfiguration(String scheme, String host, Integer port, int durationSeconds, int rate) {
        System.out.println("Running for " + durationSeconds + " seconds at rate " + rate);

        if (host == null || port == null) {
            System.out.println("Target: local WireMock server");
            wireMockServer = new WireMockServer(WireMockConfiguration.options()
                    .dynamicPort()
                    .dynamicHttpsPort()
                    .asynchronousResponseEnabled(true)
                    .asynchronousResponseThreads(50)
                    .containerThreads(50)
                    .maxRequestJournalEntries(200)
                    .notifier(new Slf4jNotifier(false))
                    .extensions(new ResponseTemplateTransformer(false)));
            wireMockServer.start();
            this.host = "localhost";
            this.port = wireMockServer.port();
            this.scheme = "http";
            wm = new WireMock(wireMockServer);
        } else {
            System.out.println("Target: " + scheme + "://" + host + ": " + port);
            this.host = host;
            this.port = port;
            this.scheme = scheme;
            wm = WireMock.create()
                    .host(host)
                    .port(port)
                    .scheme(scheme)
                    .build();
        }

        this.durationSeconds = durationSeconds;
        this.rate = rate;
    }

    public void before() {
        System.out.println("Running against: " + getBaseUrl());
        wm.resetToDefaultMappings();
    }

    public void manyStubGetScenario() {
        System.out.println("Registering stubs");

        ExecutorService executorService = Executors.newFixedThreadPool(20);

        wm.register(any(anyUrl()).atPriority(10)
            .persistent(false)
            .willReturn(notFound())
        );

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 1; i <= stubCount; i++) {
            final int count = i;
            futures.add(executorService.submit(new Runnable() {
                @Override
                public void run() {
                    wm.register(get("/load-test/" + count)
                            .persistent(false)
                            .willReturn(ok(randomAscii(2000, 5000))));

                    if (count % 100 == 0) {
                        System.out.print(count + " ");
                    }

                }
            }));

        }

        for (Future<?> future: futures) {
            try {
                future.get(30, SECONDS);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        executorService.shutdown();
    }

    public void onlyPost1000StubScenario() {
        System.out.println("Registering stubs");

        ExecutorService executorService = Executors.newFixedThreadPool(100);

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 1; i <= 1000; i++) {
            final int count = i;
            futures.add(executorService.submit(new Runnable() {
                @Override
                public void run() {
                    wm.register(post("/webhooks/customer/" + count)
                            .persistent(false)
                            .willReturn(okJson("{\"customer\":\"Customer: 993\",\"response\":\"Acknowledgement\"}")));

                    if (count % 100 == 0) {
                        System.out.print(count + " ");
                    }

                }
            }));

        }

        for (Future<?> future: futures) {
            try {
                future.get(30, SECONDS);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        executorService.shutdown();
    }

    public void getLargeStubScenario() {
        System.out.println("Registering stubs");

        ExecutorService executorService = Executors.newFixedThreadPool(10);

        wm.register(any(anyUrl()).atPriority(10)
                .persistent(false)
                .willReturn(notFound())
        );

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            final int count = i;
            futures.add(executorService.submit(new Runnable() {
                @Override
                public void run() {
                    wm.register(get("/load-test/" + count)
                            .persistent(false)
                            .willReturn(ok(randomAscii(50000, 90000))));

                    if (count % 100 == 0) {
                        System.out.print(count + " ");
                    }

                }
            }));

        }

        for (Future<?> future: futures) {
            try {
                future.get(30, SECONDS);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        executorService.shutdown();
    }

    public void mixed100StubScenario() {
        // TODO: Optionally add delay
        wm.setGlobalRandomDelayVariable(new UniformDistribution(100, 2000));

        // Basic GET
        for (int i = 1; i <= 50; i++) {
            wm.register(get("/load-test/" + i)
                .persistent(false)
                .withHeader("Accept", containing("text/plain"))
                .willReturn(ok(randomAscii(1, 2000))));
        }

        // POST JSON equality
        for (int i = 1; i <= 10; i++) {
            wm.register(post("/load-test/json")
                .persistent(false)
                .withHeader("Accept", equalTo("text/plain"))
                .withHeader("Content-Type", matching(".*/json"))
                .withRequestBody(equalToJson(POSTED_JSON))
                .willReturn(ok(randomAscii(i * 200))));
        }

        // POST JSONPath
        for (int i = 1; i <= 10; i++) {
            wm.register(post("/load-test/jsonpath")
                .persistent(false)
                .withHeader("Content-Type", equalTo("application/json"))
                .withRequestBody(matchingJsonPath("$.matchThis.inner.innermost", equalTo("42")))
                .willReturn(created()));
        }

        // POST XML equality
        for (int i = 1; i <= 2; i++) {
            wm.register(post("/load-test/xml")
                .persistent(false)
                .withHeader("Content-Type", matching(".*/xml.*"))
                .withRequestBody(equalToXml(POSTED_XML.replace("$1", String.valueOf(i))))
                .willReturn(ok(randomAscii(i * 200))));
        }

        // POST XML XPath
        for (int i = 1; i <= 10; i++) {
            wm.register(post("/load-test/xpath")
                .persistent(false)
                .withHeader("Content-Type", matching(".*/xml.*"))
//                .withRequestBody(matchingXPath("//description[@subject = 'JWT']/text()", containing("JSON Web Token")))
                .withRequestBody(matchingXPath("//description/text()", containing("JSON Web Token")))
                .willReturn(ok(randomAscii(i * 200))));
        }

        // POST text body regex
        for (int i = 1; i <= 10; i++) {
            wm.register(post("/load-test/text")
                .persistent(false)
                .withHeader("Content-Type", matching(".*text/plain.*"))
                .withRequestBody(matching(".*[0-9]{5}.*"))
                .willReturn(ok(randomAscii(i * 200))));
        }

        wm.register(put("/load-test/templated")
            .persistent(false)
            .willReturn(ok(TEMPLATED_RESPONSE).withTransformers("response-template")));

    }

    public void after() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    public String getScheme() {
        return scheme;
    }

    public String getHost() {
        return host != null ? host : "localhost";
    }

    public int getPort() {
        return port != null ? port : wireMockServer.port();
    }

    public String getBaseUrl() {
        return String.format("%s://%s:%d/", scheme, host, port);
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public int getRate() {
        return rate;
    }

    public int getRampSeconds() {
        return rampSeconds;
    }

    public Integer getStubCount() {
        return stubCount;
    }

    public static final String POSTED_JSON = "{\n" +
        "    \"things\": [\n" +
        "        {\n" +
        "            \"name\": \"First\",\n" +
        "            \"value\": 111\n" +
        "        },\n" +
        "        {\n" +
        "            \"name\": \"Second\",\n" +
        "            \"value\": 22222\n" +
        "        },\n" +
        "        {\n" +
        "            \"name\": \"Third\",\n" +
        "            \"value\": 111\n" +
        "        },\n" +
        "        {\n" +
        "            \"name\": \"Fourth\",\n" +
        "            \"value\": 555\n" +
        "        }\n" +
        "    ],\n" +
        "    \"meta\": {\n" +
        "        \"countOfThings\": 4,\n" +
        "        \"tags\": [\"one\", \"two\"]\n" +
        "    }\n" +
        "}";

    public static final String JSON_FOR_JSON_PATH_MATCH = "{\n" +
        "    \"matchThis\": {\n" +
        "        \"inner\": {\n" +
        "            \"innermost\": 42\n" +
        "        }\n" +
        "    }\n" +
        "}";

    public static final String POSTED_XML = "<?xml version=\"1.0\"?>\n" +
            "\n" +
            "<things id=\"$1\">\n" +
            "    <stuff id=\"1\"/>\n" +
            "    <fluff id=\"2\"/>\n" +
            "\n" +
            "    <inside>\n" +
            "        <deep-inside level=\"3\">\n" +
            "            <one/>\n" +
            "            <two/>\n" +
            "            <three/>\n" +
            "            <four/>\n" +
            "            <one/>\n" +
            "            <description subject=\"JWT\">\n" +
            "                JSON Web Token (JWT) is a compact, URL-safe means of representing claims to be transferred between two parties. The claims in a JWT are encoded as a JSON object that is used as the payload of a JSON Web Signature (JWS) structure or as the plaintext of a JSON Web Encryption (JWE) structure, enabling the claims to be digitally signed or integrity protected with a Message Authentication Code (MAC) and/or encrypted.\n" +
            "            </description>\n" +
            "        </deep-inside>\n" +
            "    </inside>\n" +
            "\n" +
            "</things>";

    public static final String TEMPLATED_RESPONSE = "Templated response\n" +
            "==================\n" +
            "\n" +
            "{{date offset=\"-5 months\" format=\"yyyy-MM-dd\"}}\n" +
            "\n" +
            "{{date (parseDate request.headers.MyDate) timezone='Australia/Sydney'}}\n" +
            "\n" +
            "{{randomValue length=36 type='ALPHANUMERIC_AND_SYMBOLS'}}\n" +
            "\n" +
            "{{jsonPath request.body '$..inner'}}";
}
