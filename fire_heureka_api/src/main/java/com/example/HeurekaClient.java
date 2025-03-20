package com.example;

import java.io.FileInputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.json.JSONArray;
import org.json.JSONObject;

import org.slf4j.LoggerFactory;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;



public class HeurekaClient {

    private ExitOnErrorLogger logger;
    private DataWriter writer;
    private TokenManager tokenManager;
    private static Properties properties;

    private Map<String, ArrayList<String>> grants;
    private String userId;
    private String fhirEndpoint;

    private OkHttpClient defaultClient;
    private OkHttpClient proxyClient;

    private FhirContext ctx;
    private IParser jsonParser;


    public HeurekaClient(String userId, TokenManager tokenManager, DataWriter writer) {
        this.logger = new ExitOnErrorLogger(LoggerFactory.getLogger(HeurekaClient.class));
        this.writer = writer;
        this.tokenManager = tokenManager;
        properties = ConfigReader.getConfigReader().getProperties();

        this.grants = new HashMap<>();
        grants.put("Observation", new ArrayList<>());
        grants.put("MedicationStatement", new ArrayList<>());
        grants.put("Condition", new ArrayList<>());
        grants.put("Patient", new ArrayList<>());
        this.userId = userId;

        this.defaultClient = createHttpClient(false);
        this.proxyClient = createHttpClient(true);

        ctx = FhirContext.forR4();
        jsonParser = ctx.newJsonParser().setPrettyPrint(true);
    }


    /*
     * Creates two different clients for the HTTP requests: ProxyClient and DefaultClient
     * ProxyClient used a proxy for requests (used when downloading patients and their data)
     * DefaultClient is used for configuring the Heureka connection and retrieving tokens
     */
    private OkHttpClient createHttpClient(boolean useProxy) {
        try {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            try (FileInputStream fis = new FileInputStream(properties.getProperty("cert.p12"))) {
                keyStore.load(fis, properties.getProperty("cert.pw").toCharArray());
            }

            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            try (FileInputStream trustStoreStream = new FileInputStream(properties.getProperty("cert.cacert"))) {
                trustStore.load(trustStoreStream, properties.getProperty("cert.pw").toCharArray());
            }



            OkHttpClient.Builder builder = new OkHttpClient.Builder();
            if (useProxy) {
                Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("tunnel.testing.heureka.health", 7000));
                builder.proxy(proxy);
            }
            createBuilderWithSSLContext(builder, keyStore, trustStore);
            builder.addInterceptor(new RateLimitingInterceptor());
            return builder.build();
        } catch (Exception e) {
            logger.error("Exception raised while creating the HttpClient: " + e.getMessage());
        }
        return null;
    }


    // Establishes a SSLContext (for a secure communication in the HTTP requests). Uses fire.crt and fire.key
    private static void createBuilderWithSSLContext(OkHttpClient.Builder builder, KeyStore keyStore, KeyStore trustStore) throws Exception {
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, properties.getProperty("cert.pw").toCharArray());

        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        X509Certificate caCertificate;
        try (InputStream caInputStream = new FileInputStream(properties.getProperty("cert.pem"))) {
            caCertificate = (X509Certificate) certificateFactory.generateCertificate(caInputStream);
            trustStore.setCertificateEntry("ca-fire-heureka", caCertificate);
        }


        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());

        builder.sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) tmf.getTrustManagers()[0]);

        
    }





    

    public void configureHeureka() {
        if (fhirEndpoint != null) {
            return;
        }

        String configurationUrl = "https://api.testing.heureka.health/api-configuration";
        String accessToken = tokenManager.getAccessToken();
        if (accessToken == null) {
            logger.error("Access token not in database for userId " + userId);
            return;
        }

        try {
            Request request = new Request.Builder()
                .url(configurationUrl)
                .addHeader("Authorization", "Bearer " + accessToken)
                .get()
                .build();

            try (Response response = defaultClient.newCall(request).execute()) {
                JSONObject responseObject = new JSONObject(response.body().string());
                this.fhirEndpoint = responseObject.getString("fhirEndpoint");
                JSONObject grantsObject = responseObject.getJSONObject("grants");
            
                for (String key : grantsObject.keySet()) {
                    JSONArray jsonArray = grantsObject.getJSONArray(key);
                    ArrayList<String> values = new ArrayList<>();
                    for (int i = 0; i < jsonArray.length(); i++) {
                        values.add(jsonArray.getString(i));
                    }
                    grants.put(getGrantsName(key), values);
                }

                System.out.println(grants);
            }

        } catch (java.net.UnknownHostException e) {
            logger.error("Unknown host: " + e.getMessage());
        } catch (java.net.ConnectException e) {
            logger.error("Connection failed: " + e.getMessage());
        } catch (java.net.http.HttpTimeoutException e) {
            logger.error("Request timed out: " + e.getMessage());
        } catch (Exception e) {
            logger.error("General error: " + e.getMessage());
        }
    }



    Encrypter encrypter = new Encrypter();

    public Map<String, String> getNewToken() {
        Map<String, String> newTokenInfo = new HashMap<>();

        String refreshUrl = "https://token.testing.heureka.health/oauth2/token";
        String refreshToken = tokenManager.getRefreshToken();
        if (refreshToken == null) {
            logger.error("No refresh token found in database");
            return null;
        }

        try {
            RequestBody body = new FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("client_id", System.getenv("CLIENT_ID"))
            .add("refresh_token", refreshToken)
            .build();

            Request request = new Request.Builder()
            .url(refreshUrl)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .post(body)
            .build();


            try (Response response = defaultClient.newCall(request).execute()) {
                String responseBody = response.body().string();

                if (response.code() != 200) {
                    logger.error("CURL ERROR when requesting new access token: " + responseBody);
                    return null;
                }
        
                JSONObject responseData = new JSONObject(responseBody);
        
                if (responseData.has("access_token") && responseData.has("refresh_token")) {
                    logger.info("Updated tokens for Praxis " + userId);
                    newTokenInfo.put("access_token", responseData.getString("access_token"));
                    newTokenInfo.put("refresh_token", responseData.getString("refresh_token"));
                    logger.info("Praxis " + userId + ": " + encrypter.encrypt(responseData.getString("refresh_token")));
                    newTokenInfo.put("expires_in", String.valueOf(responseData.getInt("expires_in")));
        
                    return newTokenInfo;
                } else {
                    logger.error("Access token or refresh token not found in the response.");
                    return null;
                }
            }
        } catch (Exception e) {
            logger.error("Error while requesting information: " + e.getMessage());
        }

        return null;
    }




    Map<String, String> headers = new HashMap<>();


    public void fullDownload() {
        int offset = 0;
        String uuid = UUID.randomUUID().toString();
        String contextType = "PATIENT_EXPORT";
        String heurekaRole = "SYSTEM";
        boolean hasNext = true;

        Bundle responseBundle;
        Bundle writerBundle = new Bundle();
        writerBundle.setType(Bundle.BundleType.COLLECTION);
        
        if (hasReadPermissions("Patient")) {
            while (hasNext) {
                String urlEnd = "Patient?_count=300&_offset=" + String.valueOf(offset);
                String accessToken = tokenManager.getAccessToken();
    
                headers.put("Authorization", "Bearer " + accessToken);
                headers.put("X-HEUREKA-RequestContextId", uuid);
                headers.put("X-HEUREKA-RequestContextType", contextType);
                headers.put("X-HEUREKA-UserRole", heurekaRole);
    
                responseBundle = executeGet(urlEnd, headers, true);
    
                if (responseBundle != null) {
                    hasNext = checkForMoreResources(responseBundle);
                        
                    int count = 1;
    
                    for (Bundle.BundleEntryComponent entry : responseBundle.getEntry()) {
                        IBaseResource resource = entry.getResource();
                        if (resource instanceof Patient) {
                            Patient patient = (Patient) resource;
                            writerBundle.addEntry().setResource(patient);
    
                            String patientId = patient.getIdPart();
    
                            logger.info("Patient [" + (offset+count) + "]");
                            
                            Bundle conditions = getResources(patientId, headers, "Condition");
                            Bundle observations = getResources(patientId, headers, "Observation");
                            Bundle medications = getResources(patientId, headers, "MedicationStatement");
                            
                            writerBundle.getEntry().addAll(conditions.getEntry());
                            writerBundle.getEntry().addAll(observations.getEntry());
                            writerBundle.getEntry().addAll(medications.getEntry());
    
                            count++;
                        }
                    }
                } else {
                    logger.error("Too many failed Http Requests");
                }
    
                offset += 300;
            }
        } else {
            logger.info("Permission to READ PATIENTS not found");
        }

        String jsonPatients = jsonParser.encodeResourceToString(writerBundle);
        writer.writeOnFile(jsonPatients);
    }



    // ADD CHECK OF ERROR CODE
    public Bundle getResources(String patientId, Map<String, String> headers, String resourceType) {
        int offset = 0;
        boolean hasNext = true;
        Bundle tempBundle = new Bundle();
        String urlEnd = "";

        while (hasNext) {
            urlEnd = getUrlForResource(resourceType, patientId, offset);

            if (!urlEnd.isEmpty()) {
                Bundle responseBundle;
                responseBundle = executeGet(urlEnd, headers, true);

                if (responseBundle != null) {
                    for (Bundle.BundleEntryComponent entry : responseBundle.getEntry()) {
                        if (entry.getResource() instanceof MedicationStatement) {
                            MedicationStatement medStatement = (MedicationStatement) entry.getResource();
                            
                            if (!medStatement.hasSubject()) {
                                medStatement.setSubject(new Reference("Patient/" + patientId));
                            }
            
                            tempBundle.addEntry().setResource(medStatement);
                        } else {
                            tempBundle.addEntry().setResource(entry.getResource());
                        }
                    }
            
                    hasNext = checkForMoreResources(responseBundle);
                } else {
                    logger.info(urlEnd);
                    logger.error("Too many failed Http Requests");
                }
            } else {
                break;
            }
        }

        return tempBundle;
    }


    public Bundle executeGet(String urlEnd, Map<String, String> headers, boolean useProxy) {
        String url = fhirEndpoint + "/" + urlEnd;
        OkHttpClient client = useProxy ? proxyClient : defaultClient;
        int retries = 3;

        try {
            int delay = 60000;
            for (int i = 0; i<retries; i++) {
                logger.info("Executing: " + url + ", attempt " + (i+1));
                Request.Builder requestBuilder = new Request.Builder().url(url);

                if (headers != null) {
                    headers.forEach(requestBuilder::header);
                }
                Request request = requestBuilder.build();

                try (Response response = client.newCall(request).execute()) {
                    int statusCode = response.code();

                    switch (statusCode) {
                        case 200:
                            String responseBody = response.body().string();
                            return jsonParser.parseResource(Bundle.class, responseBody);
                        
                        case 401:
                            logger.info("Returned code 401");
                            logger.error("Unauthorized access to resources");
                            break;

                        case 403:
                            logger.info("Returned code 403");
                            String newAccessToken = tokenManager.getAccessToken();
                            headers.put("Authorization", "Bearer " + newAccessToken);
                            continue;

                        case 404:
                            logger.info("Returned code 404");
                            logger.info("URL: " + url);
                            logger.info("CLIENT_ID: " + System.getenv("CLIENT_ID"));
                            logger.error("Either wrong Client_id or wrong url");
                            break;

                        /*case 429:
                            ToDo;
                            break;*/

                        case 500:
                            logger.info("Returned code 500");
                            logger.info("URL called: " + url);
                            logger.error("Error on Heureka's side. Inform them");
                            break;

                        case 502:
                            logger.info("Returned code 502");
                            Thread.sleep(delay);
                            delay *= 2;
                            continue;

                        default:
                            logger.error("Unkown error code: " + statusCode);
                            return null;
                    }
                }
                
            }
            
                
        } catch (Exception e) {
            logger.error("Error while executing HTTP request: " + e.getMessage());
            return null;
        }

        return null;
    }




    public String parseFhirResources(String resource) {
        if (resource == null) {
            logger.error("Invalid resource format");
        }

        Bundle bundle = ctx.newJsonParser().parseResource(Bundle.class, resource);
        
        return jsonParser.encodeResourceToString(bundle);
    }


    private boolean checkForMoreResources(Bundle bundle) {
        // SHOULD BE 300 IN PRODUCTION ENV
        return bundle.getTotal() >= 299;
    }


    private boolean hasReadPermissions(String resource) {
        return grants.getOrDefault(resource, new ArrayList<>()).contains("READ");
    }


    private String getUrlForResource(String resourceType, String patientId, int offset) {
        if (hasReadPermissions(resourceType)) {
            switch (resourceType) {
                case "Condition":
                    return "Condition?patient=Patient/" + patientId + "&_count=300&_offset=" + offset;
                case "Observation":
                    return "Observation?patient=Patient/" + patientId + "&_count=300&_offset=" + offset;
                case "MedicationStatement":
                    return "MedicationStatement?subject=Patient/" + patientId + "&_count=300&_offset=" + offset;
                default:
                    throw new IllegalArgumentException("Invalid resource type: " + resourceType);
            }
        } else {
            logger.error("Resource type not recognized");
            return "";
        }
    }


    private String getGrantsName(String key) {
        switch (key) {
            case "PATIENT":
                return "Patient";
            
            case "CONDITION":
                return "Condition";

            case "OBSERVATION":
                return "Observation";

            case "MEDICATION_STATEMENT":
                return "MedicationStatement";

            default:
                return "";
        }
    }
}